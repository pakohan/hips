"""Glyph variants: static instances of the Roboto Flex variable font.

The paper perturbs glyphs on a learned font manifold (Campbell and Kautz 2014). We have no such
model, so the parametric axes of a variable font stand in for it: every candidate variant is one
point in (XOPQ, YOPQ, XTRA) space near the default instance. See docs/adr/0002.
"""

from __future__ import annotations

import itertools
import json
import os
from pathlib import Path

from fontTools import subset
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.recordingPen import DecomposingRecordingPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

POC_DIR = Path(__file__).resolve().parent.parent
SOURCE_FONT = POC_DIR / "fonts" / "RobotoFlex.ttf"
# One build directory per experiment (strength, text size, printer): HIPS_BUILD=build-s15-18pt etc.
BUILD_DIR = POC_DIR / os.environ.get("HIPS_BUILD", "build")
FONT_DIR = BUILD_DIR / "fonts"

# Axis steps around the default instance. Variant 0 is the unmodified font.
AXIS_DEFAULTS = {"XOPQ": 96.0, "YOPQ": 79.0, "XTRA": 468.0}
AXIS_STEPS = {"XOPQ": 18.0, "YOPQ": 16.0, "XTRA": 30.0}
LEVELS = (0, -1, 1)
MIN_BEARING = 60  # font units (2048 per em)

FONT_NAME_PREFIX = "HIPSFlex"


def candidate_locations(strength: float = 1.0) -> list[dict[str, float]]:
    """All candidate variants, index 0 being the default instance."""
    locations = []
    for combo in itertools.product(LEVELS, repeat=len(AXIS_DEFAULTS)):
        locations.append(
            {
                tag: AXIS_DEFAULTS[tag] + level * AXIS_STEPS[tag] * strength
                for tag, level in zip(AXIS_DEFAULTS, combo)
            }
        )
    return locations


def font_name(variant: int) -> str:
    return f"{FONT_NAME_PREFIX}{variant:02d}"


def variant_of_font_name(name: str) -> int | None:
    marker = name.rfind(FONT_NAME_PREFIX)
    if marker < 0:
        return None
    digits = name[marker + len(FONT_NAME_PREFIX) : marker + len(FONT_NAME_PREFIX) + 2]
    return int(digits) if digits.isdigit() else None


def font_path(variant: int) -> Path:
    return FONT_DIR / f"{font_name(variant)}.ttf"


def _rename(font: TTFont, name: str) -> None:
    table = font["name"]
    table.names = [record for record in table.names if record.nameID in (1, 2, 3, 4, 6)]
    for record in list(table.names):
        if record.nameID != 2:
            table.setName(name, record.nameID, record.platformID, record.platEncID, record.langID)


def _fit_to_base(instance: TTFont, base: TTFont) -> None:
    """Squeezes every glyph into the horizontal extent and advance of the base glyph (paper 4.5).

    Without this a bolder or wider variant eats the gap to its neighbour: letters touch in the
    photo and the layout of the document would have to move.
    """
    glyph_set, base_set = instance.getGlyphSet(), base.getGlyphSet()
    fitted = {}
    for name in instance.getGlyphOrder():
        bounds = []
        for source in (base_set, glyph_set):
            pen = BoundsPen(source)
            source[name].draw(pen)
            bounds.append(pen.bounds)
        base_bounds, own_bounds = bounds
        matrix = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        if base_bounds and own_bounds and own_bounds[2] > own_bounds[0]:
            # Roboto lets some pairs join ("ft", "ff") or nearly join ("rv"). A minimum side
            # bearing keeps a gap between any two letters, which the segmentation relies on.
            # A negative left bearing (the hook of "j") is left alone.
            advance = base["hmtx"][name][0]
            left = max(base_bounds[0], MIN_BEARING) if base_bounds[0] >= 0 else base_bounds[0]
            right = max(min(base_bounds[2], advance - MIN_BEARING), left + 1)
            scale = (right - left) / (own_bounds[2] - own_bounds[0])
            matrix = (scale, 0.0, 0.0, 1.0, left - own_bounds[0] * scale, 0.0)
        recording = DecomposingRecordingPen(glyph_set)
        glyph_set[name].draw(recording)
        pen = TTGlyphPen(None)
        recording.replay(TransformPen(pen, matrix))
        fitted[name] = pen.glyph()
    for name, glyph in fitted.items():
        instance["glyf"][name] = glyph
        instance["hmtx"][name] = base["hmtx"][name]
    for table in ("fpgm", "prep", "cvt "):  # the hinting programs no longer match the outlines
        if table in instance:
            del instance[table]


def build_fonts(strength: float = 1.0) -> list[dict[str, float]]:
    """Writes one static, ASCII-only TTF per candidate variant and returns the axis locations."""
    FONT_DIR.mkdir(parents=True, exist_ok=True)
    options = subset.Options()
    options.layout_features = []  # no kerning, no ligatures: every character is placed on its own
    options.notdef_outline = True
    options.name_IDs = ["*"]
    variable = TTFont(SOURCE_FONT)
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=range(0x20, 0x7F))
    subsetter.subset(variable)
    subset_path = FONT_DIR / "subset-variable.ttf"
    variable.save(subset_path)

    all_defaults = {axis.axisTag: axis.defaultValue for axis in variable["fvar"].axes}
    locations = candidate_locations(strength)
    base: TTFont | None = None
    for variant, location in enumerate(locations):
        instance = instancer.instantiateVariableFont(TTFont(subset_path), {**all_defaults, **location})
        base = base or instance
        _fit_to_base(instance, base)
        _rename(instance, font_name(variant))
        instance.save(font_path(variant))
    (FONT_DIR / "variants.json").write_text(json.dumps({"strength": strength, "locations": locations}, indent=1))
    return locations


def variant_count() -> int:
    return len(json.loads((FONT_DIR / "variants.json").read_text())["locations"])
