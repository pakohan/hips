# Roadmap

## Goal

An Android phone app with two functions:

1. **Edit**: take an existing PDF and a message, write a PDF that looks the same and carries the message in its
   glyph shapes.
2. **Scan**: point the camera at such a text, printed or on a screen, and show the hidden message.

Constraints from the owner (2026-11-12):

- Everything happens on the device without internet if possible. A simple image-recognition model is preferred
  over an LLM.
- Develop as far as possible without the app and without a camera: use image files, degraded to simulate a
  camera.
- The owner is the only developer. Commit to `main`, no PRs.

## Phases

| # | Phase | Plan | State |
|---|---|---|---|
| 1 | Proof of concept (Python, desktop) | [01-proof-of-concept.md](01-proof-of-concept.md) | done for 14 pt print (owner, 2026-11-13: "14 pt is enough"); robustness items stay open there |
| 2 | App scaffolding | [02-app.md](02-app.md) | done as far as the owner wanted (no lint tooling, DI or navigation library) |
| 3 | Editing: PDF with a hidden message | [02-app.md](02-app.md) | text to PDF built and checked against the PoC; rewriting existing PDFs open |
| 4 | Scanning: photo to message on the device | [02-app.md](02-app.md) | built, passes on an emulator; real phone test open |

The owner wants the app on a phone soon to see how it really behaves, so scanning is built first: it is the
part that cannot be judged on a desktop. Every step below the UI is still tested on the JVM against data from
the PoC.

### Phase 2: scaffolding, taken over from the JustWatch project

Reference: `/Users/mogli/Developer/private/Justwatch` (`CLAUDE.md`, `build.gradle.kts`, `config/detekt/`,
`.claude/`, `docs/`). Read it again when this phase starts; this list is what was judged applicable.

Take over:

- `CLAUDE.md` + `docs/` layout (plans, ADRs, runbooks) and the rule to persist everything in the repo. Done.
- `.editorconfig` with `ktlint_official`, 120 columns. Done.
- ktlint and detekt Gradle plugins applied to all subprojects, `config/detekt/detekt.yml`; `./gradlew check` as
  the gate before a commit. Adds plugins: ask the owner first (CLAUDE.md rule).
- Version catalog discipline: versions only through the dependency runbook (`version-catalog-update` plugin) or
  the IDE version lookup, never typed by hand.
- `.claude/rules/kotlin.md` (domain modelling, coroutines, Compose rules), minus the TV and Hilt-specific lines if
  Hilt is not used.
- `.claude/hooks/gradle-gate.sh` Stop hook, so a turn cannot end with Kotlin that does not compile or lint.
- Architecture: one Activity, one `:app` module with feature packages, sealed `UiState` per ViewModel, immutable
  models, value classes, Navigation 3 with a sealed nav key.

Do not take over:

- Pull requests, branches, the "continue" merge routine: commits go straight to `main` here.
- Everything about GraphQL/Apollo, Media3, Cast, TV form factor, auth, translations, widgets.
- `GO-LIVE(<area>)` markers and their report script: there is no go-live register yet. Reconsider if the app is
  ever published.
- Release-build checks for reflection-heavy libraries, until such a library (ML Kit, LiteRT) is added.

Open for phase 2: whether to use Hilt at all in an app this small (JustWatch does).

## Questions for the owner

Collected here so they are asked once, with context. Details in the PoC plan.

1. **Font restriction.** HIPS can only hide data in text set in its own font (ADR 0001). For an arbitrary PDF the
   app must either re-set the text in the HIPS font, which changes its look and may reflow it, or refuse. Is
   "the document must be written in the HIPS font" acceptable, or should the app re-typeset?
2. **How visible may it be?** The strength knob trades visibility for capacity and robustness. Someone has to
   look at `poc/build/specimen.png` and a real print-out and say what is acceptable.
3. **Real photos.** The camera is simulated. Before phase 4 is designed in detail, a handful of real photos of a
   printed `poc/build/encoded.pdf` are needed to check the simulation against reality.
