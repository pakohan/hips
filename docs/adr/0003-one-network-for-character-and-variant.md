# ADR 0003: One network names character and variant; segmentation without an OCR engine

Status: Accepted for the proof of concept (2026-11-12). Revisit in the scanning phase.

## Context

The paper gets characters and their boxes from Tesseract and then runs one CNN per letter on a 200x200 crop. On a
phone that means an OCR engine plus dozens of networks. The owner suspects an LLM is too much for this and prefers
simple image recognition.

## Decision

- **Segmentation** is classical image processing (`poc/hips/segment.py`): flatten the lighting, threshold at 60%
  of solid ink, take connected components, trace them into lines from left to right by following the median
  bottom edge of the last five letters, attach dot-sized parts to the letter they overlap. No rectification step;
  the tracing tolerates a few degrees of tilt and mild perspective.
- **Recognition** is one small CNN (`poc/hips/model.py`, four 3x3 convolutions with 32, 64, 128, 128 channels,
  one hidden layer of 256) with one output class per (carrier letter, candidate variant) pair plus one class per
  other character. Summing a letter's classes gives the character; the classes within the letter give the
  variant probabilities the decoder needs.
- **Crops** are 64x64 greyscale, scaled so that the x-height of the page is 26 px, not scaled to the symbol's own
  box. Stroke widths stay comparable between crops, and "o"/"O" or "s"/"S" stay distinguishable. Two extra inputs:
  symbol height and bottom offset from the baseline, both in x-heights.
- Crops are greyscale, not binarised as in the paper: at 20 px x-height the grey values carry most of the stroke
  width information.

## Consequences

- No OCR dependency and a single model file of a few megabytes. It should convert to LiteRT directly; not yet done.
- The model is tied to the HIPS font. It is not a general character recogniser.
- Segmentation by connected components only works because the font guarantees gaps between letters (ADR 0002),
  and it has no way to notice a merged or split letter. One miscounted symbol shifts every block after it. The
  paper has the same weakness with OCR errors. Measured failure rates and the planned remedy (resynchronising per
  line) are in `docs/plans/01-proof-of-concept.md`.
- ML Kit text recognition (bundled model, works offline, returns per-symbol boxes) is the alternative for the
  segmentation half if the classical approach fails on real photos. It cannot be evaluated without a device.
