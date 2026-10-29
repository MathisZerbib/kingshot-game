"""Shrinks oversampled unit atlases (ppu 4.5 -> 3, about the on-screen density) and patches SpriteData.kt to match.
Saves ~55 % of their texture memory with no visible loss. Run: python3 art/tools/downscale_atlases.py"""
import os, re
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../..')
DRAW = os.path.join(ROOT, 'composeApp/src/commonMain/composeResources/drawable')
DATA = os.path.join(ROOT, 'composeApp/src/commonMain/kotlin/fr/kingshot/game/SpriteData.kt')
TARGET_PPU = 3.0

def snake(n): return re.sub(r'([A-Z])', r'_\1', n).lower()

src = open(DATA).read()
pat = re.compile(r'val (\w+) = SpriteInfo\((\d+), (\d+), ([\d.]+)f, ([\d.]+)f, ([\d.]+)f, (\d+), (\d+)\)')
def fix(m):
    name, w, h, ax, ay, ppu, cols, rows = m.groups()
    w, h, cols, rows, ppu, ax, ay = int(w), int(h), int(cols), int(rows), float(ppu), float(ax), float(ay)
    if ppu <= TARGET_PPU + 0.01 or cols * rows < 2:
        return m.group(0)
    f = TARGET_PPU / ppu
    nw, nh = round(w * f), round(h * f)
    path = os.path.join(DRAW, snake(name) + '.png')
    im = Image.open(path)
    assert im.size == (w * cols, h * rows), (name, im.size)
    im.resize((nw * cols, nh * rows), Image.LANCZOS).save(path, optimize=True)
    k = nw / w  # exact cell ratio, applied to anchor and density
    print(f'{name}: {w}x{h} -> {nw}x{nh}')
    return f'val {name} = SpriteInfo({nw}, {nh}, {ax * k:.1f}f, {ay * k:.1f}f, {ppu * k:.3f}f, {cols}, {rows})'
open(DATA, 'w').write(pat.sub(fix, src))
