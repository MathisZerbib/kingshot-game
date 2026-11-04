"""Shrinks the game's PNG sprite sheets with libimagequant (same algorithm as pngquant): 8-bit palette + alpha.

    python3 art/tools/shrink_png.py <repo_root> [--only name,name] [--dry]

Run it after any Blender / FX / UI render (render_*.py and make_*.py write full RGBA PNGs). A file is replaced
only when the quantised version is at least 15 % smaller and its colour error stays small (RMS < 8 / 255 on
visible pixels, mean alpha error < 0.6); otherwise the original is kept. Idempotent: an already paletted PNG
is skipped. Needs `pip install imagequant pillow numpy`.
"""
import io
import os
import sys

import imagequant
import numpy as np
from PIL import Image

ROOT = sys.argv[1]
ONLY = None
DRY = "--dry" in sys.argv
for i, a in enumerate(sys.argv):
    if a == "--only":
        ONLY = set(sys.argv[i + 1].split(","))
DRAW = os.path.join(ROOT, "composeApp/src/commonMain/composeResources/drawable")

before = after = 0
kept = []
for name in sorted(os.listdir(DRAW)):
    if not name.endswith(".png") or (ONLY and name[:-4] not in ONLY):
        continue
    path = os.path.join(DRAW, name)
    size0 = os.path.getsize(path)
    before += size0
    im = Image.open(path)
    if im.mode == "P":
        after += size0
        continue
    im = im.convert("RGBA")
    a = np.asarray(im).astype(np.float32)
    q = imagequant.quantize_pil_image(im, dithering_level=1.0, max_colors=256, min_quality=0, max_quality=95)
    buf = io.BytesIO()
    q.save(buf, "PNG", optimize=True)
    data = buf.getvalue()
    b = np.asarray(q.convert("RGBA")).astype(np.float32)
    vis = a[..., 3] > 8
    rms = float(np.sqrt(((a[vis, :3] - b[vis, :3]) ** 2).mean())) if vis.any() else 0.0
    da = float(np.abs(a[..., 3] - b[..., 3]).mean())
    ok = len(data) < size0 * 0.85 and rms < 8 and da < 0.6
    print("%-34s %6d -> %6d KB  rms %.1f  alpha %.2f  %s" % (name, size0 // 1024, len(data) // 1024, rms, da,
                                                          "ok" if ok else "kept"))
    if ok and not DRY:
        with open(path, "wb") as f:
            f.write(data)
        after += len(data)
    else:
        after += size0
        if not ok:
            kept.append(name)
print("total %d -> %d MB" % (before // 2**20, after // 2**20), "kept:", ", ".join(kept) or "none")
