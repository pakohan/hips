"""Checks of the Chinese Remainder coding. Run: .venv/bin/python test_coding.py"""

import math

import numpy as np

from hips import coding

rng = np.random.default_rng(0)


def one_hot(residue: int, size: int, confidence: float = 0.9) -> np.ndarray:
    p = np.full(size, (1 - confidence) / (size - 1))
    p[residue] = confidence
    return p


# Moduli are pairwise coprime, within capacity, and the search is deterministic.
moduli = coding.choose_moduli((11, 9, 8, 7, 13))
assert moduli is not None and all(m <= c for m, c in zip(moduli, (11, 9, 8, 7, 13)))
assert all(math.gcd(a, b) == 1 for i, a in enumerate(moduli) for b in moduli[i + 1 :])
assert coding.choose_moduli((2, 2, 2, 2, 2)) is None

# A window without coprime moduli drops its weakest letter and takes the next one.
blocks = coding.make_blocks([2, 2, 9, 8, 7, 5, 11, 3])
assert len(blocks) == 1 and 0 not in blocks[0].letters

# Frame round trip, and a damaged frame is rejected.
bits = coding.frame(b"hidden")
assert coding.unframe(bits) == b"hidden"
bits[20] ^= 1
assert coding.unframe(bits) is None

# Round trip through blocks, with one and with two wrong letters per block.
capacities = rng.integers(7, 20, 400).tolist()
blocks = coding.make_blocks(capacities)
message = b"Hidden in plain sight"
residues = coding.encode(coding.frame(message), blocks, rng)


def decode_with_errors(errors: int, soft: bool) -> bytes | None:
    probabilities = {}
    for block in blocks:
        wrong = set(rng.choice(len(block.letters), errors, replace=False).tolist())
        for j, (letter, modulus) in enumerate(zip(block.letters, block.moduli)):
            if j in wrong:
                # The wrong variant wins narrowly; the true one stays a close second.
                p = np.full(modulus, 0.02 / (modulus - 2))
                p[residues[letter]] = 0.45
                p[(residues[letter] + 1) % modulus] = 0.53
            else:
                p = one_hot(residues[letter], modulus)
            probabilities[letter] = p
    return coding.unframe(coding.decode(blocks, probabilities, soft)[0])


assert decode_with_errors(0, False) == message
assert decode_with_errors(1, False) == message
assert decode_with_errors(1, True) == message
assert decode_with_errors(2, True) == message
print("coding ok:", len(blocks), "blocks,", coding.capacity_bits(blocks), "bits for", len(capacities), "letters")
