# ADR 0006: For print, few far-apart variants per letter instead of many close ones

Status: Proposed (2026-11-13). Becomes Accepted when a real photo decodes end to end.

## Context

ADR 0001 and 0004 follow the paper: as many variants per letter as the network can tell apart (16 on average in
simulation, up to 25) and Chinese Remainder Codes, which need letters with at least 5, 7, 8, 9 and 11 variants in
a block.

Real 300 dpi laser prints do not support that. After training on real glyphs, on pages the network never saw
(`docs/plans/01-proof-of-concept.md`, "Real training data"):

| Variants per letter | Read correctly, 11 pt strength 0.6 | Read correctly, 14 pt strength 1.0 |
|---|---|---|
| all 27 | 49% | 60% |
| 9: XOPQ x YOPQ, counter width fixed | 84% | 88% |
| 4: thick or thin on XOPQ and on YOPQ | 97% | 96% |
| 3: thin, normal, bold (XOPQ = YOPQ) | 99% | 99% |
| 2: thin, bold | 100% | 100% |

The counter-width axis (`XTRA`) is read at 56% to 64% and costs more than it carries.

## Decision (proposed)

- The codebook for printed documents has **3 or 4 variants per carrier letter**, the same for every letter, built
  from the two stroke axes only. `XTRA` is not used.
- With equal, small capacities the Chinese Remainder construction does not apply (five pairwise coprime moduli
  need capacities up to 11). The block code becomes a conventional one over a fixed alphabet (binary or
  quaternary BCH or Reed-Solomon over grouped letters); which one is open.
- The network keeps predicting the letter and the variant; with so few variants a per-axis output is an option.
  It made no difference on real data (`poc/tools_axis_heads.py`).
- The many-variant codebook and the CRT code stay as the mode for documents that are only ever read digitally or
  from a high-resolution source, if that mode is wanted at all.

## Consequences

- Raw capacity drops from about 4 bits per letter to 1.6 or 2; after coding roughly 1 bit per letter, so about
  100 bytes in a 20-line paragraph instead of 220.
- Fewer, larger differences per letter are also what keeps the text looking even: three weights instead of 27
  shapes. Whether three weights are less visible than 27 small variations has not been judged.
- The frame (length, CRC) of ADR 0004 is unaffected. The paper's key-as-permutation encryption shrinks to
  nothing with 3 variants per letter; encryption would have to be applied to the message bits instead.
- Not shown yet: reading across photo sessions. See the plan.
