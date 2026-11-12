"""Step 7: sheets to print and photograph, so the network can learn from real prints.

Each A4 page is random text in which every carrier letter has a random variant. The labels are in
the PDF itself (the font of each character), so a photo of a printed sheet becomes labelled training
data (step 8). The paper trained on half real photos for the same reason.

Usage: 07_training_sheets.py [pages] [seed]
Environment: HIPS_BUILD (build directory, holds the fonts of one strength), HIPS_SIZE (points).
Writes <build>/sheets/sheet-NN.pdf and <build>/sheets/sheets.pdf (all pages, for printing at 100%).
"""

import sys

import numpy as np
import pymupdf

from hips import fonts, pdf, pipeline
from hips.text import CARRIERS, random_text

pages = int(sys.argv[1]) if len(sys.argv) > 1 else 3
seed = int(sys.argv[2]) if len(sys.argv) > 2 else 1
WIDTH, HEIGHT, MARGIN = 595.0, 842.0, 50.0
size = pipeline.TEXT_SIZE
variants = fonts.variant_count()
directory = fonts.BUILD_DIR / "sheets"
directory.mkdir(parents=True, exist_ok=True)

combined = pymupdf.open()
total = 0
for number in range(pages):
    rng = np.random.default_rng(seed * 1000 + number)
    words = int(60000 / size**2)  # more than fits; shortened until the page is full
    text = random_text(rng, words).split(" ")
    while True:
        glyphs, height = pdf.layout(" ".join(text), size, WIDTH, MARGIN)
        if height <= HEIGHT:
            break
        text = text[: int(len(text) * 0.95)]
    glyphs = [
        pdf.Glyph(g.char, g.x, g.y, g.size, int(rng.integers(variants)) if g.char in CARRIERS else 0) for g in glyphs
    ]
    doc = pdf.write_pdf(glyphs, WIDTH, HEIGHT, directory / f"sheet-{number + 1:02d}.pdf")
    combined.insert_pdf(doc)
    total += len(glyphs)
combined.save(directory / "sheets.pdf", garbage=3, deflate=True)
print(f"{pages} sheets, {total} glyphs, {size:.0f} pt: {directory / 'sheets.pdf'}")
