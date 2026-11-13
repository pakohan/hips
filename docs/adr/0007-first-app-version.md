# ADR 0007: The first app version: what it is built from

Status: Accepted (2026-11-13, choices made by the owner)

## Context

The owner wants the app on a phone soon "to see how it really behaves". Everything must run on the device without
a network. New dependencies need the owner's yes.

## Decision

| Need | Choice | Why, and what was not chosen |
|---|---|---|
| Run the network | **LiteRT, bundled runtime** (`com.google.ai.edge.litert:litert`), CPU only | A page has about 1,500 glyphs at 48 million multiply-adds each. The runtime in Google Play services was not chosen because it is fetched by Play services, and the app must work offline. No GPU artefact: not needed yet. |
| Get the photo | **System camera and photo picker** through the Activity Result API | No new dependency, and the photos are what the network was trained on. CameraX with a live preview can follow once reading works. |
| Write the PDF | **Platform `PdfDocument`**, one `drawText` per character with the font of its variant | No new dependency. The PDF embeds the five HIPS fonts as real fonts; the Python reference decodes it. Opening existing PDFs (PdfBox-Android) comes after this; the owner chose "both, text first". |
| Find the letters | **Port of `poc/hips/segment.py` to Kotlin** on pixel arrays (`scan/Segmenter.kt`, `scan/ImageOps.kt`) | No dependency. The OpenCV calls of the PoC are re-implemented closely enough that symbol boxes agree within one pixel and crops within one grey level on average (`SegmenterTest`). |
| Lint, dependency injection, navigation | **None for now** | Owner's choice. Two screens, one ViewModel, wired by hand. |

Consequences of the LiteRT choice:

- LiteRT 2.3.2 is compiled with Kotlin 2.4, so the project's Kotlin went from 2.2.10 to **2.4.21** (the Compose
  compiler plugin follows the same version).
- The model's batch size is fixed at 64 (`poc/11_export_model.py`); `GlyphClassifier` pads the last batch.
- LiteRT dies with SIGILL on arm64 emulators that advertise SME2 without supporting it, which is every emulator
  on an Apple M4 host with the API 36.1 phone image. The device tests skip there (`ScanOnDeviceTest`). The Android
  TV emulator image does not advertise SME2 and runs them. Real phones are not affected by this as far as known.

## Rules the code follows

- `codec/` has no Android imports and stays bit-compatible with `poc/hips/smallcode.py`; `scan/Segmenter.kt`,
  `scan/ImageOps.kt` and `scan/PageReader.kt` have none either. They are tested on the JVM against fixtures the
  PoC exports (`poc/tools_export_vectors.py`, `poc/tools_export_segmentation.py`). When the Python side changes,
  re-export and the Kotlin tests say what broke.
- The network, the fonts and the type size belong together: `assets/glyphnet.tflite` and `assets/fonts/` come
  from `poc/build-s1.0-14pt/`, and `PageFormat` sets 14 pt. Changing one means retraining and re-exporting.
- The whole scan runs on one thread (`MainViewModel.scanDispatcher`), because LiteRT wants create, run and close
  on the same thread.
- A photo is read as stored first; only when that yields no message are the other three orientations tried.
- The PDF writer refuses text with characters outside `PdfComposer.SUPPORTED` instead of dropping them: the
  fonts are ASCII subsets and the network names only those characters.
