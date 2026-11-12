"""Step 8: turn real photos of printed sheets into labelled crops.

Usage: 08_ingest_photos.py <name> <image> [more images]
Environment: HIPS_BUILD (the build directory whose sheets/ and encoded.pdf were printed).
Each photo must show one whole page. Which page it is follows from the number of symbols per line.
Writes <build>/dataset-<name>.npz in the format of step 2, plus `documents` (page index per crop).
"""

import sys
from pathlib import Path

import cv2
import numpy as np
import pymupdf

from hips import fonts, pdf, pipeline, segment

name, paths = sys.argv[1], sys.argv[2:]
variants = fonts.variant_count()
documents = sorted((fonts.BUILD_DIR / "sheets").glob("sheet-*.pdf")) + sorted(fonts.BUILD_DIR.glob("encoded*.pdf"))
truths = [pipeline.lines_of(pdf.read_glyphs(pymupdf.open(path))) for path in documents]

crops, features, labels, x_heights, document_of = [], [], [], [], []
for path in paths:
    page = segment.segment(cv2.imread(path, cv2.IMREAD_GRAYSCALE))
    counts = [len(line) for line in page.lines]

    def matching_lines(truth: list) -> int:
        return sum(len(w) == f for w, f in zip(truth, counts)) if len(truth) == len(counts) else 0

    best = max(range(len(truths)), key=lambda i: matching_lines(truths[i]))
    usable = matching_lines(truths[best])
    if usable < len(truths[best]) // 2:
        print(f"{Path(path).name}: no known page fits ({len(counts)} lines found, x-height {page.x_height:.0f} px)")
        continue
    before = len(labels)
    for line, wanted in zip(page.lines, truths[best]):
        if len(line) != len(wanted):
            continue
        for symbol in line:
            crop, feature = segment.crop(page, symbol)
            crops.append(crop)
            features.append(feature)
        labels.extend(pipeline.labels_of(wanted, variants))
    x_heights.extend([page.x_height] * (len(labels) - before))
    document_of.extend([best] * (len(labels) - before))
    print(f"{Path(path).name}: {documents[best].name}, x-height {page.x_height:.0f} px, "
          f"{usable}/{len(truths[best])} lines, {len(labels) - before} glyphs")

if labels:
    np.savez(
        fonts.BUILD_DIR / f"dataset-{name}.npz",
        crops=np.stack(crops),
        features=np.stack(features),
        labels=np.array(labels, np.int16),
        x_heights=np.array(x_heights, np.float32),
        documents=np.array(document_of, np.int16),
    )
print(f"{len(labels)} labelled glyphs from {len(paths)} photos")
