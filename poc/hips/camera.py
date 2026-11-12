"""Simulated phone camera: turns a clean page raster into something like a photo of a print-out.

This is a stand-in for real photos, not a replacement. It covers ink gain, a hand-held viewpoint,
defocus, uneven light, sensor noise and JPEG compression. It does not cover paper texture, curved
pages, moire from screens, or glare.
"""

from __future__ import annotations

import cv2
import numpy as np


def simulate(page: np.ndarray, rng: np.random.Generator, scale: float = 0.5, severity: float = 1.0) -> np.ndarray:
    """`page` is greyscale uint8, black on white. The result is `scale` times its size."""
    ink = 1.0 - page.astype(np.float32) / 255.0

    # Ink gain or loss: strokes print fatter or thinner than the outline says.
    gain = float(np.exp(rng.uniform(-0.25, 0.25) * severity))
    ink = np.power(cv2.GaussianBlur(ink, (0, 0), 0.6 / scale * 0.5), gain)

    height, width = ink.shape
    border = int(0.04 * max(height, width))
    ink = cv2.copyMakeBorder(ink, border, border, border, border, cv2.BORDER_CONSTANT, value=0.0)
    height, width = ink.shape

    # Viewpoint: rotation plus independent corner jitter (perspective).
    angle = np.deg2rad(rng.uniform(-1.5, 1.5) * severity)
    corners = np.array([[0, 0], [width, 0], [width, height], [0, height]], dtype=np.float32)
    centre = corners.mean(axis=0)
    rotation = np.array([[np.cos(angle), -np.sin(angle)], [np.sin(angle), np.cos(angle)]], dtype=np.float32)
    target = (corners - centre) @ rotation.T * scale
    target += rng.uniform(-1, 1, target.shape).astype(np.float32) * 0.008 * severity * min(height, width) * scale
    target -= target.min(axis=0)
    out_size = (int(np.ceil(target[:, 0].max())), int(np.ceil(target[:, 1].max())))
    homography = cv2.getPerspectiveTransform(corners, target.astype(np.float32))
    ink = cv2.GaussianBlur(ink, (0, 0), 0.45 / scale)  # anti-alias before sampling down
    ink = cv2.warpPerspective(ink, homography, out_size, flags=cv2.INTER_LINEAR, borderValue=0.0)

    # Defocus.
    sigma = rng.uniform(0.2, 0.2 + 0.9 * severity)
    ink = cv2.GaussianBlur(ink, (0, 0), sigma)

    # Paper and ink brightness, uneven light.
    paper = rng.uniform(200, 250)
    black = rng.uniform(15, 70)
    image = paper - (paper - black) * ink
    ys, xs = np.mgrid[0 : image.shape[0], 0 : image.shape[1]].astype(np.float32)
    direction = rng.uniform(0, 2 * np.pi)
    ramp = (xs / image.shape[1] - 0.5) * np.cos(direction) + (ys / image.shape[0] - 0.5) * np.sin(direction)
    image *= 1.0 + ramp * rng.uniform(0, 0.35) * severity

    image += rng.normal(0, rng.uniform(1, 1 + 5 * severity), image.shape)
    image = np.clip(image, 0, 255).astype(np.uint8)
    quality = int(rng.uniform(90 - 35 * min(severity, 1.5), 95))
    ok, encoded = cv2.imencode(".jpg", image, [cv2.IMWRITE_JPEG_QUALITY, quality])
    assert ok
    return cv2.imdecode(encoded, cv2.IMREAD_GRAYSCALE)
