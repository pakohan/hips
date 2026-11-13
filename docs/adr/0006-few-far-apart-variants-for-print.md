# ADR 0006: For print, few far-apart variants per letter instead of many close ones

Status: Accepted (2026-11-13). A message was recovered from 6 of 7 real photos of two printed pages
(`docs/plans/01-proof-of-concept.md`, "First message decoded from paper").

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

## Decision (the owner chose this direction on 2026-11-13)

Implemented in `poc/hips/smallcode.py`, checked by `poc/test_smallcode.py`.

- **Four variants per carrier letter**, the same for every letter: thin or thick vertical strokes (`XOPQ`) times
  thin or thick horizontal strokes (`YOPQ`), counter width unchanged. Candidates 12, 15, 21, 24 of ADR 0001. A
  letter carries two bits, one per axis, so one misread axis costs one bit.
- **Carrier letters** are the lowercase letters except "l" (ADR 0004) and **"i"**: it has no horizontal stroke and
  its `YOPQ` bit is read at chance (54% to 61% on real prints).
- **One packet per 32 carrier letters, never across a line break.** A packet is 64 bits: a BCH(63,36) codeword
  (GF(64), primitive polynomial x^6 + x + 1, corrects 5 wrong bits) plus one overall parity bit. Its 36 data bits:
  5-bit sequence number, 27 payload bits, 4-bit CRC (x^4 + x + 1) over the other 32. The CRC is there because BCH
  turns about 6% of random bit patterns into a valid codeword. Leftover letters of a line get random variants.
- **Nothing in a packet depends on another line.** With the Chinese Remainder blocks one merged or split symbol
  shifted everything after it; here it costs the packets of its own line. Line breaks are the synchronisation
  marks: they survive printing and the reader finds them reliably.
- **The message** is the frame of ADR 0004 (length, payload, CRC-16), cut into 27-bit payloads and repeated down
  the page for as many packets as the text has room for. At most 32 packets, so 105 bytes. The reader takes, per
  sequence number, the payload seen most often, and accepts the result when the CRC-16 matches.
- **Reading** restricts the network's output to the four variants and divides each variant's score by its average
  over the page: a message uses the four equally often, so a page-wide preference comes from the printer or the
  camera. The network still has one class per (letter, candidate); a per-axis output made no difference on real
  data (`poc/tools_axis_heads.py`).
- The many-variant codebook and the CRT code (ADR 0004) stay in the PoC as the mode for documents that are only
  read digitally; whether the app offers it is open.

## Consequences

- Raw capacity drops from about 4 bits per letter to 2; a packet carries 27 payload bits in 32 letters, 0.84
  bits per letter before repetition. A full A4 page of 14 pt text (30 lines) has room for 30 packets: about 100
  bytes once, or a 24-byte message nearly four times. One packet per line wastes the rest of long lines (at 14 pt
  about 20 of 52 carrier letters); packing is open.
- Hard-decision BCH ignores how sure the network is of each bit. Soft decoding is open.
- Fewer, larger differences per letter are also what keeps the text looking even: three weights instead of 27
  shapes. Whether three weights are less visible than 27 small variations has not been judged.
- The frame (length, CRC) of ADR 0004 is unaffected. The paper's key-as-permutation encryption shrinks to
  nothing with 3 variants per letter; encryption would have to be applied to the message bits instead.
- Not shown yet: reading across photo sessions. See the plan.
