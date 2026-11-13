"""Exports test vectors of the print codec for the Kotlin implementation (ADR 0005).

Usage: tools_export_vectors.py
Writes plain text files to app/src/test/resources/codec/ (no JSON: JVM unit tests have no parser):
  bch.txt      per line: 36 data bits, 64-bit word, the word with up to five bits flipped
  packets.txt  per line: message as hex, then its packets as 64-bit strings
  page.txt     first line the message as hex, then per text line: the characters, a tab, and per
               character "." (base font), "0".."3" (variant index, part of a packet) or "a".."d"
               (variant index 0..3, random padding)
"""

import numpy as np

from hips import fonts, smallcode
from hips.text import SAMPLE_TEXT

directory = fonts.POC_DIR.parent / "app" / "src" / "test" / "resources" / "codec"
directory.mkdir(parents=True, exist_ok=True)
rng = np.random.default_rng(2026)


def bits(values: list[int]) -> str:
    return "".join(map(str, values))


rows = []
for _ in range(50):
    data = rng.integers(0, 2, smallcode.K).tolist()
    word = smallcode.bch_encode(data)
    wrong = rng.choice(64, int(rng.integers(0, smallcode.T + 1)), replace=False)
    rows.append(f"{bits(data)} {bits(word)} {bits([bit ^ int(i in wrong) for i, bit in enumerate(word)])}")
(directory / "bch.txt").write_text("\n".join(rows) + "\n")

messages = [b"", b"a", b"https://example.org/hips", bytes(range(256))[:105], "Grüße, 世界".encode()]
(directory / "packets.txt").write_text(
    "\n".join(f"{m.hex() or '-'} " + " ".join(bits(p) for p in smallcode.packets_of(m)) for m in messages) + "\n"
)

message = b"https://example.org/hips"
words = SAMPLE_TEXT.split()
lines = ["".join(words[i : i + 12]) for i in range(0, len(words), 12)]
variants = smallcode.embed([list(line) for line in lines], message, rng)
rows = [message.hex()]
for line, row in zip(lines, variants):
    carriers = [i for i, char in enumerate(line) if char in smallcode.SMALL_CARRIERS]
    in_packet = set(carriers[: len(carriers) // smallcode.LETTERS_PER_PACKET * smallcode.LETTERS_PER_PACKET])
    marks = ""
    for index, variant in enumerate(row):
        if variant == 0:
            marks += "."
        else:
            four = smallcode.FOUR.index(variant)
            marks += str(four) if index in in_packet else "abcd"[four]
    rows.append(f"{line}\t{marks}")
(directory / "page.txt").write_text("\n".join(rows) + "\n")
print(f"wrote bch.txt, packets.txt, page.txt to {directory}")
