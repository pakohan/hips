"""Finds the characters of a photographed text in reading order and cuts a normalised crop of each.

No OCR engine: connected components are traced into lines from left to right, which tolerates a
few degrees of tilt and mild perspective without rectifying the image first.
"""

from __future__ import annotations

import os
from dataclasses import dataclass

import cv2
import numpy as np

CROP = 64  # crop edge in pixels
CROP_X_HEIGHT = 26.0  # every crop is scaled so the page's x-height becomes this many pixels
SEPARATION_THRESHOLD = float(os.environ.get("HIPS_SEPARATION", "0.6"))  # fraction of solid ink


@dataclass
class Symbol:
    x0: int
    y0: int
    x1: int  # exclusive
    y1: int
    line: int = -1
    baseline: float = 0.0


@dataclass
class Page:
    darkness: np.ndarray  # float32, 0 = paper, 1 = solid ink
    x_height: float
    lines: list[list[Symbol]]


def _darkness(gray: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Removes uneven light and scales ink to 1. Returns (darkness, binary mask)."""
    height, width = gray.shape
    small = cv2.resize(gray, (max(1, width // 4), max(1, height // 4)), interpolation=cv2.INTER_AREA)
    kernel = max(31, small.shape[1] // 20) | 1
    background = cv2.morphologyEx(small, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (kernel, kernel)))
    background = cv2.GaussianBlur(background, (0, 0), kernel / 2)
    background = cv2.resize(background, (width, height), interpolation=cv2.INTER_LINEAR).astype(np.float32)
    darkness = 1.0 - np.clip(gray.astype(np.float32) / np.maximum(background, 1.0), 0.0, 1.0)
    as_bytes = (darkness * 255).astype(np.uint8)
    threshold, binary = cv2.threshold(as_bytes, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    ink_level = float(np.percentile(darkness[binary > 0], 90)) if binary.any() else 1.0
    darkness = np.clip(darkness / max(ink_level, 1e-3), 0.0, 1.25)
    # Otsu sits near half the ink level, where the blur between two close letters still counts
    # as ink. A stricter cut keeps neighbours apart.
    return darkness, (darkness > SEPARATION_THRESHOLD).astype(np.uint8)


def _paper_mask(gray: np.ndarray) -> np.ndarray:
    """1 where the sheet of paper is, shrunk a little so its edge and shadow stay outside.

    The paper is taken to be the largest region brighter than 60% of the brightest areas.
    """
    height, width = gray.shape
    scale = 400.0 / width
    small = cv2.resize(gray, (400, max(1, round(height * scale))), interpolation=cv2.INTER_AREA)
    small = cv2.GaussianBlur(small, (0, 0), 4)  # text lines dissolve into light grey
    bright = (small > 0.6 * np.percentile(small, 95)).astype(np.uint8)
    count, labels, stats, _ = cv2.connectedComponentsWithStats(bright, connectivity=8)
    if count < 2:
        return np.ones_like(gray, dtype=np.uint8)
    largest = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
    contours, _ = cv2.findContours((labels == largest).astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    mask = np.zeros_like(bright)
    cv2.drawContours(mask, contours, -1, 1, cv2.FILLED)
    mask = cv2.erode(mask, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (21, 21)), borderValue=0)
    return cv2.resize(mask, (width, height), interpolation=cv2.INTER_NEAREST)


def _x_height(heights: np.ndarray) -> float:
    """Most common component height among the letter-sized components."""
    tall = heights[heights >= 0.35 * np.percentile(heights, 90)]
    histogram = np.bincount(tall.astype(int))
    smoothed = np.convolve(histogram, np.ones(3), mode="same")
    return float(np.argmax(smoothed))


def segment(gray: np.ndarray) -> Page:
    darkness, binary = _darkness(gray)
    binary = binary * _paper_mask(gray)
    count, _, stats, _ = cv2.connectedComponentsWithStats(binary, connectivity=8)
    boxes = stats[1:count]
    if len(boxes) < 5:
        return Page(darkness, 0.0, [])
    x_height = _x_height(boxes[:, cv2.CC_STAT_HEIGHT])
    boxes = boxes[boxes[:, cv2.CC_STAT_AREA] >= max(2.0, (0.07 * x_height) ** 2)]
    # A real photo shows the table around the page and the shadow at its edge: not letters.
    boxes = boxes[(boxes[:, cv2.CC_STAT_HEIGHT] <= 3.0 * x_height) & (boxes[:, cv2.CC_STAT_WIDTH] <= 4.0 * x_height)]
    symbols = [Symbol(int(b[0]), int(b[1]), int(b[0] + b[2]), int(b[1] + b[3])) for b in boxes]
    symbols.sort(key=lambda s: s.x0)

    # Trace lines through the letter-sized components by following their bottom edges.
    big = [s for s in symbols if s.y1 - s.y0 >= 0.7 * x_height]
    small = [s for s in symbols if s.y1 - s.y0 < 0.7 * x_height]
    # The running baseline of a line is the median bottom of its last few letters: descenders are
    # a minority, and a short window follows the tilt of the line.
    members: list[list[Symbol]] = []
    for symbol in big:
        baselines = [float(np.median([m.y1 for m in line_members[-5:]])) for line_members in members]
        distances = [abs(symbol.y1 - baseline) for baseline in baselines]
        line = int(np.argmin(distances)) if distances else -1
        if line < 0 or distances[line] > 0.9 * x_height:
            members.append([])
            line = len(members) - 1
        symbol.line = line
        members[line].append(symbol)
    for line_members in members:
        bottoms = [m.y1 for m in line_members]
        for index, symbol in enumerate(line_members):
            symbol.baseline = float(np.median(bottoms[max(0, index - 4) : index + 5]))

    # Dots, commas, hyphens: attach to the line whose nearby letters they sit on or above.
    for symbol in small:
        centre_x = (symbol.x0 + symbol.x1) / 2
        centre_y = (symbol.y0 + symbol.y1) / 2
        best, best_distance = -1, 1.6 * x_height
        for line, line_members in enumerate(members):
            neighbour = min(line_members, key=lambda m: abs((m.x0 + m.x1) / 2 - centre_x))
            distance = abs(centre_y - (neighbour.baseline - 0.5 * x_height))
            if distance < best_distance:
                best, best_distance = line, distance
                symbol.baseline = neighbour.baseline
        if best >= 0:
            symbol.line = best
            members[best].append(symbol)

    lines: list[list[Symbol]] = []
    for line_members in members:
        line_members.sort(key=lambda s: s.x0)
        merged: list[Symbol] = []
        for symbol in line_members:
            previous = merged[-1] if merged else None
            # Only a dot-sized part joins its neighbour: the hook of a "j" may reach under the
            # letter before it, and two letters must never merge.
            if previous is not None and min(previous.y1 - previous.y0, symbol.y1 - symbol.y0) < 0.7 * x_height:
                overlap = min(previous.x1, symbol.x1) - max(previous.x0, symbol.x0)
                if overlap >= 0.5 * min(previous.x1 - previous.x0, symbol.x1 - symbol.x0):
                    previous.x0, previous.y0 = min(previous.x0, symbol.x0), min(previous.y0, symbol.y0)
                    previous.x1, previous.y1 = max(previous.x1, symbol.x1), max(previous.y1, symbol.y1)
                    continue
            merged.append(symbol)
        lines.append(merged)
    lines.sort(key=lambda line: line[0].baseline)
    return Page(darkness, x_height, lines)


def crop(page: Page, symbol: Symbol) -> tuple[np.ndarray, np.ndarray]:
    """(CROP x CROP uint8 image, [relative height, relative bottom offset]).

    The scale is tied to the x-height of the page, not to the symbol's own box, so stroke widths
    stay comparable between crops and upper/lower case pairs of the same shape stay apart.
    """
    scale = CROP_X_HEIGHT / page.x_height
    # One pixel of margin keeps the anti-aliased fringe that the binary mask cut off.
    patch = page.darkness[max(0, symbol.y0 - 1) : symbol.y1 + 1, max(0, symbol.x0 - 1) : symbol.x1 + 1]
    width = max(1, min(CROP, round(patch.shape[1] * scale)))
    height = max(1, min(CROP, round(patch.shape[0] * scale)))
    resized = cv2.resize(patch, (width, height), interpolation=cv2.INTER_AREA if scale < 1 else cv2.INTER_CUBIC)
    canvas = np.zeros((CROP, CROP), dtype=np.float32)
    top, left = (CROP - height) // 2, (CROP - width) // 2
    canvas[top : top + height, left : left + width] = resized
    features = np.array(
        [(symbol.y1 - symbol.y0) / page.x_height, (symbol.y1 - symbol.baseline) / page.x_height],
        dtype=np.float32,
    )
    return (np.clip(canvas, 0.0, 1.0) * 255).astype(np.uint8), features
