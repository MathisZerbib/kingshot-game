"""Menu v2 mock-ups ("le héros tient l'écran"): the hero background is the hero selector, one nameplate, one
primary button, one tab bar. Reference for the Compose implementation (spec: art/ui/DA-v2.md).

    python mockup_v2.py <repo_root> <out_dir>
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.argv = sys.argv[:3]
import mockup as M  # noqa: E402  (shares img/nine/font/text helpers and the argv convention)

# DA v3: round display face instead of Cinzel, white titles, soft shadows instead of outlines
_font = M.font
M.font = lambda name, size: _font("lilita_one_regular" if name.startswith("cinzel") else name, size)
_text = M.text
M.text = lambda draw, xy, s, fnt, fill=M.WHITE, anchor="mm", stroke=0, stroke_fill=(20, 12, 4), shadow=True: _text(
    draw, xy, s, fnt, M.WHITE if fill == M.GOLD_LIGHT else fill, anchor, 0, stroke_fill, shadow)
M.GOLD_LIGHT = (255, 255, 255)

W, H = M.W, M.H
NAVY_DEEP = (12, 19, 38)
GOLD, GOLD_LIGHT, WHITE, INK = (255, 200, 60), (255, 255, 255), M.WHITE, (20, 30, 60)
CLASSES = [
    dict(key="warrior", name="GUERRIER", role="Tient la ligne au corps à corps", ability="Tourbillon", icon="sword", stats=(3, 2, 1)),
    dict(key="ranger", name="RÔDEUR", role="Tir rapide à longue portée", ability="Pluie de flèches", icon="bow", stats=(1, 3, 3)),
    dict(key="builder", name="BÂTISSEUR", role="Bâtit vite, répare les murs", ability="Réparation", icon="hammer", stats=(2, 1, 2)),
]
STAT_NAMES = ["Vie", "Attaque", "Vitesse"]
STAT_BARS = ["ui_bar_red", "ui_bar_gold", "ui_bar_blue"]


def background(cls):
    """Background fitted to the width and pinned to the top; the bottom 480 px melt into deep navy where the UI lives."""
    bg = M.img("menu_bg_" + cls)
    bg = bg.resize((W, int(bg.height * W / bg.width)), Image.LANCZOS)  # 1080 x 1920
    c = Image.new("RGBA", (W, H), NAVY_DEEP + (255,))
    c.alpha_composite(bg, (0, 0))
    arr = np.array(c).astype(float)
    yy = np.arange(H)[:, None]
    # top vignette for the logotype, bottom melt into navy
    top = np.clip((300 - yy) / 300, 0, 1) ** 1.4 * 0.55
    melt = np.clip((yy - 1500) / 420, 0, 1) ** 1.1
    navy = np.array(NAVY_DEEP, float)
    rgb = arr[..., :3]
    rgb = rgb * (1 - top[..., None])
    rgb = rgb * (1 - melt[..., None]) + navy * melt[..., None]
    arr[..., :3] = rgb
    return Image.fromarray(arr.astype("uint8"), "RGBA")


def portrait_chip(c, cx, cy, cls, size, selected):
    d = ImageDraw.Draw(c)
    if selected:
        glow = Image.new("RGBA", (size + 80, size + 80), (0, 0, 0, 0))
        ImageDraw.Draw(glow).ellipse((20, 20, size + 60, size + 60), fill=(255, 200, 60, 190))
        glow = glow.filter(ImageFilter.GaussianBlur(16))
        c.alpha_composite(glow, (int(cx - size / 2 - 40), int(cy - size / 2 - 40)))
    ring = M.img("ui_circle").resize((size, size), Image.LANCZOS)
    c.alpha_composite(ring, (int(cx - size / 2), int(cy - size / 2)))
    ps = int(size * 0.8)
    p = M.img("portrait_" + cls["key"]).resize((ps, ps), Image.LANCZOS)
    mask = Image.new("L", p.size, 0)
    ImageDraw.Draw(mask).ellipse((1, 1, ps - 1, ps - 1), 255)
    c.paste(p, (int(cx - ps / 2), int(cy - ps / 2)), mask)
    if selected:
        d.ellipse((cx - size / 2 + 4, cy - size / 2 + 4, cx + size / 2 - 4, cy + size / 2 - 4), outline=(255, 214, 90, 255), width=7)
    else:
        shade = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        ImageDraw.Draw(shade).ellipse((0, 0, size - 1, size - 1), fill=(10, 14, 30, 95))
        c.alpha_composite(shade, (int(cx - size / 2), int(cy - size / 2)))


def ornament(d, cx, y, half=250, gap=190):
    """Gold rule + diamond on each side of the hero name."""
    for s in (-1, 1):
        x0, x1 = cx + s * gap, cx + s * half
        d.line((x0, y, x1, y), fill=(255, 255, 255, 110), width=3)
        dx = x1
        d.ellipse((dx - 7, y - 7, dx + 7, y + 7), fill=GOLD + (255,))


def nameplate(c, cls, hero_level=12, xp=0.62):
    d = ImageDraw.Draw(c)
    cx = W // 2
    M.text(d, (cx, 1600), cls["name"], M.font("cinzel_bold", 68), GOLD_LIGHT, stroke=3)
    ornament(d, cx, 1600)
    M.text(d, (cx, 1668), cls["role"], M.font("nunito_extrabold", 32), (235, 240, 255), stroke=0)
    # hero level + xp under the name
    c.alpha_composite(M.nine("ui_bar_track", 300, 26), (cx - 150, 1712))
    c.alpha_composite(M.nine("ui_bar_blue", int(300 * xp), 26), (cx - 150, 1712))
    M.text(d, (cx, 1760), "Héros niv. %d" % hero_level, M.font("nunito_black", 24), (150, 185, 255), stroke=0)
    # three stat rows: label left, bar right
    x0, bw, bh = 110, 330, 36
    for i, (name, bar) in enumerate(zip(STAT_NAMES, STAT_BARS)):
        y = 1812 + i * 56
        M.text(d, (x0, y + bh / 2), name, M.font("nunito_extrabold", 30), WHITE, anchor="lm", stroke=0)
        bx = x0 + 160
        c.alpha_composite(M.nine("ui_bar_track", bw, bh), (bx, y))
        frac = cls["stats"][i] / 3
        c.alpha_composite(M.nine(bar, max(60, int(bw * frac)), bh), (bx, y))
        for k in range(1, 3):  # pip separators
            d.line((bx + bw * k / 3, y + 6, bx + bw * k / 3, y + bh - 6), fill=(12, 19, 38, 200), width=4)
    # ability chip on the right
    cxr = 850
    M.text(d, (cxr, 1826), "Capacité", M.font("nunito_extrabold", 24), (170, 180, 210), stroke=0)
    chip = M.nine("ui_pill", 330, 84)
    c.alpha_composite(chip, (cxr - 165, 1850))
    ic = M.img("ui_icon_" + cls["icon"]).resize((54, 54), Image.LANCZOS)
    c.alpha_composite(ic, (cxr - 165 + 22, 1865))
    M.text(d, (cxr + 20, 1892), cls["ability"], M.font("nunito_black", 26), GOLD_LIGHT, stroke=0)


def tabbar(c, d, active=-1, badge=2):
    bar = Image.new("RGBA", (W, 130), (0, 0, 0, 0))
    ImageDraw.Draw(bar).rounded_rectangle((40, 0, W - 40, 130), 30, fill=(16, 26, 48, 235), outline=(255, 255, 255, 28), width=3)
    c.alpha_composite(bar, (0, 2196))
    tw = 236
    for i, (lab, ic) in enumerate([("BOUTIQUE", "chest"), ("TALENTS", "star"), ("MISSIONS", "scroll"), ("STATS", "trophy")]):
        x = 62 + i * (tw + 6)
        on = i == active
        c.alpha_composite(M.nine("ui_tab_on" if on else "ui_tab_off", tw, 110), (x, 2206))
        icon = M.img("ui_icon_" + ic).resize((46, 46), Image.LANCZOS)
        c.alpha_composite(icon, (x + 16, 2238))
        M.text(d, (x + tw / 2 + 22, 2262), lab, M.font("cinzel_bold", 23), INK if on else WHITE, stroke=0 if on else 2)
        if i == 2 and badge:
            d.ellipse((x + tw - 44, 2194, x + tw, 2238), fill=(215, 59, 59, 255), outline=WHITE + (255,), width=3)
            M.text(d, (x + tw - 22, 2216), str(badge), M.font("nunito_black", 26), WHITE, shadow=False)


def main_menu(picked=0, has_save=True, gift=False, treasure="4 432"):
    cls = CLASSES[picked]
    c = background(cls["key"])
    d = ImageDraw.Draw(c)
    # top bar: settings left, treasure right, gift only when available
    M.circle_icon(c, 100, 150, "gear", 104)
    if gift:
        M.circle_icon(c, 100, 290, "gift", 96, red=True, badge="!")
    M.pill(c, d, W - 330, 108, 280, 84, treasure, "coins")
    # logotype, smaller, pinned top: no crown, no tagline
    logo = M.img("ui_logotype")
    logo = logo.resize((680, int(logo.height * 680 / logo.width)), Image.LANCZOS)
    c.alpha_composite(logo, (W // 2 - 340, 215))
    # hero chips over the melt line
    size, big = 150, 186
    xs = [W // 2 - 300, W // 2, W // 2 + 300]
    for i, cc in enumerate(CLASSES):
        sel = i == picked
        portrait_chip(c, xs[i], 1462 - (14 if sel else 0), cc, big if sel else size, sel)
    # swipe hint: dots are the chips themselves, so nothing else
    nameplate(c, cls)
    # primary button, one per screen
    if has_save:
        M.button(c, d, W // 2 - 380, 1976, 760, 160, "gold", "CONTINUER", "Niveau 8 · La Clairière", icon="play")
    else:
        M.button(c, d, W // 2 - 380, 1976, 760, 160, "gold", "JOUER", "%s · nouvelle partie" % cls["name"].title(), icon="play")
    tabbar(c, d, badge=2 if picked != 2 else 0)
    if has_save:
        M.text(d, (W // 2, 2168), "Nouvelle partie", M.font("nunito_extrabold", 26), (200, 190, 160), stroke=0)
        d.line((W // 2 - 95, 2184, W // 2 + 95, 2184), fill=(200, 190, 160, 200), width=2)
    return c


def tint(im, hue_shift=0.0, sat=1.0, val=1.0):
    """HSV recolor of a sprite (preview of a skin)."""
    hsv = np.array(im.convert("RGBA").convert("RGB").convert("HSV")).astype(float)
    a = np.array(im.convert("RGBA"))[..., 3]
    hsv[..., 0] = (hsv[..., 0] + hue_shift * 255) % 255
    hsv[..., 1] = np.clip(hsv[..., 1] * sat, 0, 255)
    hsv[..., 2] = np.clip(hsv[..., 2] * val, 0, 255)
    rgb = Image.fromarray(hsv.astype("uint8"), "HSV").convert("RGBA")
    out = np.array(rgb); out[..., 3] = a
    return Image.fromarray(out, "RGBA")


SKINS = [  # name, price/unlock, hue shift, sat, val, state
    ("Roi de sang", "Équipé", 0.0, 1.0, 1.0, "equipped"),
    ("Roi d'or", "1 500", 0.1, 1.3, 1.15, "buy"),
    ("Roi de givre", "3 000", 0.55, 1.1, 1.1, "buy"),
    ("Roi des ombres", "6 000", 0.0, 0.3, 0.55, "locked"),
    ("Roi d'émeraude", "Niveau 10", 0.3, 1.2, 1.0, "locked"),
    ("Roi solaire", "Boss 1 vaincu", 0.12, 1.5, 1.3, "locked"),
]


def shop(category=0):
    c = M.background(0.0, 0.0)
    shade = Image.new("RGBA", (W, H), (8, 12, 26, 205))
    c.alpha_composite(shade)
    d = ImageDraw.Draw(c)
    # header: close left, title, treasure right
    M.circle_icon(c, 100, 150, "close", 100)
    M.text(d, (W // 2, 150), "BOUTIQUE", M.font("cinzel_bold", 62), GOLD_LIGHT, stroke=3)
    M.pill(c, d, W - 330, 108, 280, 84, "4 432", "coins")
    # category tabs
    cats = [("ROIS", "crown"), ("ÉTENDARDS", "flag"), ("MONTURES", "heart"), ("TRAÎNÉES", "coins")]
    tw = 236
    for i, (lab, ic) in enumerate(cats):
        x = 62 + i * (tw + 6)
        on = i == category
        c.alpha_composite(M.nine("ui_tab_on" if on else "ui_tab_off", tw, 100), (x, 250))
        icon = M.img("ui_icon_" + ic).resize((40, 40), Image.LANCZOS)
        c.alpha_composite(icon, (x + 16, 278))
        M.text(d, (x + tw / 2 + 18, 300), lab, M.font("cinzel_bold", 22), INK if on else WHITE, stroke=0 if on else 2)
    # showcase: the selected skin, big, on the hero's own background
    panel = M.nine("ui_panel_dark", 980, 760)
    c.alpha_composite(panel, (50, 390))
    bg = M.img("menu_bg_warrior").resize((540, 960), Image.LANCZOS).crop((40, 300, 500, 760))
    mask = Image.new("L", bg.size, 0); ImageDraw.Draw(mask).rounded_rectangle((0, 0, bg.width - 1, bg.height - 1), 28, 255)
    c.paste(bg, (90, 440), mask)
    big = tint(M.img("portrait_warrior"), 0.1, 1.3, 1.15).resize((360, 360), Image.LANCZOS)
    c.alpha_composite(big, (140, 500))
    M.text(d, (780, 480), "ROI D'OR", M.font("cinzel_bold", 46), GOLD_LIGHT, stroke=2)
    M.text(d, (780, 540), "Guerrier · skin", M.font("nunito_extrabold", 26), (170, 180, 210), stroke=0)
    for k, line in enumerate(["Armure dorée et cape pourpre", "Traînée de pièces dorée incluse", "Aucun bonus de jeu : le style, c'est tout"]):
        M.text(d, (780, 610 + k * 44), line, M.font("nunito_extrabold", 24), WHITE, stroke=0)
    M.button(c, d, 600, 780, 360, 120, "gold", "1 500", None, icon="coins")
    M.text(d, (780, 940), "Essayer en jeu : la skin s'applique dès la prochaine vague", M.font("nunito_extrabold", 20), (170, 180, 210), stroke=0)
    # grid 3 x 2
    cw, ch = 300, 400
    for i, (name, price, hs, sa, va, state) in enumerate(SKINS):
        x = 60 + (i % 3) * (cw + 30)
        y = 1200 + (i // 3) * (ch + 30)
        card = M.nine("ui_card", cw, ch)
        c.alpha_composite(card, (x, y))
        if state == "equipped":
            glow = Image.new("RGBA", (cw + 40, ch + 40), (0, 0, 0, 0))
            ImageDraw.Draw(glow).rounded_rectangle((10, 10, cw + 30, ch + 30), 36, fill=(255, 215, 90, 180))
            glow = glow.filter(ImageFilter.GaussianBlur(14))
            c.alpha_composite(glow, (x - 20, y - 20)); c.alpha_composite(card, (x, y))
        M.text(d, (x + cw / 2, y + 56), name.upper(), M.font("cinzel_bold", 22), GOLD_LIGHT, stroke=1)
        p = tint(M.img("portrait_warrior"), hs, sa, va).resize((200, 200), Image.LANCZOS)
        if state == "locked":
            p = tint(p, 0, 0.0, 0.7)
        c.alpha_composite(p, (x + cw // 2 - 100, y + 95))
        if state == "locked":
            lock = M.img("ui_icon_lock").resize((70, 70), Image.LANCZOS)
            c.alpha_composite(lock, (x + cw // 2 - 35, y + 160))
            M.text(d, (x + cw / 2, y + 345), price, M.font("nunito_black", 24), (200, 200, 220), stroke=0)
        elif state == "equipped":
            M.button(c, d, x + 40, y + 300, cw - 80, 80, "green", "ÉQUIPÉ")
        else:
            M.button(c, d, x + 40, y + 300, cw - 80, 80, "gold", price, icon="coins")
    M.text(d, (W // 2, 2130), "Les skins ne changent pas la difficulté. Tout s'achète avec le trésor gagné en jeu.", M.font("nunito_extrabold", 22), (170, 180, 210), stroke=0)
    return c


def strip(frames, scale=0.5):
    w = int(W * scale)
    h = int(H * scale)
    out = Image.new("RGB", (len(frames) * (w + 20) - 20, h), (0, 0, 0))
    for i, f in enumerate(frames):
        out.paste(f.resize((w, h), Image.LANCZOS).convert("RGB"), (i * (w + 20), 0))
    return out


if __name__ == "__main__":
    os.makedirs(M.OUTDIR, exist_ok=True)
    frames = []
    for i, (save, gift) in enumerate([(True, False), (False, True), (True, False)]):
        im = main_menu(i, save, gift)
        im.convert("RGB").save(os.path.join(M.OUTDIR, "maquette-v2-%s.png" % CLASSES[i]["key"]), quality=92)
        frames.append(im)
    sh = shop()
    sh.convert("RGB").save(os.path.join(M.OUTDIR, "maquette-v2-boutique.png"), quality=92)
    frames.append(sh)
    strip(frames).save(os.path.join(M.OUTDIR, "maquettes-v2-menu.png"), quality=90)
    print("ok")
