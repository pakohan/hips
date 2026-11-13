"""Step 9: continue training on real photos of printed sheets and test on pages held out entirely.

Usage: 09_train_real.py <held-out page>[,<held-out page>...] [epochs]
  e.g. 09_train_real.py sheet-03.pdf,encoded-long.pdf
Environment: HIPS_BUILD.
Reads glyphnet.pt (trained on simulated photos, step 3), every dataset-real*.npz (step 8) and
dataset-train.npz. Trains on the real glyphs of all other pages mixed with as many simulated ones,
writes glyphnet-real.pt, and reports recognition on the held-out pages before and after.
"""

import sys

import numpy as np
import torch

from hips import fonts, model, pipeline
from hips.text import CARRIERS

held_out = set(sys.argv[1].split(","))
epochs = int(sys.argv[2]) if len(sys.argv) > 2 else 30
variants = fonts.variant_count()
levels = np.array([[(0, -1, 1)[(v // 3 ** (2 - a)) % 3] for a in range(3)] for v in range(variants)])

parts = {"crops": [], "features": [], "labels": [], "pages": []}
for path in sorted(fonts.BUILD_DIR.glob("dataset-real*.npz")):
    data = np.load(path)
    parts["crops"].append(data["crops"])
    parts["features"].append(data["features"])
    parts["labels"].append(data["labels"])
    parts["pages"].append(data["document_names"][data["documents"]])
crops, features, labels, pages = (np.concatenate(parts[key]) for key in ("crops", "features", "labels", "pages"))
is_test = np.isin(pages, list(held_out))
print(f"{(~is_test).sum()} real glyphs to train on ({sorted(set(pages[~is_test]))}), "
      f"{is_test.sum()} held out ({sorted(set(pages[is_test]))})")


def evaluate(net: model.GlyphNet, title: str) -> None:
    for page in sorted(set(pages[is_test])):
        chosen = (pages == page) & (labels < len(CARRIERS) * variants)
        truth = labels[chosen].astype(int)
        probabilities = model.predict(net, crops[chosen], features[chosen])
        within = probabilities[:, : len(CARRIERS) * variants].reshape(len(truth), len(CARRIERS), variants)
        predicted = within[np.arange(len(truth)), truth // variants].argmax(axis=1)
        axes = (levels[predicted] == levels[truth % variants]).mean(axis=0)
        print(f"  {title}, {page}: {len(truth)} carrier glyphs, exact variant (of {variants}) "
              f"{np.mean(predicted == truth % variants):.3f}; level right XOPQ {axes[0]:.3f} YOPQ {axes[1]:.3f} "
              f"XTRA {axes[2]:.3f}")


net = pipeline.load_model(variants)
evaluate(net, "before")

simulated = np.load(fonts.BUILD_DIR / "dataset-train.npz")
rng = np.random.default_rng(0)
real = np.flatnonzero(~is_test)
sample = np.sort(rng.choice(len(simulated["labels"]), min(len(real), len(simulated["labels"])), replace=False))
train_crops = np.concatenate([crops[real], simulated["crops"][sample]])
train_features = np.concatenate([features[real], simulated["features"][sample]])
train_labels = np.concatenate([labels[real], simulated["labels"][sample]])
torch.manual_seed(0)
model.train(net, train_crops, train_features, train_labels, epochs, batch=128, lr=5e-4, log=lambda _: None)
torch.save(net.state_dict(), fonts.BUILD_DIR / "glyphnet-real.pt")
evaluate(net, "after")
