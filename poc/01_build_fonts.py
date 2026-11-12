"""Step 1: build the candidate variant fonts and a specimen to judge how visible they are.

Usage: 01_build_fonts.py [strength]
"""

import sys

import cv2
import numpy as np

from hips import fonts, pdf
from hips.text import CARRIERS, SAMPLE_TEXT

strength = float(sys.argv[1]) if len(sys.argv) > 1 else 1.0
locations = fonts.build_fonts(strength)
print(f"{len(locations)} variant fonts in {fonts.FONT_DIR}")

rng = np.random.default_rng(1)
text = SAMPLE_TEXT[:420]
glyphs, height = pdf.layout(text, 11, 330)
mixed = [
    pdf.Glyph(g.char, g.x, g.y, g.size, int(rng.integers(len(locations))) if g.char in CARRIERS else 0) for g in glyphs
]
plain = pdf.rasterise(pdf.write_pdf(glyphs, 330, height), 22, 11)
perturbed = pdf.rasterise(pdf.write_pdf(mixed, 330, height), 22, 11)
cv2.imwrite(str(fonts.BUILD_DIR / "specimen.png"), np.vstack([plain, perturbed]))

# Every variant of a few letters, large, base variant first.
rows = []
for letter in "aegnst":
    row, _ = pdf.layout(" ".join(letter * len(locations)), 30, 2000)
    row = [pdf.Glyph(g.char, g.x, g.y, g.size, i) for i, g in enumerate(row)]
    rows.append(pdf.rasterise(pdf.write_pdf(row, 1150, 70), 30, 30))
cv2.imwrite(str(fonts.BUILD_DIR / "variants.png"), np.vstack(rows))
print("specimen.png: top = base font, bottom = random variants; variants.png: all variants of a e g n s t")
