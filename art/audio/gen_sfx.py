"""Procedural fallback sounds and music for Kingshot (used where no CC0 file was found).

python gen_sfx.py <repo_root> [--only name,name]
Writes composeResources/files/sfx/<name>.wav (22050 Hz mono 16-bit) and files/music/<name>.m4a (via ffmpeg).
"""
import math
import os
import struct
import subprocess
import sys
import wave

import numpy as np

SR = 22050
ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
ONLY = None
for i, a in enumerate(sys.argv):
    if a == "--only":
        ONLY = set(sys.argv[i + 1].split(","))
SFX_DIR = os.path.join(ROOT, "composeApp/src/commonMain/composeResources/files/sfx")
MUSIC_DIR = os.path.join(ROOT, "composeApp/src/commonMain/composeResources/files/music")
os.makedirs(SFX_DIR, exist_ok=True)
os.makedirs(MUSIC_DIR, exist_ok=True)
rng = np.random.default_rng(7)


# ----------------------------------------------------------------------------- building blocks

def t(dur):
    return np.arange(int(SR * dur)) / SR


def env(n, a=0.005, d=0.1, s=0.0, r=0.1, hold=0.0):
    """ADSR over n samples (seconds for a/d/r, hold = sustain time)."""
    a_n, d_n, h_n, r_n = int(a * SR), int(d * SR), int(hold * SR), int(r * SR)
    e = np.zeros(n)
    i = 0
    seg = np.linspace(0, 1, max(1, a_n))[: n - i]
    e[i:i + len(seg)] = seg; i += len(seg)
    seg = np.linspace(1, s, max(1, d_n))[: max(0, n - i)]
    e[i:i + len(seg)] = seg; i += len(seg)
    seg = np.full(max(0, min(h_n, n - i)), s)
    e[i:i + len(seg)] = seg; i += len(seg)
    seg = np.linspace(s, 0, max(1, r_n))[: max(0, n - i)]
    e[i:i + len(seg)] = seg
    return e


def osc(freq, dur, kind="sine", detune=0.0):
    tt = t(dur)
    f = freq if np.isscalar(freq) else freq[: len(tt)]
    ph = 2 * np.pi * np.cumsum(np.broadcast_to(f, tt.shape) * (1 + detune)) / SR
    if kind == "sine":
        return np.sin(ph)
    if kind == "square":
        return np.sign(np.sin(ph)) * 0.6
    if kind == "saw":
        return ((ph / np.pi) % 2 - 1) * 0.6
    if kind == "tri":
        return (2 / np.pi) * np.arcsin(np.sin(ph))
    raise ValueError(kind)


def noise(dur):
    return rng.uniform(-1, 1, int(SR * dur))


def lowpass(x, cutoff):
    """One-pole low-pass (cutoff in Hz, scalar or per-sample array)."""
    y = np.zeros_like(x)
    c = cutoff if not np.isscalar(cutoff) else np.full(len(x), cutoff)
    alpha = 1 - np.exp(-2 * np.pi * np.clip(c, 20, SR / 2) / SR)
    acc = 0.0
    for i in range(len(x)):
        acc += alpha[i] * (x[i] - acc)
        y[i] = acc
    return y


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def reverb(x, mix=0.25, delays=(0.031, 0.047, 0.071), fb=0.45):
    out = x.copy()
    for d in delays:
        dn = int(d * SR)
        buf = np.zeros(len(x) + dn * 6)
        buf[: len(x)] = x
        for i in range(dn, len(buf)):
            buf[i] += buf[i - dn] * fb
        out = out + buf[: len(x)] * (mix / len(delays))
    return out


def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[: len(p)] += p
    return out


def at(x, offset, part):
    """Adds part into x at offset seconds (x grown if needed)."""
    o = int(offset * SR)
    n = max(len(x), o + len(part))
    out = np.zeros(n)
    out[: len(x)] = x
    out[o:o + len(part)] += part
    return out


def norm(x, peak=0.9):
    m = np.max(np.abs(x)) or 1.0
    return x / m * peak


def save_wav(name, x, folder=SFX_DIR):
    x = np.clip(x, -1, 1)
    path = os.path.join(folder, name + ".wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((x * 32767).astype("<i2").tobytes())
    print("wav", name, "%.2fs" % (len(x) / SR))
    return path


def want(name):
    return ONLY is None or name in ONLY


# ----------------------------------------------------------------------------- effects

def sfx_shoot():
    d = 0.18
    body = osc(np.linspace(900, 300, int(SR * d)), d, "tri") * env(int(SR * d), 0.002, 0.08, 0.2, 0.08)
    whoosh = highpass(noise(d), 2500) * env(int(SR * d), 0.001, 0.06, 0.0, 0.05) * 0.5
    return norm(body + whoosh, 0.7)


def sfx_hit():
    d = 0.16
    thud = osc(np.linspace(180, 60, int(SR * d)), d, "sine") * env(int(SR * d), 0.001, 0.12, 0.0, 0.04)
    clank = lowpass(noise(d), 3000) * env(int(SR * d), 0.001, 0.05, 0.0, 0.03) * 0.6
    return norm(thud + clank, 0.8)


def sfx_hit_flesh():
    d = 0.14
    body = lowpass(noise(d), np.linspace(1800, 300, int(SR * d))) * env(int(SR * d), 0.001, 0.1, 0.0, 0.03)
    sub = osc(np.linspace(120, 50, int(SR * d)), d) * env(int(SR * d), 0.001, 0.1, 0.0, 0.03) * 0.7
    return norm(body + sub, 0.75)


def sfx_coin():
    d = 0.35
    a = osc(1760, d) * env(int(SR * d), 0.001, 0.25, 0.0, 0.1)
    b = osc(2637, d) * env(int(SR * d), 0.001, 0.2, 0.0, 0.1) * 0.6
    b = at(np.zeros(1), 0.045, b)
    return norm(reverb(mix(a, b), 0.15), 0.6)


def sfx_coin_stack():
    d = 0.3
    a = osc(1318, d, "tri") * env(int(SR * d), 0.001, 0.18, 0.0, 0.1)
    b = at(np.zeros(1), 0.06, osc(1760, d, "tri") * env(int(SR * d), 0.001, 0.18, 0.0, 0.1) * 0.7)
    return norm(mix(a, b), 0.55)


def sfx_build():
    out = np.zeros(1)
    for i, tm in enumerate((0.0, 0.14, 0.27)):
        d = 0.12
        knock = lowpass(noise(d), 1200) * env(int(SR * d), 0.001, 0.08, 0.0, 0.03)
        tone = osc(220 + i * 40, d, "tri") * env(int(SR * d), 0.001, 0.06, 0.0, 0.03) * 0.5
        out = at(out, tm, knock + tone)
    return norm(reverb(out, 0.2), 0.8)


def sfx_gate():
    d = 0.7
    creak = osc(np.linspace(140, 260, int(SR * d)) + 12 * np.sin(2 * np.pi * 9 * t(d)), d, "saw")
    creak = lowpass(creak, 900) * env(int(SR * d), 0.03, 0.3, 0.5, 0.25)
    wood = lowpass(noise(d), 500) * env(int(SR * d), 0.01, 0.4, 0.3, 0.2) * 0.4
    return norm(creak + wood, 0.6)


def sfx_wave_horn():
    d = 1.4
    f = 196.0
    tone = sum(osc(f * k, d, "saw") * (0.6 / k) for k in (1, 2, 3)) * env(int(SR * d), 0.08, 0.3, 0.7, 0.4)
    tone += osc(f * 1.5, d, "saw") * env(int(SR * d), 0.5, 0.3, 0.6, 0.4) * 0.5
    return norm(reverb(lowpass(tone, 2200), 0.35), 0.8)


def sfx_boss_roar():
    d = 1.3
    f = 70 + 25 * np.sin(2 * np.pi * 5 * t(d)) + np.linspace(0, -20, int(SR * d))
    growl = osc(f, d, "saw") + osc(f * 1.01, d, "saw")
    growl = lowpass(growl * (1 + 0.5 * noise(d)), 700) * env(int(SR * d), 0.05, 0.5, 0.7, 0.5)
    breath = lowpass(noise(d), 1500) * env(int(SR * d), 0.05, 0.6, 0.5, 0.4) * 0.5
    return norm(reverb(growl + breath, 0.4), 0.9)


def sfx_ability():
    d = 0.9
    sweep = lowpass(noise(d), np.linspace(400, 6000, int(SR * d))) * env(int(SR * d), 0.05, 0.3, 0.6, 0.4)
    shine = osc(np.linspace(600, 1800, int(SR * d)), d, "tri") * env(int(SR * d), 0.1, 0.3, 0.4, 0.4) * 0.5
    return norm(reverb(sweep + shine, 0.3), 0.75)


def fanfare(notes, step, kind="square", release=0.35):
    out = np.zeros(1)
    for i, f in enumerate(notes):
        d = step * 1.2 + release
        n = (osc(f, d, kind) + osc(f * 2, d, "tri") * 0.4) * env(int(SR * d), 0.01, 0.1, 0.7, release, hold=step)
        out = at(out, i * step, n)
    return out


def sfx_victory():
    notes = [523.25, 659.25, 783.99, 1046.5]
    out = fanfare(notes, 0.14)
    out = at(out, 0.14 * 4 + 0.05, (osc(1046.5, 0.8, "square") + osc(1318.5, 0.8, "tri") * 0.6) * env(int(SR * 0.8), 0.01, 0.2, 0.7, 0.4, hold=0.3))
    return norm(reverb(lowpass(out, 5000), 0.3), 0.8)


def sfx_defeat():
    notes = [392.0, 349.23, 311.13, 261.63]
    out = fanfare(notes, 0.3, "saw", 0.5)
    return norm(reverb(lowpass(out, 1800), 0.4), 0.75)


def sfx_click():
    d = 0.08
    return norm(osc(1200, d, "tri") * env(int(SR * d), 0.001, 0.05, 0.0, 0.02) + lowpass(noise(d), 4000) * env(int(SR * d), 0.001, 0.02, 0.0, 0.01) * 0.4, 0.5)


def sfx_level_up():
    notes = [523.25, 659.25, 783.99, 1046.5, 1318.5, 1568.0]
    out = fanfare(notes, 0.09, "tri", 0.5)
    shimmer = highpass(noise(1.2), 5000) * env(int(SR * 1.2), 0.3, 0.5, 0.2, 0.4) * 0.15
    return norm(reverb(mix(out, shimmer), 0.35), 0.8)


def sfx_king_hurt():
    d = 0.25
    hit = lowpass(noise(d), np.linspace(2500, 400, int(SR * d))) * env(int(SR * d), 0.001, 0.15, 0.0, 0.08)
    voice = osc(np.linspace(330, 200, int(SR * d)), d, "saw") * env(int(SR * d), 0.01, 0.15, 0.0, 0.08) * 0.5
    return norm(lowpass(hit + voice, 3000), 0.8)


def sfx_wall_break():
    d = 0.8
    crack = highpass(noise(d), 1500) * env(int(SR * d), 0.001, 0.1, 0.0, 0.05)
    rumble = lowpass(noise(d), np.linspace(600, 120, int(SR * d))) * env(int(SR * d), 0.01, 0.5, 0.4, 0.3)
    debris = np.zeros(1)
    for tm in (0.15, 0.27, 0.36, 0.5):
        debris = at(debris, tm, lowpass(noise(0.08), 1500) * env(int(SR * 0.08), 0.001, 0.05, 0.0, 0.03) * 0.5)
    return norm(reverb(mix(crack, rumble, debris), 0.3), 0.9)


def sfx_recruit():
    out = fanfare([659.25, 880.0], 0.1, "tri", 0.3)
    return norm(reverb(out, 0.2), 0.6)


def sfx_forge():
    out = np.zeros(1)
    for tm in (0.0, 0.22):
        d = 0.35
        ring = (osc(2200, d) + osc(3300, d) * 0.5 + osc(4700, d) * 0.3) * env(int(SR * d), 0.001, 0.3, 0.0, 0.1)
        hit = lowpass(noise(0.08), 3000) * env(int(SR * 0.08), 0.001, 0.05, 0.0, 0.02)
        out = at(out, tm, mix(ring, hit))
    return norm(reverb(out, 0.25), 0.7)


def sfx_repair():
    d = 0.6
    saw = lowpass(noise(d) * (0.6 + 0.4 * np.sign(np.sin(2 * np.pi * 14 * t(d)))), 1500) * env(int(SR * d), 0.02, 0.3, 0.6, 0.2)
    chime = at(np.zeros(1), 0.4, osc(1046.5, 0.3, "tri") * env(int(SR * 0.3), 0.005, 0.2, 0.0, 0.1) * 0.6)
    return norm(mix(saw * 0.6, chime), 0.6)


EFFECTS = {
    "shoot": sfx_shoot, "hit": sfx_hit, "hit_flesh": sfx_hit_flesh, "coin": sfx_coin, "coin_stack": sfx_coin_stack,
    "build": sfx_build, "gate": sfx_gate, "wave_horn": sfx_wave_horn, "boss_roar": sfx_boss_roar, "ability": sfx_ability,
    "victory": sfx_victory, "defeat": sfx_defeat, "click": sfx_click, "level_up": sfx_level_up, "king_hurt": sfx_king_hurt,
    "wall_break": sfx_wall_break, "recruit": sfx_recruit, "forge": sfx_forge, "repair": sfx_repair,
}


# ----------------------------------------------------------------------------- music

def midi(n):
    return 440.0 * 2 ** ((n - 69) / 12)


def note(freq, dur, kind="square", vol=0.3, a=0.01, r=0.08, cutoff=None, sustain=0.8):
    n = int(SR * dur)
    x = osc(freq, dur, kind) * env(n, a, 0.05, sustain, r, hold=max(0.0, dur - a - 0.05 - r))
    if cutoff:
        x = lowpass(x, cutoff)
    return x * vol


def pad(freqs, dur, vol=0.12, cutoff=900):
    x = np.zeros(int(SR * dur))
    for f in freqs:
        x += osc(f, dur, "saw", 0.004) + osc(f, dur, "saw", -0.004)
    return lowpass(x, cutoff) * env(len(x), 0.4, 0.3, 0.8, 0.6, hold=max(0.0, dur - 1.3)) * vol


def kick(vol=0.8):
    d = 0.25
    return osc(np.linspace(150, 40, int(SR * d)), d) * env(int(SR * d), 0.001, 0.2, 0.0, 0.05) * vol


def snare(vol=0.5):
    d = 0.18
    return (highpass(noise(d), 1500) * 0.8 + osc(190, d) * 0.4) * env(int(SR * d), 0.001, 0.12, 0.0, 0.05) * vol


def hat(vol=0.18):
    d = 0.05
    return highpass(noise(d), 7000) * env(int(SR * d), 0.001, 0.03, 0.0, 0.02) * vol


def timpani(freq, vol=0.8):
    d = 1.2
    return (osc(freq, d) + osc(freq * 1.5, d) * 0.3) * env(int(SR * d), 0.002, 0.8, 0.0, 0.3) * vol


def music_menu():
    """Calm, 100 bpm: slow pad on Am F C G, plucked arpeggio, soft bass. 4 bars x 2 = 19.2 s loop."""
    bpm, beats = 100, 4
    bar = 60 / bpm * beats
    chords = [(57, 60, 64), (53, 57, 60), (48, 52, 55), (55, 59, 62)]  # Am F C G
    total = bar * 8
    x = np.zeros(int(SR * total))
    for rep in range(2):
        for ci, ch in enumerate(chords):
            start = (rep * 4 + ci) * bar
            x = at(x, start, pad([midi(n) for n in ch], bar * 1.05))
            x = at(x, start, note(midi(ch[0] - 24), bar, "tri", 0.22, 0.02, 0.3, 500))
            arp = [ch[0] + 12, ch[1] + 12, ch[2] + 12, ch[1] + 24] if rep == 0 else [ch[2] + 12, ch[1] + 12, ch[0] + 12, ch[2] + 24]
            for k in range(8):
                x = at(x, start + k * bar / 8, note(midi(arp[k % 4]), bar / 8 * 1.5, "tri", 0.16, 0.003, 0.2, 3000, 0.4))
    return norm(reverb(x[: int(SR * total)], 0.35, (0.053, 0.079, 0.113), 0.5), 0.8)


def music_battle():
    """Driving, 140 bpm: Dm Bb F C, bass eighths, drums, square lead. 8 bars = 13.7 s loop."""
    bpm, beats = 140, 4
    bar = 60 / bpm * beats
    b8 = bar / 8
    chords = [(62, 65, 69), (58, 62, 65), (53, 57, 60), (60, 64, 67)]  # Dm Bb F C
    total = bar * 8
    x = np.zeros(int(SR * total))
    melody = [74, 77, 81, 77, 74, 72, 69, 72, 70, 74, 77, 74, 70, 69, 65, 69,
              65, 69, 72, 69, 65, 64, 60, 64, 67, 72, 76, 72, 67, 64, 60, 64]
    melody2 = [81, 79, 77, 74, 72, 74, 77, 79, 77, 74, 70, 74, 77, 74, 70, 65,
               69, 72, 69, 65, 60, 64, 65, 69, 72, 76, 79, 76, 72, 67, 64, 60]
    for rep in range(2):
        for ci, ch in enumerate(chords):
            start = (rep * 4 + ci) * bar
            x = at(x, start, pad([midi(n) for n in ch], bar * 1.02, 0.08, 1400))
            for k in range(8):
                root = ch[0] - 24 if k % 2 == 0 else ch[0] - 12
                x = at(x, start + k * b8, note(midi(root), b8 * 0.9, "saw", 0.25, 0.003, 0.05, 700))
                x = at(x, start + k * b8, hat(0.14 if k % 2 else 0.2))
            for k in (0, 4):
                x = at(x, start + k * b8, kick(0.9))
            for k in (2, 6):
                x = at(x, start + k * b8, snare(0.5))
            x = at(x, start + 7 * b8, kick(0.5))
            mel = melody if rep == 0 else melody2
            for k in range(8):
                x = at(x, start + k * b8, note(midi(mel[ci * 8 + k]), b8 * 1.1, "square", 0.17, 0.004, 0.06, 3500, 0.7))
    return norm(reverb(x[: int(SR * total)], 0.2, (0.029, 0.041), 0.35), 0.85)


def music_boss():
    """Ominous, 120 bpm in E minor: low drone, timpani, tritone motif, choir-like pad. 8 bars = 16 s loop."""
    bpm, beats = 120, 4
    bar = 60 / bpm * beats
    b4 = bar / 4
    total = bar * 8
    x = np.zeros(int(SR * total))
    drone = (osc(midi(40), total, "saw", 0.003) + osc(midi(40), total, "saw", -0.003) + osc(midi(28), total, "sine") * 1.5)
    x += lowpass(drone, 300) * 0.18 * env(len(x), 1.0, 0.5, 1.0, 1.0, hold=total - 2.5)
    motif = [52, 58, 55, 52, 51, 58, 55, 50]
    for bi in range(8):
        start = bi * bar
        x = at(x, start, timpani(midi(40), 0.9))
        x = at(x, start + 2 * b4, timpani(midi(40), 0.6))
        x = at(x, start + 3 * b4 + b4 / 2, timpani(midi(43), 0.5))
        for k in (1, 3):
            x = at(x, start + k * b4, snare(0.35))
        if bi >= 2:
            n = motif[bi % 8]
            x = at(x, start, note(midi(n), bar * 0.95, "saw", 0.2, 0.05, 0.3, 1200, 0.9))
            x = at(x, start, note(midi(n + 7), bar * 0.95, "saw", 0.1, 0.05, 0.3, 1200, 0.9))
        if bi % 2 == 0:
            x = at(x, start, pad([midi(n) for n in (64, 67, 70, 74)], bar * 2.05, 0.07, 1000))
    return norm(reverb(x[: int(SR * total)], 0.45, (0.061, 0.089, 0.127), 0.55), 0.85)


MUSIC = {"menu": music_menu, "battle": music_battle, "boss": music_boss}


def save_music(name, x):
    tmp = save_wav(name, x, MUSIC_DIR)
    out = os.path.join(MUSIC_DIR, name + ".m4a")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp, "-c:a", "aac", "-b:a", "96k", out], check=True)
    os.remove(tmp)
    print("m4a", name, os.path.getsize(out) // 1024, "KB")


for name, fn in EFFECTS.items():
    if want(name):
        save_wav(name, fn())
for name, fn in MUSIC.items():
    if want(name):
        save_music(name, fn())
