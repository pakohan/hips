"""One small CNN that names the character and its variant at once.

The paper trains one CNN per letter on 200x200 crops and gets the letter from Tesseract. Here a
single network outputs a class per (letter, variant) pair, so no OCR engine is needed.
"""

from __future__ import annotations

import numpy as np
import torch
from torch import nn

from .segment import CROP


def device() -> torch.device:
    return torch.device("mps" if torch.backends.mps.is_available() else "cpu")


class GlyphNet(nn.Module):
    def __init__(self, classes: int) -> None:
        super().__init__()

        def stage(inputs: int, outputs: int) -> list[nn.Module]:
            return [nn.Conv2d(inputs, outputs, 3, padding=1), nn.BatchNorm2d(outputs), nn.ReLU(), nn.MaxPool2d(2)]

        self.features = nn.Sequential(*stage(1, 32), *stage(32, 64), *stage(64, 128), *stage(128, 128))
        self.head = nn.Sequential(
            nn.Linear(128 * (CROP // 16) ** 2 + 2, 256), nn.ReLU(), nn.Dropout(0.3), nn.Linear(256, classes)
        )

    def forward(self, crops: torch.Tensor, features: torch.Tensor) -> torch.Tensor:
        return self.head(torch.cat([self.features(crops).flatten(1), features], dim=1))


def to_tensors(crops: np.ndarray, features: np.ndarray, where: torch.device) -> tuple[torch.Tensor, torch.Tensor]:
    return (
        torch.from_numpy(crops).to(where).float().div_(255.0).unsqueeze(1),
        torch.from_numpy(features).to(where).float(),
    )


@torch.no_grad()
def predict(model: GlyphNet, crops: np.ndarray, features: np.ndarray, batch: int = 2048) -> np.ndarray:
    """Class probabilities, one row per crop."""
    model.eval()
    where = next(model.parameters()).device
    out = []
    for start in range(0, len(crops), batch):
        x, f = to_tensors(crops[start : start + batch], features[start : start + batch], where)
        out.append(torch.softmax(model(x, f), dim=1).cpu().numpy())
    return np.concatenate(out) if out else np.zeros((0, 0), dtype=np.float32)


def train(
    model: GlyphNet,
    crops: np.ndarray,
    features: np.ndarray,
    labels: np.ndarray,
    epochs: int,
    batch: int = 256,
    lr: float = 2e-3,
    log=print,
) -> None:
    where = device()
    model.to(where)
    optimiser = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=1e-4)
    steps = epochs * (len(crops) // batch)
    schedule = torch.optim.lr_scheduler.OneCycleLR(optimiser, max_lr=lr, total_steps=steps)
    loss_fn = nn.CrossEntropyLoss(label_smoothing=0.05)
    all_labels = torch.from_numpy(labels.astype(np.int64))
    for epoch in range(epochs):
        model.train()
        order = torch.randperm(len(crops))
        total, correct, loss_sum = 0, 0, 0.0
        for start in range(0, len(order) - batch + 1, batch):
            index = order[start : start + batch]
            x, f = to_tensors(crops[index.numpy()], features[index.numpy()], where)
            y = all_labels[index].to(where)
            # A sub-pixel shift is the only augmentation; everything else is in the simulated camera.
            x = torch.roll(x, shifts=(int(torch.randint(-1, 2, ())), int(torch.randint(-1, 2, ()))), dims=(2, 3))
            logits = model(x, f)
            loss = loss_fn(logits, y)
            optimiser.zero_grad()
            loss.backward()
            optimiser.step()
            schedule.step()
            total += len(y)
            correct += int((logits.argmax(1) == y).sum())
            loss_sum += loss.item() * len(y)
        log(f"epoch {epoch + 1}/{epochs} loss {loss_sum / total:.3f} train acc {correct / total:.3f}")
