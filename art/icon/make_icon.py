"""Hexcrown app icon: gold hexagon badge with a white crown on a navy-blue hex lattice.
Writes the Android launcher resources (adaptive + legacy), the in-game logo, the 512 px store icon and the
1024x500 feature graphic.  Run: python3 art/icon/make_icon.py   (needs Pillow + numpy)"""
import math, os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '../..')
RES = os.path.join(ROOT, 'composeApp/src/androidMain/res')
DRAW = os.path.join(ROOT, 'composeApp/src/commonMain/composeResources/drawable')
FONT = os.path.join(ROOT, 'composeApp/src/commonMain/composeResources/font/lilita_one_regular.ttf')
N = 1024  # working size (108 dp adaptive canvas); the safe zone is the central 66 dp = 62 %
NAVY, BLUE, INK = (12, 19, 38), (30, 70, 170), (11, 19, 38)
GOLD, GOLD_DARK, GOLD_LIGHT = (255, 197, 49), (196, 128, 20), (255, 236, 160)


def hexagon(cx, cy, r, rot=-90):
    return [(cx + r * math.cos(math.radians(rot + 60 * k)), cy + r * math.sin(math.radians(rot + 60 * k))) for k in range(6)]


def radial(w, h, c0, c1, cx=0.5, cy=0.42, rad=0.75):
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.clip(np.hypot(xx - w * cx, yy - h * cy) / (max(w, h) * rad), 0, 1)[..., None]
    rgb = np.array(c0, np.float32) * (1 - d) + np.array(c1, np.float32) * d
    out = np.zeros((h, w, 4), np.uint8); out[..., :3] = rgb.astype(np.uint8); out[..., 3] = 255
    return Image.fromarray(out, 'RGBA')


def lattice(w, h, r, alpha=26, width=3):
    """Thin pointy-top hex lattice, fading toward the edges."""
    lay = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    dx, dy = r * math.sqrt(3), r * 1.5
    row = 0
    y = -r
    while y < h + r:
        x = -dx + (dx / 2 if row % 2 else 0)
        while x < w + dx:
            d.polygon(hexagon(x, y, r), outline=(255, 255, 255, alpha), width=width)
            x += dx
        y += dy; row += 1
    mask = radial(w, h, (255, 255, 255), (0, 0, 0), rad=0.6).convert('L')
    lay.putalpha(Image.fromarray((np.array(lay.split()[3], np.float32) * np.array(mask, np.float32) / 255).astype(np.uint8)))
    return lay


def background(w=N, h=N):
    bg = radial(w, h, BLUE, NAVY)
    return Image.alpha_composite(bg, lattice(w, h, w * 0.11))


def crown(d, cx, cy, s, fill=(255, 255, 255), shadow=None):
    """Three-point crown; s = half width."""
    base = cy + s * 0.55
    pts = [(cx - s, base), (cx - s, cy - s * 0.35), (cx - s * 0.5, cy + s * 0.05), (cx, cy - s * 0.7),
           (cx + s * 0.5, cy + s * 0.05), (cx + s, cy - s * 0.35), (cx + s, base)]
    if shadow:
        d.polygon([(x + s * 0.05, y + s * 0.08) for x, y in pts], fill=shadow)
    d.polygon(pts, fill=fill)
    d.rounded_rectangle([cx - s, base - s * 0.2, cx + s, base + s * 0.05], radius=s * 0.05, fill=fill)
    for x, y in [(cx - s, cy - s * 0.35), (cx, cy - s * 0.7), (cx + s, cy - s * 0.35)]:
        d.ellipse([x - s * 0.11, y - s * 0.11, x + s * 0.11, y + s * 0.11], fill=fill)
    d.ellipse([cx - s * 0.13, base - s * 0.22, cx + s * 0.13, base + s * 0.04], fill=(225, 48, 70))  # ruby


def badge(size, cx=None, cy=None, r=None):
    """Gold hexagon with a navy core and a white crown, with drop shadow, on a transparent canvas."""
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    cx = cx or size / 2; cy = cy or size * 0.5; r = r or size * 0.30
    sh = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(sh).polygon(hexagon(cx, cy + r * 0.08, r * 1.02), fill=(0, 0, 0, 140))
    im = Image.alpha_composite(im, sh.filter(ImageFilter.GaussianBlur(size * 0.02)))
    d = ImageDraw.Draw(im)
    d.polygon(hexagon(cx, cy, r), fill=GOLD_DARK)
    d.polygon(hexagon(cx, cy - r * 0.035, r * 0.96), fill=GOLD)
    # top-left highlight on the rim
    hl = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(hl).polygon(hexagon(cx, cy - r * 0.035, r * 0.96), fill=(255, 255, 255, 0))
    d.polygon(hexagon(cx, cy - r * 0.035, r * 0.96)[0:3] + [(cx, cy - r * 0.035)], fill=GOLD_LIGHT + (0,))
    d.polygon(hexagon(cx, cy, r * 0.74), fill=INK)
    d.polygon(hexagon(cx, cy, r * 0.70), fill=(20, 38, 86))
    # inner glow at the bottom of the core
    glow = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([cx - r * 0.6, cy + r * 0.1, cx + r * 0.6, cy + r * 0.9], fill=(60, 120, 255, 110))
    core = Image.new('L', (size, size), 0); ImageDraw.Draw(core).polygon(hexagon(cx, cy, r * 0.70), fill=255)
    glow = glow.filter(ImageFilter.GaussianBlur(size * 0.03)); glow.putalpha(Image.fromarray((np.array(glow.split()[3]) * (np.array(core) / 255)).astype(np.uint8)))
    im = Image.alpha_composite(im, glow)
    d = ImageDraw.Draw(im)
    crown(d, cx, cy + r * 0.06, r * 0.48, shadow=(0, 0, 0, 90))
    return im


def rounded(im, r):
    mask = Image.new('L', im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, im.size[0] - 1, im.size[1] - 1], r, fill=255)
    out = Image.new('RGBA', im.size, (0, 0, 0, 0))
    out.paste(im, (0, 0), mask)
    return out


def feature_graphic(w=1024, h=500):
    im = background(w * 2, h * 2)
    b = badge(h * 2, cx=h * 0.95, cy=h * 1.0, r=h * 0.62)
    im.alpha_composite(b, (0, 0))
    d = ImageDraw.Draw(im)
    size = int(h * 0.40)
    font = ImageFont.truetype(FONT, size)
    x0, y0 = int(h * 1.62), int(h * 0.96)
    d.text((x0, y0), 'HEXCROWN', font=font, fill=(0, 0, 0, 120), anchor='lm', stroke_width=16, stroke_fill=(0, 0, 0, 120))
    d.text((x0, y0 - 14), 'HEXCROWN', font=font, fill=INK, anchor='lm', stroke_width=16, stroke_fill=INK)
    # white → gold gradient through a mask
    mask = Image.new('L', im.size, 0); ImageDraw.Draw(mask).text((x0, y0 - 14), 'HEXCROWN', font=font, fill=255, anchor='lm')
    yy = np.linspace(0, 1, im.size[1])[:, None, None]
    top, low = np.array((255, 252, 240), np.float32), np.array((255, 160, 30), np.float32)
    t = np.clip((yy - 0.42) / 0.25, 0, 1)
    grad = np.zeros((im.size[1], im.size[0], 4), np.uint8); grad[..., :3] = (top * (1 - t) + low * t).astype(np.uint8); grad[..., 3] = 255
    fill = Image.new('RGBA', im.size, (0, 0, 0, 0)); fill.paste(Image.fromarray(grad, 'RGBA'), (0, 0), mask)
    im.alpha_composite(fill)
    small = ImageFont.truetype(FONT, int(h * 0.15))
    d = ImageDraw.Draw(im)
    d.text((x0 + 8, y0 + h * 0.42), 'Plante ta bannière. Tiens la ligne.', font=small, fill=(230, 236, 255), anchor='lm')
    return im.resize((w, h), Image.LANCZOS)


if __name__ == '__main__':
    bg, fg = background(), badge(N)
    full = Image.alpha_composite(bg, fg)
    full.save(os.path.join(HERE, 'logo.png'))
    full.resize((512, 512), Image.LANCZOS).save(os.path.join(HERE, 'store_icon_512.png'))
    feature_graphic().save(os.path.join(HERE, 'feature_graphic.png'))
    # logo used by the in-game menu (transparent background)
    fg.crop((int(N * 0.15), int(N * 0.15), int(N * 0.85), int(N * 0.85))).resize((512, 512), Image.LANCZOS).save(os.path.join(DRAW, 'logo.png'))
    for name, scale in [('mdpi', 1), ('hdpi', 1.5), ('xhdpi', 2), ('xxhdpi', 3), ('xxxhdpi', 4)]:
        dirp = os.path.join(RES, 'mipmap-' + name)
        os.makedirs(dirp, exist_ok=True)
        a = int(108 * scale)
        bg.resize((a, a), Image.LANCZOS).save(os.path.join(dirp, 'ic_launcher_background.png'))
        fg.resize((a, a), Image.LANCZOS).save(os.path.join(dirp, 'ic_launcher_foreground.png'))
        l = int(48 * scale)
        crop = full.crop((N // 6, N // 6, N - N // 6, N - N // 6)).resize((l, l), Image.LANCZOS)
        rounded(crop, l // 5).save(os.path.join(dirp, 'ic_launcher.png'))
        circ = Image.new('L', (l, l), 0); ImageDraw.Draw(circ).ellipse([0, 0, l - 1, l - 1], fill=255)
        rnd = Image.new('RGBA', (l, l), (0, 0, 0, 0)); rnd.paste(crop, (0, 0), circ)
        rnd.save(os.path.join(dirp, 'ic_launcher_round.png'))
    v26 = os.path.join(RES, 'mipmap-anydpi-v26')
    os.makedirs(v26, exist_ok=True)
    xml = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
'''
    for n in ('ic_launcher.xml', 'ic_launcher_round.xml'):
        open(os.path.join(v26, n), 'w').write(xml)
    print('ok')
