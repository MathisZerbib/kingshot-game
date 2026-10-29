"""Builds the app icons from the Blender hero render: python make_icons.py <repo_root> <logo_fg.png> [preview.png]

Writes Android adaptive icons (mipmap-*/ic_launcher_foreground|background + legacy ic_launcher) and the iOS AppIcon set.
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

root, fg_path = sys.argv[1], sys.argv[2]
preview = sys.argv[3] if len(sys.argv) > 3 else None
S = 1024


def radial(size, inner, outer, cx=0.5, cy=0.42):
    """Radial gradient from `inner` (center) to `outer` (corners)."""
    im = Image.new("RGBA", (size, size))
    px = im.load()
    rmax = math.hypot(max(cx, 1 - cx), max(cy, 1 - cy))
    for y in range(size):
        for x in range(size):
            t = min(1.0, math.hypot(x / size - cx, y / size - cy) / rmax)
            t = t ** 1.35
            px[x, y] = tuple(int(inner[i] + (outer[i] - inner[i]) * t) for i in range(3)) + (255,)
    return im


def background(size):
    bg = radial(size, (78, 150, 255), (14, 38, 110))
    # sun rays from the top-center
    rays = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(rays)
    cx, cy = size * 0.5, size * 0.1
    for i in range(14):
        a0 = math.radians(i * 360 / 14 + 5)
        a1 = a0 + math.radians(9)
        pts = [(cx, cy), (cx + math.cos(a0) * size * 2, cy + math.sin(a0) * size * 2),
               (cx + math.cos(a1) * size * 2, cy + math.sin(a1) * size * 2)]
        d.polygon(pts, fill=(255, 255, 255, 22))
    bg.alpha_composite(rays)
    # ground: a soft green hill in the lower part
    hill = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(hill)
    d.ellipse((-size * 0.3, size * 0.74, size * 1.3, size * 1.5), fill=(72, 160, 60, 255))
    d.ellipse((-size * 0.1, size * 0.8, size * 1.1, size * 1.4), fill=(96, 190, 72, 255))
    bg.alpha_composite(hill)
    return bg


def foreground(size, scale=0.72):
    """The hero render, fitted into `scale` of the canvas (adaptive safe zone is 66 %), with a soft shadow."""
    fg = Image.open(fg_path).convert("RGBA")
    bbox = fg.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox()
    fg = fg.crop(bbox)
    k = size * scale / max(fg.width, fg.height)
    fg = fg.resize((max(1, int(fg.width * k)), max(1, int(fg.height * k))), Image.LANCZOS)
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    x = (size - fg.width) // 2
    y = int(size * 0.52 - fg.height / 2)
    shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    sh = Image.new("RGBA", fg.size, (0, 0, 0, 0))
    sh.putalpha(fg.getchannel("A").point(lambda a: int(a * 0.55)))
    shadow.alpha_composite(sh, (x + int(size * 0.012), y + int(size * 0.02)))
    shadow = shadow.filter(ImageFilter.GaussianBlur(size * 0.012))
    layer.alpha_composite(shadow)
    layer.alpha_composite(fg, (x, y))
    return layer


def rounded(im, radius):
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, im.width - 1, im.height - 1), radius=radius, fill=255)
    out = im.copy()
    out.putalpha(mask)
    return out


bg = background(S)
fg = foreground(S)
full = bg.copy()
full.alpha_composite(fg)

# ---- Android
res = os.path.join(root, "composeApp/src/androidMain/res")
dpis = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
for dpi, k in dpis.items():
    d = os.path.join(res, "mipmap-" + dpi)
    os.makedirs(d, exist_ok=True)
    a = int(108 * k)
    bg.resize((a, a), Image.LANCZOS).save(os.path.join(d, "ic_launcher_background.png"), optimize=True)
    fg.resize((a, a), Image.LANCZOS).save(os.path.join(d, "ic_launcher_foreground.png"), optimize=True)
    # legacy icon: the adaptive composite cropped to the 72 dp center, rounded
    l = int(48 * k)
    inner = full.crop((S // 6, S // 6, S - S // 6, S - S // 6)).resize((l, l), Image.LANCZOS)
    rounded(inner, int(l * 0.18)).save(os.path.join(d, "ic_launcher.png"), optimize=True)
    rounded(inner, l // 2).save(os.path.join(d, "ic_launcher_round.png"), optimize=True)
d = os.path.join(res, "mipmap-anydpi-v26")
os.makedirs(d, exist_ok=True)
xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
       '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
       '    <background android:drawable="@mipmap/ic_launcher_background" />\n'
       '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
       '</adaptive-icon>\n')
for n in ("ic_launcher.xml", "ic_launcher_round.xml"):
    open(os.path.join(d, n), "w").write(xml)

# ---- iOS (single 1024 px icon, Xcode 14+)
ios = os.path.join(root, "iosApp/iosApp/Assets.xcassets")
os.makedirs(os.path.join(ios, "AppIcon.appiconset"), exist_ok=True)
open(os.path.join(ios, "Contents.json"), "w").write(json.dumps({"info": {"author": "xcode", "version": 1}}, indent=2))
inner = full.crop((S // 10, S // 10, S - S // 10, S - S // 10)).resize((S, S), Image.LANCZOS).convert("RGB")
inner.save(os.path.join(ios, "AppIcon.appiconset/icon-1024.png"), optimize=True)
open(os.path.join(ios, "AppIcon.appiconset/Contents.json"), "w").write(json.dumps({
    "images": [{"filename": "icon-1024.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"}],
    "info": {"author": "xcode", "version": 1}}, indent=2))

if preview:
    sheet = Image.new("RGB", (1024 + 40 + 512 + 40 + 192 + 40, 1064), (40, 40, 48))
    sheet.paste(inner.resize((1024, 1024)), (20, 20))
    sheet.paste(rounded(inner.resize((512, 512)), 110).convert("RGB"), (1084, 20))
    circ = full.crop((S // 6, S // 6, S - S // 6, S - S // 6)).resize((192, 192), Image.LANCZOS)
    sheet.paste(rounded(circ, 96), (1636, 20), rounded(circ, 96))
    sheet.save(preview, quality=92)
print("icons written")
