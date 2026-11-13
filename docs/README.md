# Documentation index

| Where | What |
|---|---|
| [paper-summary.md](paper-summary.md) | What the FontCode paper says: pipeline, network, codebook construction, error correction, limits. Read this instead of the PDF. |
| [plans/00-roadmap.md](plans/00-roadmap.md) | Goal, constraints, phase order, what is taken over from the JustWatch project, questions for the owner |
| [plans/01-proof-of-concept.md](plans/01-proof-of-concept.md) | Phase 1: what was built, measured results, what was learned, open work |
| [plans/02-app.md](plans/02-app.md) | Phases 2 to 4: the app. What is built, what was verified and how, what is open (first of all: a real phone) |
| [adr/](adr/) | Architecture decision records: 0001 variable-font axes as glyph source, 0002 variant metrics and spacing, 0003 one network and own segmentation, 0004 message frame and carrier letters, 0005 PoC in Python, 0006 the print codec: four far-apart variants per letter, one BCH packet per line, 0007 what the first app version is built from and the rules its code follows |
| [../poc/README.md](../poc/README.md) | How to run the proof of concept, step by step |

Conventions: a plan is a checklist that gets ticked off, with a "Learned" section for measured facts; an ADR is
one decision that is expensive to reverse (Status: Proposed, Accepted, Superseded). Sequencing goes in plans,
reasoning goes in ADRs.
