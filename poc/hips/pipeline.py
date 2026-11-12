"""Glue between the parts: message -> variants per character, photo -> message."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import pymupdf

from . import camera, coding, fonts, model, pdf, segment
from .text import CARRIERS, CHARSET, class_of

Codebook = dict[str, list[int]]  # letter -> variant ids; position in the list is the embedded integer

CODEBOOK_PATH = fonts.BUILD_DIR / "codebook.json"
MODEL_PATH = fonts.BUILD_DIR / "glyphnet.pt"


def load_codebook(path: Path = CODEBOOK_PATH) -> Codebook:
    return json.loads(path.read_text())["letters"]


def capacities(chars: list[str], codebook: Codebook) -> tuple[list[int], list[int]]:
    """(positions of the carrier letters in `chars`, their capacities)."""
    positions = [i for i, c in enumerate(chars) if c in CARRIERS]
    return positions, [len(codebook[chars[i]]) for i in positions]


def embed_message(chars: list[str], message: bytes, codebook: Codebook, rng: np.random.Generator) -> list[int]:
    """Variant id for every character. Letters outside any block get a random variant."""
    positions, caps = capacities(chars, codebook)
    blocks = coding.make_blocks(caps)
    residues = coding.encode(coding.frame(message), blocks, rng)
    variants = [0] * len(chars)
    for index, position in enumerate(positions):
        residue = residues.get(index, int(rng.integers(caps[index])))
        variants[position] = codebook[chars[position]][residue]
    return variants


def photograph(
    doc: pymupdf.Document,
    size: float,
    x_height: float,
    rng: np.random.Generator,
    severity: float,
    printer_dpi: int | None = None,
) -> np.ndarray:
    """Simulated photo of page 0 in which the x-height is `x_height` pixels.

    Without `printer_dpi` the camera sees an ideal anti-aliased page, which no printer produces.
    With it, the page first becomes a one-bit raster at that resolution: at 300 dpi this alone
    brings recognition down to what real laser prints gave (plan 01, "Real photos").
    """
    if printer_dpi is None:
        return camera.simulate(pdf.rasterise(doc, 2 * x_height, size), rng, 0.5, severity)
    printed_x_height = pdf.X_HEIGHT_EM * size * printer_dpi / 72
    dots = np.where(pdf.rasterise(doc, printed_x_height, size) < 128, 0, 255).astype(np.uint8)
    return camera.simulate(dots, rng, x_height / printed_x_height, severity)


def crops_of(page: segment.Page) -> tuple[np.ndarray, np.ndarray]:
    pairs = [segment.crop(page, symbol) for line in page.lines for symbol in line]
    if not pairs:
        return np.zeros((0, segment.CROP, segment.CROP), np.uint8), np.zeros((0, 2), np.float32)
    return np.stack([p[0] for p in pairs]), np.stack([p[1] for p in pairs])


@dataclass
class Reading:
    chars: list[str]  # recognised character per symbol
    variant_probabilities: list[np.ndarray | None]  # per symbol, over the letter's codebook entries
    message: bytes | None
    block_distances: list[int]


def read(probabilities: np.ndarray, codebook: Codebook, variants: int, soft: bool = False) -> Reading:
    """`probabilities` has one row per symbol in reading order, one column per class."""
    carrier_part = probabilities[:, : len(CARRIERS) * variants].reshape(len(probabilities), len(CARRIERS), variants)
    char_scores = np.concatenate([carrier_part.sum(axis=2), probabilities[:, len(CARRIERS) * variants :]], axis=1)
    chars = [CHARSET[i] for i in char_scores.argmax(axis=1)]
    per_symbol: list[np.ndarray | None] = []
    for row, char in enumerate(chars):
        if char in CARRIERS:
            p = carrier_part[row, CARRIERS.index(char), codebook[char]]
            per_symbol.append(p / max(float(p.sum()), 1e-9))
        else:
            per_symbol.append(None)
    positions, caps = capacities(chars, codebook)
    blocks = coding.make_blocks(caps)
    bits, distances = coding.decode(blocks, {i: per_symbol[p] for i, p in enumerate(positions)}, soft)
    return Reading(chars, per_symbol, coding.unframe(bits), distances)


def load_model(variants: int) -> model.GlyphNet:
    import torch

    from .text import class_count

    net = model.GlyphNet(class_count(variants))
    net.load_state_dict(torch.load(MODEL_PATH, map_location="cpu"))
    return net.to(model.device()).eval()


def labels_of(glyphs: list[pdf.Glyph], variants: int) -> np.ndarray:
    return np.array([class_of(g.char, g.variant, variants) for g in glyphs], dtype=np.int16)


def lines_of(glyphs: list[pdf.Glyph]) -> list[list[pdf.Glyph]]:
    lines: dict[float, list[pdf.Glyph]] = {}
    for glyph in glyphs:
        lines.setdefault(round(glyph.y, 1), []).append(glyph)
    return [sorted(line, key=lambda g: g.x) for _, line in sorted(lines.items())]
