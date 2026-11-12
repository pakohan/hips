# ADR 0005: The proof of concept is Python, outside the Android build

Status: Accepted (2026-11-12)

## Context

Before any app code, the owner wants to know whether the method works at all: no code or codebook came with the
paper. Font manipulation, PDF rewriting, network training and image simulation all have mature Python libraries,
and none of them needs a phone.

## Decision

`poc/` holds a self-contained Python project (venv, pinned `requirements.txt`, numbered scripts). It is not
referenced from Gradle. Everything it generates goes to `poc/build/`, which is gitignored.

What is expected to carry over to the app:

| PoC part | In the app |
|---|---|
| `fonts.py`: variant fonts | Built on the desktop, shipped as assets. Never runs on the phone. |
| `model.py` and training | Trained on the desktop, exported to LiteRT, shipped as an asset. |
| `coding.py`, frame, codebook JSON | Re-implemented in pure Kotlin; `poc/` exports test vectors for JVM unit tests. |
| `segment.py` | Re-implemented in Kotlin on bitmaps (or replaced by ML Kit, ADR 0003). |
| `pdf.py` (PyMuPDF) | Replaced by an Android PDF library. PyMuPDF is AGPL and has no Android build. |
| `camera.py` | Stays on the desktop as the test image generator. |

## Consequences

- The PoC answers feasibility with numbers before any Android dependency is chosen.
- Two implementations of the codec must agree; shared test vectors are the contract.
- The PDF half of the PoC proves the document model (font switch per character), not the Android library.
