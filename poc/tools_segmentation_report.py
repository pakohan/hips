"""Diagnostic: how often does segmentation return the wrong number of symbols for a line?

Usage: tools_segmentation_report.py [pages] [english]
Writes the first few failing lines to build/bad<N>.png with the symbol boxes drawn in.
"""

import sys

import cv2
import numpy as np

from hips import fonts, pdf, pipeline, segment
from hips.text import CARRIERS, SAMPLE_TEXT, random_text

pages = int(sys.argv[1]) if len(sys.argv) > 1 else 30
english = len(sys.argv) > 2
variants = fonts.variant_count()
saved = 0
stats = {"lines": 0, "merged": 0, "split": 0, "pages": 0, "pages_ok": 0}
for seed in range(pages):
    rng = np.random.default_rng(seed)
    glyphs, height = pdf.layout(SAMPLE_TEXT if english else random_text(rng, 110), 11, 300)
    glyphs = [pdf.Glyph(g.char, g.x, g.y, g.size, int(rng.integers(variants)) if g.char in CARRIERS else 0) for g in glyphs]
    x_height, severity = float(rng.uniform(16, 44)), float(rng.uniform(0.3, 1.3))
    photo = pipeline.photograph(pdf.write_pdf(glyphs, 300, height), 11, x_height, rng, severity)
    page, expected = segment.segment(photo), pipeline.lines_of(glyphs)
    stats["pages"] += 1
    stats["lines"] += len(expected)
    if len(page.lines) != len(expected):
        print(f"page {seed}: {len(page.lines)} lines found, {len(expected)} expected (x-height {x_height:.0f}, severity {severity:.1f})")
        continue
    ok = True
    for found, wanted in zip(page.lines, expected):
        if len(found) == len(wanted):
            continue
        ok = False
        stats["merged" if len(found) < len(wanted) else "split"] += 1
        if saved < 8:
            saved += 1
            strip = photo[max(0, min(s.y0 for s in found) - 8) : max(s.y1 for s in found) + 8].copy()
            for s in found:
                cv2.rectangle(strip, (s.x0, 0), (s.x1, strip.shape[0] - 1), 0, 1)
            cv2.imwrite(str(fonts.BUILD_DIR / f"bad{saved}.png"), strip)
            print(f"bad{saved}: x-height {x_height:.0f} severity {severity:.1f} found {len(found)} of {len(wanted)}: {''.join(g.char for g in wanted)}")
    stats["pages_ok"] += ok
print(stats)
