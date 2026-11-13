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

| 6 | `.venv/bin/python 06_decode_photo.py <image>...` | seconds | decodes a real photo or scan, compares with the printed PDF (`HIPS_TRUTH`, default `build/encoded.pdf`) |
| 7 | `.venv/bin/python 07_training_sheets.py 3 1` | seconds | `sheets/sheets.pdf`: A4 pages of random text with random variants, to print at 100% and photograph |
| 8 | `.venv/bin/python 08_ingest_photos.py real <image>...` | seconds | `dataset-real.npz`: labelled crops from photos of the printed sheets |
| 9 | `.venv/bin/python 09_train_real.py sheet-03.pdf` | 5 min | `glyphnet-real.pt`: the network trained further on the real crops, tested on the named held-out pages |
| 10 | `.venv/bin/python 10_small_codebook.py encode [message]`, later `... decode <image>...` | seconds | the print codec (ADR 0006): `encoded-small.pdf` to print, then the message from photos of it |

`09_train_real.py none` trains on every real page with nothing held out; that is the network to use for a new
printed page.

After step 9, `tools_small_codebooks.py <held-out pages>` shows how codebooks of 2 to 9 far-apart variants are
read on the held-out pages (ADR 0006), and `tools_axis_heads.py <held-out pages>` repeats step 9 with one output
per axis.

The argument of step 1 is the perturbation strength (ADR 0001). Changing it means repeating steps 2 to 5.

One experiment, one build directory. Three environment variables set the conditions for all steps:
`HIPS_BUILD` (directory under `poc/`, default `build`), `HIPS_SIZE` (text size in points, default 11) and
`HIPS_PRINTER_DPI` (one-bit printer raster in the simulated photos, default none). `./sweep_print.sh 300 "1.5 14"
"0.6 11"` runs steps 1 to 4 with a reduced budget for each "strength size" pair into
`build-s<strength>-<size>pt-<dpi>dpi/`. `print/` holds copies of the PDFs waiting to be printed.

Checks and diagnostics:

- `.venv/bin/python test_coding.py`: block coding, frame, correction of one and two wrong letters per block.
- `.venv/bin/python test_smallcode.py`: the print codec: BCH correction of up to five bits, rejection beyond,
  message through noise, a lost letter and a ruined line.
- `.venv/bin/python tools_segmentation_report.py 40 [english]`: how many lines the segmentation miscounts;
  failing lines are written to `build/badN.png`.
- `HIPS_TRUTH=build/encoded-long.pdf .venv/bin/python tools_real_finetune.py <image>...`: trains further on the
  upper lines of real photos and tests on the lower ones: does the print keep the information?
- `HIPS_TRUTH=build/encoded-long.pdf .venv/bin/python tools_axis_confusion.py <image>...`: per variant axis, true
  level against recognised level on a real image; writes real and simulated crops side by side.

## Modules

| File | Role |
|---|---|
| `hips/fonts.py` | Candidate variants from the variable font, fitted to the base glyph box (ADR 0001, 0002) |
| `hips/pdf.py` | Lay out a cover PDF, rewrite the text of an existing PDF with variant fonts, read it back, rasterise |
| `hips/coding.py` | Chinese Remainder block code, message frame, hard and soft decoding (ADR 0004) |
| `hips/smallcode.py` | Print codec: four variants per letter, BCH(63,36) packet per line (ADR 0006) |
| `hips/camera.py` | Simulated phone photo: ink gain, viewpoint, blur, uneven light, noise, JPEG |
| `hips/segment.py` | Photo to symbols in reading order, normalised crops (ADR 0003) |
| `hips/model.py` | The CNN, training loop, prediction |
| `hips/text.py` | Character set, class indices, random training text, the English sample text |
| `hips/pipeline.py` | Message to variants per character; class probabilities to message |
