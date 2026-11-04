"""Realm (open world) exploration music built from real CC0 instrument samples (VCSL), no oscillators.

    python3 make_music_realm.py <repo_root> <sources_dir> [--preview out.mp3]

Reuses the sampler / mixing helpers of make_sfx_natural.py (same <sources_dir> layout).
92 bpm, D dorian, 16 bars (41.7 s) looped seamlessly: folk harp arpeggios, bass recorder drone, alto recorder
phrases on bars 5-8 and 13-16 doubled by a soft glockenspiel, frame drum on 2 and 4, wind chimes at the turns.
Writes composeResources/files/music/realm.m4a (AAC 96 kb/s).
"""
import importlib.util
import os
import subprocess
import sys
import wave

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("sfx", os.path.join(HERE, "make_sfx_natural.py"))
sfx = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sfx)

SR = sfx.SR
ROOT = sfx.ROOT
MUSIC_DIR = os.path.join(ROOT, "composeApp/src/commonMain/composeResources/files/music")
PREVIEW = sfx.PREVIEW

BPM = 92
BAR = 60 / BPM * 4
BEAT = BAR / 4
BARS = 16
TOTAL = BAR * BARS

NOTE_NAMES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]


def name(m):
    return "%s%d" % (NOTE_NAMES[m % 12], m // 12 - 1)


# Dm  C  G  Am  (midi), root position around C3
CHORDS = [(50, 53, 57), (48, 52, 55), (43, 47, 50), (45, 48, 52)]
BASS = [50, 48, 43, 45]  # D3 C3 G2 A2 for the bass recorder

# (beat in bar, midi, beats) — alto recorder phrases
PHRASE_A = [
    [(0, 74, 1), (1, 72, 1), (2, 69, 2)],
    [(0, 72, 1), (1, 71, 1), (2, 67, 2)],
    [(0, 69, 1), (1, 71, 1), (2, 74, 2)],
    [(0, 72, 1.5), (1.5, 71, 0.5), (2, 69, 2)],
]
PHRASE_B = [
    [(0, 77, 1), (1, 76, 1), (2, 74, 2)],
    [(0, 72, 1), (1, 74, 1), (2, 76, 2)],
    [(0, 74, 1), (1, 71, 1), (2, 69, 2)],
    [(0, 72, 0.5), (0.5, 71, 0.5), (1, 69, 1), (2, 74, 2)],
]


def even(x, peak=0.6):
    """Samples of one instrument differ in level from note to note: give every note the same peak."""
    return x * (peak / (np.max(np.abs(x)) + 1e-9))


def harp_note(m, dur):
    return even(sfx.fharp(name(m), dur))


def recorder(m, beats):
    x = even(sfx.arec(name(m), "Sustain", None))
    return sfx.cut(x, 0, beats * BEAT * 0.98, 0.06, 0.14)


def bass(m, dur):
    x = even(sfx.brec(name(m), "SusVib", None), 0.5)
    return sfx.cut(x, 0, dur, 0.3, 0.35)


def drum(soft=True):
    pat = "HDrumL_Hand_rr1_Sum.wav" if soft else "HDrumL_HitMuted_v2_rr1_Sum.wav"
    return sfx.one(sfx.FRAME, pat, 0, 0.6, 0.2)


def chimes(kind="slowAsc1"):
    return sfx.one(sfx.WIND, "windchimes_%s.wav" % kind, 0, 2.5, 0.8)


def render_pass(out, t0):
    """Adds the 16-bar cycle into out starting at t0 seconds."""
    for bar in range(BARS):
        ch = CHORDS[bar % 4]
        start = t0 + bar * BAR
        hi = [c + 12 for c in ch]
        # harp: root 5th oct 10th 12th 10th oct 5th, softer on the second half of the cycle's bars
        pattern = [hi[0], hi[2], hi[0] + 12, hi[1] + 12, hi[2] + 12, hi[1] + 12, hi[0] + 12, hi[2]]
        vel = 0.5 if bar % 8 < 4 else 0.42
        for k, m in enumerate(pattern):
            if bar >= 4 and bar % 8 >= 4 and k % 2 == 1:
                continue  # thinner under the recorder phrases
            out = sfx.at(out, start + k * BEAT / 2, harp_note(m, 1.4) * vel * (1.0 if k == 0 else 0.85))
        # bass recorder drone, one note per bar
        out = sfx.at(out, start, bass(BASS[bar % 4], BAR * 1.02) * 0.22)
        # frame drum on 2 and 4, muted hit on 1 every other bar
        out = sfx.at(out, start + BEAT, drum(True) * 0.3)
        out = sfx.at(out, start + 3 * BEAT, drum(True) * 0.26)
        if bar % 2 == 1:
            out = sfx.at(out, start, drum(False) * 0.22)
        # melody
        phrase = PHRASE_A if 4 <= bar < 8 else PHRASE_B if 12 <= bar < 16 else None
        if phrase:
            for beat, m, beats in phrase[bar % 4]:
                out = sfx.at(out, start + beat * BEAT, recorder(m, beats) * 0.4)
                if beats >= 2:
                    out = sfx.at(out, start + beat * BEAT, sfx.glock(name(m + 12), "soft", 1.6) * 0.3)
        # chimes at the turns
        if bar in (3, 11):
            out = sfx.at(out, start + 3 * BEAT, chimes("slowAsc1") * 0.14)
        if bar == 7:
            out = sfx.at(out, start + 3.5 * BEAT, chimes("asc1") * 0.12)
    return out


def main():
    out = np.zeros(1)
    out = render_pass(out, 0.0)
    out = render_pass(out, TOTAL)
    out = sfx.reverb(out, 0.3, (0.047, 0.071, 0.109), 0.45, 3500)
    a, b = sfx.n(TOTAL), sfx.n(TOTAL * 2)
    loop = out[a:b]
    loop = loop - loop.mean()
    rms = np.sqrt(np.mean(loop * loop) + 1e-12)
    loop = loop * (10 ** (-20 / 20) / rms)
    pk = np.max(np.abs(loop))
    if pk > 0.9:
        loop = loop * (0.9 / pk)
    tmp = os.path.join(MUSIC_DIR, "realm_tmp.wav")
    with wave.open(tmp, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((np.clip(loop, -1, 1) * 32767).astype("<i2").tobytes())
    dst = os.path.join(MUSIC_DIR, "realm.m4a")
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", tmp, "-c:a", "aac", "-b:a", "96k", dst], check=True)
    print("m4a realm %.1fs %d KB  seam first=%+.3f last=%+.3f" % (len(loop) / SR, os.path.getsize(dst) // 1024,
                                                                   loop[0], loop[-1]))
    if PREVIEW:
        subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", tmp, "-codec:a", "libmp3lame", "-b:a", "128k", PREVIEW],
                       check=True)
        print("preview", PREVIEW)
    os.remove(tmp)


if __name__ == "__main__":
    main()
