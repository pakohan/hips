# FontCode: what the paper says

Source: `paper/3152823.pdf`. Xiao, Zhang, Zheng: "FontCode: Embedding Information in Text Documents Using Glyph
Perturbation", ACM Transactions on Graphics 37(2), Article 15, 2018. https://doi.org/10.1145/3152823

This file is the working extract. Read it instead of the PDF; section numbers refer to the paper. What HIPS does
differently is in `adr/`.

## Idea

Every letter of a text is drawn with one of several slightly different glyphs. Which glyph was used encodes an
integer. A reader that can tell the glyphs apart recovers the integers, and an error-correcting code turns them
back into the message. The text itself is unchanged, and the message survives printing and photographing.

## Pipeline

Embedding (section 3.1, 4.5):

1. Offline, once per font: build a **codebook**. For each character it lists N perturbed glyphs `u_0 .. u_{N-1}`.
   N differs per character (around 25 or fewer; "g" has 23 in their 30pt codebook, 14 in the 15pt one).
2. Read the document: text, letter positions, font.
3. Split the letters into blocks, turn the message bits into one integer per block, turn each integer into one
   small integer per letter (section 5).
4. Replace the glyph of each letter by `u_i` for its integer `i`, **scaled to fit the bounding box of the original
   letter**.

Retrieval (section 3.2, 4.1, 4.5):

1. From a PDF: compare glyph outlines with the codebook (L2 distance of polyline vertices after scaling to the
   same bounding box). Always exact.
2. From a photo: an OCR engine (they use Tesseract) gives each character and its bounding box. Crop, binarise with
   Otsu, resize to 200x200, classify with a CNN. The class is the integer.
3. Decode the integers block by block.

## Glyph perturbation (sections 3, 4.4, 6)

- Glyphs come from a **font manifold** (Campbell and Kautz 2014): a learned 2D space per character in which every
  point is a glyph. The paper says any generative font model would do.
- Candidates: 400 points on a grid around the original font. A Mechanical Turk study (2AFC, Bradley-Terry model)
  scores perceptual similarity to the original in [0, 1]; candidates scoring above 0.85 are kept (hundreds).
- Confusion test, repeated until the set stops shrinking (up to 5 rounds): pick 100 random pairs, train a
  two-class CNN per pair on synthetic images, call the pair confusable when accuracy is below 95%. Build a graph
  with an edge between every distinguishable pair and keep the **maximum clique**.
- Final check: train the N-class CNN on synthetic and real photos, drop glyphs recognised below 90%.
- A codebook built for small print must be more conservative (fewer glyphs per letter).

## Recognition network (sections 4.2, 4.3)

- One CNN **per letter and font**. Input 200x200 binary image. Conv 8x8x32, 5x5x64, 3x3x32, each with ReLU and
  2x2 max pooling, then FC(128), dropout 0.5, FC(N), softmax.
- Training data: half synthetic renderings, half real photos (each glyph printed, 10 photos with varying light and
  angle). Augmentation: Gaussian noise (sigma 3), Gaussian blur (sigma 0 to 3 px), random perspective, constant
  border 0 to 30 px. Adam, lr 1e-3, batch 15, 1e5 iterations.
- Reported accuracy: above 90% per glyph for crops of about 220x220 px. Accuracy drops with letter height in
  pixels and with smaller print (figure 11).

## Error correction (section 5)

Why not Reed-Solomon: letters have different capacities, and one misread letter corrupts a whole symbol.

**Chinese Remainder Codes.** Block of `n` letters with capacities `s_1 .. s_n`.

- Choose pairwise coprime `p_i <= s_i` by depth-first search, maximising the product of the `k` smallest. Call
  that product `M_t`. If no coprime set exists, skip the block's lowest-capacity letter and take the next letter
  of the text instead.
- The block carries an integer `m_t` of `floor(log2 M_t)` bits, cut sequentially from the message bit string.
- Letter `i` embeds `r_i = m_t mod p_i`.
- Minimum Hamming distance between codewords is `n - k + 1`, so `floor((n - k) / 2)` wrong letters per block can
  be corrected. The paper uses **n = 5, k = 3**: one wrong letter per block.
- Decoding: compute `m` by CRT from the read residues; if `m < M_t` accept it. Otherwise search all
  `m in [0, M_t)` for the codeword with the smallest Hamming distance to the read residues (brute force is fast
  enough for these sizes).

**Maximum likelihood decoding** (5.3). When several codewords tie at the smallest Hamming distance, pick the one
with the highest likelihood: the product, over the letters where codeword and reading disagree, of the network's
softmax probability for the codeword's glyph. This corrects **two** wrong letters per block of five in their
tests (40% instead of 20%). Block success with per-letter accuracy P1: sum of the binomial terms for 0, 1, 2
errors.

Implementation notes from the paper:

- A special end-of-message bit chunk marks where the message stops.
- Capacity for English text with their Times New Roman codebook: 1.77 bits per letter, so a 128-bit hash needs
  about 73 letters.
- Timing on a desktop GPU: 0.89 s to decode 176 letters, 7.28 s to encode them.

## Applications (section 7)

1. Metadata that survives format conversion and printing.
2. Unobtrusive optical code (replacement for a QR code on a poster). Their phone app sends the photo to a server.
3. Encryption: the order of the glyph list of each letter is a secret key (a permutation per letter).
4. Signature: embed a hash of the text (keyed codebook order, or RSA-encrypted hash). Per-segment hashes locate
   tampering.

## Limits the paper states (section 9)

- Only regular styles; bold and italic would need their own codebook entries.
- Letters need enough pixels: small print or low-resolution photos fail.
- **An OCR error cannot be recovered.** A misread character has a different capacity, so the block structure
  shifts and everything after it is lost. The same holds for occluded or missing text.
- Crumpled or curved paper fails; training data was flat.
- No code or codebook was published with the paper.
