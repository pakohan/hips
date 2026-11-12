# ADR 0001: Variable-font axes instead of a font manifold

Status: Accepted (2026-11-12)

## Context

The paper perturbs glyphs on a learned font manifold (Campbell and Kautz 2014) and picks candidates with a
Mechanical Turk study. Neither the manifold model nor the paper's codebook is published, and we cannot run a
perceptual study. We need another source of glyphs that differ slightly, look designed, and can be generated
without a network on a phone.

## Decision

Use the parametric axes of an open variable font. The base font is **Roboto Flex** (SIL OFL 1.1, source file in
`poc/fonts/`). A candidate variant is a point near the default instance on three axes:

| Axis | Meaning | Default | Step at strength 1.0 |
|---|---|---|---|
| `XOPQ` | thickness of vertical strokes | 96 | 18 |
| `YOPQ` | thickness of horizontal strokes | 79 | 16 |
| `XTRA` | width of the counters | 468 | 30 |

Each axis takes the levels (0, -1, +1) times its step times a global `strength`, giving 27 candidates; candidate 0
is the unmodified font. The per-letter codebook is the subset of candidates the recognition network can tell
apart (`poc/04_build_codebook.py`, the paper's clique selection).

`strength` replaces the perceptual study: it is the single knob that trades visibility against recognition. Its
current value and the measurements behind it are in `docs/plans/01-proof-of-concept.md`.

## Consequences

- HIPS can only hide data in text set in its own font. A PDF in another font must be re-set in the HIPS font
  first, which changes how it looks, or be rejected. The paper has the same limit (one codebook per font).
- Variants are ordinary static TrueType fonts (about 9 kB each for ASCII), built once and shipped as assets. The
  phone never needs fontTools or a font model.
- At most 27 variants per letter, against roughly 25 in the paper, and all letters share the same candidates.
  More axes (`YTLC`, `GRAD`, `slnt`) are available if capacity matters more than subtlety.
- Whether readers notice the variants has only been judged by eye (`poc/build/specimen.png`). A real perceptual
  check is open.
- Roboto Flex is OFL: derived fonts may be embedded in PDFs and shipped, but must not be named "Roboto". The
  variants are named `HIPSFlexNN`.
