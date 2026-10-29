"""Static mock-ups of the Kingshot menu screens built from the UI kit (reference for the Compose implementation).

    python mockup.py <repo_root> <out_dir>
"""
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT, OUTDIR = sys.argv[1], sys.argv[2]
D = os.path.join(ROOT, "composeApp/src/commonMain/composeResources/drawable")
F = os.path.join(ROOT, "composeApp/src/commonMain/composeResources/font")
SL = json.load(open(os.path.join(ROOT, "art/ui/ui_kit.json")))
W, H = 1080, 2400
GOLD, GOLD_LIGHT, PARCH, NAVY, WHITE, INK = (242, 193, 78), (255, 232, 150), (243, 230, 200), (21, 33, 59), (255, 255, 255), (58, 36, 12)


def img(name):
    return Image.open(os.path.join(D, name + ".png")).convert("RGBA")


def font(name, size):
    return ImageFont.truetype(os.path.join(F, name + ".ttf"), size)


def nine(name, w, h):
    """9-slice scale of a kit asset to w x h."""
    im = img(name)
    s = SL[name]
    l, t, r, b = s["left"], s["top"], s["right"], s["bottom"]
    sw, sh = im.size
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    xs = [(0, l, 0, l), (l, sw - r, l, w - r), (sw - r, sw, w - r, w)]
    ys = [(0, t, 0, t), (t, sh - b, t, h - b), (sh - b, sh, h - b, h)]
    for (sx0, sx1, dx0, dx1) in xs:
        for (sy0, sy1, dy0, dy1) in ys:
            if dx1 <= dx0 or dy1 <= dy0:
                continue
            piece = im.crop((sx0, sy0, sx1, sy1)).resize((dx1 - dx0, dy1 - dy0), Image.LANCZOS)
            out.alpha_composite(piece, (dx0, dy0))
    return out


def text(draw, xy, s, fnt, fill=WHITE, anchor="mm", stroke=0, stroke_fill=(20, 12, 4), shadow=True):
    x, y = xy
    if shadow:
        draw.text((x + 2, y + 4), s, font=fnt, fill=(0, 0, 0, 150), anchor=anchor, stroke_width=stroke, stroke_fill=(0, 0, 0, 150))
    draw.text((x, y), s, font=fnt, fill=fill, anchor=anchor, stroke_width=stroke, stroke_fill=stroke_fill)


def button(canvas, draw, x, y, w, h, kind, label, sub=None, icon=None):
    canvas.alpha_composite(nine("ui_button_" + kind, w, h), (x, y))
    fill = INK if kind == "gold" else WHITE
    ty = y + h * 0.42 if sub else y + h * 0.44
    if icon:
        ic = img("ui_icon_" + icon).resize((int(h * 0.62), int(h * 0.62)), Image.LANCZOS)
        canvas.alpha_composite(ic, (x + 28, int(y + h * 0.44 - ic.height / 2)))
    text(draw, (x + w / 2 + (18 if icon else 0), ty), label, font("cinzel_bold", int(h * 0.36)), fill, stroke=2 if kind != "gold" else 0)
    if sub:
        text(draw, (x + w / 2, y + h * 0.72), sub, font("nunito_extrabold", int(h * 0.2)), fill if kind == "gold" else (235, 240, 255))


def ribbon(canvas, draw, cx, y, w, label, color="red", size=46):
    canvas.alpha_composite(nine("ui_ribbon_" + color, w, 130), (int(cx - w / 2), y))
    text(draw, (cx, y + 62), label, font("cinzel_bold", size), GOLD_LIGHT, stroke=2)


def circle_icon(canvas, cx, cy, icon, size=120, red=False, badge=None):
    c = img("ui_circle_red" if red else "ui_circle").resize((size, size), Image.LANCZOS)
    canvas.alpha_composite(c, (int(cx - size / 2), int(cy - size / 2)))
    ic = img("ui_icon_" + icon).resize((int(size * 0.62), int(size * 0.62)), Image.LANCZOS)
    canvas.alpha_composite(ic, (int(cx - ic.width / 2), int(cy - ic.height / 2 - size * 0.02)))
    if badge:
        d = ImageDraw.Draw(canvas)
        bx, by = cx + size * 0.34, cy - size * 0.34
        d.ellipse((bx - 22, by - 22, bx + 22, by + 22), fill=(215, 59, 59, 255), outline=(255, 255, 255, 255), width=3)
        text(d, (bx, by), str(badge), font("nunito_black", 26), WHITE, shadow=False)


def background(dark_top=0.22, dark_bottom=0.5):
    bg = img("menu_bg_warrior")
    k = max(W / bg.width, H / bg.height)
    bg = bg.resize((int(bg.width * k), int(bg.height * k)), Image.LANCZOS)
    bg = bg.crop(((bg.width - W) // 2, 0, (bg.width - W) // 2 + W, H))
    import numpy as np
    arr = np.array(bg).astype(float)
    yy = np.linspace(0, 1, H)[:, None]
    top = np.clip((dark_top - yy) / dark_top, 0, 1) ** 1.5 * 0.45
    bot = np.clip((yy - (1 - dark_bottom)) / dark_bottom, 0, 1) ** 1.2 * 0.78
    shade = 1 - np.maximum(top, bot)
    arr[..., :3] *= shade[..., None]
    return Image.fromarray(arr.astype("uint8"), "RGBA")


def pill(canvas, draw, x, y, w, h, label, icon=None):
    canvas.alpha_composite(nine("ui_pill", w, h), (x, y))
    off = 0
    if icon:
        ic = img("ui_icon_" + icon).resize((int(h * 0.7), int(h * 0.7)), Image.LANCZOS)
        canvas.alpha_composite(ic, (x + 18, int(y + h * 0.15)))
        off = 26
    text(draw, (x + w / 2 + off, y + h / 2), label, font("nunito_black", int(h * 0.42)), GOLD_LIGHT)


def main_menu():
    c = background()
    d = ImageDraw.Draw(c)
    # top bar
    circle_icon(c, 100, 150, "gear", 120)
    pill(c, d, W - 360, 95, 300, 84, "1 240", "coins")
    circle_icon(c, 100, 300, "gift", 110, red=True, badge="!")
    # hero level + xp bar
    c.alpha_composite(nine("ui_bar_track", 360, 40), (W - 400, 200))
    c.alpha_composite(nine("ui_bar_blue", int(360 * 0.62), 40), (W - 400, 200))
    text(d, (W - 220, 220), "Héros niv. 12", font("nunito_black", 24), WHITE, stroke=2)
    # logotype + crown
    crown = img("ui_icon_crown").resize((150, 150), Image.LANCZOS)
    c.alpha_composite(crown, (W // 2 - 75, 330))
    logo = img("ui_logotype")
    logo = logo.resize((880, int(logo.height * 880 / logo.width)), Image.LANCZOS)
    c.alpha_composite(logo, (W // 2 - 440, 430))
    text(d, (W // 2, 700), "DÉFENDS TON ROYAUME", font("cinzel_bold", 36), GOLD_LIGHT, stroke=2)
    # hero selection
    ribbon(c, d, W // 2, 1330, 620, "CHOISIS TON HÉROS", "blue", 40)
    names = ["GUERRIER", "RÔDEUR", "BÂTISSEUR"]
    ports = ["portrait_warrior", "portrait_ranger", "portrait_builder"]
    roles = ["Corps à corps · très solide", "Tir rapide à longue portée", "Constructions -30 % · aimant"]
    hp = [3, 1, 2]
    cw, ch = 320, 430
    for i in range(3):
        x = 40 + i * (cw + 30)
        y = 1480
        card = nine("ui_card", cw, ch)
        if i == 0:
            glow = Image.new("RGBA", (cw + 40, ch + 40), (0, 0, 0, 0))
            ImageDraw.Draw(glow).rounded_rectangle((10, 10, cw + 30, ch + 30), 36, fill=(255, 215, 90, 200))
            glow = glow.filter(ImageFilter.GaussianBlur(14))
            c.alpha_composite(glow, (x - 20, y - 20))
        c.alpha_composite(card, (x, y))
        text(d, (x + cw / 2, y + 60), names[i], font("cinzel_bold", 30), GOLD_LIGHT, stroke=1)
        ring = img("ui_circle").resize((150, 150), Image.LANCZOS)
        c.alpha_composite(ring, (x + cw // 2 - 75, y + 100))
        p = img(ports[i]).resize((134, 134), Image.LANCZOS)
        mask = Image.new("L", p.size, 0)
        ImageDraw.Draw(mask).ellipse((2, 2, 132, 132), 255)
        c.paste(p, (x + cw // 2 - 67, y + 104), mask)
        for k in range(3):
            heart = img("ui_icon_heart").resize((40, 40), Image.LANCZOS)
            if k >= hp[i]:
                heart = Image.fromarray((__import__("numpy").array(heart) * [0.45, 0.45, 0.45, 1]).astype("uint8"))
            c.alpha_composite(heart, (x + cw // 2 - 66 + k * 46, y + 265))
        text(d, (x + cw / 2, y + 340), roles[i], font("nunito_extrabold", 22), INK, shadow=False)
        text(d, (x + cw / 2, y + 385), ["Tourbillon", "Pluie de flèches", "Réparation"][i], font("nunito_black", 24), (150, 80, 10), shadow=False)
    # buttons
    button(c, d, W // 2 - 300, 1960, 600, 150, "green", "JOUER", "Guerrier · nouvelle partie", icon="play")
    button(c, d, W // 2 - 260, 2130, 520, 110, "gold", "CONTINUER", "Niveau 8 · La Clairière")
    # tabs
    for i, (lab, ic) in enumerate([("TALENTS", "star"), ("MISSIONS", "scroll"), ("STATS", "trophy")]):
        x = 60 + i * 330
        c.alpha_composite(nine("ui_tab_on" if i == 1 else "ui_tab_off", 300, 92), (x, 2290))
        icon = img("ui_icon_" + ic).resize((52, 52), Image.LANCZOS)
        c.alpha_composite(icon, (x + 30, 2308))
        text(d, (x + 180, 2338), lab, font("cinzel_bold", 28), INK if i == 1 else WHITE, stroke=0 if i == 1 else 2)
        if i == 1:
            d.ellipse((x + 268, 2282, x + 308, 2322), fill=(215, 59, 59, 255), outline=WHITE, width=3)
            text(d, (x + 288, 2302), "2", font("nunito_black", 24), WHITE, shadow=False)
    return c


def overlay():
    c = background(0.0, 0.0)
    shade = Image.new("RGBA", (W, H), (8, 12, 26, 185))
    c.alpha_composite(shade)
    return c


def pause():
    c = overlay()
    d = ImageDraw.Draw(c)
    pw, ph = 860, 1060
    x, y = (W - pw) // 2, 640
    c.alpha_composite(nine("ui_panel_dark", pw, ph), (x, y))
    ribbon(c, d, W // 2, y - 50, 560, "PAUSE", "blue", 52)
    text(d, (W // 2, y + 150), "Guerrier · Niveau 8 · Vague 39", font("nunito_black", 32), GOLD_LIGHT)
    text(d, (W // 2, y + 200), "Partie sauvegardée à chaque vague", font("nunito_extrabold", 24), (170, 190, 230))
    button(c, d, W // 2 - 300, y + 280, 600, 150, "green", "REPRENDRE", icon="play")
    button(c, d, W // 2 - 260, y + 460, 520, 120, "blue", "RÉGLAGES", icon="gear")
    button(c, d, W // 2 - 260, y + 610, 520, 120, "wood", "MENU PRINCIPAL", icon="flag")
    # settings toggles preview
    for i, (lab, on) in enumerate([("Sons", True), ("Musique", True), ("Vibrations", False)]):
        yy = y + 790 + i * 70
        text(d, (x + 110, yy), lab, font("nunito_extrabold", 28), WHITE, anchor="lm")
        tog = nine("ui_bar_track", 120, 52)
        c.alpha_composite(tog, (x + pw - 240, yy - 26))
        knob = img("ui_circle").resize((48, 48), Image.LANCZOS)
        c.alpha_composite(knob, (x + pw - 240 + (70 if on else 2), yy - 24))
        if on:
            c.alpha_composite(nine("ui_bar_green", 76, 52), (x + pw - 240, yy - 26))
            c.alpha_composite(knob, (x + pw - 170, yy - 24))
    return c


def defeat():
    c = overlay()
    d = ImageDraw.Draw(c)
    pw, ph = 900, 1180
    x, y = (W - pw) // 2, 560
    c.alpha_composite(nine("ui_panel", pw, ph), (x, y))
    ribbon(c, d, W // 2, y - 50, 760, "LE DONJON EST TOMBÉ", "red", 44)
    skull = img("ui_icon_skull").resize((150, 150), Image.LANCZOS)
    c.alpha_composite(skull, (W // 2 - 75, y + 110))
    stats = [("8", "Niveau"), ("39", "Vague"), ("412", "Ennemis"), ("2 350", "Or ramassé")]
    for i, (v, k) in enumerate(stats):
        sx = x + 70 + i * 195
        c.alpha_composite(nine("ui_pill", 170, 110), (sx, y + 300))
        text(d, (sx + 85, y + 338), v, font("nunito_black", 38), GOLD_LIGHT, shadow=False)
        text(d, (sx + 85, y + 383), k, font("nunito_extrabold", 20), (170, 190, 230), shadow=False)
    text(d, (W // 2, y + 470), "Nouveau record !", font("cinzel_bold", 34), (40, 130, 60), shadow=False)
    coins = img("ui_icon_coins").resize((56, 56), Image.LANCZOS)
    c.alpha_composite(coins, (x + 200, y + 505))
    text(d, (W // 2 + 30, y + 533), "+1 175 pièces au trésor", font("nunito_black", 28), INK, shadow=False)
    button(c, d, W // 2 - 320, y + 600, 640, 160, "gold", "REVIVRE", "Coûte 300 pièces · une seule fois", icon="heart")
    button(c, d, W // 2 - 280, y + 790, 560, 130, "green", "RÉESSAYER", icon="arrow")
    button(c, d, W // 2 - 280, y + 950, 560, 120, "wood", "MENU", icon="flag")
    return c


def talents():
    c = overlay()
    d = ImageDraw.Draw(c)
    pw, ph = 980, 1500
    x, y = (W - pw) // 2, 420
    c.alpha_composite(nine("ui_panel_dark", pw, ph), (x, y))
    ribbon(c, d, W // 2, y - 50, 560, "TALENTS", "blue", 52)
    pill(c, d, W // 2 - 150, y + 100, 300, 84, "1 240", "coins")
    names = [("Bourse", "coins", 2), ("Vigueur", "heart", 1), ("Visée", "bow", 3), ("Chantiers", "hammer", 0), ("Murailles", "shield", 1), ("Butin", "chest", 0),
             ("Cadence", "hourglass", 2), ("Garde", "sword", 0), ("Couronne", "crown", 0)]
    cw, ch = 290, 330
    for i, (n, ic, lv) in enumerate(names):
        cx = x + 55 + (i % 3) * (cw + 20)
        cy = y + 230 + (i // 3) * (ch + 20)
        c.alpha_composite(nine("ui_card", cw, ch), (cx, cy))
        text(d, (cx + cw / 2, cy + 52), n.upper(), font("cinzel_bold", 26), GOLD_LIGHT, stroke=1)
        icon = img("ui_icon_" + ic).resize((110, 110), Image.LANCZOS)
        if lv == 0 and i >= 6:
            icon = Image.fromarray((__import__("numpy").array(icon) * [0.5, 0.5, 0.55, 1]).astype("uint8"))
        c.alpha_composite(icon, (cx + cw // 2 - 55, cy + 95))
        for k in range(3):
            st = img("ui_icon_star").resize((40, 40), Image.LANCZOS)
            if k >= lv:
                st = Image.fromarray((__import__("numpy").array(st) * [0.4, 0.4, 0.45, 1]).astype("uint8"))
            c.alpha_composite(st, (cx + cw // 2 - 66 + k * 46, cy + 215))
        if i >= 6 and lv == 0:
            lock = img("ui_icon_lock").resize((40, 40), Image.LANCZOS)
            c.alpha_composite(lock, (cx + 18, cy + 272))
            text(d, (cx + cw / 2 + 10, cy + 292), "Niveau héros 15", font("nunito_extrabold", 20), (120, 120, 130), shadow=False)
        else:
            c.alpha_composite(nine("ui_button_gold" if lv < 3 else "ui_button_grey", 200, 64), (cx + cw // 2 - 100, cy + 258))
            text(d, (cx + cw / 2, cy + 286), "MAX" if lv >= 3 else "● %d" % (120 * (lv + 1)), font("nunito_black", 24), INK if lv < 3 else WHITE, shadow=False)
    button(c, d, W // 2 - 220, y + ph - 150, 440, 110, "green", "FERMER", icon="close")
    return c


def victory():
    c = overlay()
    d = ImageDraw.Draw(c)
    pw, ph = 920, 1500
    x, y = (W - pw) // 2, 420
    c.alpha_composite(nine("ui_panel", pw, ph), (x, y))
    ribbon(c, d, W // 2, y - 50, 760, "NIVEAU 3 TERMINÉ", "blue", 46)
    for k in range(3):
        st = img("ui_icon_star").resize((150 if k == 1 else 120, 150 if k == 1 else 120), Image.LANCZOS)
        if k == 2:
            st = Image.fromarray((__import__("numpy").array(st) * [0.45, 0.45, 0.5, 1]).astype("uint8"))
        c.alpha_composite(st, (W // 2 - 60 + (k - 1) * 170 - (15 if k == 1 else 0), y + 110 + (0 if k == 1 else 20)))
    text(d, (W // 2, y + 300), "Donjon intact · Aucun mur perdu", font("nunito_black", 26), (40, 110, 60), shadow=False)
    chest = img("ui_icon_chest").resize((220, 220), Image.LANCZOS)
    c.alpha_composite(chest, (W // 2 - 110, y + 340))
    text(d, (W // 2, y + 590), "Touche le coffre !", font("cinzel_bold", 32), INK, shadow=False)
    # NEW unlock card
    c.alpha_composite(nine("ui_card", 760, 440), (W // 2 - 380, y + 650))
    text(d, (W // 2, y + 705), "NOUVEAU : ÉCURIE", font("cinzel_bold", 34), GOLD_LIGHT, stroke=1)
    stable = img("stable")
    stable = stable.resize((int(stable.width * 230 / stable.height), 230), Image.LANCZOS)
    c.alpha_composite(stable, (W // 2 - 360, y + 760))
    text(d, (W // 2 + 110, y + 830), "Roi 20 % plus rapide", font("nunito_black", 28), INK, shadow=False)
    text(d, (W // 2 + 110, y + 880), "Escouade +2 archers", font("nunito_black", 28), INK, shadow=False)
    text(d, (W // 2 + 110, y + 940), "Chantier débloqué au nord", font("nunito_extrabold", 22), (120, 80, 30), shadow=False)
    button(c, d, W // 2 - 300, y + 1160, 600, 150, "green", "NIVEAU SUIVANT", icon="arrow")
    text(d, (W // 2, y + 1380), "+320 pièces au trésor", font("nunito_black", 26), (150, 80, 10), shadow=False)
    return c


if __name__ == "__main__":
    os.makedirs(OUTDIR, exist_ok=True)
    screens = {"menu": main_menu, "pause": pause, "defaite": defeat, "talents": talents, "victoire": victory}
    outs = []
    for n, fn in screens.items():
        im = fn().convert("RGB")
        im.save(os.path.join(OUTDIR, "maquette-%s.png" % n), optimize=True)
        outs.append(im)
        print(n)
    k = 0.42
    sheet = Image.new("RGB", (int(W * k) * len(outs) + 20 * (len(outs) + 1), int(H * k) + 40), (30, 30, 40))
    for i, im in enumerate(outs):
        sheet.paste(im.resize((int(W * k), int(H * k)), Image.LANCZOS), (20 + i * (int(W * k) + 20), 20))
    sheet.save(os.path.join(OUTDIR, "maquettes-menu.png"), optimize=True)
