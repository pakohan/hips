# Phase 1: proof of concept

Question: can a message be hidden in the glyphs of a PDF and read back from a photo, offline, with a model small
enough for a phone, when the paper comes without code, font model or codebook?

**Answer so far (2026-11-12): yes in simulation.** The full chain works on simulated photos. It has not seen a
real photo, a real print-out or a phone yet. Those are the remaining risks, listed under Open.

Code: `poc/` (how to run: `poc/README.md`). Decisions: ADR 0001 to 0005.

## Done

- [x] Paper read and extracted (`docs/paper-summary.md`)
- [x] Glyph variants from Roboto Flex axes, 27 candidates per letter (ADR 0001)
- [x] Variants fitted to the base glyph box, minimum side bearing (ADR 0002)
- [x] Chinese Remainder block code, frame with length and CRC, hard and soft decoding (ADR 0004);
      `poc/test_coding.py` passes, including two wrong letters per block
- [x] PDF: cover document, read back characters and positions, remove the text, rewrite it with one font per
      character, read the message back from the PDF itself
- [x] Camera simulation (`poc/hips/camera.py`)
- [x] Segmentation without OCR, one CNN for character and variant (ADR 0003)
- [x] Codebook by confusion test and largest clique
- [x] End-to-end measurement on simulated photos

## Results

Setup: strength 0.6 (XOPQ ±10.8, YOPQ ±9.6, XTRA ±18), 408k training crops from 600 simulated pages, 6 epochs
(20 minutes on an M4, training accuracy still rising when it stopped), network 3.8 MB as float32 weights.

**Recognition, all 27 candidates, held-out pages:** character identity 99.96% (all but 3 of the errors are "l"
against "I", neither of which carries data). Variant accuracy 81% overall: 71% at 16 to 20 px x-height, 87% at
40 to 44 px. Per letter from 98% ("s") down to 11% ("i", which has no horizontal strokes and no counter).

**Codebook** (pair confusion at most 3%, accuracy within the list at least 95%): 16.4 variants per letter on
average, from 25 ("s") and 21 ("m") down to 11 ("t") and 3 ("i"). Accuracy within the lists 97.6% to 100%. The
lists were selected and scored on the same test set, so these accuracies are optimistic; the end-to-end numbers
below come from fresh photos.

**Capacity:** the 941-character sample text has 879 carrier letters, 175 blocks, 1790 bits: **2.04 bits per
letter** (paper: 1.77), about 220 bytes of message in a 20-line paragraph.

**PDF:** cover 9 kB, encoded 208 kB (the variant fonts are embedded, unsubsetted). Reading the message back from
the PDF by font name is exact.

**End to end**, 11 pt text, 20 simulated photos per row. "x-height" is the height of a lowercase "x" in the
photo; a 12 MP photo (4000x3000) of a full A4 page of 11 pt text gives about 27 px, half a page filling the
frame about 38 px. Severity 1.0 is roughly a casual hand-held photo (blur sigma up to 1.1 px, ±1.5° rotation,
perspective, uneven light, noise, JPEG quality 55 to 95); 0.5 is a careful one. "hard" is the paper's decoder,
"soft" is maximum likelihood over all five letters.

Short message (24 bytes, the first 22 of 175 blocks):

| x-height px | severity | segmentation ok | variant accuracy | message ok hard | message ok soft |
|---|---|---|---|---|---|
| 18 | 0.5 | 20/20 | 0.985 | 19/20 | 20/20 |
| 18 | 1.0 | 15/20 | 0.932 | 15/20 | 19/20 |
| 24 | 0.5 | 20/20 | 0.990 | 20/20 | 20/20 |
| 24 | 1.0 | 19/20 | 0.942 | 18/20 | 20/20 |
| 30 | 0.5 | 20/20 | 0.999 | 20/20 | 20/20 |
| 30 | 1.0 | 20/20 | 0.991 | 18/20 | 19/20 |
| 36 | 0.5 | 20/20 | 1.000 | 20/20 | 20/20 |
| 36 | 1.0 | 20/20 | 0.997 | 19/20 | 20/20 |
| 42 | 0.5 | 20/20 | 1.000 | 20/20 | 20/20 |
| 42 | 1.0 | 20/20 | 0.999 | 20/20 | 20/20 |

Long message (200 bytes, 159 of 175 blocks, so nearly every letter on the page must be right):

| x-height px | severity | segmentation ok | variant accuracy | message ok hard | message ok soft |
|---|---|---|---|---|---|
| 18 | 0.5 | 20/20 | 0.979 | 17/20 | 20/20 |
| 18 | 1.0 | 13/20 | 0.909 | 3/20 | 10/20 |
| 24 | 0.5 | 20/20 | 0.994 | 20/20 | 20/20 |
| 24 | 1.0 | 20/20 | 0.952 | 12/20 | 19/20 |
| 30 | 0.5 | 20/20 | 1.000 | 20/20 | 20/20 |
| 30 | 1.0 | 19/20 | 0.991 | 17/20 | 19/20 |
| 36 | 0.5 | 20/20 | 1.000 | 20/20 | 20/20 |
| 36 | 1.0 | 20/20 | 0.994 | 19/20 | 20/20 |
| 42 | 0.5 | 20/20 | 1.000 | 20/20 | 20/20 |
| 42 | 1.0 | 20/20 | 0.999 | 20/20 | 20/20 |

Raw tables with block error counts: `poc/build/e2e-results.md` after running step 5. "Variant accuracy" is over
photos in which every character was segmented and named correctly.

## Learned

- **Soft decoding beats the paper's decoder clearly** (long message at 24 px, severity 1.0: 19/20 against 12/20).
  Use maximum likelihood over all five letters in the app; the Hamming-then-tie-break rule has no advantage.
- **The font must guarantee gaps between letters.** With stock Roboto spacing "ft", "ff", "rv" and others touch
  and connected-component segmentation got 0 of 40 pages right. With a 60-unit minimum side bearing built into the
  variant fonts it is about 35 of 40 over the whole severity range (ADR 0002). The price is visibly loose spacing
  after "f", "v", "y".
- **Thresholding at 60% of solid ink separates letters better than Otsu** (Otsu lands near 50%, where the blur
  between neighbours still counts as ink; 70% starts breaking thin strokes at small sizes).
- **A line that starts with a descender derails naive baseline tracking.** Following the median bottom of the
  last five letters fixed it.
- **The failures left are almost all segmentation, and only below about 24 px or with strong blur.** One merged
  or split symbol shifts every block after it; nothing in the format recovers from that. A short message near the
  start of the text survives because the damage usually comes later.
- **Scale crops by the page's x-height, not by the symbol's box**, and keep them greyscale. Character identity is
  then nearly free (99.96%) from the same network.
- "i" carries almost nothing (3 variants), "l" nothing. Letters with counters and both stroke directions carry
  the most.
- At strength 1.0 the text looks like a ransom note (uneven boldness at a glance). At 0.6 the unevenness is
  visible when you look for it at large size and plausibly passes at reading size; nobody but the developer has
  judged it.
- Training on MPS takes about 3.3 minutes per epoch for 408k crops. Background shells of the IDE die when the
  session disconnects; start long jobs with `nohup`.

## Open

Ordered by how much each could change the answer.

- [ ] **Real photos.** Print `poc/build/encoded.pdf` (and view it on a screen), photograph it with a phone at a
      few distances, run the decoder on the files. The simulation has no paper texture, printer dot gain pattern,
      screen moire, glare, lens distortion or curved pages. Needs the owner; no app required
      (a small `06_decode_photo.py` taking an image path is the missing piece).
- [ ] **Resynchronisation.** Make an error local instead of fatal: restart the block structure at every line (line
      breaks survive printing and are visible to the reader) and add an outer code or repetition across lines.
      Without it, a long message needs every one of roughly 900 symbols segmented correctly.
- [ ] **Visibility.** Measure recognition and capacity at strength 0.4 and 0.3; show specimens to someone who does
      not know what to look for. Fix the loose spacing after "f", "v", "y" (per-glyph bearing instead of a global
      minimum).
- [ ] **Partial scans.** The reader must currently see the text from its first letter. A phone held close sees a
      few lines. Needs a way to find the start (per-line framing would give this too).
- [ ] **Font restriction** (roadmap question 1): only text in the HIPS font can carry data.
- [ ] Longer training and a held-out set for codebook selection; the network had not converged.
- [ ] Export the network to LiteRT and measure size and latency; int8 quantisation.
- [ ] Export test vectors (codebook, text, message, variants per letter) for the Kotlin codec.
- [ ] PDF on Android: find a library that can read character positions and fonts and rewrite text with embedded
      fonts (PdfBox-Android is the first candidate; unverified). Multi-page documents, text in several fonts and
      sizes, and existing kerning are untested even in the PoC.
- [ ] Uppercase letters, digits and non-English letters as carriers.
- [ ] Encryption by keyed variant order (paper section 7.3) and signatures (7.4): not started, no format change
      needed.
