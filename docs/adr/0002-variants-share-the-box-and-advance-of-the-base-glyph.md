# ADR 0002: Variants share the box and advance of the base glyph, with a minimum side bearing

Status: Accepted (2026-11-12)

## Context

Two problems showed up as soon as variants were mixed in one line:

1. A bolder or wider instance of a variable font has different advance widths. Swapping it in either moves the
   layout or makes the letter eat the gap to its neighbour.
2. Stock Roboto lets some pairs join on purpose ("ft", "ff": the crossbars meet) and leaves almost no gap in
   others ("rv", "ws"). The reader finds letters as connected components, so every such pair merged two letters
   into one. With the stock spacing, no page of English text out of 40 was segmented correctly.

## Decision

When the variant fonts are built (`poc/hips/fonts.py`, `_fit_to_base`), every glyph of every variant is scaled
horizontally into the extent of the same glyph in the base font and gets the base glyph's advance width. This is
the paper's "scale the glyph to fit the bounding box of the original letter" (section 4.5), done once in the font
instead of per document.

In the same step every glyph, including the base variant, is squeezed so that at least 60 font units (of 2048 per
em) stay free on each side. A negative left bearing (the hook of "j") is kept.

## Consequences

- All variants are metric-compatible: hiding a message is a font switch per character, the layout never moves,
  and a PDF writer needs no per-glyph positioning.
- The base font is no longer stock Roboto Flex: diagonal letters and "f", "t", "r" are a few percent narrower.
  A cover document must be set in `HIPSFlex00`, not in Roboto.
- With this spacing 35 of 40 simulated pages segment without a single miscounted line (before: 0 of 40). The
  rest of the segmentation problem is in `docs/plans/01-proof-of-concept.md`.
- Width (`XTRA`) differences survive only as proportions inside the box, not as overall width.
- Hinting programs are dropped because they no longer match the outlines. Irrelevant for PDF and print.
