"""Step 2: labelled glyph crops from simulated photos of random text with random variants.

Every page goes through the same PDF writer, camera simulation and segmentation as a real scan.
Lines whose symbol count differs from the text are dropped (and counted).

Usage: 02_make_dataset.py <name> <pages> <seed> [min_x_height max_x_height]
Environment: HIPS_BUILD (build directory), HIPS_SIZE (text size in points), HIPS_PRINTER_DPI.
"""

import sys
import time
from multiprocessing import Pool

import numpy as np

from hips import fonts, pdf, pipeline, segment
from hips.text import CARRIERS, random_text

SIZE = pipeline.TEXT_SIZE
WIDTH = 300.0


def make_page(args: tuple[int, int, float, float]) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray, int, int]:
    seed, variants, low, high = args
    rng = np.random.default_rng(seed)
    glyphs, height = pdf.layout(random_text(rng, 110), SIZE, WIDTH)
    glyphs = [
        pdf.Glyph(g.char, g.x, g.y, g.size, int(rng.integers(variants)) if g.char in CARRIERS else 0) for g in glyphs
    ]
    x_height = float(rng.uniform(low, high))
    doc = pdf.write_pdf(glyphs, WIDTH, height)
    photo = pipeline.photograph(doc, SIZE, x_height, rng, float(rng.uniform(0.3, 1.3)), pipeline.PRINTER_DPI)
    doc.close()
    page = segment.segment(photo)
    expected = pipeline.lines_of(glyphs)
    crops, features, labels = [], [], []
    dropped = 0
    found_lines = page.lines if len(page.lines) == len(expected) else [[] for _ in expected]
    for found, wanted in zip(found_lines, expected):
        if len(found) != len(wanted):
            dropped += 1
            continue
        for symbol in found:
            image, feature = segment.crop(page, symbol)
            crops.append(image)
            features.append(feature)
        labels.extend(pipeline.labels_of(wanted, variants))
    if not crops:
        return (np.zeros((0, segment.CROP, segment.CROP), np.uint8), np.zeros((0, 2), np.float32),
                np.zeros(0, np.int16), np.zeros(0, np.float32), dropped, len(expected))
    return (np.stack(crops), np.stack(features), np.array(labels, np.int16),
            np.full(len(labels), x_height, np.float32), dropped, len(expected))


if __name__ == "__main__":
    name, pages, seed = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
    low, high = (float(sys.argv[4]), float(sys.argv[5])) if len(sys.argv) > 5 else (16.0, 44.0)
    variants = fonts.variant_count()
    started = time.time()
    with Pool(8) as pool:
        results = pool.map(make_page, [(seed * 100_000 + i, variants, low, high) for i in range(pages)], chunksize=4)
    crops = np.concatenate([r[0] for r in results])
    dropped, lines = sum(r[4] for r in results), sum(r[5] for r in results)
    np.savez(
        fonts.BUILD_DIR / f"dataset-{name}.npz",
        crops=crops,
        features=np.concatenate([r[1] for r in results]),
        labels=np.concatenate([r[2] for r in results]),
        x_heights=np.concatenate([r[3] for r in results]),
    )
    print(f"{len(crops)} crops from {pages} pages in {time.time() - started:.0f}s; "
          f"{dropped}/{lines} lines dropped by segmentation ({100 * dropped / lines:.1f}%)")
