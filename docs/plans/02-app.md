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

## Learned on the phone (Pixel 9a, 2026-11-13)

- **A debug build started from Android Studio is far too slow to scan.** The owner saw the scan "stuck". The
  scan thread was busy, not blocked: `simpleperf` showed 60% of its time in ART's on-stack-replacement lookups
  and most of the rest in the switch interpreter (`ExecuteSwitchImplCpp`), so the pixel loops of the
  segmentation ran interpreted. After 45 s of CPU time the scan had not finished. The release build is the one
  to try the app with; it is signed with the debug key so it installs directly (`app/build.gradle.kts`).
- **The template's partial R8 optimisation breaks the release build**: with `optimization { enable = true;
  packageScope = androidx, kotlin, kotlinx }` the app dies at start with `IllegalAccessError: Class
  kotlin.collections.j extended by class ih is inaccessible` (seen on the TV emulator; that build was also on
  the phone for a few minutes). Optimisation is switched off for now.
- Release build without optimisation: all four device tests pass on the TV emulator
  (`./gradlew :app:connectedReleaseAndroidTest -Phips.testBuildType=release`), and the app starts on the phone.
  How long a scan takes there is still unknown.

- **"No hidden message found" for photos the owner picked** (same day). Checked with one real photo of the
  14 pt page (`PXL_20261113_111501621.jpg`) pushed through the app's own pipeline on the TV emulator: same
  x-height (37), lines (30) and symbols (1887) as Python, 30 of 30 packets, message read. So the pipeline is not
  what failed. Of all photos taken so far only three hold a message this app can read: the 14 pt page
  `encoded-small-14pt.pdf`, photos `111455351`, `111501621`, `111503455`. The training sheets and
  `encoded-long.pdf` hold no print-codec message, and the 11 pt page is beyond the app's 14 pt network (5 to 11
  of 46 packets in Python, one of them with a bogus sequence number). Which photos the owner picked is not known.
- Reading that real photo took about 5 s per orientation on the emulator (M4 host). The reader therefore no
  longer runs the network on all four orientations: the segmentation tells sideways from upright (text on its
  side breaks into many short lines), and the network runs once, or twice when the first reading has no message.

## Added after the first device run

- [x] "Placeholder text" on the Write tab: lorem ipsum long enough to carry the message three times, or to the
      end of the first page once it fits (`PdfComposer.placeholderText`, `LoremIpsum`).
- [x] A hint under "No hidden message found" saying which pages can be read.
- [x] **Scan a PDF directly** ("Open PDF"): `scan/PdfPages.kt` draws each page with the platform `PdfRenderer`
      at 5.3 px per point (14 pt type gets a 38 px x-height) and hands it to the same reader; pages are read in
      order until one holds a message. The owner had found that a screenshot of a PDF reads fine. The PDF is
      drawn, not parsed, so no PDF library is involved and any PDF whose page looks like a HIPS page works.
      Device test `aPdfWrittenByTheAppBecomesItsMessage`: placeholder text, written by `PdfComposer`, drawn by
      `PdfPages`, read by LiteRT, all packets decoded.
- [x] **Cancel** while a scan runs. The screen is free at once; the scan thread stops at the reader's next
      checkpoint (between segmentation, cropping and the network), which can take a few seconds.
- [x] **App icon**: adaptive vector icon (`drawable/ic_launcher_*.xml`), four lines of text as bars with the
      thick "letters" in amber. The template's bitmap icons are gone; minSdk 29 always uses the adaptive one.
- [x] **Intro that explains the name** (`ui/IntroScreen.kt`): H, I, P, S stacked in thick amber, then the rest
      of "Hidden In Plain Sight" slides in thin and white; about 2.8 s, a tap skips it, once per launch. The
      window background and, from Android 12, the system splash screen use the same navy with the icon, so the
      start flows into the intro. No splash-screen library. Seen on the TV emulator; `IntroScreenTest` checks
      that it ends on its own and on a tap.

- [x] **Design in the icon's colours** (owner: "a beautiful design that uses the logo's colour scheme").
      `ui/theme/`: navy ink on warm paper in the light theme, the icon's night with amber as the light in the
      dark theme; dynamic (wallpaper) colour is off on purpose, it would replace exactly these colours. A navy
      header with the mark, the name and its meaning, and an amber pill that switches between the two halves,
      now called "Reveal" and "Hide". Reveal: one amber tile for the camera, two quiet tiles for photo and PDF,
      the found message amber-on-navy, the scan's facts as small pills. Hide: two numbered steps and one amber
      button. Shared pieces in `ui/Components.kt`; `ui/Previews.kt` holds previews of both halves in both
      themes, which is how the design was looked at (Android Studio preview renderer), not on the phone.
      Icons are three vector drawables; no icon library was added.

## Open

- [ ] Release build with R8: find out why the partial optimisation breaks class access (LiteRT pulls in
      WorkManager and androidx.startup, whose initialiser is where it fails) or optimise the whole app with
      keep rules. Until then the APK is unshrunk.

- [ ] **Device test by the owner.** Use the **release** variant (Build Variants panel, or
      `./gradlew :app:assembleRelease` and `adb install -r app/build/outputs/apk/release/app-release.apk`); a
      debug run is too slow, see "Learned on the phone". Write tab: paste a text of at
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
