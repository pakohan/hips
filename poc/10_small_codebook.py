"""Step 10: the print codec end to end (ADR 0006): four variants per letter, one packet per line.

Usage:
  10_small_codebook.py encode [message]     writes <build>/cover-small.pdf and <build>/encoded-small.pdf (A4)
  10_small_codebook.py decode <image>...    decodes photos of the printed encoded-small.pdf
Environment: HIPS_BUILD, HIPS_SIZE. Uses glyphnet-real.pt (step 9) when it exists, else glyphnet.pt.
"""

import sys
from pathlib import Path

import cv2
import numpy as np
import pymupdf

from hips import fonts, pdf, pipeline, smallcode
from hips.text import SAMPLE_TEXT

WIDTH, HEIGHT, MARGIN = 595.0, 842.0, 50.0
SIZE = pipeline.TEXT_SIZE
cover_path, encoded_path = fonts.BUILD_DIR / "cover-small.pdf", fonts.BUILD_DIR / "encoded-small.pdf"
variants = fonts.variant_count()
real_model = fonts.BUILD_DIR / "glyphnet-real.pt"
net = pipeline.load_model(variants, real_model if real_model.exists() else pipeline.MODEL_PATH)


def truth_lines() -> list[list[pdf.Glyph]]:
    return pipeline.lines_of(pdf.read_glyphs(pymupdf.open(encoded_path)))


def report(name: str, gray: np.ndarray) -> None:
    # A phone held flat over the page records no reliable orientation: read all four and keep the
    # one in which the most packets decode.
    turns = (None, cv2.ROTATE_90_CLOCKWISE, cv2.ROTATE_90_COUNTERCLOCKWISE, cv2.ROTATE_180)
    attempts = [pipeline.read_small(gray if turn is None else cv2.rotate(gray, turn), net, variants) for turn in turns]
    reading, lines = max(attempts, key=lambda attempt: (attempt[0].decoded, attempt[0].slots))
    print(f"{name}: message {reading.message!r}")
    print(f"   {len(lines)} lines, {reading.decoded}/{reading.slots} packets decoded, "
          f"{reading.corrected_bits} bits corrected in them, sequence numbers {reading.sequences}")
    # Against the document: how many lines have the right letters, and how many bits are read right.
    wanted_lines = truth_lines()
    lines_right = bits = bits_right = 0
    for line, wanted in zip(lines, wanted_lines) if len(lines) == len(wanted_lines) else []:
        if [char for char, _ in line] != [g.char for g in wanted]:
            continue
        lines_right += 1
        for (char, scores), glyph in zip(line, wanted):
            if scores is not None and glyph.variant in smallcode.FOUR:
                truth = smallcode.FOUR.index(glyph.variant)
                bits += 2
                bits_right += int(scores[2] + scores[3] > scores[0] + scores[1]) == truth // 2
                bits_right += int(scores[1] + scores[3] > scores[0] + scores[2]) == truth % 2
    print(f"   {lines_right}/{len(wanted_lines)} lines read letter for letter; in those, "
          f"{bits_right}/{bits} bits right ({bits_right / max(bits, 1):.3f})")


command = sys.argv[1] if len(sys.argv) > 1 else "encode"
if command == "encode":
    message = (sys.argv[2] if len(sys.argv) > 2 else "https://example.org/hips").encode()
    rng = np.random.default_rng(7)
    glyphs, height = pdf.layout(SAMPLE_TEXT + "\n" + SAMPLE_TEXT, SIZE, WIDTH, MARGIN)
    if height > HEIGHT:
        raise ValueError(f"the text needs {height:.0f} pt at {SIZE} pt type")
    pdf.write_pdf(glyphs, WIDTH, HEIGHT, cover_path)
    lines = pipeline.lines_of(pdf.read_glyphs(pymupdf.open(cover_path)))
    chosen = smallcode.embed([[g.char for g in line] for line in lines], message, rng)
    pdf.embed(cover_path, [v for row in chosen for v in row], encoded_path)

    exact = [
        [(g.char, np.eye(4)[smallcode.FOUR.index(g.variant)] if g.variant in smallcode.FOUR else None) for g in line]
        for line in truth_lines()
    ]
    reading = smallcode.read(exact)
    print(f"{len(smallcode.packets_of(message))} packets for {len(message)} bytes; the page holds {reading.slots} "
          f"packets on {len(lines)} lines; decoded from the PDF itself: {reading.message!r}")
    simulated = pipeline.photograph(pymupdf.open(encoded_path), SIZE, pdf.X_HEIGHT_EM * SIZE * 4000 / (HEIGHT * 1.08),
                                    rng, 0.7, pipeline.PRINTER_DPI)
    report("simulated photo (not evidence for paper)", simulated)
    print(f"print at 100%: {encoded_path}")
elif command == "decode":
    for path in sys.argv[2:]:
        gray = cv2.imread(path, cv2.IMREAD_GRAYSCALE)
        report(Path(path).name, gray)
