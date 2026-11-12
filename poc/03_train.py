"""Step 3: train the glyph network on all candidate variants and measure what it confuses.

Usage: 03_train.py [epochs]
Reads build/dataset-train.npz and build/dataset-test.npz, writes build/glyphnet.pt and
build/confusion.npz (per letter: variants x variants counts, rows = truth).
"""

import sys

import numpy as np
import torch

from hips import fonts, model, pipeline
from hips.text import CARRIERS, CHARSET, char_of_class, class_count

epochs = int(sys.argv[1]) if len(sys.argv) > 1 else 12
variants = fonts.variant_count()
train = np.load(fonts.BUILD_DIR / "dataset-train.npz")
test = np.load(fonts.BUILD_DIR / "dataset-test.npz")
print(f"{len(train['labels'])} training crops, {len(test['labels'])} test crops, {class_count(variants)} classes")

torch.manual_seed(0)
net = model.GlyphNet(class_count(variants))
model.train(net, train["crops"], train["features"], train["labels"], epochs)
torch.save(net.state_dict(), pipeline.MODEL_PATH)

probabilities = model.predict(net, test["crops"], test["features"])
predicted = probabilities.argmax(axis=1)
truth = test["labels"].astype(int)
true_chars = np.array([CHARSET.index(char_of_class(i, variants)[0]) for i in truth])
predicted_chars = np.array([CHARSET.index(char_of_class(i, variants)[0]) for i in predicted])
print(f"character identity accuracy: {np.mean(true_chars == predicted_chars):.5f}")
wrong = np.flatnonzero(true_chars != predicted_chars)
pairs: dict[str, int] = {}
for i in wrong:
    key = f"{CHARSET[true_chars[i]]}->{CHARSET[predicted_chars[i]]}"
    pairs[key] = pairs.get(key, 0) + 1
print("character confusions:", sorted(pairs.items(), key=lambda kv: -kv[1])[:15])

is_carrier = truth < len(CARRIERS) * variants
same_char = is_carrier & (true_chars == predicted_chars)
print(f"variant accuracy over all {variants} candidates: {np.mean(predicted[same_char] == truth[same_char]):.4f}")
x_heights = test["x_heights"]
for low in range(16, 44, 4):
    band = same_char & (x_heights >= low) & (x_heights < low + 4)
    print(f"  x-height {low}-{low + 4}px: {np.mean(predicted[band] == truth[band]):.4f} ({band.sum()} crops)")

confusion = np.zeros((len(CARRIERS), variants, variants), dtype=np.int64)
np.add.at(confusion, (truth[same_char] // variants, truth[same_char] % variants, predicted[same_char] % variants), 1)
np.savez(fonts.BUILD_DIR / "confusion.npz", confusion=confusion)
for letter, matrix in zip(CARRIERS, confusion):
    print(f"  {letter}: {np.trace(matrix) / max(matrix.sum(), 1):.3f}", end="")
print()
