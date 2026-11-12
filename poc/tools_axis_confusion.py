"""Diagnostic: on a real photo of a known document, which axis of the variants is misread, and how?

Usage: HIPS_TRUTH=build/encoded-long.pdf tools_axis_confusion.py <image> [more images]
Prints, per axis, a 3x3 table of true level (rows: -1, 0, +1) against recognised level (columns),
over all carrier letters in lines that were segmented correctly. Writes build/crops-<image>.png with
the first crops of the photo (top) next to the same glyphs from a simulated photo (bottom).
"""

import os
import sys
from pathlib import Path

import cv2
import numpy as np
import pymupdf

from hips import fonts, model, pdf, pipeline, segment
from hips.text import CARRIERS

AXES = list(fonts.AXIS_DEFAULTS)
variants = fonts.variant_count()
levels = np.array([[(0, -1, 1)[(v // 3 ** (2 - a)) % 3] for a in range(3)] for v in range(variants)])
net = pipeline.load_model(variants)
truth_doc = pymupdf.open(os.environ.get("HIPS_TRUTH", str(fonts.BUILD_DIR / "encoded.pdf")))
truth_lines = pipeline.lines_of(pdf.read_glyphs(truth_doc))


def matched(page: segment.Page) -> list[tuple[segment.Symbol, pdf.Glyph]]:
    pairs = []
    for line, wanted in zip(page.lines, truth_lines):
        if len(line) == len(wanted):
            pairs.extend(zip(line, wanted))
    return pairs


simulated = segment.segment(pipeline.photograph(truth_doc, 11.0, 45, np.random.default_rng(0), 0.5))
simulated_crops = {id(g): segment.crop(simulated, s)[0] for s, g in matched(simulated)}

for path in sys.argv[1:]:
    page = segment.segment(cv2.imread(path, cv2.IMREAD_GRAYSCALE))
    pairs = [(s, g) for s, g in matched(page) if g.char in CARRIERS]
    crops, features = zip(*(segment.crop(page, s) for s, _ in pairs))
    probabilities = model.predict(net, np.stack(crops), np.stack(features))
    table = np.zeros((3, 3, 3), dtype=int)
    right = 0
    for row, (_, glyph) in zip(probabilities, pairs):
        start = CARRIERS.index(glyph.char) * variants
        predicted = int(np.argmax(row[start : start + variants]))
        right += predicted == glyph.variant
        for axis in range(3):
            table[axis, levels[glyph.variant, axis] + 1, levels[predicted, axis] + 1] += 1
    print(f"\n== {Path(path).name}: {len(pairs)} carrier letters, exact variant (of {variants}) {right / len(pairs):.3f}")
    for axis, name in enumerate(AXES):
        print(f"   {name}: level right {np.trace(table[axis]) / table[axis].sum():.3f}   "
              + "   ".join(f"true {level:+d}: {table[axis, i].tolist()}" for i, level in enumerate((-1, 0, 1))))
    shown = [(c, simulated_crops[id(g)]) for c, (_, g) in zip(crops, pairs) if id(g) in simulated_crops][:24]
    sheet = np.vstack([np.hstack([real for real, _ in shown]), np.hstack([sim for _, sim in shown])])
    cv2.imwrite(str(fonts.BUILD_DIR / f"crops-{Path(path).stem}.png"), 255 - sheet)
