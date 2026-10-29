"""UI kit v3 "moderne" : same file names and 9-slice insets as v1 (art/ui/ui_kit.json) so the Compose code keeps working,
but a flat, rounded, gradient look (casual-premium mobile) instead of wood + gold + parchment.

    python make_ui_kit_v3.py <repo_root>
"""
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = sys.argv[1]
OUT = os.path.join(ROOT, "composeApp/src/commonMain/composeResources/drawable")
FONTS = os.path.join(ROOT, "composeApp/src/commonMain/composeResources/font")
SS = 2  # supersampling

INK = (11, 19, 38)
GLASS = (16, 26, 48)
GLASS_LIGHT = (26, 40, 70)
WHITE = (255, 255, 255)
LIGHT = (244, 247, 255)
LIGHT2 = (226, 233, 247)

GRADS = {  # top, bottom, 3D edge, border
    "gold": ((255, 221, 92), (255, 160, 38), (196, 112, 0), (120, 70, 0)),
    "green": ((118, 236, 118), (42, 182, 78), (24, 122, 50), (12, 80, 34)),
    "blue": ((100, 170, 255), (46, 108, 232), (28, 70, 170), (16, 44, 120)),
    "red": ((255, 128, 112), (226, 54, 72), (160, 28, 44), (110, 16, 30)),
    "grey": ((142, 154, 178), (90, 102, 130), (58, 68, 90), (40, 48, 66)),
    "wood": ((48, 64, 104), (28, 40, 70), (14, 22, 46), (90, 110, 160)),  # "wood" slot = dark ghost/secondary button
}
BAR_FILLS = {
    "gold": ((255, 226, 110), (255, 170, 40)),
    "blue": ((110, 180, 255), (48, 112, 236)),
    "red": ((255, 130, 120), (224, 60, 76)),
    "green": ((130, 236, 130), (46, 186, 86)),
}


def canvas(w, h):
    return Image.new("RGBA", (w * SS, h * SS), (0, 0, 0, 0))


def down(im, w, h):
    return im.resize((w, h), Image.LANCZOS)


def vgrad(w, h, top, bottom, alpha=255):
    t = np.linspace(0, 1, h)[:, None, None]
    c = np.array(top, float)[None, None, :] * (1 - t) + np.array(bottom, float)[None, None, :] * t
    arr = np.zeros((h, w, 4), np.uint8)
    arr[..., :3] = np.repeat(c, w, axis=1).astype(np.uint8)
    arr[..., 3] = alpha
    return Image.fromarray(arr, "RGBA")


def rrect_mask(w, h, r):
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w - 1, h - 1), r, fill=255)
    return m


def paste_rrect(dst, grad, box, r):
    x, y, w, h = box
    dst.paste(grad, (x, y), rrect_mask(w, h, r))


def highlight(dst, box, r, strength=90, height=0.42):
    """Soft white sheen on the upper part of a rounded rect."""
    x, y, w, h = box
    hh = int(h * height)
    g = vgrad(w, hh, (255, 255, 255), (255, 255, 255))
    a = np.linspace(strength, 0, hh).astype(np.uint8)
    arr = np.array(g); arr[..., 3] = a[:, None]
    g = Image.fromarray(arr, "RGBA")
    m = rrect_mask(w, h, r).crop((0, 0, w, hh))
    layer = Image.new("RGBA", dst.size, (0, 0, 0, 0))
    layer.paste(g, (x, y), m)
    dst.alpha_composite(layer)


def button(kind, w=480, h=150):
    top, bottom, edge, border = GRADS[kind]
    c = canvas(w, h)
    W, H = w * SS, h * SS
    r = 44 * SS
    depth = 22 * SS  # lives inside the 52 px bottom inset
    # border + 3D edge
    paste_rrect(c, vgrad(W, H, border, border), (0, 0, W, H), r)
    paste_rrect(c, vgrad(W - 6, H - 6, edge, edge), (3, 3, W - 6, H - 6), r - 3)
    # face
    face = vgrad(W - 6, H - 6 - depth, top, bottom)
    paste_rrect(c, face, (3, 3, W - 6, H - 6 - depth), r - 3)
    highlight(c, (3, 3, W - 6, H - 6 - depth), r - 3, strength=110 if kind != "wood" else 40)
    if kind == "wood":  # ghost button: thin light border
        d = ImageDraw.Draw(c)
        d.rounded_rectangle((3, 3, W - 4, H - 4), r - 3, outline=(150, 170, 220, 140), width=3 * SS)
    return down(c, w, h)


def panel(dark, w=600, h=600):
    c = canvas(w, h)
    W, H = w * SS, h * SS
    r = 40 * SS
    if dark:
        paste_rrect(c, vgrad(W, H, (255, 255, 255), (255, 255, 255), 36), (0, 0, W, H), r)  # faint border
        paste_rrect(c, vgrad(W - 4, H - 4, GLASS_LIGHT, GLASS, 238), (2, 2, W - 4, H - 4), r - 2)
        highlight(c, (2, 2, W - 4, H - 4), r - 2, strength=28, height=0.25)
    else:
        paste_rrect(c, vgrad(W, H, (200, 210, 235), (170, 184, 215)), (0, 0, W, H), r)
        paste_rrect(c, vgrad(W - 4, H - 4, LIGHT, LIGHT2), (2, 2, W - 4, H - 4), r - 2)
    return down(c, w, h)


def card(w=360, h=520, header=104):
    c = canvas(w, h)
    W, H, hd = w * SS, h * SS, header * SS
    r = 32 * SS
    paste_rrect(c, vgrad(W, H, (255, 255, 255), (255, 255, 255), 40), (0, 0, W, H), r)
    paste_rrect(c, vgrad(W - 4, H - 4, GLASS_LIGHT, GLASS, 240), (2, 2, W - 4, H - 4), r - 2)
    # header band (title) : blue gradient, rounded only at the top
    band = vgrad(W - 4, hd, (96, 166, 255), (48, 110, 232))
    m = rrect_mask(W - 4, hd + r, r - 2).crop((0, 0, W - 4, hd))
    layer = Image.new("RGBA", c.size, (0, 0, 0, 0)); layer.paste(band, (2, 2), m)
    c.alpha_composite(layer)
    highlight(c, (2, 2, W - 4, hd), r - 2, strength=70, height=0.5)
    return down(c, w, h)


def ribbon(kind, w=800, h=170):
    """Modern title plate: wide rounded band with a gradient and a glow, no medieval tails."""
    top, bottom, edge, border = GRADS[kind]
    c = canvas(w, h)
    W, H = w * SS, h * SS
    r = 48 * SS
    glow = Image.new("RGBA", c.size, (0, 0, 0, 0))
    ImageDraw.Draw(glow).rounded_rectangle((60 * SS, 28 * SS, W - 60 * SS, H - 20 * SS), r, fill=bottom + (160,))
    glow = glow.filter(ImageFilter.GaussianBlur(14 * SS))
    c.alpha_composite(glow)
    box = (80 * SS, 34 * SS, W - 160 * SS, H - 70 * SS)
    paste_rrect(c, vgrad(box[2], box[3], border, border), box, r)
    inner = (box[0] + 3, box[1] + 3, box[2] - 6, box[3] - 6)
    paste_rrect(c, vgrad(inner[2], inner[3], top, bottom), inner, r - 3)
    highlight(c, inner, r - 3, strength=90)
    return down(c, w, h)


def bar(fill, w=400, h=44):
    c = canvas(w, h)
    W, H = w * SS, h * SS
    r = H // 2
    if fill is None:
        paste_rrect(c, vgrad(W, H, (6, 10, 22), (14, 22, 44), 230), (0, 0, W, H), r)
        d = ImageDraw.Draw(c)
        d.rounded_rectangle((0, 0, W - 1, H - 1), r, outline=(255, 255, 255, 40), width=2 * SS)
    else:
        top, bottom = BAR_FILLS[fill]
        paste_rrect(c, vgrad(W - 8, H - 8, top, bottom), (4, 4, W - 8, H - 8), r - 4)
        highlight(c, (4, 4, W - 8, H - 8), r - 4, strength=120, height=0.5)
    return down(c, w, h)


def tab(on, w=320, h=110):
    c = canvas(w, h)
    W, H = w * SS, h * SS
    r = 30 * SS
    if on:
        top, bottom, edge, border = GRADS["blue"]
        paste_rrect(c, vgrad(W, H - 10 * SS, edge, edge), (0, 0, W, H - 10 * SS), r)
        paste_rrect(c, vgrad(W, H - 18 * SS, top, bottom), (0, 0, W, H - 18 * SS), r)
        highlight(c, (0, 0, W, H - 18 * SS), r, strength=90)
    else:
        paste_rrect(c, vgrad(W, H - 10 * SS, (255, 255, 255), (255, 255, 255), 18), (0, 0, W, H - 10 * SS), r)
    return down(c, w, h)


def pill(w=300, h=90):
    c = canvas(w, h)
    W, H = w * SS, h * SS
    r = H // 2
    paste_rrect(c, vgrad(W, H, (255, 255, 255), (255, 255, 255), 44), (0, 0, W, H), r)
    paste_rrect(c, vgrad(W - 4, H - 4, (10, 16, 34), (18, 28, 52), 235), (2, 2, W - 4, H - 4), r - 2)
    return down(c, w, h)


def circle(red, size=200):
    c = canvas(size, size)
    S = size * SS
    d = ImageDraw.Draw(c)
    if red:
        top, bottom, edge, border = GRADS["red"]
        d.ellipse((0, 0, S - 1, S - 1), fill=edge + (255,))
        g = vgrad(S - 8, S - 8 - 8 * SS, top, bottom)
        m = Image.new("L", g.size, 0); ImageDraw.Draw(m).ellipse((0, 0, g.width - 1, g.height - 1), 255)
        c.paste(g, (4, 4), m)
    else:
        d.ellipse((0, 0, S - 1, S - 1), fill=(255, 255, 255, 50))
        g = vgrad(S - 6, S - 6, GLASS_LIGHT, GLASS, 240)
        m = Image.new("L", g.size, 0); ImageDraw.Draw(m).ellipse((0, 0, g.width - 1, g.height - 1), 255)
        c.paste(g, (3, 3), m)
    # sheen
    sheen = Image.new("RGBA", c.size, (0, 0, 0, 0))
    ImageDraw.Draw(sheen).ellipse((S * 0.18, S * 0.08, S * 0.82, S * 0.5), fill=(255, 255, 255, 70))
    sheen = sheen.filter(ImageFilter.GaussianBlur(10 * SS))
    c.alpha_composite(sheen)
    return down(c, size, size)


def logotype(w=1200, h=360):
    """KINGSHOT in Lilita One: white→gold gradient fill, navy outline, drop shadow, slight upward arc."""
    c = canvas(w, h)
    W, H = w * SS, h * SS
    text = "KINGSHOT"
    size = 290 * SS
    while True:  # largest size whose letters (plus outline) fit the width
        font = ImageFont.truetype(os.path.join(FONTS, "lilita_one_regular.ttf"), int(size))
        widths = [font.getlength(ch) for ch in text]
        total = sum(widths) + (len(text) - 1) * 6 * SS
        if total + 40 * SS <= W or size < 100:
            break
        size *= 0.97
    x = (W - total) / 2
    n = len(text)
    layer = Image.new("RGBA", c.size, (0, 0, 0, 0))
    mask = Image.new("L", c.size, 0)
    dm = ImageDraw.Draw(mask)
    dl = ImageDraw.Draw(layer)
    for i, ch in enumerate(text):
        t = (i - (n - 1) / 2) / ((n - 1) / 2)
        dy = int(18 * SS * t * t)  # ends lower, middle higher
        pos = (x, H * 0.5 + dy)
        dl.text(pos, ch, font=font, fill=(0, 0, 0, 255), anchor="lm", stroke_width=int(14 * SS), stroke_fill=(0, 0, 0, 255))
        dm.text(pos, ch, font=font, fill=255, anchor="lm")
        x += widths[i] + 6 * SS
    # shadow
    shadow = layer.copy().filter(ImageFilter.GaussianBlur(8 * SS))
    sh = np.array(shadow); sh[..., :3] = (0, 0, 0); sh[..., 3] = (sh[..., 3] * 0.55).astype(np.uint8)
    c.alpha_composite(Image.fromarray(sh, "RGBA"), (0, int(14 * SS)))
    # outline (navy)
    out = np.array(layer); out[..., :3] = INK
    c.alpha_composite(Image.fromarray(out, "RGBA"))
    # fill gradient
    # white top, warm gold bottom, with a crisp break at mid-height (classic casual-game logo)
    yy = np.linspace(0, 1, H)
    top_c = np.array((255, 252, 240), float); low_c = np.array((255, 196, 56), float); low2 = np.array((255, 150, 30), float)
    rgb = np.zeros((H, 3), float)
    up = yy < 0.5
    rgb[up] = top_c[None, :] * (1 - yy[up, None] * 0.3) + low_c[None, :] * (yy[up, None] * 0.3)
    t = np.clip((yy[~up] - 0.5) / 0.42, 0, 1)[:, None]
    rgb[~up] = low_c[None, :] * (1 - t) + low2[None, :] * t
    arr = np.zeros((H, W, 4), np.uint8); arr[..., :3] = rgb[:, None, :].astype(np.uint8); arr[..., 3] = 255
    g = Image.fromarray(arr, "RGBA")
    fill = Image.new("RGBA", c.size, (0, 0, 0, 0)); fill.paste(g, (0, 0), mask)
    c.alpha_composite(fill)
    return down(c, w, h)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    kit = json.load(open(os.path.join(ROOT, "art/ui/ui_kit.json")))
    outs = {}
    for k in GRADS:
        outs["ui_button_" + k] = button(k)
    outs["ui_panel"] = panel(False)
    outs["ui_panel_dark"] = panel(True)
    outs["ui_card"] = card()
    outs["ui_ribbon_red"] = ribbon("red")
    outs["ui_ribbon_blue"] = ribbon("blue")
    outs["ui_bar_track"] = bar(None)
    for k in BAR_FILLS:
        outs["ui_bar_" + k] = bar(k)
    outs["ui_tab_on"] = tab(True)
    outs["ui_tab_off"] = tab(False)
    outs["ui_pill"] = pill()
    outs["ui_circle"] = circle(False)
    outs["ui_circle_red"] = circle(True)
    outs["ui_logotype"] = logotype()
    for name, im in outs.items():
        if name in kit:
            assert im.size == (kit[name]["w"], kit[name]["h"]), name
        im.save(os.path.join(OUT, name + ".png"), optimize=True)
    print("ok", len(outs))
