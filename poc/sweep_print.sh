#!/usr/bin/env bash
# Which strength and text size survive a printer? Trains a reduced network per configuration on
# photos simulated with a one-bit printer raster and reports recognition and codebook size.
# Usage: ./sweep_print.sh <printer dpi> "<strength> <size>" ["<strength> <size>" ...]
# Each configuration gets its own build directory build-s<strength>-<size>pt-<dpi>dpi (about 8 minutes on an M4).
set -euo pipefail
cd "$(dirname "$0")"
dpi=$1
shift
for config in "$@"; do
  read -r strength size <<<"$config"
  export HIPS_BUILD="build-s${strength}-${size}pt-${dpi}dpi" HIPS_SIZE="$size" HIPS_PRINTER_DPI="$dpi"
  mkdir -p "$HIPS_BUILD"
  {
    echo "== strength $strength, $size pt, printer $dpi dpi"
    .venv/bin/python 01_build_fonts.py "$strength"
    .venv/bin/python 02_make_dataset.py train 300 1 20 50
    .venv/bin/python 02_make_dataset.py test 60 2 20 50
    .venv/bin/python -u 03_train.py 4
    .venv/bin/python 04_build_codebook.py
    echo "== finished"
  } >"$HIPS_BUILD/sweep.log" 2>&1
done
