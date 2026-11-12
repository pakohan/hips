# ADR 0004: Message frame and carrier letters

Status: Accepted for the proof of concept (2026-11-12)

## Context

The paper leaves the bit-level format open: "a special chunk of bits" ends the message, and all letters (and in
principle digits) carry data. A phone reader also has to know whether what it decoded is a message at all.

## Decision

Block coding is the paper's: Chinese Remainder Codes with n = 5 letters per block, k = 3, moduli chosen by
depth-first search, a window without coprime moduli drops its lowest-capacity letter (`poc/hips/coding.py`).

Additions and deviations:

- **Frame**: 1 byte payload length, the payload, CRC-16/CCITT-FALSE over length and payload, most significant bit
  first. Maximum payload 255 bytes. The CRC tells the reader whether it found a message; there is no end marker.
- **Padding**: blocks after the frame carry random bits and letters outside any block get a random variant, so the
  whole text looks equally perturbed.
- **Carrier letters** are the lowercase letters except "l": `abcdefghijkmnopqrstuvwxyz`. Uppercase letters,
  digits and punctuation are always drawn in the base variant. Reasons: "l" and "I" are nearly the same bar in
  Roboto, and a carrier mistaken for a non-carrier (or the reverse) shifts every block after it; uppercase letters
  are about 3% of English text and would double the number of classes.
- **Moduli only use the first `p_i` variants** of a letter's list, and the decoder renormalises the network's
  probabilities over those `p_i` variants before deciding.
- **Decoding** offers the paper's rule (minimum Hamming distance, ties broken by the likelihood of the mismatching
  letters) and a plain maximum-likelihood rule over all five letters (`soft=True`). Which one the app uses follows
  from the measurements in `docs/plans/01-proof-of-concept.md`.

## Consequences

- 24 bits of overhead per message.
- The order of a letter's variant list is free; a keyed permutation of it is the paper's encryption scheme
  (section 7.3) and needs no change to this format.
- Resynchronisation after a segmentation error is not part of this frame yet (see the plan).
