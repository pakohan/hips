"""Step 5: the whole chain. Cover PDF -> PDF with a hidden message -> simulated photos -> message.

Usage: 05_end_to_end.py [message] [trials per setting]
Writes build/cover.pdf, build/encoded.pdf, build/e2e-photo-*.jpg and build/e2e-results.md.
"""

import sys

import cv2
import numpy as np
import pymupdf

from hips import coding, fonts, model, pdf, pipeline, segment
from hips.text import CARRIERS, SAMPLE_TEXT, class_count, class_of

message = (sys.argv[1] if len(sys.argv) > 1 else "https://example.org/hips").encode()
trials = int(sys.argv[2]) if len(sys.argv) > 2 else 20
SIZE, WIDTH = 11.0, 300.0
variants = fonts.variant_count()
codebook = pipeline.load_codebook()
rng = np.random.default_rng(7)

# 1. A cover document in the base font, as a user would bring it.
cover_path, encoded_path = fonts.BUILD_DIR / "cover.pdf", fonts.BUILD_DIR / "encoded.pdf"
glyphs, height = pdf.layout(SAMPLE_TEXT, SIZE, WIDTH)
pdf.write_pdf(glyphs, WIDTH, height, cover_path)

# 2. Read it back, compute the variants, rewrite the PDF.
chars = [g.char for g in pdf.read_glyphs(pymupdf.open(cover_path))]
positions, caps = pipeline.capacities(chars, codebook)
blocks = coding.make_blocks(caps)
needed = len(coding.frame(message))
print(f"{len(chars)} characters, {len(positions)} carrier letters, {len(blocks)} blocks, "
      f"{coding.capacity_bits(blocks)} bits capacity ({coding.capacity_bits(blocks) / len(positions):.2f} bits/letter); "
      f"message needs {needed} bits")
chosen = pipeline.embed_message(chars, message, codebook, rng)
pdf.embed(cover_path, chosen, encoded_path)

# 3. Decode straight from the PDF (the variant is the font name).
encoded = pymupdf.open(encoded_path)
encoded_glyphs = pdf.read_glyphs(encoded)
exact = np.zeros((len(encoded_glyphs), class_count(variants)), dtype=np.float32)
for row, glyph in enumerate(encoded_glyphs):
    exact[row, class_of(glyph.char, glyph.variant, variants)] = 1.0
print("decoded from the PDF itself:", pipeline.read(exact, codebook, variants).message)

# 4. Decode from simulated photos.
net = pipeline.load_model(variants)
truth_residue = [codebook[c].index(v) if c in CARRIERS else -1 for c, v in zip(chars, chosen)]
block_starts = np.cumsum([0] + [b.bits for b in blocks[:-1]])
message_blocks = int(np.sum(block_starts < needed))  # blocks that carry message bits, not padding
rows = ["| x-height px | severity | segmentation ok | char errors | variant accuracy | blocks wrong (hard / soft) | message ok (hard / soft) |",
        "|---|---|---|---|---|---|---|"]
for x_height in (18, 24, 30, 36, 42):
    for severity in (0.5, 1.0):
        seg_ok = hard_ok = soft_ok = 0
        char_errors = letters = letters_right = block_total = hard_blocks = soft_blocks = 0
        for trial in range(trials):
            photo = pipeline.photograph(encoded, SIZE, x_height, rng, severity)
            if trial == 0:
                cv2.imwrite(str(fonts.BUILD_DIR / f"e2e-photo-{x_height}-{severity}.jpg"), photo)
            page = segment.segment(photo)
            crops, features = pipeline.crops_of(page)
            probabilities = model.predict(net, crops, features)
            readings = [pipeline.read(probabilities, codebook, variants, soft) for soft in (False, True)]
            hard_ok += readings[0].message == message
            soft_ok += readings[1].message == message
            if len(crops) != len(chars):
                continue
            seg_ok += 1
            char_errors += sum(a != b for a, b in zip(readings[0].chars, chars))
            if readings[0].chars != chars:
                continue
            for p, residue in zip(readings[0].variant_probabilities, truth_residue):
                if residue >= 0:
                    letters += 1
                    letters_right += int(np.argmax(p)) == residue
            per_symbol = {i: readings[0].variant_probabilities[p] for i, p in enumerate(positions)}
            for block in blocks[:message_blocks]:
                true_m = None
                for m in range(1 << block.bits):
                    if all(m % mod == truth_residue[positions[l]] for l, mod in zip(block.letters, block.moduli)):
                        true_m = m
                        break
                block_total += 1
                probs = [per_symbol[l] for l in block.letters]
                hard_blocks += coding.decode_block(block, probs, False)[0] != true_m
                soft_blocks += coding.decode_block(block, probs, True)[0] != true_m
        rows.append(
            f"| {x_height} | {severity} | {seg_ok}/{trials} | {char_errors} | "
            f"{letters_right / max(letters, 1):.3f} | {hard_blocks}/{block_total} / {soft_blocks}/{block_total} | "
            f"{hard_ok}/{trials} / {soft_ok}/{trials} |"
        )
        print(rows[-1])
(fonts.BUILD_DIR / "e2e-results.md").write_text("\n".join(rows) + "\n")
