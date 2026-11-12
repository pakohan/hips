# Proof of concept

Answers one question before any app code exists: can a message be hidden in the glyphs of a PDF and read back
from a photo, with models small enough for a phone? Results and conclusions: `../docs/plans/01-proof-of-concept.md`.
Decisions: `../docs/adr/`.

## Setup

```
cd poc
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
```

`fonts/RobotoFlex.ttf` is the variable font from github.com/google/fonts (`ofl/robotoflex`), licence in
`fonts/OFL.txt`.

## Steps

Run from `poc/`. Everything is written to `build/` (gitignored).

| Step | Command | Time (M4) | Output |
|---|---|---|---|
| 1 | `.venv/bin/python 01_build_fonts.py 0.6` | 10 s | 27 variant fonts `build/fonts/HIPSFlexNN.ttf`; `specimen.png` (base font above, random variants below), `variants.png` |
| 2 | `.venv/bin/python 02_make_dataset.py train 600 1` and `... test 100 2` | 70 s | labelled 64x64 crops from simulated photos, 408k and 68k |
| 3 | `.venv/bin/python 03_train.py 6` | 20 min | `glyphnet.pt`, accuracy per x-height band and per letter |
| 4 | `.venv/bin/python 04_build_codebook.py` | 10 s | `codebook.json`: per letter the variants that can be told apart |
| 5 | `.venv/bin/python 05_end_to_end.py` | 2 min | `cover.pdf`, `encoded.pdf`, sample photos, `e2e-results.md` |

The argument of step 1 is the perturbation strength (ADR 0001). Changing it means repeating steps 2 to 5.

Checks and diagnostics:

- `.venv/bin/python test_coding.py`: block coding, frame, correction of one and two wrong letters per block.
- `.venv/bin/python tools_segmentation_report.py 40 [english]`: how many lines the segmentation miscounts;
  failing lines are written to `build/badN.png`.
- `.venv/bin/python 06_decode_photo.py <image>...`: decode a real photo or scan and compare it with the printed
  document (`build/encoded.pdf`, or the PDF named by `HIPS_TRUTH`).
- `HIPS_TRUTH=build/encoded-long.pdf .venv/bin/python tools_axis_confusion.py <image>...`: per variant axis, true
  level against recognised level on a real image; writes real and simulated crops side by side.

## Modules

| File | Role |
|---|---|
| `hips/fonts.py` | Candidate variants from the variable font, fitted to the base glyph box (ADR 0001, 0002) |
| `hips/pdf.py` | Lay out a cover PDF, rewrite the text of an existing PDF with variant fonts, read it back, rasterise |
| `hips/coding.py` | Chinese Remainder block code, message frame, hard and soft decoding (ADR 0004) |
| `hips/camera.py` | Simulated phone photo: ink gain, viewpoint, blur, uneven light, noise, JPEG |
| `hips/segment.py` | Photo to symbols in reading order, normalised crops (ADR 0003) |
| `hips/model.py` | The CNN, training loop, prediction |
| `hips/text.py` | Character set, class indices, random training text, the English sample text |
| `hips/pipeline.py` | Message to variants per character; class probabilities to message |
