"""The print codec (ADR 0006): four far-apart variants per letter, one self-contained packet per line.

A carrier letter carries two bits, one per stroke axis: vertical strokes thin or thick (XOPQ) and
horizontal strokes thin or thick (YOPQ). 32 carrier letters hold one 64-bit packet: an extended
BCH(63,36) codeword that corrects five wrong bits. Its 36 data bits are a 5-bit sequence number,
27 payload bits and a 4-bit check. A packet never crosses a line, and nothing in a packet depends on
any other line, so a symbol that segmentation merges or splits costs the packets of that line only.
The message frame (ADR 0004) is cut into packets and repeated down the page.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from . import coding
from .text import CARRIERS

# "i" has no horizontal stroke, so its YOPQ bit cannot be read (54% to 61% on real prints).
SMALL_CARRIERS = CARRIERS.replace("i", "")
# Candidate indices (fonts.candidate_locations) of (XOPQ, YOPQ) = (-,-), (-,+), (+,-), (+,+), XTRA unchanged.
FOUR = (12, 15, 21, 24)
LETTERS_PER_PACKET = 32
N, K, T = 63, 36, 5
SEQUENCE_BITS, PAYLOAD_BITS, CHECK_BITS = 5, 27, 4

# --- GF(64) and the BCH(63,36) code -------------------------------------------------------------

_EXP = [1] * 126
for _i in range(1, 126):
    _v = _EXP[_i - 1] << 1
    _EXP[_i] = _v ^ 0b1000011 if _v & 64 else _v  # primitive polynomial x^6 + x + 1
_LOG = {value: power for power, value in enumerate(_EXP[:63])}


def _mul(a: int, b: int) -> int:
    return 0 if a == 0 or b == 0 else _EXP[_LOG[a] + _LOG[b]]


def _generator() -> list[int]:
    """Product of the minimal polynomials of alpha^1 .. alpha^(2T); coefficients, lowest power first."""
    roots: set[int] = set()
    for power in range(1, 2 * T + 1):
        conjugate = power
        while conjugate not in roots:
            roots.add(conjugate)
            conjugate = conjugate * 2 % 63
    poly = [1]
    for root in sorted(roots):
        shifted = [0] + poly
        scaled = [_mul(c, _EXP[root]) for c in poly] + [0]
        poly = [a ^ b for a, b in zip(shifted, scaled)]
    assert all(c in (0, 1) for c in poly) and len(poly) == N - K + 1
    return poly


_GENERATOR = _generator()


def bch_encode(data: list[int]) -> list[int]:
    """63-bit systematic codeword (parity in the low powers) plus one overall parity bit."""
    remainder = [0] * (N - K) + list(data)
    for power in range(N - 1, N - K - 1, -1):
        if remainder[power]:
            for offset, coefficient in enumerate(_GENERATOR):
                remainder[power - (N - K) + offset] ^= coefficient
    word = remainder[: N - K] + list(data)
    return word + [sum(word) % 2]


def bch_decode(received: list[int]) -> tuple[list[int], int] | None:
    """(data bits, number of corrected bits), or None when more than T bits are wrong."""
    word = list(received[:N])
    syndromes = [0] * (2 * T)
    for position, bit in enumerate(word):
        if bit:
            for j in range(2 * T):
                syndromes[j] ^= _EXP[position * (j + 1) % 63]
    errors = 0
    if any(syndromes):
        # Berlekamp-Massey: the shortest recurrence that generates the syndromes.
        locator, previous, length, shift, last = [1], [1], 0, 1, 1
        for step in range(2 * T):
            discrepancy = syndromes[step]
            for i in range(1, length + 1):
                if i < len(locator):
                    discrepancy ^= _mul(locator[i], syndromes[step - i])
            if discrepancy == 0:
                shift += 1
                continue
            scale = _mul(discrepancy, _EXP[(63 - _LOG[last]) % 63])
            update = [0] * shift + [_mul(scale, c) for c in previous]
            candidate = [a ^ b for a, b in zip(locator + [0] * len(update), update + [0] * len(locator))]
            candidate = candidate[: max(len(locator), len(update))]
            if 2 * length <= step:
                previous, last, length, shift = locator, discrepancy, step + 1 - length, 1
            else:
                shift += 1
            locator = candidate
        while locator and locator[-1] == 0:
            locator.pop()
        degree = len(locator) - 1
        positions = []
        for position in range(N):  # Chien search: alpha^(-position) is a root where a bit is wrong
            value = 0
            for power, coefficient in enumerate(locator):
                value ^= _mul(coefficient, _EXP[(63 - position) * power % 63])
            if value == 0:
                positions.append(position)
        if degree > T or len(positions) != degree:
            return None
        for position in positions:
            word[position] ^= 1
        errors = degree
    if sum(word) % 2 != received[N]:
        errors += 1  # either the parity bit itself or an odd number of undetected wrong bits
        if errors > T:
            return None
    return word[N - K :], errors


# --- packets ---------------------------------------------------------------------------------------


def _check(bits: list[int]) -> list[int]:
    """4-bit CRC (x^4 + x + 1): catches most packets that BCH 'corrects' out of garbage."""
    register = 0
    for bit in bits:
        register = (register << 1) | bit
        if register & 16:
            register ^= 0b10011
    for _ in range(CHECK_BITS):
        register <<= 1
        if register & 16:
            register ^= 0b10011
    return [(register >> shift) & 1 for shift in range(CHECK_BITS - 1, -1, -1)]


def packets_of(message: bytes) -> list[list[int]]:
    """The framed message as 64-bit packets, in sequence order."""
    bits = coding.frame(message)
    bits += [0] * (-len(bits) % PAYLOAD_BITS)
    count = len(bits) // PAYLOAD_BITS
    if count > 1 << SEQUENCE_BITS:
        raise ValueError(f"message needs {count} packets, the format allows {1 << SEQUENCE_BITS}")
    packets = []
    for sequence in range(count):
        head = [(sequence >> shift) & 1 for shift in range(SEQUENCE_BITS - 1, -1, -1)]
        body = head + bits[sequence * PAYLOAD_BITS : (sequence + 1) * PAYLOAD_BITS]
        packets.append(bch_encode(body + _check(body)))
    return packets


def embed(lines: list[list[str]], message: bytes, rng: np.random.Generator) -> list[list[int]]:
    """Variant per character, line by line. Letters outside a packet get a random one of the four."""
    packets = packets_of(message)
    variants: list[list[int]] = []
    next_packet = 0
    for line in lines:
        carriers = [i for i, char in enumerate(line) if char in SMALL_CARRIERS]
        chosen = [0] * len(line)
        for index in carriers:
            chosen[index] = FOUR[int(rng.integers(4))]
        for start in range(0, len(carriers) - LETTERS_PER_PACKET + 1, LETTERS_PER_PACKET):
            packet = packets[next_packet % len(packets)]
            next_packet += 1
            for j in range(LETTERS_PER_PACKET):
                chosen[carriers[start + j]] = FOUR[2 * packet[2 * j] + packet[2 * j + 1]]
        variants.append(chosen)
    if next_packet < len(packets):
        raise ValueError(f"message needs {len(packets)} packets, the text has room for {next_packet}")
    return variants


@dataclass
class Reading:
    message: bytes | None
    slots: int  # packets the text has room for, as the reader sees the lines
    decoded: int  # packets that passed BCH and the check
    corrected_bits: int  # bits BCH corrected in those packets
    sequences: list[int]  # sequence number of every decoded packet, in reading order


def read(lines: list[list[tuple[str, np.ndarray | None]]]) -> Reading:
    """`lines`: per character its name and, for carrier letters, scores over the four variants."""
    candidates: dict[int, list[list[int]]] = {}
    slots = decoded = corrected = 0
    sequences = []
    for line in lines:
        carriers = [scores for char, scores in line if char in SMALL_CARRIERS and scores is not None]
        for start in range(0, len(carriers) - LETTERS_PER_PACKET + 1, LETTERS_PER_PACKET):
            slots += 1
            received = []
            for scores in carriers[start : start + LETTERS_PER_PACKET]:
                received += [int(scores[2] + scores[3] > scores[0] + scores[1]), int(scores[1] + scores[3] > scores[0] + scores[2])]
            result = bch_decode(received)
            if result is None:
                continue
            data, errors = result
            if _check(data[: SEQUENCE_BITS + PAYLOAD_BITS]) != data[SEQUENCE_BITS + PAYLOAD_BITS :]:
                continue
            sequence = int("".join(map(str, data[:SEQUENCE_BITS])), 2)
            candidates.setdefault(sequence, []).append(data[SEQUENCE_BITS : SEQUENCE_BITS + PAYLOAD_BITS])
            decoded += 1
            corrected += errors
            sequences.append(sequence)

    def majority(payloads: list[list[int]]) -> list[int]:
        return max(payloads, key=lambda p: sum(p == other for other in payloads))

    message = None
    if 0 in candidates:
        bits: list[int] = []
        sequence = 0
        while sequence in candidates:
            bits += majority(candidates[sequence])
            sequence += 1
        message = coding.unframe(bits)
    return Reading(message, slots, decoded, corrected, sequences)
