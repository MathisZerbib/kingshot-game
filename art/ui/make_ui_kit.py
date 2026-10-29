"""
Draws the Kingshot UI kit (frames, buttons, ribbons, bars, logotype) with Pillow, supersampled 4x.

    python make_ui_kit.py <repo_root>

Outputs to composeApp/src/commonMain/composeResources/drawable/ui_*.png. Every 9-slice asset lists its insets in
SLICES below (also written to art/ui/ui_kit.json for the Compose side).
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
OUT = os.path.join(ROOT, "composeApp/src/commonMain/composeResources/drawable")
FONTS = os.path.join(ROOT, "composeApp/src/commonMain/composeResources/font")
SS = 4  # supersampling

# palette
NAVY = (21, 33, 59)
NAVY_DEEP = (12, 19, 38)
ROYAL = (36, 87, 214)
ROYAL_DARK = (22, 52, 140)
GOLD = (242, 193, 78)
GOLD_LIGHT = (255, 232, 150)
GOLD_DARK = (160, 108, 20)
GOLD_SHADE = (96, 60, 8)
PARCH = (243, 230, 200)
PARCH_DARK = (214, 190, 146)
WOOD = (107, 68, 35)
WOOD_DARK = (64, 38, 18)
WOOD_LIGHT = (150, 98, 52)
RED = (215, 59, 59)
RED_DARK = (140, 28, 28)
GREEN = (72, 180, 76)
GREEN_DARK = (34, 110, 42)
GREY = (122, 128, 142)
GREY_DARK = (70, 74, 86)
WHITE = (255, 255, 255)

SLICES = {}


def canvas(w, h):
    return Image.new("RGBA", (w * SS, h * SS), (0, 0, 0, 0))


def down(im, w, h):
    return im.resize((w, h), Image.LANCZOS)


def rr(draw, box, r, fill=None, outline=None, width=1):
    draw.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)


def vgrad(w, h, top, bottom, mid=None):
    """Vertical gradient image (RGBA)."""
    t = np.linspace(0, 1, h)[:, None, None]
    top, bottom = np.array(top, float), np.array(bottom, float)
    if mid is not None:
        mid = np.array(mid, float)
        col = np.where(t < 0.5, top + (mid - top) * (t / 0.5), mid + (bottom - mid) * ((t - 0.5) / 0.5))
    else:
        col = top + (bottom - top) * t
    arr = np.repeat(col, w, axis=1)
    out = np.concatenate([arr, np.full((h, w, 1), 255.0)], axis=2)
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def masked(grad, mask):
    g = grad.copy()
    g.putalpha(mask)
    return g


def shape_mask(w, h, fn):
    m = Image.new("L", (w, h), 0)
    fn(ImageDraw.Draw(m))
    return m


def noise(w, h, amount=12, seed=1):
    rnd = np.random.RandomState(seed)
    n = rnd.normal(0, amount, (h, w, 1))
    return n


def add_noise(im, amount=8, seed=1):
    arr = np.array(im).astype(np.float32)
    arr[..., :3] += noise(im.width, im.height, amount, seed)
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA")


def shadow(mask, blur, offset, color=(0, 0, 0, 140)):
    sh = Image.new("RGBA", mask.size, color)
    sh.putalpha(mask.filter(ImageFilter.GaussianBlur(blur)))
    out = Image.new("RGBA", mask.size, (0, 0, 0, 0))
    out.alpha_composite(sh, offset)
    return out


def save(im, name, w, h, insets=None):
    im = down(im, w, h)
    im.save(os.path.join(OUT, name + ".png"), optimize=True)
    if insets:
        SLICES[name] = dict(w=w, h=h, left=insets[0], top=insets[1], right=insets[2], bottom=insets[3])
    print(name, im.size)


# ----------------------------------------------------------------------------- buttons


def button(name, top, bottom, shade, w=480, h=150, r=34, edge=12):
    W, H, R, E = w * SS, h * SS, r * SS, edge * SS
    im = canvas(w, h)
    # dark base (bottom edge = the chunky depth)
    base = shape_mask(W, H, lambda d: rr(d, (0, 0, W - 1, H - 1), R, 255))
    im.alpha_composite(masked(Image.new("RGBA", (W, H), shade + (255,)), base))
    # face
    face = shape_mask(W, H, lambda d: rr(d, (0, 0, W - 1, H - 1 - E), R, 255))
    im.alpha_composite(masked(vgrad(W, H, top, bottom), face))
    # inner rim (light) + outer dark outline
    d = ImageDraw.Draw(im)
    rr(d, (SS * 3, SS * 3, W - 1 - SS * 3, H - 1 - E - SS * 3), R - SS * 3, outline=(255, 255, 255, 70), width=SS * 2)
    rr(d, (0, 0, W - 1, H - 1), R, outline=(30, 18, 8, 230), width=SS * 3)
    # glossy highlight band on the upper third
    hl = shape_mask(W, H, lambda dd: rr(dd, (SS * 10, SS * 8, W - SS * 10, int(H * 0.42)), R - SS * 6, 255))
    hl = hl.filter(ImageFilter.GaussianBlur(SS * 2))
    im.alpha_composite(masked(Image.new("RGBA", (W, H), (255, 255, 255, 255)), hl.point(lambda a: int(a * 0.16))))
    save(im, name, w, h, (r + 6, r + 6, r + 6, r + edge + 6))


# ----------------------------------------------------------------------------- panels


def panel(name, inner_top, inner_bottom, w=600, h=600, frame=34, r=40, parchment=True):
    W, H, F, R = w * SS, h * SS, frame * SS, r * SS
    im = canvas(w, h)
    outer = shape_mask(W, H, lambda d: rr(d, (0, 0, W - 1, H - 1), R, 255))
    im.alpha_composite(shadow(outer, SS * 6, (0, SS * 8), (0, 0, 0, 160)))
    # wood frame with grain
    wood = vgrad(W, H, WOOD_LIGHT, WOOD_DARK, WOOD)
    wood = add_noise(wood, 10, 3)
    arr = np.array(wood).astype(np.float32)
    yy = np.arange(H)[:, None]
    grain = (np.sin(yy / (SS * 3.1)) * 6 + np.sin(yy / (SS * 7.7) + 1.3) * 5)
    arr[..., :3] += grain[..., None]
    wood = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA")
    im.alpha_composite(masked(wood, outer))
    d = ImageDraw.Draw(im)
    rr(d, (0, 0, W - 1, H - 1), R, outline=(28, 16, 6, 255), width=SS * 3)
    # gold rim between frame and inner
    rim = F - SS * 10
    rr(d, (rim, rim, W - 1 - rim, H - 1 - rim), R - rim // 2, outline=GOLD_DARK + (255,), width=SS * 6)
    rr(d, (rim + SS * 2, rim + SS * 2, W - 1 - rim - SS * 2, H - 1 - rim - SS * 2), R - rim // 2 - SS * 2, outline=GOLD + (255,), width=SS * 3)
    # inner
    inner = shape_mask(W, H, lambda dd: rr(dd, (F, F, W - 1 - F, H - 1 - F), R - F // 2, 255))
    g = vgrad(W, H, inner_top, inner_bottom)
    if parchment:
        g = add_noise(g, 6, 5)
    im.alpha_composite(masked(g, inner))
    # inner shadow at the top of the inner area
    ish = shape_mask(W, H, lambda dd: dd.rectangle((F, F, W - F, F + SS * 14), 255)).filter(ImageFilter.GaussianBlur(SS * 5))
    im.alpha_composite(masked(Image.new("RGBA", (W, H), (0, 0, 0, 255)), Image.fromarray((np.array(ish) * np.array(inner) / 255 * 0.35).astype(np.uint8))))
    # corner rivets
    for (cx, cy) in ((F // 2, F // 2), (W - F // 2, F // 2), (F // 2, H - F // 2), (W - F // 2, H - F // 2)):
        rad = SS * 9
        d.ellipse((cx - rad, cy - rad, cx + rad, cy + rad), fill=GOLD_DARK + (255,))
        d.ellipse((cx - rad + SS * 2, cy - rad + SS * 2, cx + rad - SS * 2, cy + rad - SS * 2), fill=GOLD + (255,))
        d.ellipse((cx - rad // 2, cy - rad // 2 - SS, cx, cy - SS), fill=GOLD_LIGHT + (255,))
    save(im, name, w, h, (frame + 24, frame + 24, frame + 24, frame + 24))


def card(name, w=360, h=520):
    """Class / unlock card: wood frame, parchment face, dark header band for the title."""
    panel(name, PARCH, PARCH_DARK, w, h, frame=22, r=30)
    im = Image.open(os.path.join(OUT, name + ".png")).convert("RGBA")
    d = ImageDraw.Draw(im)
    F = 22
    band_h = 58
    d.rounded_rectangle((F + 6, F + 6, w - F - 6, F + 6 + band_h), radius=12, fill=NAVY + (255,))
    d.line((F + 10, F + 6 + band_h - 2, w - F - 10, F + 6 + band_h - 2), fill=GOLD + (255,), width=2)
    im.save(os.path.join(OUT, name + ".png"), optimize=True)
    SLICES[name] = dict(w=w, h=h, left=F + 24, top=F + band_h + 24, right=F + 24, bottom=F + 24)


# ----------------------------------------------------------------------------- ribbons


def ribbon(name, main, dark, w=800, h=170):
    W, H = w * SS, h * SS
    im = canvas(w, h)
    d = ImageDraw.Draw(im)
    tail = int(W * 0.14)
    body_t, body_b = int(H * 0.2), int(H * 0.8)
    # tails (folded, darker), with a notch
    for sx in (0, 1):
        x0 = 0 if sx == 0 else W - tail - SS * 2
        pts = [(x0, body_t + SS * 10), (x0 + tail, body_t + SS * 10), (x0 + tail, body_b + SS * 10), (x0, body_b + SS * 10)]
        notch = (x0 + int(tail * 0.3), (body_t + body_b) // 2 + SS * 10) if sx == 0 else (x0 + int(tail * 0.7), (body_t + body_b) // 2 + SS * 10)
        if sx == 0:
            pts = [pts[0], pts[1], pts[2], pts[3], notch]
            pts = [pts[1], pts[2], pts[3], notch, pts[0]]
            pts = [(x0 + tail, body_t + SS * 10), (x0 + tail, body_b + SS * 10), (x0, body_b + SS * 10), notch, (x0, body_t + SS * 10)]
        else:
            pts = [(x0, body_t + SS * 10), (x0, body_b + SS * 10), (x0 + tail, body_b + SS * 10), notch, (x0 + tail, body_t + SS * 10)]
        d.polygon(pts, fill=dark + (255,), outline=(40, 10, 10, 255))
    # body with a slight curve (polygon approximating a banner bulge)
    body = shape_mask(W, H, lambda dd: dd.rounded_rectangle((tail - SS * 6, body_t, W - tail + SS * 6, body_b), radius=SS * 10, fill=255))
    im.alpha_composite(shadow(body, SS * 4, (0, SS * 6), (0, 0, 0, 150)))
    im.alpha_composite(masked(vgrad(W, H, tuple(min(255, c + 35) for c in main), dark, main), body))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((tail - SS * 6, body_t, W - tail + SS * 6, body_b), radius=SS * 10, outline=(40, 10, 10, 255), width=SS * 3)
    d.rounded_rectangle((tail, body_t + SS * 7, W - tail, body_b - SS * 7), radius=SS * 6, outline=GOLD + (255,), width=SS * 2)
    save(im, name, w, h, (int(w * 0.14) + 30, 40, int(w * 0.14) + 30, 40))


# ----------------------------------------------------------------------------- circles, bars, tabs


def circle_button(name, ring=GOLD, inner=NAVY, size=200):
    S = size * SS
    im = canvas(size, size)
    d = ImageDraw.Draw(im)
    m = shape_mask(S, S, lambda dd: dd.ellipse((SS * 4, SS * 4, S - SS * 4, S - SS * 4), 255))
    im.alpha_composite(shadow(m, SS * 4, (0, SS * 5)))
    d.ellipse((SS * 4, SS * 4, S - SS * 4, S - SS * 4), fill=GOLD_SHADE + (255,))
    d.ellipse((SS * 4, SS * 4, S - SS * 4, S - SS * 10), fill=GOLD_DARK + (255,))
    inner_m = shape_mask(S, S, lambda dd: dd.ellipse((SS * 16, SS * 16, S - SS * 16, S - SS * 22), 255))
    im.alpha_composite(masked(vgrad(S, S, tuple(min(255, c + 30) for c in inner), tuple(max(0, c - 20) for c in inner)), inner_m))
    d = ImageDraw.Draw(im)
    d.ellipse((SS * 8, SS * 8, S - SS * 8, S - SS * 14), outline=ring + (255,), width=SS * 5)
    d.ellipse((SS * 4, SS * 4, S - SS * 4, S - SS * 4), outline=(30, 18, 8, 230), width=SS * 3)
    # top highlight
    hl = shape_mask(S, S, lambda dd: dd.ellipse((SS * 30, SS * 20, S - SS * 30, S // 2), 255)).filter(ImageFilter.GaussianBlur(SS * 6))
    im.alpha_composite(masked(Image.new("RGBA", (S, S), (255, 255, 255, 255)), hl.point(lambda a: int(a * 0.12))))
    save(im, name, size, size)


def bar(name, fill_top=None, fill_bottom=None, w=400, h=44, track=True):
    W, H = w * SS, h * SS
    im = canvas(w, h)
    d = ImageDraw.Draw(im)
    if track:
        d.rounded_rectangle((0, 0, W - 1, H - 1), radius=H // 2, fill=(10, 14, 28, 230), outline=GOLD_DARK + (255,), width=SS * 3)
        d.rounded_rectangle((SS * 3, SS * 3, W - 1 - SS * 3, H - 1 - SS * 3), radius=H // 2 - SS * 3, outline=(0, 0, 0, 120), width=SS * 2)
    else:
        m = shape_mask(W, H, lambda dd: dd.rounded_rectangle((SS * 6, SS * 6, W - 1 - SS * 6, H - 1 - SS * 6), radius=H // 2 - SS * 6, fill=255))
        im.alpha_composite(masked(vgrad(W, H, fill_top, fill_bottom), m))
        hl = shape_mask(W, H, lambda dd: dd.rounded_rectangle((SS * 10, SS * 9, W - SS * 10, H // 2), radius=SS * 6, fill=255))
        im.alpha_composite(masked(Image.new("RGBA", (W, H), (255, 255, 255, 255)), hl.point(lambda a: int(a * 0.35))))
    save(im, name, w, h, (h // 2 + 2, h // 2 - 2, h // 2 + 2, h // 2 - 2))


def tab(name, top, bottom, w=320, h=110, r=28):
    W, H, R = w * SS, h * SS, r * SS
    im = canvas(w, h)
    m = shape_mask(W, H, lambda d: d.rounded_rectangle((0, 0, W - 1, H + R), radius=R, fill=255))
    im.alpha_composite(masked(vgrad(W, H, top, bottom), m))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, W - 1, H + R), radius=R, outline=(30, 18, 8, 230), width=SS * 3)
    d.rounded_rectangle((SS * 3, SS * 3, W - 1 - SS * 3, H + R), radius=R - SS * 3, outline=(255, 255, 255, 60), width=SS * 2)
    save(im, name, w, h, (r + 6, r + 6, r + 6, 10))


def pill(name, color, w=300, h=90):
    W, H = w * SS, h * SS
    im = canvas(w, h)
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, W - 1, H - 1), radius=H // 2, fill=color + (255,), outline=GOLD_DARK + (255,), width=SS * 3)
    d.rounded_rectangle((SS * 3, SS * 3, W - 1 - SS * 3, H - 1 - SS * 3), radius=H // 2 - SS * 3, outline=GOLD + (255,), width=SS * 2)
    save(im, name, w, h, (h // 2 + 2, h // 2 - 2, h // 2 + 2, h // 2 - 2))


# ----------------------------------------------------------------------------- logotype


def logotype(name, text="KINGSHOT", w=1200, h=360):
    W, H = w * SS, h * SS
    im = canvas(w, h)
    font = ImageFont.truetype(os.path.join(FONTS, "cinzel_decorative_black.ttf"), int(H * 0.56))
    tmp = Image.new("L", (W, H), 0)
    td = ImageDraw.Draw(tmp)
    bbox = td.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    x, y = (W - tw) // 2 - bbox[0], (H - th) // 2 - bbox[1] + int(H * 0.06)
    td.text((x, y), text, font=font, fill=255)
    # drop shadow, thick dark outline, gold gradient fill with a bevel highlight
    im.alpha_composite(shadow(tmp, SS * 5, (0, SS * 10), (0, 0, 0, 200)))
    outline = tmp.filter(ImageFilter.MaxFilter(SS * 4 + 1))
    im.alpha_composite(masked(Image.new("RGBA", (W, H), (46, 22, 4, 255)), outline))
    fill = vgrad(W, H, GOLD_LIGHT, GOLD_DARK, GOLD)
    # bevel: brighter upper half of each glyph
    g = masked(fill, tmp)
    im.alpha_composite(g)
    shifted = Image.new("L", (W, H), 0)
    shifted.paste(tmp, (0, SS * 3))
    hl = Image.fromarray(np.clip(np.array(tmp).astype(int) - np.array(shifted).astype(int), 0, 255).astype(np.uint8))
    im.alpha_composite(masked(Image.new("RGBA", (W, H), (255, 250, 220, 255)), hl.point(lambda a: int(a * 0.85))))
    # engraved line through the middle
    mid = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mid).rectangle((0, y + th // 2 + SS * 2, W, y + th // 2 + SS * 4), 255)
    mid = Image.fromarray((np.array(mid) * np.array(tmp) / 255).astype(np.uint8))
    im.alpha_composite(masked(Image.new("RGBA", (W, H), (120, 75, 10, 255)), mid.point(lambda a: int(a * 0.6))))
    save(im, name, w, h)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    button("ui_button_gold", GOLD_LIGHT, GOLD_DARK, GOLD_SHADE)
    button("ui_button_green", (120, 215, 110), GREEN_DARK, (22, 70, 28))
    button("ui_button_blue", (95, 150, 255), ROYAL_DARK, (14, 32, 90))
    button("ui_button_red", (240, 110, 100), RED_DARK, (90, 16, 16))
    button("ui_button_grey", (150, 156, 170), GREY_DARK, (40, 44, 54))
    button("ui_button_wood", WOOD_LIGHT, WOOD_DARK, (34, 18, 6))
    panel("ui_panel", PARCH, PARCH_DARK)
    panel("ui_panel_dark", (38, 52, 86), NAVY_DEEP, parchment=False)
    card("ui_card")
    ribbon("ui_ribbon_red", RED, RED_DARK)
    ribbon("ui_ribbon_blue", ROYAL, ROYAL_DARK)
    circle_button("ui_circle")
    circle_button("ui_circle_red", GOLD, RED_DARK)
    bar("ui_bar_track")
    bar("ui_bar_gold", GOLD_LIGHT, GOLD_DARK, track=False)
    bar("ui_bar_blue", (120, 180, 255), ROYAL_DARK, track=False)
    bar("ui_bar_red", (255, 120, 110), RED_DARK, track=False)
    bar("ui_bar_green", (140, 230, 120), GREEN_DARK, track=False)
    tab("ui_tab_on", GOLD_LIGHT, GOLD_DARK)
    tab("ui_tab_off", (70, 84, 118), NAVY_DEEP)
    pill("ui_pill", NAVY_DEEP)
    logotype("ui_logotype")
    with open(os.path.join(ROOT, "art/ui/ui_kit.json"), "w") as f:
        json.dump(SLICES, f, indent=1)
    print("slices written")
