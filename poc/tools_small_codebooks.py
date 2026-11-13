"""Diagnostic: how well would a small codebook of far-apart variants be read on real photos?

Takes the network trained on real glyphs (glyphnet-real.pt, step 9) and the held-out pages, keeps
only glyphs whose true variant belongs to a candidate codebook and lets the network choose among
that codebook's variants only.

Usage: tools_small_codebooks.py <held-out page>[,<held-out page>...]
Environment: HIPS_BUILD.
"""

import sys

import numpy as np

from hips import fonts, model, pipeline
from hips.text import CARRIERS

held_out = set(sys.argv[1].split(","))
variants = fonts.variant_count()
levels = np.array([[(0, -1, 1)[(v // 3 ** (2 - a)) % 3] for a in range(3)] for v in range(variants)])


def pick(rule) -> list[int]:
    return [v for v in range(variants) if rule(*levels[v])]


CODEBOOKS = {
    "YOPQ only, 3 levels": pick(lambda x, y, t: x == 0 and t == 0),
    "XOPQ only, 3 levels": pick(lambda x, y, t: y == 0 and t == 0),
    "weight (XOPQ = YOPQ), 3 levels": pick(lambda x, y, t: x == y and t == 0),
    "weight, thin and bold only": pick(lambda x, y, t: x == y and x != 0 and t == 0),
    "XOPQ x YOPQ corners, 4": pick(lambda x, y, t: x != 0 and y != 0 and t == 0),
    "XOPQ x YOPQ, 9": pick(lambda x, y, t: t == 0),
    "all 27": list(range(variants)),
}

parts = {"crops": [], "features": [], "labels": [], "pages": []}
for path in sorted(fonts.BUILD_DIR.glob("dataset-real*.npz")):
    data = np.load(path)
    keep = (data["labels"] < len(CARRIERS) * variants) & np.isin(data["document_names"][data["documents"]], list(held_out))
    for key in ("crops", "features", "labels"):
        parts[key].append(data[key][keep])
crops, features, labels = (np.concatenate(parts[key]) for key in ("crops", "features", "labels"))
truth = labels.astype(int)
net = pipeline.load_model(variants, fonts.BUILD_DIR / "glyphnet-real.pt")
probabilities = model.predict(net, crops, features)
within = probabilities[:, : len(CARRIERS) * variants].reshape(len(truth), len(CARRIERS), variants)
own = within[np.arange(len(truth)), truth // variants]  # probabilities over the variants of the true letter

print(f"{len(truth)} held-out carrier glyphs from {sorted(held_out)}")
print("  codebook: right as read / right after balancing (glyphs, bits per letter before coding)")
for title, codebook in CODEBOOKS.items():
    chosen = np.isin(truth % variants, codebook)
    scores = own[chosen][:, codebook]
    # Balancing: a message uses all variants about equally often, so a variant the network favours
    # across the whole page is favoured because of the printer or the camera, not the glyphs.
    # Dividing by the page average removes that shift without knowing anything about the device.
    balanced = scores / scores.mean(axis=0, keepdims=True)
    right = [np.mean(np.array(codebook)[s.argmax(axis=1)] == truth[chosen] % variants) for s in (scores, balanced)]
    print(f"  {title}: {right[0]:.3f} / {right[1]:.3f} ({chosen.sum()} glyphs, {np.log2(len(codebook)):.2f} bits)")
