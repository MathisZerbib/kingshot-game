"""Procedural VFX sprite sheets for the hero abilities (and better particles): horizontal strips of square frames,
white/neutral so the game tints them per class. Glow sheets are meant to be drawn with BlendMode.Plus.

    python make_fx.py <repo_root>
Writes composeApp/.../drawable/fx_*.png and art/fx/fx.json (frame size, frame count, blend hint).
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = sys.argv[1]
OUT = os.path.join(ROOT, "composeApp/src/commonMain/composeResources/drawable")
SS = 2
META = {}


def strip(frames, size, name, blend):
    sheet = Image.new("RGBA", (size * len(frames), size), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        sheet.alpha_composite(f.resize((size, size), Image.LANCZOS) if f.size != (size, size) else f, (i * size, 0))
    sheet.save(os.path.join(OUT, name + ".png"), optimize=True)
    META[name] = dict(frame=size, frames=len(frames), blend=blend)


def blank(size):
    return Image.new("RGBA", (size * SS, size * SS), (0, 0, 0, 0))


def radial(size, inner=0.0, outer=1.0, power=1.0, color=(255, 255, 255)):
    """Soft radial falloff disc: alpha 1 inside `inner`, 0 at `outer` (fractions of the half size)."""
    n = size * SS
    yy, xx = np.mgrid[0:n, 0:n]
    r = np.hypot(xx - n / 2 + 0.5, yy - n / 2 + 0.5) / (n / 2)
    a = np.clip((outer - r) / max(1e-6, outer - inner), 0, 1) ** power
    arr = np.zeros((n, n, 4), np.uint8)
    arr[..., :3] = color
    arr[..., 3] = (a * 255).astype(np.uint8)
    return Image.fromarray(arr, "RGBA")


def ring(size, radius, width, soft=0.35, color=(255, 255, 255), alpha=1.0):
    n = size * SS
    yy, xx = np.mgrid[0:n, 0:n]
    r = np.hypot(xx - n / 2 + 0.5, yy - n / 2 + 0.5) / (n / 2)
    d = np.abs(r - radius)
    a = np.clip(1 - (d - width * (1 - soft)) / max(1e-6, width * soft), 0, 1)
    a[d < width * (1 - soft)] = 1
    arr = np.zeros((n, n, 4), np.uint8)
    arr[..., :3] = color
    arr[..., 3] = (a * 255 * alpha).astype(np.uint8)
    return Image.fromarray(arr, "RGBA")


def arc(size, r0, r1, a0, a1, color=(255, 255, 255), fade_tail=True):
    """Crescent slash: polar band between radii r0..r1 (fractions) from angle a0 to a1 (degrees, CCW); the tail fades."""
    n = size * SS
    yy, xx = np.mgrid[0:n, 0:n]
    x = xx - n / 2 + 0.5
    y = n / 2 - 0.5 - yy
    r = np.hypot(x, y) / (n / 2)
    ang = (np.degrees(np.arctan2(y, x)) - a0) % 360
    span = (a1 - a0) % 360 or 360
    inside = (ang <= span)
    t = np.clip(ang / span, 0, 1)  # 0 at tail, 1 at the blade tip
    mid = (r0 + r1) / 2
    half = (r1 - r0) / 2
    # the band thins towards the tail and tip (crescent) and has a soft edge
    thick = half * (0.25 + 0.75 * np.sin(np.pi * np.clip(t * 1.05, 0, 1)))
    edge = np.clip(1 - (np.abs(r - mid) - thick * 0.6) / np.maximum(thick * 0.4, 1e-6), 0, 1)
    a = edge * inside * (t ** 0.6 if fade_tail else 1)
    # hot white core towards the outer edge
    core = np.clip(1 - np.abs(r - (mid + thick * 0.3)) / np.maximum(thick * 0.35, 1e-6), 0, 1) * inside
    arr = np.zeros((n, n, 4), np.float32)
    col = np.array(color, np.float32)
    arr[..., :3] = col[None, None, :] * (1 - core[..., None] * 0.6) + 255 * core[..., None] * 0.6
    arr[..., 3] = np.clip(a, 0, 1) * 255
    return Image.fromarray(arr.astype(np.uint8), "RGBA")


def star(size, points, r_out, r_in, rot, color=(255, 255, 255), blur=0):
    im = blank(size)
    d = ImageDraw.Draw(im)
    n = size * SS
    pts = []
    for i in range(points * 2):
        rr = r_out if i % 2 == 0 else r_in
        a = math.radians(rot + i * 180 / points)
        pts.append((n / 2 + math.cos(a) * rr * n / 2, n / 2 - math.sin(a) * rr * n / 2))
    d.polygon(pts, fill=color + (255,))
    if blur:
        im = im.filter(ImageFilter.GaussianBlur(blur * SS))
    return im


def composite(*layers):
    out = layers[0].copy()
    for l in layers[1:]:
        out.alpha_composite(l)
    return out


def scale_alpha(im, k):
    arr = np.array(im)
    arr[..., 3] = np.clip(arr[..., 3] * k, 0, 255).astype(np.uint8)
    return Image.fromarray(arr, "RGBA")


def down(im, size):
    return im.resize((size, size), Image.LANCZOS)


# ------------------------------------------------------------------------------------------------ sheets

def fx_slash(size=256, n=8):
    """A blade arc sweeping a full turn in 8 frames: frame i covers angles [i*45-150, i*45] with a fading tail."""
    frames = []
    for i in range(n):
        tip = i * 45
        a = arc(size, 0.52, 0.98, tip - 150, tip, color=(255, 236, 170))
        glow = arc(size, 0.44, 1.0, tip - 160, tip + 4, color=(255, 190, 80)).filter(ImageFilter.GaussianBlur(6 * SS))
        frames.append(down(composite(scale_alpha(glow, 0.7), a), size))
    strip(frames, size, "fx_slash", "plus")


def fx_ring(size=256, n=8):
    frames = []
    for i in range(n):
        t = (i + 1) / n
        rad = 0.15 + 0.8 * t
        width = 0.16 * (1 - 0.6 * t) + 0.02
        core = ring(size, rad, width * 0.5, soft=0.5, alpha=1 - 0.75 * t)
        halo = ring(size, rad, width, soft=0.9, alpha=(1 - t) * 0.55, color=(255, 230, 190))
        frames.append(down(composite(halo, core), size))
    strip(frames, size, "fx_ring", "plus")


def fx_spark(size=128, n=6):
    frames = []
    for i in range(n):
        t = i / (n - 1)
        flash = radial(size, 0.0, 0.9 * (1 - t) + 0.1, power=2.2)
        flash = scale_alpha(flash, (1 - t) ** 1.5)
        s1 = star(size, 4, 0.98 * (0.5 + 0.5 * t), 0.08 * (1 - t) + 0.02, 0, color=(255, 250, 230), blur=1)
        s2 = star(size, 4, 0.7 * (0.5 + 0.5 * t), 0.06 * (1 - t) + 0.02, 45, color=(255, 220, 150), blur=1)
        frames.append(down(composite(flash, scale_alpha(s2, 1 - t), scale_alpha(s1, 1 - t * 0.8)), size))
    strip(frames, size, "fx_spark", "plus")


def fx_puff(size=128, n=8, seed=3):
    rnd = np.random.default_rng(seed)
    blobs = [(rnd.uniform(-0.25, 0.25), rnd.uniform(-0.25, 0.25), rnd.uniform(0.18, 0.3)) for _ in range(7)]
    frames = []
    for i in range(n):
        t = i / (n - 1)
        im = blank(size)
        d = ImageDraw.Draw(im)
        nn = size * SS
        for (bx, by, br) in blobs:
            k = 1 + 1.6 * t
            r = br * (0.6 + 0.9 * t) * nn / 2
            cx = nn / 2 + bx * k * nn / 2
            cy = nn / 2 + by * k * nn / 2 - 0.12 * t * nn  # dust rises
            shade = int(215 - 40 * (by + 0.25))
            d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(shade, shade - 12, shade - 35, 255))
        im = im.filter(ImageFilter.GaussianBlur((3 + 6 * t) * SS))
        frames.append(down(scale_alpha(im, (1 - t) ** 0.9 * 0.95), size))
    strip(frames, size, "fx_puff", "over")


def fx_sparkle(size=64, n=6):
    frames = []
    for i in range(n):
        t = i / (n - 1)
        k = math.sin(math.pi * t)
        s = star(size, 4, 0.95 * k + 0.05, 0.12, 0, color=(255, 255, 240), blur=0.6)
        s2 = star(size, 4, 0.5 * k + 0.05, 0.1, 45, color=(255, 240, 180), blur=0.6)
        glow = scale_alpha(radial(size, 0, 0.6 * k + 0.1, power=2), 0.6 * k)
        frames.append(down(composite(glow, s2, s), size))
    strip(frames, size, "fx_sparkle", "plus")


def fx_glow(size=256):
    g = radial(size, 0.0, 1.0, power=2.6)
    strip([down(g, size)], size, "fx_glow", "plus")


def fx_shadow(size=128):
    n = size * SS
    yy, xx = np.mgrid[0:n, 0:n]
    r = np.hypot((xx - n / 2 + 0.5) / (n / 2), (yy - n / 2 + 0.5) / (n / 4))
    a = np.clip(1 - r, 0, 1) ** 1.6
    arr = np.zeros((n, n, 4), np.uint8)
    arr[..., :3] = (10, 12, 20)
    arr[..., 3] = (a * 255 * 0.8).astype(np.uint8)
    strip([down(Image.fromarray(arr, "RGBA"), size)], size, "fx_shadow", "over")


def fx_trail(size=256):
    """Horizontal streak: bright head on the right, fading tail to the left; rotate along the velocity in game."""
    n = size * SS
    yy, xx = np.mgrid[0:n, 0:n]
    x = xx / n
    y = (yy - n / 2 + 0.5) / (n / 2)
    w = 0.06 + 0.1 * (1 - x)  # thinner towards the head
    a = np.clip(1 - np.abs(y) / w, 0, 1) ** 1.5 * (x ** 1.8)
    arr = np.zeros((n, n, 4), np.float32)
    arr[..., :3] = 255
    arr[..., 3] = a * 255
    strip([down(Image.fromarray(arr.astype(np.uint8), "RGBA"), size)], size, "fx_trail", "plus")


def fx_reticle(size=256):
    im = blank(size)
    d = ImageDraw.Draw(im)
    n = size * SS
    c = n / 2
    d.ellipse((c - 0.78 * c, c - 0.78 * c, c + 0.78 * c, c + 0.78 * c), outline=(255, 255, 255, 255), width=int(0.045 * c))
    d.ellipse((c - 0.12 * c, c - 0.12 * c, c + 0.12 * c, c + 0.12 * c), fill=(255, 255, 255, 255))
    for k in range(4):
        a = math.radians(90 * k)
        x0, y0 = c + math.cos(a) * 0.62 * c, c - math.sin(a) * 0.62 * c
        x1, y1 = c + math.cos(a) * 0.98 * c, c - math.sin(a) * 0.98 * c
        d.line((x0, y0, x1, y1), fill=(255, 255, 255, 255), width=int(0.07 * c))
    strip([down(im, size)], size, "fx_reticle", "plus")


def fx_rune(size=512):
    """Repair sigil: two rings, 12 ticks, a hammer-ish cross, dashed outer ring. Rotates slowly under the keep."""
    im = blank(size)
    d = ImageDraw.Draw(im)
    n = size * SS
    c = n / 2
    col = (255, 255, 255, 255)
    d.ellipse((c - 0.95 * c, c - 0.95 * c, c + 0.95 * c, c + 0.95 * c), outline=col, width=int(0.018 * c))
    d.ellipse((c - 0.62 * c, c - 0.62 * c, c + 0.62 * c, c + 0.62 * c), outline=col, width=int(0.03 * c))
    for k in range(24):
        a = math.radians(15 * k)
        r0, r1 = (0.84, 0.92) if k % 2 else (0.78, 0.92)
        d.line((c + math.cos(a) * r0 * c, c - math.sin(a) * r0 * c, c + math.cos(a) * r1 * c, c - math.sin(a) * r1 * c), fill=col, width=int(0.02 * c))
    for k in range(6):
        a = math.radians(60 * k + 30)
        d.line((c + math.cos(a) * 0.2 * c, c - math.sin(a) * 0.2 * c, c + math.cos(a) * 0.6 * c, c - math.sin(a) * 0.6 * c), fill=col, width=int(0.022 * c))
    d.regular_polygon((c, c, 0.2 * c), 6, rotation=30, outline=col, width=int(0.03 * c))
    im = im.filter(ImageFilter.GaussianBlur(0.8 * SS))
    strip([down(im, size)], size, "fx_rune", "plus")


def fx_arrow_stuck(size=64):
    """An arrow planted in the ground (shaft + fletching), seen from the game's 3/4 angle."""
    im = blank(size)
    d = ImageDraw.Draw(im)
    n = size * SS
    d.line((n * 0.5, n * 0.95, n * 0.62, n * 0.2), fill=(120, 80, 40, 255), width=int(n * 0.06))
    d.polygon([(n * 0.62, n * 0.2), (n * 0.5, n * 0.3), (n * 0.56, n * 0.42), (n * 0.68, n * 0.32)], fill=(236, 60, 60, 255))
    d.polygon([(n * 0.62, n * 0.2), (n * 0.74, n * 0.3), (n * 0.68, n * 0.42), (n * 0.58, n * 0.32)], fill=(255, 255, 255, 255))
    strip([down(im, size)], size, "fx_arrow_stuck", "over")


def fx_dot(size=32):
    """Round soft particle to replace the square ones."""
    strip([down(radial(size, 0.15, 1.0, power=1.4), size)], size, "fx_dot", "over")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    fx_slash(); fx_ring(); fx_spark(); fx_puff(); fx_sparkle(); fx_glow(); fx_shadow(); fx_trail(); fx_reticle(); fx_rune()
    fx_arrow_stuck(); fx_dot()
    with open(os.path.join(ROOT, "art/fx/fx.json"), "w") as f:
        json.dump(META, f, indent=1)
    print("ok", list(META))
