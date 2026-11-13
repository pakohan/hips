"""Checks of the print codec. Run: .venv/bin/python test_smallcode.py"""

import numpy as np

from hips import smallcode
from hips.text import SAMPLE_TEXT

rng = np.random.default_rng(0)

# BCH(63,36): every pattern of up to five wrong bits is corrected, in the 63 bits or the parity bit.
for trial in range(400):
    data = rng.integers(0, 2, smallcode.K).tolist()
    word = smallcode.bch_encode(data)
    assert len(word) == 64
    wrong = rng.choice(64, int(rng.integers(0, smallcode.T + 1)), replace=False)
    received = [bit ^ (i in wrong) for i, bit in enumerate(word)]
    result = smallcode.bch_decode(received)
    assert result is not None and result[0] == data and result[1] == len(wrong), (trial, len(wrong))

# Six to nine wrong bits are never silently "corrected" back to the sent data, and mostly rejected.
rejected = 0
for trial in range(400):
    data = rng.integers(0, 2, smallcode.K).tolist()
    word = smallcode.bch_encode(data)
    wrong = rng.choice(64, int(rng.integers(6, 10)), replace=False)
    result = smallcode.bch_decode([bit ^ (i in wrong) for i, bit in enumerate(word)])
    assert result is None or result[0] != data
    rejected += result is None
assert rejected > 300, rejected

# Whole path on text: perfect scores, then with noise, a missing letter and a ruined line.
words = SAMPLE_TEXT.split()
lines = [list("".join(words[i : i + 12])) for i in range(0, len(words), 12)]
message = b"Hidden in plain sight"
variants = smallcode.embed(lines, message, rng)


def scores(variant: int, flip: bool) -> np.ndarray:
    index = smallcode.FOUR.index(variant) ^ (int(rng.integers(1, 4)) if flip else 0)
    out = np.full(4, 0.05)
    out[index] = 0.85
    return out


def observe(error_rate: float) -> list:
    return [
        [(c, scores(v, rng.random() < error_rate) if c in smallcode.SMALL_CARRIERS else None) for c, v in zip(line, row)]
        for line, row in zip(lines, variants)
    ]


clean = smallcode.read(observe(0.0))
assert clean.message == message and clean.decoded == clean.slots and clean.corrected_bits == 0
assert smallcode.read(observe(0.03)).message == message

damaged = observe(0.02)
del damaged[0][3]  # a letter lost by segmentation: the first line's packet is gone, the rest must hold
damaged[2] = [(c, None if s is None else np.full(4, 0.25) + rng.random(4)) for c, s in damaged[2]]
reading = smallcode.read(damaged)
assert reading.message == message, reading
print(f"small code ok: {len(smallcode.packets_of(message))} packets for {len(message)} bytes, "
      f"{clean.slots} packet slots in the sample text, {reading.decoded} decoded after damage")
