"""PDF side: lay out a cover text, rewrite an existing PDF with variant glyphs, read it back, rasterise."""

from __future__ import annotations

from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

import numpy as np
import pymupdf

from . import fonts

X_HEIGHT_EM = 514 / 1000  # YTLC default of Roboto Flex: x-height as a fraction of the font size


@dataclass(frozen=True)
class Glyph:
    char: str
    x: float  # origin (left end of the advance, on the baseline), in PDF points
    y: float
    size: float
    variant: int = 0


@lru_cache(maxsize=None)
def _font(variant: int) -> pymupdf.Font:
    return pymupdf.Font(fontfile=str(fonts.font_path(variant)))


def layout(text: str, size: float, width: float, margin: float = 24.0, leading: float = 1.45) -> tuple[list[Glyph], float]:
    """Greedy word wrap with the advances of the base font. Returns the glyphs and the page height."""
    base = _font(0)
    glyphs: list[Glyph] = []
    space = base.glyph_advance(32) * size
    y = margin + size
    for paragraph in text.split("\n"):
        x = margin
        for word in paragraph.split():
            word_width = sum(base.glyph_advance(ord(c)) for c in word) * size
            if x > margin and x + word_width > width - margin:
                x = margin
                y += size * leading
            for c in word:
                glyphs.append(Glyph(c, x, y, size))
                x += base.glyph_advance(ord(c)) * size
            x += space
        y += size * leading
    return glyphs, y - size * leading + margin


def write_pdf(glyphs: list[Glyph], width: float, height: float, path: Path | None = None, base: pymupdf.Document | None = None) -> pymupdf.Document:
    """Draws every glyph on its own. All variants share the advances of the base font, so a
    variant sits exactly where the base glyph sat."""
    doc = base if base is not None else pymupdf.open()
    page = doc[0] if base is not None else doc.new_page(width=width, height=height)
    writer = pymupdf.TextWriter(page.rect)
    for glyph in glyphs:
        writer.append((glyph.x, glyph.y), glyph.char, font=_font(glyph.variant), fontsize=glyph.size)
    writer.write_text(page)
    if path is not None:
        doc.save(path, garbage=3, deflate=True)
    return doc


def read_glyphs(doc: pymupdf.Document) -> list[Glyph]:
    """Characters of page 0 in reading order, with the variant taken from the font name."""
    glyphs: list[Glyph] = []
    for block in doc[0].get_text("rawdict")["blocks"]:
        for line in block.get("lines", []):
            for span in line["spans"]:
                variant = fonts.variant_of_font_name(span["font"])
                for char in span["chars"]:
                    if char["c"].strip():
                        x, y = char["origin"]
                        glyphs.append(Glyph(char["c"], x, y, span["size"], -1 if variant is None else variant))
    glyphs.sort(key=lambda g: (round(g.y, 1), g.x))
    return glyphs


def embed(source: Path, variants: list[int], target: Path) -> None:
    """Rewrites the text of an existing PDF. `variants` has one entry per non-space character.

    Only text set in the base font can carry data; anything else stops the PoC.
    """
    doc = pymupdf.open(source)
    page = doc[0]
    originals = read_glyphs(doc)
    if any(g.variant != 0 for g in originals):
        raise ValueError("the PDF contains text that is not set in the base font")
    if len(originals) != len(variants):
        raise ValueError("one variant per character expected")
    page.add_redact_annot(page.rect, fill=False)
    page.apply_redactions(images=pymupdf.PDF_REDACT_IMAGE_NONE, graphics=pymupdf.PDF_REDACT_LINE_ART_NONE)
    rewritten = [Glyph(g.char, g.x, g.y, g.size, v) for g, v in zip(originals, variants)]
    write_pdf(rewritten, page.rect.width, page.rect.height, base=doc)
    doc.save(target, garbage=3, deflate=True)


def rasterise(doc: pymupdf.Document, x_height_px: float, size: float) -> np.ndarray:
    """Greyscale image of page 0 in which the x-height of `size`-point text is `x_height_px`."""
    zoom = x_height_px / (X_HEIGHT_EM * size)
    pixmap = doc[0].get_pixmap(matrix=pymupdf.Matrix(zoom, zoom), colorspace=pymupdf.csGRAY, alpha=False)
    return np.frombuffer(pixmap.samples, dtype=np.uint8).reshape(pixmap.height, pixmap.width).copy()
