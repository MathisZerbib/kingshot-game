"""Store screenshots for Hexcrown: frames raw Pixel captures (1080x2400) into 1080x1920 Play Store shots with a
caption band in the game's typography.   python3 art/store/make_shots.py <repo_root>
Reads docs/captures/store/raw_<n>.png, captions from CAPTIONS, writes docs/store/shot_<n>_<lang>.png."""
import os, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
RAW = os.path.join(ROOT, "docs/captures/store")
OUT = os.path.join(ROOT, "docs/store")
FONT = os.path.join(ROOT, "composeApp/src/commonMain/composeResources/font/lilita_one_regular.ttf")
W, H = 1080, 1920
NAVY, GOLD = (12, 19, 38), (255, 197, 49)
CAPTIONS = {
    "fr": ["Étends tes terres", "Tiens la ligne", "Lâche le Tourbillon", "Choisis ta relique", "Bats le boss", "Habille ton roi"],
    "en": ["Grow your lands", "Hold the line", "Unleash the Whirlwind", "Pick your relic", "Beat the boss", "Dress your king"],
}


def frame(raw, caption):
    im = Image.new("RGBA", (W, H), NAVY + (255,))
    # soft blurred copy of the capture as background
    bg = raw.resize((W, int(W * raw.height / raw.width))).filter(ImageFilter.GaussianBlur(40))
    im.paste(bg.crop((0, 0, W, H)), (0, 0))
    im = Image.alpha_composite(im, Image.new("RGBA", (W, H), (12, 19, 38, 140)))
    # phone-shaped capture, 82 % wide, rounded corners, drop shadow
    y = 250
    ph = H - y - 40; pw = int(ph * raw.width / raw.height)  # fit by height so the whole screen shows
    shot = raw.resize((pw, ph), Image.LANCZOS)
    mask = Image.new("L", (pw, ph), 0); ImageDraw.Draw(mask).rounded_rectangle([0, 0, pw - 1, ph - 1], 60, fill=255)
    x = (W - pw) // 2
    sh = Image.new("RGBA", (W, H), (0, 0, 0, 0)); ImageDraw.Draw(sh).rounded_rectangle([x, y + 30, x + pw, y + ph + 30], 60, fill=(0, 0, 0, 160))
    im = Image.alpha_composite(im, sh.filter(ImageFilter.GaussianBlur(30)))
    im.paste(shot, (x, y), mask)
    d = ImageDraw.Draw(im)
    font = ImageFont.truetype(FONT, 88)
    while d.textlength(caption, font=font) > W - 120:
        font = ImageFont.truetype(FONT, font.size - 4)
    d.text((W / 2 + 4, 130 + 6), caption, font=font, fill=(0, 0, 0, 150), anchor="mm")
    d.text((W / 2, 130), caption, font=font, fill=GOLD, anchor="mm")
    return im.convert("RGB")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for i in range(1, 7):
        p = os.path.join(RAW, "raw_%d.png" % i)
        if not os.path.exists(p):
            print("missing", p); continue
        raw = Image.open(p).convert("RGBA")
        for lang in CAPTIONS:
            frame(raw, CAPTIONS[lang][i - 1]).save(os.path.join(OUT, "shot_%d_%s.png" % (i, lang)), optimize=True)
        print("shot", i)
