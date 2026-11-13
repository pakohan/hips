"""Experiment: one small output per axis (3 levels each) instead of one class per (letter, variant).

With 675 variant classes a few thousand real glyphs are a handful per class. The three axes are
shared by all letters, so every real glyph teaches each axis. This script swaps the last layer of the
simulated-data network for three 3-way outputs, trains on real plus simulated glyphs and reports
recognition on held-out pages, to compare with 09_train_real.py.

Usage: tools_axis_heads.py <held-out page>[,<held-out page>...] [epochs]
Environment: HIPS_BUILD.
"""

import sys

import numpy as np
import torch
from torch import nn

from hips import fonts, model, pipeline
from hips.text import CARRIERS

held_out = set(sys.argv[1].split(","))
epochs = int(sys.argv[2]) if len(sys.argv) > 2 else 30
variants = fonts.variant_count()
levels = np.array([[(0, -1, 1)[(v // 3 ** (2 - a)) % 3] + 1 for a in range(3)] for v in range(variants)])  # 0, 1, 2

parts = {"crops": [], "features": [], "labels": [], "pages": []}
for path in sorted(fonts.BUILD_DIR.glob("dataset-real*.npz")):
    data = np.load(path)
    carrier = data["labels"] < len(CARRIERS) * variants
    parts["crops"].append(data["crops"][carrier])
    parts["features"].append(data["features"][carrier])
    parts["labels"].append(data["labels"][carrier])
    parts["pages"].append(data["document_names"][data["documents"]][carrier])
crops, features, labels, pages = (np.concatenate(parts[key]) for key in ("crops", "features", "labels", "pages"))
is_test = np.isin(pages, list(held_out))

simulated = np.load(fonts.BUILD_DIR / "dataset-train.npz")
rng = np.random.default_rng(0)
candidates = np.flatnonzero(simulated["labels"] < len(CARRIERS) * variants)
sample = np.sort(rng.choice(candidates, min(int((~is_test).sum()), len(candidates)), replace=False))
train_crops = np.concatenate([crops[~is_test], simulated["crops"][sample]])
train_features = np.concatenate([features[~is_test], simulated["features"][sample]])
train_levels = levels[np.concatenate([labels[~is_test], simulated["labels"][sample]]).astype(int) % variants]

net = pipeline.load_model(variants)
where = model.device()
net.head[-1] = nn.Linear(256, 9).to(where)
optimiser = torch.optim.AdamW(net.parameters(), lr=5e-4, weight_decay=1e-4)
steps = epochs * (len(train_crops) // 128)
schedule = torch.optim.lr_scheduler.OneCycleLR(optimiser, max_lr=5e-4, total_steps=steps)
loss_fn = nn.CrossEntropyLoss()
targets = torch.from_numpy(train_levels)
torch.manual_seed(0)
for _ in range(epochs):
    net.train()
    order = torch.randperm(len(train_crops))
    for start in range(0, len(order) - 127, 128):
        index = order[start : start + 128].numpy()
        x, f = model.to_tensors(train_crops[index], train_features[index], where)
        logits = net(x, f).view(-1, 3, 3)
        loss = sum(loss_fn(logits[:, axis], targets[index, axis].to(where)) for axis in range(3))
        optimiser.zero_grad()
        loss.backward()
        optimiser.step()
        schedule.step()

net.eval()
print(f"trained on {(~is_test).sum()} real + {len(sample)} simulated carrier glyphs")
for page in sorted(set(pages[is_test])):
    chosen = pages == page
    with torch.no_grad():
        x, f = model.to_tensors(crops[chosen], features[chosen], where)
        predicted = net(x, f).view(-1, 3, 3).argmax(dim=2).cpu().numpy()
    truth = levels[labels[chosen].astype(int) % variants]
    right = predicted == truth
    print(f"  {page}: {chosen.sum()} carrier glyphs, all three levels right {right.all(axis=1).mean():.3f}; "
          f"XOPQ {right[:, 0].mean():.3f} YOPQ {right[:, 1].mean():.3f} XTRA {right[:, 2].mean():.3f}; "
          f"XOPQ and YOPQ both right {right[:, :2].all(axis=1).mean():.3f}")
