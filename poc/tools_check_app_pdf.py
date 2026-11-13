"""Checks a PDF written by the Android app with the Python reference.

Usage: HIPS_BUILD=build-s1.0-14pt HIPS_SIZE=14 tools_check_app_pdf.py <pdf>
Get the file from a device with
  adb exec-out run-as com.pakohan.hips cat files/composed.pdf > composed.pdf
after running PdfComposerOnDeviceTest. Reports the fonts the PDF embeds, decodes the message from the
PDF itself (variant = font of each character) and from a simulated photo of it.
"""

import sys

import numpy as np
import pymupdf

from hips import fonts, pdf, pipeline, smallcode

doc = pymupdf.open(sys.argv[1])
page = doc[0]
print(f"{len(doc)} page(s), {page.rect.width:.0f} x {page.rect.height:.0f} pt, fonts: "
      f"{sorted({font[3] for font in page.get_fonts()})}")

glyphs = pdf.read_glyphs(doc)
sizes = sorted({round(g.size, 2) for g in glyphs})
unknown = sum(g.variant < 0 for g in glyphs)
lines = pipeline.lines_of(glyphs)
exact = [
    [(g.char, np.eye(4)[smallcode.FOUR.index(g.variant)] if g.variant in smallcode.FOUR else None) for g in line]
    for line in lines
]
reading = smallcode.read(exact)
print(f"{len(glyphs)} characters on {len(lines)} lines, sizes {sizes}, {unknown} in a font that is not a HIPS variant")
print(f"from the PDF itself: {reading.message!r} ({reading.decoded}/{reading.slots} packets)")

rng = np.random.default_rng(3)
net = pipeline.load_model(fonts.variant_count(), fonts.BUILD_DIR / "glyphnet-real.pt")
x_height = pdf.X_HEIGHT_EM * pipeline.TEXT_SIZE * 4000 / (page.rect.height * 1.08)
photo = pipeline.photograph(doc, pipeline.TEXT_SIZE, x_height, rng, 0.7, 600)
reading, _ = pipeline.read_small(photo, net, fonts.variant_count())
print(f"from a simulated photo: {reading.message!r} ({reading.decoded}/{reading.slots} packets, "
      f"{reading.corrected_bits} bits corrected)")
