# HIPS: hidden in plain sight

Android phone app that hides a message in the glyph shapes of a text document and reads it back with the camera.
It implements the FontCode paper (`paper/3152823.pdf`; the working extract is `docs/paper-summary.md`, read that
instead of the PDF). Two features: rewrite an existing PDF so it carries a message, and scan printed or displayed
text to reveal one. Everything runs on the device without a network.

Start with `docs/README.md`: roadmap, the current plan with its Open list, and the ADRs.

Package `com.pakohan.hips`, one `:app` Gradle module, Kotlin + Jetpack Compose. The Android side so far is the
Studio template plus the print codec in `codec/` (pure Kotlin, ADR 0006); the plan is `docs/plans/02-app.md`.
The proof of concept lives in `poc/` as Python and is not part of the Gradle build; it remains the reference
implementation, the trainer of the network and the source of test vectors.

## Build and verify

```
# proof of concept (Python 3.12, macOS); details and the meaning of each step in poc/README.md
cd poc && python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/python test_coding.py            # coding checks, seconds
.venv/bin/python 05_end_to_end.py          # needs the artefacts of steps 01 to 04 in poc/build/

# Android. The shell's Java is 11; run Gradle through Android Studio (its Gradle tool) or with a JDK 17+.
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest           # codec tests against vectors from poc/tools_export_vectors.py
```

`poc/build/` is generated and gitignored (fonts, datasets, model, PDFs). Regenerate it with steps 01 to 04; about
25 minutes on an M4, most of it training.

## Working rules

- Commit directly to `main`. The owner is the only developer: no branches, no PRs. Do not push unless asked.
- Commit messages: `<type>(<scope>): <imperative summary>` (`feat(poc)`, `docs(adr)`, `chore(build)`).
- IMPORTANT: persist what a later session needs in the repo, not in the conversation. A session may be cleared at
  any time and starts from this file, `docs/` and the code. A decision and its reason go into a new ADR
  (`docs/adr/`); a measured result, a cause found by debugging, or a fact learned on hardware goes into the
  current plan under "Learned"; open follow-ups are plan checklist items. Memory files hold only how the owner
  likes to work, never project facts.
- Work as far as possible without a device or the app: test with image files (rendered pages degraded by the
  camera simulation in `poc/hips/camera.py`) before asking for photos or a phone.
- IMPORTANT: do not add Android dependencies, Gradle plugins, or repositories without asking first, and never
  type a library version number by hand (use the version lookup tooling). Python packages for `poc/` are fine;
  pin them in `poc/requirements.txt`.
- If a fix needs a special case, a suppression, a `TODO`, or a commented-out block, stop and say so.
- Report measured numbers, including the bad ones. The point of each phase is to find out what does not work.
- Before calling a change done: the relevant checks pass, and you state what was verified and how.

## Architecture rules (apply once Android code exists; see `docs/plans/00-roadmap.md`, phase 2)

- One Activity, one `:app` module, feature packages (`codec/`, `pdf/`, `scan/`, `ui/`). No use-case layer.
- The codec (codebook, block coding, message frame) is pure Kotlin without Android imports, so it runs in JVM unit
  tests against vectors exported from `poc/`.
- Small immutable data models, value classes for ids and units, sealed interfaces for UI state.
- ViewModels expose one `StateFlow<UiState>`; `viewModelScope` only.
- Compose versions come only from the Compose BOM.

## Style

Kotlin: `ktlint_official`, trailing commas, 120 columns, no comments that restate the code. Python in `poc/`:
120 columns, type hints, small modules; scripts are numbered in the order they run.
