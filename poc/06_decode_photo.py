"""Step 6: decode a real photo or scan.

Usage: 06_decode_photo.py <image> [more images]
The reading is also compared line by line with the document that was printed: build/encoded.pdf, or
the PDF named by the environment variable HIPS_TRUTH.
"""

import os
import sys
from pathlib import Path

import cv2
import numpy as np
import pymupdf

from hips import fonts, model, pdf, pipeline, segment
from hips.text import CARRIERS

variants = fonts.variant_count()
codebook = pipeline.load_codebook()
net = pipeline.load_model(variants)
truth_path = os.environ.get("HIPS_TRUTH", str(fonts.BUILD_DIR / "encoded.pdf"))
truth_lines = pipeline.lines_of(pdf.read_glyphs(pymupdf.open(truth_path)))

for path in sys.argv[1:]:
    photo = cv2.imread(path, cv2.IMREAD_GRAYSCALE)
    page = segment.segment(photo)
    crops, features = pipeline.crops_of(page)
    probabilities = model.predict(net, crops, features)
    hard = pipeline.read(probabilities, codebook, variants, soft=False)
    soft = pipeline.read(probabilities, codebook, variants, soft=True)
    print(f"\n== {Path(path).name}: {photo.shape[1]}x{photo.shape[0]}, x-height {page.x_height:.0f} px, "
          f"{len(page.lines)} lines, {len(crops)} symbols")
    print(f"   message (paper's decoder): {hard.message!r}")
    print(f"   message (soft decoder):    {soft.message!r}")

    # Diagnosis against the known document.
    offset = 0
    lines_ok = chars_wrong = letters = letters_right = 0
    for index, line in enumerate(page.lines):
        read_chars = soft.chars[offset : offset + len(line)]
        read_probs = soft.variant_probabilities[offset : offset + len(line)]
        offset += len(line)
        wanted = truth_lines[index] if index < len(truth_lines) else []
        if len(wanted) != len(line):
            if len(page.lines) <= len(truth_lines) + 3:
                print(f"   line {index + 1}: {len(line)} symbols, expected {len(wanted)}: {''.join(read_chars)}")
            continue
        lines_ok += 1
        for char, p, glyph in zip(read_chars, read_probs, wanted):
            if char != glyph.char:
                chars_wrong += 1
            elif char in CARRIERS and glyph.variant in codebook[char]:
                letters += 1
                letters_right += int(np.argmax(p)) == codebook[char].index(glyph.variant)
    print(f"   {lines_ok}/{len(truth_lines)} lines with the right symbol count, {chars_wrong} characters misread, "
          f"variant accuracy {letters_right}/{letters} = {letters_right / max(letters, 1):.3f}")
