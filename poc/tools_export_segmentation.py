"""Exports a segmentation fixture for the Kotlin port of segment.py (ADR 0005).

Usage: HIPS_BUILD=build-s1.0-14pt HIPS_SIZE=14 tools_export_segmentation.py
Writes to app/src/test/resources/scan/:
  page.png      a simulated photo of a few lines of encoded text (lossless, so both sides see the same pixels)
  page.txt      first line: x-height; then per text line the symbols as "left,top,right,bottom,baseline"
  crops.bin     per symbol in reading order: 4096 bytes of crop, then two float32 features (little endian)
  probabilities.bin  per symbol: float32 class probabilities of the network, for the classifier test on a device
"""

import cv2
import numpy as np

from hips import fonts, model, pdf, pipeline, segment, smallcode
from hips.text import SAMPLE_TEXT

directory = fonts.POC_DIR.parent / "app" / "src" / "test" / "resources" / "scan"
directory.mkdir(parents=True, exist_ok=True)
rng = np.random.default_rng(14)
size = pipeline.TEXT_SIZE

glyphs, height = pdf.layout(SAMPLE_TEXT[:330], size, 330.0, 20.0)
lines = pipeline.lines_of(glyphs)
variants = smallcode.embed([[g.char for g in line] for line in lines], b"hi", rng)
glyphs = [pdf.Glyph(g.char, g.x, g.y, g.size, v) for line, row in zip(lines, variants) for g, v in zip(line, row)]
photo = pipeline.photograph(pdf.write_pdf(glyphs, 330.0, height), size, 38.0, rng, 0.5, 600)
cv2.imwrite(str(directory / "page.png"), photo)

page = segment.segment(photo)
rows = [f"{page.x_height:.1f}"]
blob = bytearray()
crops, features = [], []
for line in page.lines:
    rows.append(" ".join(f"{s.x0},{s.y0},{s.x1},{s.y1},{s.baseline:.2f}" for s in line))
    for symbol in line:
        crop, feature = segment.crop(page, symbol)
        crops.append(crop)
        features.append(feature)
        blob += crop.tobytes() + feature.astype("<f4").tobytes()
(directory / "page.txt").write_text("\n".join(rows) + "\n")
(directory / "crops.bin").write_bytes(bytes(blob))

net = pipeline.load_model(fonts.variant_count(), fonts.BUILD_DIR / "glyphnet-real.pt")
probabilities = model.predict(net, np.stack(crops), np.stack(features))
(directory / "probabilities.bin").write_bytes(probabilities.astype("<f4").tobytes())
reading, _ = pipeline.read_small(photo, net, fonts.variant_count())
print(f"{photo.shape[1]}x{photo.shape[0]} photo, x-height {page.x_height}, {len(page.lines)} lines, {len(crops)} symbols, "
      f"message read back: {reading.message!r} ({reading.decoded}/{reading.slots} packets)")
