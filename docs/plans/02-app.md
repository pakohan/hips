# Phases 2 to 4: the app

Goal of the first version (owner, 2026-11-13): get the app onto a phone soon "to see how it really behaves".
Target is the configuration that works on paper: 14 pt text, strength 1.0, the print codec of ADR 0006. 14 pt is
enough; 11 pt is not pursued. What it is built from and why: ADR 0007.

**State (2026-11-13): a first version is built and installs. It has not run on a real phone yet.** Scan tab: take
or pick a photo, see the message. Write tab: text and message in, PDF out.

## Done

- [x] Print codec in pure Kotlin: `codec/` (`Bch`, `MessageFrame`, `PrintCodec`). JVM tests against vectors from
      the Python reference (`poc/tools_export_vectors.py`): same BCH words, same packets, same packet letters,
      and the Kotlin reader decodes the page the Python encoder wrote.
- [x] Network exported to LiteRT: `poc/11_export_model.py` writes `glyphnet.tflite`, 3.8 MB, largest difference
      to PyTorch 8e-6. Copied to `app/src/main/assets/` with the five fonts (`fonts/HIPSFlex00`, `12`, `15`, `21`,
      `24`) from `poc/build-s1.0-14pt/`.
- [x] Segmentation in Kotlin: `scan/Segmenter.kt`, `scan/ImageOps.kt`. On the fixture photo
      (`poc/tools_export_segmentation.py`) it finds the same x-height, lines and symbols as Python, boxes within
      one pixel, crops within one grey level on average (`SegmenterTest`).
- [x] Reader: `scan/PageReader.kt` (characters, four variant scores, page balancing, `PrintCodec.read`). With
      the network's reference answers it reads the fixture's message on the JVM (`PageReaderTest`).
- [x] `scan/GlyphClassifier.kt` (LiteRT), `scan/PhotoLoader.kt` (platform decoder, EXIF, OpenCV's luma weights).
- [x] PDF: `pdf/TextLayout.kt` (the PoC's greedy wrap), `pdf/PdfComposer.kt` (platform `PdfDocument`).
- [x] UI: one Activity, `MainViewModel` with one `StateFlow<UiState>`, `ui/HipsApp.kt` with two tabs,
      `ui/ScanScreen.kt`, `ui/WriteScreen.kt`. FileProvider for the camera photo.
- [x] Template example tests removed. No lint tooling, Hilt or navigation library (owner's choice, ADR 0007).

## Verified, and how

- `./gradlew :app:testDebugUnitTest`: 17 tests pass (codec, segmentation, reader).
- `./gradlew :app:assembleDebug :app:lintDebug`: builds, lint reports no errors.
- `./gradlew :app:connectedDebugAndroidTest` on two emulators:
  - Android TV emulator (API 36, arm64): all 4 pass. LiteRT on the device answers like PyTorch (largest
    difference below 1e-3 over 267 crops), and the fixture photo becomes its message through the platform image
    decoder, the Kotlin segmentation and LiteRT.
  - Phone emulator `Medium_Phone_API_36.1`: the two PDF tests pass; the two LiteRT tests are skipped because
    LiteRT crashes there (SME2, ADR 0007).
- The PDF the app wrote on the emulator (`PdfComposerOnDeviceTest`, pulled with `adb exec-out run-as`) checked
  with `poc/tools_check_app_pdf.py`: one A4 page, five embedded fonts `HIPSFlex00/12/15/21/24`, all text at
  14 pt, message decoded from the PDF itself and from a simulated photo of it, 10 of 10 packets.
- The app starts on the phone emulator and both tabs show their content (UI hierarchy dump).

Not verified: anything on a real phone. In particular the camera hand-off, the photo picker, saving the PDF
through the system file dialog, memory and time for a 12 MP photo, and whether the app reads a page it wrote
itself once that page is printed.

## Open

- [ ] **Device test by the owner.** Run the app from Android Studio on the phone. Write tab: paste a text of at
      least a few full lines, type a message, create the PDF, print at 100%. Scan tab: photograph the print.
      Also pick one of the earlier photos of `encoded-small-14pt.pdf`: the PoC reads `https://example.org/hips`
      from those. Report the details line under the result (lines, packets, seconds).
- [ ] Time and memory per photo on the phone. If slow: settle orientation before running the network (line
      structure) and run the network in fewer, larger batches.
- [ ] Rewrite existing PDFs with PdfBox-Android (owner: "both, text first"). Needs the dependency added and the
      font question answered in practice: only PDFs set in the HIPS font keep their layout.
- [ ] Tell the user how much message fits while typing, instead of refusing afterwards.
- [ ] Multi-page texts are written, but the reader handles one page per photo and each page repeats the
      message from wherever the packet counter stands; untested.
- [ ] CameraX with a preview and guidance (fill the frame, hold flat) if photos from the system camera turn out
      to be the weak point.
- [ ] The robustness items of plan 01 (soft decisions, more packets per line, code across packets, other
      printers and phones) once real use shows which of them matters.
