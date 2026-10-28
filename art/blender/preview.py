"""Builds a labeled preview sheet of the rendered sprites: python preview.py <repo_root> <out.png>"""
import re
import sys

from PIL import Image, ImageDraw, ImageFont

root, out = sys.argv[1], sys.argv[2]
src = open(f"{root}/composeApp/src/commonMain/kotlin/fr/kingshot/game/SpriteData.kt").read()
info = {}
for m in re.finditer(r"val (\w+) = SpriteInfo\((\d+), (\d+), ([\d.]+)f, ([\d.]+)f, ([\d.]+)f, (\d+), (\d+)\)", src):
    info[m.group(1)] = dict(w=int(m.group(2)), h=int(m.group(3)), ppu=float(m.group(6)), cols=int(m.group(7)), rows=int(m.group(8)))
snake = {"coinSpin": "coin_spin", "coinFlat": "coin_flat"}


def cell(name, col, row):
    i = info[name]
    im = Image.open(f"{root}/composeApp/src/commonMain/composeResources/drawable/{snake.get(name, name)}.png").convert("RGBA")
    return im.crop((col * i["w"], row * i["h"], (col + 1) * i["w"], (row + 1) * i["h"]))


# (label, name, col, row, scale factor relative to game scale)
picks = [
    ("roi (face)", "king", 0, 0), ("roi (galop)", "king", 3, 1), ("roi (dos)", "king", 2, 4), ("roi (profil)", "king", 4, 6),
    ("archer", "archer", 0, 0), ("archer (course)", "archer", 3, 1), ("archer (tir)", "archer", 8, 2), ("archer (dos)", "archer", 0, 4),
    ("chevalier", "knight", 0, 0), ("chevalier (course)", "knight", 2, 1), ("chevalier (attaque)", "knight", 7, 2), ("chevalier (profil)", "knight", 1, 6),
    ("boss", "boss", 0, 0), ("boss (marche)", "boss", 2, 1), ("boss (attaque)", "boss", 5, 2), ("boss (dos)", "boss", 1, 5),
    ("donjon", "castle", 0, 0), ("maison des archers", "house", 0, 0), ("tour de guet", "tower", 0, 0), ("palissade", "stake", 0, 0),
    ("sapin", "pine0", 0, 0), ("sapin 2", "pine1", 0, 0), ("arbre", "roundtree", 0, 0), ("rocher", "rock0", 0, 0), ("rocher 2", "rock1", 0, 0),
    ("pièce", "coinSpin", 0, 0), ("pièce", "coinSpin", 2, 0), ("pièce à plat", "coinFlat", 0, 0),
]
items = []
for label, name, col, row in picks:
    im = cell(name, col, row)
    # scale every sprite to the same on-screen scale (game units), 2.2 screen px per unit
    k = (1.5 if info[name]["rows"] == 8 else 1.0) / info[name]["ppu"] * 2.2
    im = im.resize((max(1, int(im.width * k)), max(1, int(im.height * k))), Image.LANCZOS)
    items.append((label, im))

W = 1900
font = ImageFont.load_default(size=22)
rows, cur, cx, rh = [], [], 0, 0
for label, im in items:
    if cx + im.width + 40 > W and cur:
        rows.append((cur, rh))
        cur, cx, rh = [], 0, 0
    cur.append((label, im))
    cx += im.width + 40
    rh = max(rh, im.height)
rows.append((cur, rh))
H = sum(r[1] + 70 for r in rows) + 40
sheet = Image.new("RGBA", (W, H), (86, 140, 78, 255))
d = ImageDraw.Draw(sheet)
y = 30
for cur, rh in rows:
    x = 30
    for label, im in cur:
        sheet.alpha_composite(im, (x, y + rh - im.height))
        d.text((x + im.width / 2, y + rh + 14), label, fill=(255, 250, 235), font=font, anchor="mt")
        x += im.width + 40
    y += rh + 70
sheet.convert("RGB").save(out, quality=92)
print("wrote", out, sheet.size)
