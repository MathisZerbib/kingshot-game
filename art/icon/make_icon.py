"""Draws the Kingshot app icon (crowned shield over crossed swords) and writes the Android launcher resources.
Run: python3 art/icon/make_icon.py   (needs Pillow)"""
import math, os
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '../../composeApp/src/androidMain/res')
N = 1024  # working size (108 dp adaptive canvas)

def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))

def background():
    im = Image.new('RGBA', (N, N))
    px = im.load()
    c0, c1 = (64, 132, 255), (16, 34, 98)
    for y in range(N):
        for x in range(N):
            d = min(1, math.hypot(x - N / 2, y - N * 0.42) / (N * 0.72))
            px[x, y] = lerp(c0, c1, d) + (255,)
    rays = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(rays)
    cx, cy = N / 2, N * 0.45
    for k in range(16):
        a0 = k * 2 * math.pi / 16
        a1 = a0 + math.pi / 32
        d.polygon([(cx, cy), (cx + math.cos(a0) * N, cy + math.sin(a0) * N), (cx + math.cos(a1) * N, cy + math.sin(a1) * N)], fill=(255, 255, 255, 22))
    return Image.alpha_composite(im, rays)

def foreground():
    im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cx = N / 2
    # crossed swords behind the shield
    for sgn in (1, -1):
        blade = [(-14, -330), (14, -330), (14, 150), (0, 190), (-14, 150)]
        a = math.radians(38 * sgn)
        def rot(p, ox=cx, oy=N * 0.52):
            return (ox + p[0] * math.cos(a) - p[1] * math.sin(a), oy + p[0] * math.sin(a) + p[1] * math.cos(a))
        d.polygon([rot(p) for p in blade], fill=(226, 232, 240), outline=(120, 130, 150))
        d.polygon([rot(p) for p in [(-70, 150), (70, 150), (70, 178), (-70, 178)]], fill=(214, 160, 40))
        d.polygon([rot(p) for p in [(-16, 178), (16, 178), (16, 270), (-16, 270)]], fill=(110, 62, 30))
        d.ellipse([rot((0, 290))[0] - 26, rot((0, 290))[1] - 26, rot((0, 290))[0] + 26, rot((0, 290))[1] + 26], fill=(214, 160, 40))
    # shield: gold rim, red field, white chevron
    top, bot, hw = N * 0.33, N * 0.80, N * 0.215
    def shield(m):
        pts = [(cx - hw + m, top + m), (cx + hw - m, top + m), (cx + hw - m, N * 0.55)]
        for i in range(21):
            t = i / 20
            pts.append((cx + (hw - m) * (1 - t) ** 1.3 * math.cos(t * math.pi / 2), N * 0.55 + (bot - m - N * 0.55) * math.sin(t * math.pi / 2)))
        pts += [(cx - x + cx, y) for (x, y) in reversed(pts[2:])][0:0]
        right = pts
        left = [(2 * cx - x, y) for (x, y) in reversed(right)]
        return right + left
    shadow = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).polygon([(x + 10, y + 18) for (x, y) in shield(0)], fill=(0, 0, 0, 110))
    im = Image.alpha_composite(im, shadow.filter(ImageFilter.GaussianBlur(14)))
    d = ImageDraw.Draw(im)
    d.polygon(shield(0), fill=(240, 176, 40))
    d.polygon(shield(26), fill=(205, 45, 45))
    d.polygon(shield(26)[:len(shield(26)) // 2], fill=(180, 32, 36))  # shaded half
    d.polygon([(cx - hw * 0.62, N * 0.50), (cx, N * 0.60), (cx + hw * 0.62, N * 0.50), (cx + hw * 0.62, N * 0.58), (cx, N * 0.68), (cx - hw * 0.62, N * 0.58)], fill=(255, 255, 255))
    # crown above the shield
    cb, ch, cw = N * 0.33, N * 0.13, N * 0.20
    crown = [(cx - cw, cb), (cx - cw, cb - ch * 0.55), (cx - cw * 0.55, cb - ch * 0.15), (cx - cw * 0.3, cb - ch),
             (cx, cb - ch * 0.3), (cx + cw * 0.3, cb - ch), (cx + cw * 0.55, cb - ch * 0.15), (cx + cw, cb - ch * 0.55), (cx + cw, cb)]
    d.polygon([(x + 6, y + 10) for x, y in crown], fill=(150, 90, 10))
    d.polygon(crown, fill=(255, 205, 50))
    d.rectangle([cx - cw, cb - 26, cx + cw, cb + 6], fill=(230, 160, 30))
    for x, y in [(cx - cw, cb - ch * 0.55), (cx - cw * 0.3, cb - ch), (cx + cw * 0.3, cb - ch), (cx + cw, cb - ch * 0.55)]:
        d.ellipse([x - 20, y - 20, x + 20, y + 20], fill=(255, 235, 140))
    d.ellipse([cx - 22, cb - 34, cx + 22, cb + 10], fill=(220, 40, 60))
    return im

def rounded(im, r):
    mask = Image.new('L', im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, im.size[0] - 1, im.size[1] - 1], r, fill=255)
    out = Image.new('RGBA', im.size, (0, 0, 0, 0))
    out.paste(im, (0, 0), mask)
    return out

bg, fg = background(), foreground()
full = Image.alpha_composite(bg, fg)
full.save(os.path.join(HERE, 'logo.png'))
# logo used by the in-game menu (transparent background)
fg.crop((int(N * 0.15), int(N * 0.12), int(N * 0.85), int(N * 0.86))).resize((512, 545), Image.LANCZOS).save(
    os.path.join(HERE, '../../composeApp/src/commonMain/composeResources/drawable/logo.png'))
for name, scale in [('mdpi', 1), ('hdpi', 1.5), ('xhdpi', 2), ('xxhdpi', 3), ('xxxhdpi', 4)]:
    dirp = os.path.join(RES, 'mipmap-' + name)
    os.makedirs(dirp, exist_ok=True)
    a = int(108 * scale)
    bg.resize((a, a), Image.LANCZOS).save(os.path.join(dirp, 'ic_launcher_background.png'))
    fg.resize((a, a), Image.LANCZOS).save(os.path.join(dirp, 'ic_launcher_foreground.png'))
    l = int(48 * scale)
    # legacy icon: the central 72 of 108 dp, rounded square / circle
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
