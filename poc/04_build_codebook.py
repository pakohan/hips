"""Step 4: pick, per letter, the variants the network can tell apart (paper section 4.4).

Two variants are connected when the network rarely takes one for the other. The largest clique of
that graph is the letter's variant list; variants that are still recognised too rarely within the
clique are then removed one by one.

Usage: 04_build_codebook.py [max_pair_confusion] [min_accuracy] [min_x_height]
"""

import json
import sys

import numpy as np

from hips import fonts, model, pipeline
from hips.text import CARRIERS

max_pair_confusion = float(sys.argv[1]) if len(sys.argv) > 1 else 0.03
min_accuracy = float(sys.argv[2]) if len(sys.argv) > 2 else 0.95
min_x_height = float(sys.argv[3]) if len(sys.argv) > 3 else 0.0
variants = fonts.variant_count()

test = np.load(fonts.BUILD_DIR / "dataset-test.npz")
keep = (test["labels"] < len(CARRIERS) * variants) & (test["x_heights"] >= min_x_height)
truth = test["labels"][keep].astype(int)
probabilities = model.predict(pipeline.load_model(variants), test["crops"][keep], test["features"][keep])
# The letter is taken as known here; the variant is the best one within that letter.
within = probabilities[:, : len(CARRIERS) * variants].reshape(len(truth), len(CARRIERS), variants)
predicted = within[np.arange(len(truth)), truth // variants].argmax(axis=1)
confusion = np.zeros((len(CARRIERS), variants, variants))
np.add.at(confusion, (truth // variants, truth % variants, predicted), 1)


def largest_clique(adjacent: np.ndarray) -> list[int]:
    best: list[int] = []

    def expand(clique: list[int], candidates: list[int]) -> None:
        nonlocal best
        if len(clique) > len(best):
            best = clique
        for index, node in enumerate(candidates):
            if len(clique) + len(candidates) - index <= len(best):
                return
            expand(clique + [node], [other for other in candidates[index + 1 :] if adjacent[node, other]])

    expand([], list(range(len(adjacent))))
    return best


letters: dict[str, list[int]] = {}
accuracies: dict[str, float] = {}
for letter, counts in zip(CARRIERS, confusion):
    rates = counts / np.maximum(counts.sum(axis=1, keepdims=True), 1)
    pair_confusion = (rates + rates.T) / 2
    chosen = largest_clique(pair_confusion <= max_pair_confusion)
    while len(chosen) > 1:
        sub = counts[np.ix_(chosen, chosen)]
        accuracy = np.diag(sub) / np.maximum(sub.sum(axis=1), 1)
        if accuracy.min() >= min_accuracy:
            break
        chosen.pop(int(accuracy.argmin()))
    sub = counts[np.ix_(chosen, chosen)]
    letters[letter] = chosen
    accuracies[letter] = float(np.trace(sub) / max(sub.sum(), 1))

pipeline.CODEBOOK_PATH.write_text(
    json.dumps(
        {
            "max_pair_confusion": max_pair_confusion,
            "min_accuracy": min_accuracy,
            "min_x_height": min_x_height,
            "letters": letters,
        },
        indent=1,
    )
)
print("letter: variants (accuracy within the list)")
for letter in CARRIERS:
    print(f"  {letter}: {len(letters[letter]):2d} ({accuracies[letter]:.3f})  {letters[letter]}")
print("mean variants per letter:", np.mean([len(v) for v in letters.values()]))
