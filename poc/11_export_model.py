"""Step 11: export the network to LiteRT for the app.

Runs in its own environment, because the converter pins its own torch:
  python3 -m venv .venv-export
  .venv-export/bin/pip install litert-torch torch numpy opencv-python-headless pymupdf fonttools
  HIPS_BUILD=build-s1.0-14pt .venv-export/bin/python 11_export_model.py

Reads <build>/glyphnet-real.pt, writes <build>/glyphnet.tflite and checks it against PyTorch.
Inputs: crops float32 [64, 1, 64, 64] in 0..1 (0 = paper, 1 = ink) and features float32 [64, 2].
Output: float32 [64, classes], probabilities. The batch is fixed at 64; the app pads the last one.
"""

import litert_torch
import numpy as np
import torch
from ai_edge_litert.interpreter import Interpreter

from hips import fonts
from hips.model import GlyphNet
from hips.segment import CROP
from hips.text import class_count

BATCH = 64


class Probabilities(torch.nn.Module):
    def __init__(self, net: GlyphNet) -> None:
        super().__init__()
        self.net = net

    def forward(self, crops: torch.Tensor, features: torch.Tensor) -> torch.Tensor:
        return torch.softmax(self.net(crops, features), dim=1)


variants = fonts.variant_count()
net = GlyphNet(class_count(variants))
net.load_state_dict(torch.load(fonts.BUILD_DIR / "glyphnet-real.pt", map_location="cpu"))
module = Probabilities(net).eval()

# Real crops make a better check than noise: take them from the real dataset when there is one.
real = sorted(fonts.BUILD_DIR.glob("dataset-real*.npz"))
if real:
    data = np.load(real[0])
    crops = data["crops"][:BATCH].astype(np.float32)[:, None] / 255.0
    features = data["features"][:BATCH].astype(np.float32)
else:
    rng = np.random.default_rng(0)
    crops = rng.random((BATCH, 1, CROP, CROP), dtype=np.float32)
    features = rng.random((BATCH, 2), dtype=np.float32)

path = fonts.BUILD_DIR / "glyphnet.tflite"
litert_torch.convert(module, (torch.from_numpy(crops), torch.from_numpy(features))).export(str(path))

with torch.no_grad():
    expected = module(torch.from_numpy(crops), torch.from_numpy(features)).numpy()
interpreter = Interpreter(model_path=str(path))
interpreter.allocate_tensors()
for detail in interpreter.get_input_details():
    interpreter.set_tensor(detail["index"], crops if len(detail["shape"]) == 4 else features)
    print("input", detail["name"], detail["shape"].tolist(), detail["dtype"].__name__)
interpreter.invoke()
output = interpreter.get_output_details()[0]
actual = interpreter.get_tensor(output["index"])
print("output", output["name"], output["shape"].tolist())
print(f"{path.name}: {path.stat().st_size / 1e6:.1f} MB, largest difference to PyTorch {np.abs(actual - expected).max():.2e}, "
      f"same best class for {np.mean(actual.argmax(1) == expected.argmax(1)):.0%} of {BATCH} crops")
