# Phases 2 to 4: the app

Goal of the first version (owner, 2026-11-13): get the app onto a phone soon "to see how it really behaves".
Target is the configuration that works on paper: 14 pt text, strength 1.0, the print codec of ADR 0006. 14 pt is
enough; 11 pt is not pursued.

What a first version must do: take or pick a photo of a printed HIPS page and show the message; turn a text and
a message into a PDF that can be printed.

## Done

- [x] Print codec in pure Kotlin: `app/src/main/java/com/pakohan/hips/codec/` (`Bch`, `MessageFrame`,
      `PrintCodec`), no Android imports. 13 JVM tests, among them vectors exported from the Python reference
      (`poc/tools_export_vectors.py` writes `app/src/test/resources/codec/*.txt`): same BCH words, same packets,
      same packet letters, and the Kotlin reader decodes the page the Python encoder wrote.
- [x] Network exported to LiteRT: `poc/11_export_model.py` writes `poc/build-s1.0-14pt/glyphnet.tflite`, 3.8 MB,
      largest difference to PyTorch 8e-6. Inputs `crops` float32 [64, 1, 64, 64] (0 = paper, 1 = ink) and
      `features` float32 [64, 2]; output float32 [64, 722] probabilities. Batch fixed at 64.

## Decisions waiting for the owner

Adding dependencies needs the owner's yes (CLAUDE.md). Recommendation first in each row.

| Need | Recommended | Alternative |
|---|---|---|
| Run the network | LiteRT runtime (`com.google.ai.edge.litert`). No way around it: the network costs about 48 million multiply-adds per glyph and a page has 1,500 glyphs. | none |
| Get a photo | System camera and photo picker through the Activity Result API that `activity-compose` already brings. No new dependency, and the photos are exactly what the network was trained on. | CameraX with a live preview and guidance. Nicer, more code, four artefacts; worth it once decoding from a photo works. |
| Write the PDF | Platform `PdfDocument` and `Canvas` with the five fonts as assets: the app sets a text in the HIPS font and writes a new PDF. No new dependency. | PdfBox-Android to open an existing PDF, read its text and positions, and rewrite it. Needed for "alter an existing PDF" in the literal sense; only PDFs already set in the HIPS font keep their layout (roadmap question 1). |
| Find the letters in the photo | Port `poc/hips/segment.py` to Kotlin on pixel arrays. No dependency. Risk: the crops must match what the network saw in training (resize and blur details); checked with crops exported from Python. | OpenCV for Android (exact same calls as the PoC, about 10 MB per ABI), or ML Kit text recognition. |
| Lint and static analysis | ktlint and detekt Gradle plugins as in the JustWatch project, `./gradlew check` as the gate. | none for now |
| Dependency injection, navigation | Neither: two screens, hand-wired. | Hilt and Navigation 3 as in the JustWatch project. |

## Order of work once decided

1. Scaffolding: remove the template tests and greeting, package layout (`codec/`, `scan/`, `pdf/`, `ui/`),
   lint tooling if approved, `.claude/rules/kotlin.md` and the Gradle gate hook from the JustWatch project.
2. Scan, bottom up, each step testable on the JVM against data exported from the PoC:
   segmentation (photo pixels to symbols and crops; compare with Python crops of the owner's real photos),
   reader (LiteRT, page balancing, `PrintCodec.read`, four orientations), then the screen.
3. Compose a PDF: lay out text with the base font metrics, `PrintCodec.embed`, draw with the variant fonts.
   Check: the PoC decodes the PDF the app wrote, and the app decodes a photo of its print.
4. Device test by the owner with the real phone and printer.

## Open

- [ ] The network is tied to this printer family, this phone and 14 pt. A second printer or phone is untested.
- [ ] Time per page on a phone is unknown: 1,500 glyphs times four orientations. Orientation should be settled
      before the network runs (for example from the line structure), not by decoding four times.
- [ ] Assets: `HIPSFlex00`, `12`, `15`, `21`, `24` from `poc/build-s1.0-14pt/fonts/` (9 kB each) and
      `glyphnet.tflite`. They are generated and gitignored today; the app needs them committed under
      `app/src/main/assets/`.
- [ ] Soft-decision decoding, more than one packet per line, a code across packets (plan 01, item 3b) are not
      needed for a first version and can come from real use.
