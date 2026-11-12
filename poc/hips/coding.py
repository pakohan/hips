"""Chinese Remainder Codes with maximum-likelihood decoding (paper section 5).

Letters are grouped into blocks of N letters. A block carries one integer m < 2^bits, stored as
the residues m mod p_i, one residue per letter, where the p_i are pairwise coprime and p_i does
not exceed the number of variants the letter has in the codebook.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from functools import lru_cache

import numpy as np

N = 5  # letters per block
K = 3  # residues that determine the integer; the other N - K are redundancy

LENGTH_BITS = 8
CRC_BITS = 16


@dataclass(frozen=True)
class Block:
    letters: tuple[int, ...]  # indices into the carrier-letter sequence
    moduli: tuple[int, ...]
    bits: int


@lru_cache(maxsize=None)
def choose_moduli(capacities: tuple[int, ...], k: int = K) -> tuple[int, ...] | None:
    """Pairwise coprime p_i <= capacities[i], maximising the product of the k smallest."""
    best: tuple[int, tuple[int, ...]] | None = None
    chosen: list[int] = []

    def search(position: int) -> None:
        nonlocal best
        if position == len(capacities):
            value = math.prod(sorted(chosen)[:k])
            if best is None or value > best[0]:
                best = (value, tuple(chosen))
            return
        for p in range(capacities[position], 1, -1):
            if all(math.gcd(p, q) == 1 for q in chosen):
                chosen.append(p)
                search(position + 1)
                chosen.pop()

    search(0)
    return None if best is None else best[1]


def make_blocks(capacities: list[int], n: int = N, k: int = K) -> list[Block]:
    """Splits the carrier letters into blocks. Encoder and decoder must call this identically.

    When a window has no pairwise coprime moduli, its lowest-capacity letter is skipped and the
    next letter joins the window (paper section 5.2.2).
    """
    blocks: list[Block] = []
    pending = [i for i, capacity in enumerate(capacities) if capacity >= 2]
    window: list[int] = []
    cursor = 0
    while True:
        while len(window) < n and cursor < len(pending):
            window.append(pending[cursor])
            cursor += 1
        if len(window) < n:
            return blocks
        moduli = choose_moduli(tuple(capacities[i] for i in window), k)
        if moduli is None:
            window.remove(min(window, key=lambda i: capacities[i]))
            continue
        bits = int(math.log2(math.prod(sorted(moduli)[:k])))
        blocks.append(Block(tuple(window), moduli, bits))
        window = []


def crc16(data: bytes) -> int:
    crc = 0xFFFF
    for byte in data:
        crc ^= byte << 8
        for _ in range(8):
            crc = ((crc << 1) ^ 0x1021) & 0xFFFF if crc & 0x8000 else (crc << 1) & 0xFFFF
    return crc


def frame(message: bytes) -> list[int]:
    """length (1 byte) + message + CRC-16, as bits, most significant first."""
    if len(message) >= 1 << LENGTH_BITS:
        raise ValueError("message too long")
    body = bytes([len(message)]) + message
    body += crc16(body).to_bytes(2, "big")
    return [(byte >> shift) & 1 for byte in body for shift in range(7, -1, -1)]


def unframe(bits: list[int]) -> bytes | None:
    """The message, or None when the bits do not hold a frame with a matching CRC."""
    def read(offset: int, count: int) -> bytes | None:
        if offset + 8 * count > len(bits):
            return None
        chunk = bits[offset : offset + 8 * count]
        return bytes(int("".join(map(str, chunk[i : i + 8])), 2) for i in range(0, len(chunk), 8))

    header = read(0, 1)
    if header is None:
        return None
    body = read(0, 1 + header[0] + 2)
    if body is None or crc16(body[:-2]) != int.from_bytes(body[-2:], "big"):
        return None
    return body[1:-2]


def capacity_bits(blocks: list[Block]) -> int:
    return sum(block.bits for block in blocks)


def encode(bits: list[int], blocks: list[Block], rng: np.random.Generator) -> dict[int, int]:
    """Residue per carrier-letter index. Blocks after the message carry random bits."""
    if len(bits) > capacity_bits(blocks):
        raise ValueError(f"message needs {len(bits)} bits, the text carries {capacity_bits(blocks)}")
    stream = list(bits) + rng.integers(0, 2, capacity_bits(blocks) - len(bits)).tolist()
    residues: dict[int, int] = {}
    offset = 0
    for block in blocks:
        m = int("".join(map(str, stream[offset : offset + block.bits])), 2)
        offset += block.bits
        for letter, modulus in zip(block.letters, block.moduli):
            residues[letter] = m % modulus
    return residues


def decode_block(block: Block, probabilities: list[np.ndarray], soft: bool = False) -> tuple[int, int]:
    """Decodes one block from per-letter probability vectors over [0, p_i).

    Returns (m, hamming distance of the chosen codeword to the hard decisions).
    Default is the paper's rule: minimum Hamming distance, ties broken by the likelihood of the
    mismatching letters. `soft` maximises the likelihood over all letters instead.
    """
    candidates = np.arange(1 << block.bits)
    hamming = np.zeros(len(candidates), dtype=np.int64)
    mismatch_loglik = np.zeros(len(candidates))
    total_loglik = np.zeros(len(candidates))
    for j, modulus in enumerate(block.moduli):
        residues = candidates % modulus
        # Residues >= p_i are never embedded, so those variants are ruled out up front.
        allowed = probabilities[j][:modulus]
        loglik = np.log(np.maximum(allowed / max(float(allowed.sum()), 1e-9), 1e-9))[residues]
        differs = residues != int(np.argmax(allowed))
        hamming += differs
        mismatch_loglik += np.where(differs, loglik, 0.0)
        total_loglik += loglik
    if soft:
        best = int(np.argmax(total_loglik))
    else:
        best = int(np.lexsort((-mismatch_loglik, hamming))[0])
    return best, int(hamming[best])


def decode(blocks: list[Block], probabilities: dict[int, np.ndarray], soft: bool = False) -> tuple[list[int], list[int]]:
    """Bits of all blocks and the Hamming distance each block was corrected by."""
    bits: list[int] = []
    distances: list[int] = []
    for block in blocks:
        m, distance = decode_block(block, [probabilities[letter] for letter in block.letters], soft)
        bits.extend((m >> shift) & 1 for shift in range(block.bits - 1, -1, -1))
        distances.append(distance)
    return bits, distances
