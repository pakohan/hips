"""Step 8: turn real photos of printed sheets into labelled crops.

Usage: 08_ingest_photos.py <name> <image> [more images]
Environment: HIPS_BUILD (the build directory whose sheets/ and encoded*.pdf were printed).
Each photo must show one whole page. The characters are read with the network (which names
characters reliably even where it cannot tell variants apart) and aligned with the text of every
known page; the best-fitting page supplies the variant labels. A merged or split symbol therefore
costs only itself, not its line.
Writes <build>/dataset-<name>.npz in the format of step 2, plus `documents` (page index per crop).
"""

import sys
from difflib import SequenceMatcher
from pathlib import Path

import cv2
import numpy as np
import pymupdf

from hips import fonts, model, pdf, pipeline, segment
from hips.text import CARRIERS, CHARSET

name, paths = sys.argv[1], sys.argv[2:]
variants = fonts.variant_count()
documents = sorted((fonts.BUILD_DIR / "sheets").glob("sheet-*.pdf")) + sorted(fonts.BUILD_DIR.glob("encoded*.pdf"))
truths = [pdf.read_glyphs(pymupdf.open(path)) for path in documents]
texts = ["".join(g.char for g in truth) for truth in truths]
# A build directory without its own network borrows the character reader of the default build.
reader_path = pipeline.MODEL_PATH if pipeline.MODEL_PATH.exists() else fonts.POC_DIR / "build" / "glyphnet.pt"
net = pipeline.load_model(variants, reader_path)

crops, features, labels, x_heights, document_of = [], [], [], [], []
def align(gray: np.ndarray):
    """(page, crops, features, best document, matching blocks), or None when no known page fits."""
    page = segment.segment(gray)
    page_crops, page_features = pipeline.crops_of(page)
    if len(page_crops) == 0:
        return None
    probabilities = model.predict(net, page_crops, page_features)
    carrier_part = probabilities[:, : len(CARRIERS) * variants].reshape(len(probabilities), len(CARRIERS), variants)
    scores = np.concatenate([carrier_part.sum(axis=2), probabilities[:, len(CARRIERS) * variants :]], axis=1)
    read = "".join(CHARSET[i] for i in scores.argmax(axis=1))
    matchers = [SequenceMatcher(None, read, text, autojunk=False) for text in texts]
    best = max(range(len(texts)), key=lambda i: matchers[i].ratio())
    blocks = matchers[best].get_matching_blocks()
    if sum(block.size for block in blocks) < 0.5 * len(texts[best]):
        return None
    return page, page_crops, page_features, best, blocks


for path in paths:
    gray = cv2.imread(path, cv2.IMREAD_GRAYSCALE)
    # A phone held flat over the page often records no orientation: try the quarter turns too.
    turns = (None, cv2.ROTATE_90_CLOCKWISE, cv2.ROTATE_90_COUNTERCLOCKWISE)
    aligned = next((a for a in (align(gray if t is None else cv2.rotate(gray, t)) for t in turns) if a), None)
    if aligned is None:
        print(f"{Path(path).name}: no known page fits")
        continue
    page, page_crops, page_features, best, blocks = aligned
    matched = 0
    for block in blocks:
        for offset in range(block.size):
            glyph = truths[best][block.b + offset]
            crops.append(page_crops[block.a + offset])
            features.append(page_features[block.a + offset])
            labels.append(pipeline.labels_of([glyph], variants)[0])
            matched += 1
    x_heights.extend([page.x_height] * matched)
    document_of.extend([best] * matched)
    print(f"{Path(path).name}: {documents[best].name}, x-height {page.x_height:.0f} px, {len(page.lines)} lines, "
          f"{matched}/{len(texts[best])} glyphs labelled")

if labels:
    np.savez(
        fonts.BUILD_DIR / f"dataset-{name}.npz",
        crops=np.stack(crops),
        features=np.stack(features),
        labels=np.array(labels, np.int16),
        x_heights=np.array(x_heights, np.float32),
        documents=np.array(document_of, np.int16),
        document_names=np.array([d.name for d in documents]),
    )
print(f"{len(labels)} labelled glyphs from {len(paths)} photos")
