"""Diagnostic: does a real print carry the variant information at all, or only the simulation?

Takes real photos of a known document, fine-tunes the network on the glyphs of the upper lines and
tests on the lower lines, which it has never seen in any photo. If accuracy on the held-out lines
rises well above the untuned network, the print keeps the information and the simulation is what is
wrong; if it stays low, the printer destroyed it.

Usage: HIPS_TRUTH=build/encoded-long.pdf tools_real_finetune.py <image> [more images]
"""

import os
import sys

import cv2
import numpy as np
import pymupdf
import torch

from hips import fonts, model, pdf, pipeline, segment
from hips.text import CARRIERS, class_of

variants = fonts.variant_count()
truth_lines = pipeline.lines_of(pdf.read_glyphs(pymupdf.open(os.environ["HIPS_TRUTH"])))
split = len(truth_lines) * 2 // 3

sets = {"train": ([], [], []), "test": ([], [], [])}
for path in sys.argv[1:]:
    page = segment.segment(cv2.imread(path, cv2.IMREAD_GRAYSCALE))
    for index, (line, wanted) in enumerate(zip(page.lines, truth_lines)):
        if len(line) != len(wanted):
            continue
        crops, features, labels = sets["train" if index < split else "test"]
        for symbol, glyph in zip(line, wanted):
            crop, feature = segment.crop(page, symbol)
            crops.append(crop)
            features.append(feature)
            labels.append(class_of(glyph.char, glyph.variant, variants))
data = {name: tuple(np.array(part) for part in parts) for name, parts in sets.items()}
levels = np.array([[(0, -1, 1)[(v // 3 ** (2 - a)) % 3] for a in range(3)] for v in range(variants)])


def evaluate(net: model.GlyphNet, title: str) -> None:
    crops, features, labels = data["test"]
    carrier = labels < len(CARRIERS) * variants
    probabilities = model.predict(net, crops[carrier], features[carrier])
    truth = labels[carrier].astype(int)
    within = probabilities[:, : len(CARRIERS) * variants].reshape(len(truth), len(CARRIERS), variants)
    predicted = within[np.arange(len(truth)), truth // variants].argmax(axis=1)
    axes = (levels[predicted] == levels[truth % variants]).mean(axis=0)
    print(f"{title}: {len(truth)} held-out carrier glyphs, exact variant (of {variants}) "
          f"{np.mean(predicted == truth % variants):.3f}, level right XOPQ {axes[0]:.3f} YOPQ {axes[1]:.3f} XTRA {axes[2]:.3f}")


net = pipeline.load_model(variants)
evaluate(net, "before fine-tuning")
torch.manual_seed(0)
crops, features, labels = data["train"]
print(f"fine-tuning on {len(labels)} real glyphs from lines 1 to {split}")
model.train(net, crops, features, labels, epochs=40, batch=64, lr=5e-4, log=lambda _: None)
evaluate(net, "after fine-tuning")
