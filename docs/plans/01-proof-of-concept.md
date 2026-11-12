# Phase 1: proof of concept

Question: can a message be hidden in the glyphs of a PDF and read back from a photo, offline, with a model small
enough for a phone, when the paper comes without code, font model or codebook?

**Answer so far (2026-11-12): yes in simulation, no on the first real print.** The full chain works on simulated
photos. On the owner's photos and scan of a printed page, finding and naming the letters works, but the variant is
recognised for only 26% to 49% of the letters (about 95% is needed, chance is 7%), so no message is recovered.
The cause is that the network has only ever seen simulated pages: after brief further training on 2500 real
glyphs it reads 60% of held-out real glyphs (of 27 variants), up from 21%. See "Real photos" below. The method
is not refuted; it needs real training data, and probably somewhat stronger variants or larger type.

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

## Real photos (2026-11-12)

The owner printed `encoded.pdf` (the 200-byte message; kept as `poc/build/encoded-long.pdf`) and supplied five
images in `~/Desktop/pic`: one Google Drive document scan (2275x3206) and four Pixel camera photos (3000x4000),
page slightly curled, dark table around it. Printer model and resolution are not known yet. Decoded with
`poc/06_decode_photo.py`; x-height 41 to 49 px.

| Image | Lines with the right symbol count | Characters misread | Variant right (codebook) | Message |
|---|---|---|---|---|
| Drive scan | 23/24 | 10 | 221/834 = 26% | none |
| photo 143250 | 21/24 | 15 | 271/751 = 36% | none |
| photo 144616 | 23/24 | 14 | 323/835 = 39% | none |
| photo 146567 | 24/24 | 5 | 431/878 = 49% | none |
| photo 147954 | 24/24 | 14 | 328/868 = 38% | none |

A simulated photo at the same size gives 99% to 100%.

What the diagnosis showed (`poc/tools_axis_confusion.py`):

- **Segmentation and character naming carry over to real images** once everything outside the sheet is masked
  out (`_paper_mask`; before that the table and the page edge produced hundreds of junk symbols). The one
  repeated miss is "rt" in "forty" printing as one blob.
- **Each axis is still read better than chance but far from well**: the level (-1, 0, +1) is right for 68% to 78%
  of letters on the stroke axes and 45% to 49% on the counter axis. Errors are biased: strokes read thinner and
  counters wider than they are. Real crops look thinner and rougher than simulated ones
  (`poc/build/crops-<image>.png`).
- **It is not a uniform weight shift.** Thickening or blurring the real image before decoding (gamma 0.35 to 1,
  blur 0 to 1.5 px) never got above 49%.
- **Printer resolution explains a large part.** At 11 pt a stem is 0.52 pt wide, 4.3 dots at 600 dpi, and the
  strength-0.6 step changes it by 0.06 pt, half a dot. Putting a one-bit printer raster into the simulation drops
  variant accuracy from 100% to 94% to 99% at 1200 dpi, 73% to 87% at 600 dpi and 47% to 50% at 300 dpi, with
  the network unchanged. The real images are worse than the 600 dpi simulation, so more is missing (dot gain,
  the printer's own stem snapping, phone sharpening, the scanner app's contrast processing).
- The Drive scan is the worst input, not the best: its image processing removes more of the signal than a plain
  photo does.

**Update, same day.** The printer is a laser printer at 300 dpi, A4. The 300 dpi raster simulation (47% to 50%)
therefore matches the real photos (36% to 49%) closely: printer resolution is the explanation, and
`pipeline.photograph(..., printer_dpi=300)` is now the honest test condition. At 300 dpi and 11 pt a stem is 2.2
dots wide and one strength-1.0 step is 0.4 dot; a full dot needs strength 2.5 at 11 pt or 1.5 at 18 pt.

**Monitor photo** (`PXL_20261112_154825950.jpg`, same document on a 4K monitor, x-height 54 px in the photo):
23/24 lines segmented, 25 characters misread, variant right 117/832 = **14%**, no message. Worse than paper. Axis
levels right 41% to 46%, biased the other way: strokes read thicker than they are. The page was shown small: one
screen pixel covers 4 to 5 camera pixels, so the x-height on screen was about 12 pixels and a stem about 2; the
pixel grid is visible in the photo and dark-on-white text blooms.

Taken together: every output device has a dot about as large as, or larger than, the differences we encode, and
each shifts stroke weight globally in its own direction (laser thinner, screen thicker) by more than one step.
A network that has only seen ideal pages cannot read that. (First conclusion here was that it cannot work at all;
the next section corrects it.)

### Second look: the print keeps the information, the simulation is what is wrong

The conclusion above ("cannot work") was too strong. Three experiments on the same day:

1. **Training on the simulated 300 dpi raster** (`poc/sweep_print.sh`, 300 pages, 4 epochs each). In simulation
   the raster costs little once the network has seen it:

   | Strength, size | Variant right, all 27 | Variants per letter in the codebook |
   |---|---|---|
   | 0.6, 11 pt | 79% | 16.9 |
   | 2.5, 11 pt | 91% | 23.6 |
   | 1.5, 14 pt | 91% | 24.1 |
   | 1.5, 18 pt | 91% | 23.7 |

   Simulated end to end at 1.5 / 14 pt / 300 dpi (200-byte message, full A4 page): 10/10 at 32 px x-height,
   8/10 to 10/10 at 25 px, 2/10 to 5/10 at 19 px; every failure is segmentation, no block was decoded wrongly.
2. **That network on the real photos**: no better (21% to 25% of 27 on the print photos, 11% on the monitor),
   with the same bias toward thin strokes. So the one-bit raster is not what a laser printer does, and the
   earlier agreement between "300 dpi simulation" and the real photos was partly coincidence. **Simulated
   results say nothing reliable about paper yet**, including the table in item 1.
3. **Fine-tuning on real glyphs** (`poc/tools_real_finetune.py`): the network is trained further on the glyphs
   of lines 1 to 16 of the four print photos (2513 glyphs) and tested on lines 17 to 24, which it never saw.
   Exact variant of 27 goes from 21% to **60%**; axis levels from 65% / 51% / 47% to 84% / 86% / 73%. With one
   photo each and about 600 training glyphs, the monitor photo goes from 8% to 29% and the scan from 16% to 25%.

So a 300 dpi laser print of 11 pt text at strength 0.6 does carry most of the signal; the network has simply
never seen real print. 2500 real glyphs are far too few (675 classes), and they already triple the accuracy. The
paper trained on half real photos for this reason (section 4.3). Next step: real training data from printed
sheets (`poc/07_training_sheets.py`, `poc/08_ingest_photos.py`).

Consequence: the simulation in `camera.py` was too kind because it starts from an ideal anti-aliased page. Results
in the tables above hold for a perfect printer or for text shown on a screen (untested), not for paper.

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

- [x] Real photos of a print-out decoded with `06_decode_photo.py`: fails, see "Real photos".
- [ ] **Make the variants survive a printer.** In this order:
      1. [x] Printer raster in the simulation (`pipeline.photograph(printer_dpi=...)`); 300 dpi reproduces the
         real numbers. Not yet used for training data or in `05_end_to_end.py`.
      2. [ ] **Real training data.** Sheets are built and waiting for the owner to print and photograph:
         `poc/print/sheets-11pt-strength0.6.pdf` (3 pages, build directory `build`) and
         `poc/print/sheets-14pt-strength1.0.pdf` (4 pages, build directory `build-s1.0-14pt`). Print at 100%,
         photograph every page two or three times (whole page in frame, different distance and light). Then
         `08_ingest_photos.py` makes labelled crops.
      3. [ ] Train on simulated plus real crops (`03_train.py` cannot mix yet), hold out one printed page per
         configuration (11 pt: the already printed `encoded-long.pdf`; 14 pt: sheet 4), rebuild the codebook from
         real confusion, decode a real photo end to end. Decide strength and minimum size from that.
      4. [ ] Make the simulation match print (compare real and simulated crops of the same glyph); until then no
         simulated number counts for paper.
      A model-free calibration sheet (nearest twin, per-axis discriminant on the photo itself) was tried and
      dropped: it could not even separate variants on an ideal simulated page, so it measured the instrument.
      Also worth trying: axes that change shape more than stroke weight (`YTLC`, `slnt`, terminals), and adding
      the real crops (labels are known from `encoded-long.pdf`) to training.
- [x] Screen instead of paper: tested, 14%, worse than paper at the zoom used. Add a screen model to the
      simulation (display pixel raster, blooming) before retrying; a larger zoom on screen is the cheap retest.
- [ ] **Relative decoding.** Each medium shifts all strokes one way. Normalise per page (for example against the
      non-carrier letters, which are always the base variant) so only differences between letters count.
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
