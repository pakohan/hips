"""Character set, class indices and test texts."""

from __future__ import annotations

import string
from pathlib import Path

import numpy as np

# Letters that carry data. "l" is left out: in Roboto it is nearly the same bar as "I", and a
# letter that is mistaken for a non-carrier (or the reverse) shifts every block after it.
CARRIERS = "abcdefghijkmnopqrstuvwxyz"
OTHERS = "l" + string.ascii_uppercase + string.digits + ".,;:!?'-()"
CHARSET = CARRIERS + OTHERS


def class_count(variants: int) -> int:
    return len(CARRIERS) * variants + len(OTHERS)


def class_of(char: str, variant: int, variants: int) -> int:
    carrier = CARRIERS.find(char)
    if carrier >= 0:
        return carrier * variants + variant
    return len(CARRIERS) * variants + OTHERS.index(char)


def char_of_class(index: int, variants: int) -> tuple[str, int]:
    if index < len(CARRIERS) * variants:
        return CARRIERS[index // variants], index % variants
    return OTHERS[index - len(CARRIERS) * variants], 0


def _dictionary() -> list[str]:
    path = Path("/usr/share/dict/words")
    if not path.exists():
        return []
    return [w for w in path.read_text().split() if 2 <= len(w) <= 10 and w.isascii() and w.isalpha()]


_WORDS = _dictionary()


def random_text(rng: np.random.Generator, words: int) -> str:
    """Half dictionary words, half uniform letter strings, so rare letters get enough samples."""
    out = []
    for _ in range(words):
        kind = rng.random()
        if kind < 0.45 and _WORDS:
            word = _WORDS[int(rng.integers(len(_WORDS)))]
        elif kind < 0.9:
            word = "".join(rng.choice(list(CARRIERS + "l"), size=int(rng.integers(2, 9))))
        else:
            word = "".join(rng.choice(list(string.ascii_uppercase + string.digits), size=int(rng.integers(1, 5))))
        if rng.random() < 0.08:
            word = word.capitalize()
        if rng.random() < 0.12:
            word += str(rng.choice(list(".,;:!?")))
        elif rng.random() < 0.03:
            word = "(" + word + ")"
        elif rng.random() < 0.03:
            word = word + "'s" if rng.random() < 0.5 else word[:1] + "-" + word[1:]
        out.append(word)
    return " ".join(out)


SAMPLE_TEXT = (
    "The harbour at the end of the valley was quiet when the first boats came back that morning. "
    "Nobody on the quay expected them before noon, and the fog had not yet lifted from the water, "
    "so the lamps along the pier were still burning. A dog ran ahead of the fishermen, barking at "
    "the gulls, while the children counted the crates as they were carried up the slope. Every box "
    "was weighed twice, once by the buyer and once by the crew, because trust is cheap in fine "
    "weather and expensive in a storm. By the time the sun broke through, the market was full of "
    "voices, and the price of mackerel had dropped by a third. Old Jakob, who kept the books for "
    "the cooperative, wrote down each sale in a narrow hand and never looked up from the page. He "
    "had seen forty winters on this coast; he knew which captains would pay their debts and which "
    "would vanish before the next tide. Up on the hill, the baker opened his shutters, the smell "
    "of fresh bread drifted down to the quay, and for a moment even the buyers stopped arguing. "
    "It was an ordinary day, exactly like the ones before it, and nobody noticed anything unusual "
    "about the notice pinned to the harbour office door."
)
