"""Natural sound effects for Hexcrown, built from real CC0 recordings (no oscillators).

    python3 make_sfx_natural.py <repo_root> <sources_dir> [--only a,b] [--preview out.mp3]

<sources_dir> holds the CC0 libraries (see art/audio/CREDITS.md):
    lav/   https://github.com/lavenderdotpet/CC0-Public-Domain-Sounds  (Kenney packs, 80-CC0-RPG-SFX, creature,
           wood/metal, Organic Wooshes, water, loops, Record Fuzzies ... all CC0)
    vcsl/  https://github.com/sgossner/VCSL  (Versilian Community Sample Library, CC0: glockenspiel, tubular bells,
           hand chimes, harps, timpani, bass drum, cymbals, anvil, bell tree, frame drum, recorders, didgeridoo)
Every sound is decoded with ffmpeg to 22050 Hz mono, trimmed, layered, lightly filtered and loudness-matched, then
written to composeResources/files/sfx/<name>.wav (same names as before: no code change needed).
"""
import glob
import os
import re
import subprocess
import sys
import wave

import numpy as np

SR = 22050
ROOT = sys.argv[1]
SRC = sys.argv[2]
ONLY = None
PREVIEW = None
for i, a in enumerate(sys.argv):
    if a == "--only":
        ONLY = set(sys.argv[i + 1].split(","))
    if a == "--preview":
        PREVIEW = sys.argv[i + 1]
OUT = os.path.join(ROOT, "composeApp/src/commonMain/composeResources/files/sfx")
os.makedirs(OUT, exist_ok=True)

LAV = os.path.join(SRC, "lav")
VC = os.path.join(SRC, "vcsl")
K_RPG = os.path.join(LAV, "kenney_rpgaudio/Audio")
K_IMP = os.path.join(LAV, "kenney_impactsounds/Audio")
K_UI = os.path.join(LAV, "kenney_interfacesounds/Audio")
K_UI2 = os.path.join(LAV, "kenney_uiaudio/Audio")
RPG = os.path.join(LAV, "80-CC0-RPG-SFX")
CRE = os.path.join(LAV, "80-CC0-creature-SFX")
CRE2 = os.path.join(LAV, "80-CC0-creature-sfx-2")
WM = os.path.join(LAV, "100-CC0-wood-metal-SFX")
WOO = os.path.join(LAV, "Micro Pack - Organic Wooshes")
WAT = os.path.join(LAV, "40-cc0-water-splash-slime-sfx")
LOOPS = os.path.join(LAV, "30-cc0-sfx-loops")
BFH = os.path.join(LAV, "75-cc0-breaking-falling-hit-sfx")
FUZZ = os.path.join(LAV, "Micro Pack - Record Fuzzies")
GLOCK = os.path.join(VC, "Idiophones/Struck Idiophones/Glockenspiel")
TUB = os.path.join(VC, "Idiophones/Struck Idiophones/Tubular Bells 1")
CHIME = os.path.join(VC, "Idiophones/Struck Idiophones/Hand Chimes")
HARP = os.path.join(VC, "Chordophones/Composite Chordophones/Concert Harp")
FHARP = os.path.join(VC, "Chordophones/Composite Chordophones/Folk Harp")
TIMP = os.path.join(VC, "Membranophones/Struck Membranophones/Timpani 1")
BDRUM = os.path.join(VC, "Membranophones/Struck Membranophones/Bass Drum 1")
CRASH = os.path.join(VC, "Idiophones/Struck Idiophones/Clash Cymbals 1")
SUS = os.path.join(VC, "Idiophones/Struck Idiophones/Suspended Cymbal 1")
ANVIL = os.path.join(VC, "Idiophones/Struck Idiophones/Anvil")
FING = os.path.join(VC, "Idiophones/Struck Idiophones/Finger Cymbals")
BTREE = os.path.join(VC, "Idiophones/Struck Idiophones/Bell Tree")
FRAME = os.path.join(VC, "Membranophones/Struck Membranophones/Frame Drum")
WIND = os.path.join(VC, "Idiophones/Struck Idiophones/Mark Trees/Legacy")
DIDG = os.path.join(VC, "Aerophones/Lip Aerophones/Didgeridoo")
BREC = os.path.join(VC, "Aerophones/Edge-blown Aerophones/Baroque Bass Recorder")
AREC = os.path.join(VC, "Aerophones/Edge-blown Aerophones/Baroque Alto Recorder")
SNARE = os.path.join(VC, "Membranophones/Struck Membranophones/Snare Drum, Rope Tension")

_cache = {}


USED = {}  # sound name -> set of source paths (written to art/audio/sfx_sources.md for the credits)
CURRENT = [None]


def load(path):
    """Decodes any audio file to a float32 mono array at SR (cached)."""
    if not os.path.exists(path):
        m = glob.glob(path)
        if not m:
            raise FileNotFoundError(path)
        path = sorted(m)[0]
    if CURRENT[0]:
        USED.setdefault(CURRENT[0], set()).add(os.path.relpath(path, SRC))
    if path in _cache:
        return _cache[path].copy()
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                         check=True, capture_output=True).stdout
    x = np.frombuffer(raw, np.float32).astype(np.float64)
    _cache[path] = x
    return x.copy()


def f(folder, name):
    return os.path.join(folder, name)


# ----------------------------------------------------------------------------- dsp helpers

def n(sec):
    return int(round(sec * SR))


def cut(x, start=0.0, dur=None, fade_in=0.004, fade_out=0.03):
    """Slice [start, start+dur] seconds with short fades."""
    a = n(start)
    b = len(x) if dur is None else min(len(x), a + n(dur))
    y = x[a:b].copy()
    fi, fo = min(n(fade_in), len(y)), min(n(fade_out), len(y))
    if fi > 0:
        y[:fi] *= np.linspace(0, 1, fi)
    if fo > 0:
        y[-fo:] *= np.linspace(1, 0, fo)
    return y


def trim_start(x, thresh=0.02):
    """Drops leading silence (below thresh of the peak)."""
    peak = np.max(np.abs(x)) + 1e-9
    idx = np.argmax(np.abs(x) > peak * thresh)
    return x[max(0, idx - n(0.003)):]


def gain(x, db):
    return x * (10 ** (db / 20))


def lp(x, cutoff):
    """One-pole low-pass, applied twice (12 dB/oct)."""
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = x
    for _ in range(2):
        out = np.empty_like(y)
        s = 0.0
        for i in range(len(y)):
            s = a * s + (1 - a) * y[i]
            out[i] = s
        y = out
    return y


def lp_fast(x, cutoff):
    """Vectorised one-pole low-pass via lfilter-free recursion (same as lp but using numpy cumulative trick)."""
    a = float(np.exp(-2 * np.pi * cutoff / SR))
    y = np.asarray(x, dtype=np.float64)
    for _ in range(2):
        # IIR y[i] = a*y[i-1] + (1-a)*x[i]  computed with an exact closed form in blocks
        out = np.empty_like(y)
        s = 0.0
        # block length chosen so a**blk stays above 1e-30 (no underflow of pw for high cutoffs)
        blk = int(max(8, min(4096, -69.0 / np.log(a)))) if a < 1 else 4096
        for b0 in range(0, len(y), blk):
            seg = y[b0:b0 + blk]
            k = np.arange(1, len(seg) + 1)
            pw = a ** k
            conv = (1 - a) * np.cumsum(seg / pw) * pw
            out[b0:b0 + blk] = conv + s * pw
            s = out[b0 + len(seg) - 1]
        y = out
    return y


def hp(x, cutoff):
    return x - lp_fast(x, cutoff)


def env(x, attack=0.0, hold=None, release=0.1, start=0.0):
    """Shapes a sustained sample: fade in over attack, keep until start+attack+hold, then release to silence."""
    y = x.copy()
    a = n(attack)
    if a > 0:
        y[:a] *= np.linspace(0, 1, min(a, len(y)))
    if hold is not None:
        end = n(start + attack + hold)
        r = n(release)
        if end < len(y):
            tail = y[end:end + r]
            tail *= np.linspace(1, 0, len(tail))
            y = y[:end + r]
    return y


def pitch(x, semitones):
    """Resamples (changes pitch and length together), like a sampler."""
    k = 2 ** (semitones / 12)
    m = int(len(x) / k)
    return np.interp(np.arange(m) * k, np.arange(len(x)), x)


def reverb(x, mix=0.2, delays=(0.029, 0.041, 0.057), fb=0.4, damp=4000):
    out = x.copy()
    wet = np.zeros(len(x) + n(0.6))
    for d in delays:
        buf = np.zeros(len(wet))
        dn = n(d)
        src = np.concatenate([x, np.zeros(len(wet) - len(x))])
        acc = src.copy()
        g = 1.0
        for k in range(1, 6):
            g *= fb
            if dn * k >= len(wet):
                break
            acc[dn * k:] += src[: len(wet) - dn * k] * g
        buf += acc
        wet += buf / len(delays)
    wet = lp_fast(wet, damp)
    out = np.concatenate([out, np.zeros(len(wet) - len(out))])
    return out * (1 - mix * 0.5) + wet * mix


def at(out, t, part):
    """Adds part into out at t seconds, growing out if needed."""
    a = n(t)
    need = a + len(part)
    if need > len(out):
        out = np.concatenate([out, np.zeros(need - len(out))])
    out[a:need] += part
    return out


def mix(*parts):
    L = max(len(p) for p in parts)
    out = np.zeros(L)
    for p in parts:
        out[: len(p)] += p
    return out


def level(x, target_db=-14.0, peak=0.95):
    """Normalises the loudest 50 ms RMS window to target_db, then peak-limits."""
    w = n(0.05)
    if len(x) < w:
        x = np.concatenate([x, np.zeros(w - len(x))])
    sq = np.convolve(x * x, np.ones(w) / w, mode="valid")
    rms = np.sqrt(sq.max() + 1e-12)
    y = x * (10 ** (target_db / 20) / rms)
    pk = np.max(np.abs(y))
    if pk > peak:
        y = y * (peak / pk)
    return y


NOTE_RE = re.compile(r"^([A-G])(#?)(-?\d)$")
NAMES = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def midi(note):
    m = NOTE_RE.match(note)
    return 12 * (int(m.group(3)) + 1) + NAMES[m.group(1)] + (1 if m.group(2) else 0)


def sampler(folder, pattern, note, dur=None, fade_out=0.25, max_shift=4):
    """Loads the closest available sample of `note` from folder (pattern has {note}) and resamples to the note."""
    files = glob.glob(os.path.join(folder, "**", "*.wav"), recursive=True)
    best, best_d = None, 99
    for p in files:
        m = re.search(pattern.replace("{note}", r"([A-G]#?-?\d)"), os.path.basename(p))
        if not m:
            continue
        d = midi(note) - midi(m.group(1))
        if abs(d) < abs(best_d):
            best, best_d = p, d
    if best is None:
        raise FileNotFoundError(folder + " " + pattern + " " + note)
    if abs(best_d) > max_shift:
        print("  warn: %s shifted %+d semitones (%s)" % (note, best_d, os.path.basename(best)))
    x = trim_start(load(best))
    x = pitch(x, best_d) if best_d else x
    if dur is not None:
        x = cut(x, 0, dur, 0.002, fade_out)
    return x


def glock(note, dyn="medium", dur=1.2):
    return sampler(GLOCK, "glock_" + dyn + "_{note}_", note, dur, 0.4)


def chime(note, dur=1.6):
    return sampler(CHIME, "sus_{note}_", note, dur, 0.5)


def tub(note, dyn="ff", dur=2.0):
    return sampler(TUB, "chimes_{note}_" + dyn, note, dur, 0.6)


def harp(note, dur=0.9):
    return sampler(HARP, "KSHarp_{note}_(f|mf)", note, dur, 0.3, max_shift=3)


def fharp(note, dur=0.9):
    return sampler(FHARP, "EWHarp_Normal_{note}_v3", note, dur, 0.3)


def brec(note, kind="SusVib", dur=1.2):
    return sampler(BREC, "BassRecorder_" + kind + "_{note}_", note, dur, 0.3)


def arec(note, kind="Sustain", dur=1.2):
    return sampler(AREC, "AltRecorder_" + ("Sus" if kind == "Sustain" else kind) + "_{note}_", note, dur, 0.3)


def one(folder, name, start=0.0, dur=None, fade_out=0.03):
    return cut(trim_start(load(f(folder, name))), start, dur, 0.002, fade_out)


def arpeggio(fn, notes, step, dur=0.8, vel=1.0):
    out = np.zeros(1)
    for i, nt in enumerate(notes):
        out = at(out, i * step, fn(nt, dur) * vel)
    return out


# ----------------------------------------------------------------------------- the sounds
# (name, target loudness dB, builder)

def s_shoot():
    swish = hp(one(WOO, "Gentle Swish.wav", 0.0, 0.26, 0.06), 700)
    twang = cut(fharp("D2", 0.14), 0, 0.14, 0.001, 0.08)
    return lp_fast(mix(swish * 0.8, gain(twang, -8)), 9000)


def s_hit():
    return lp_fast(mix(one(RPG, "blade_01.ogg", 0, 0.24), gain(one(K_IMP, "impactSoft_medium_000.ogg", 0, 0.2), -6)), 8000)


def s_hit_flesh():
    return mix(one(K_IMP, "impactPunch_medium_000.ogg", 0, 0.3), gain(one(WOO, "Thunk 1.wav", 0, 0.25), -8))


def s_coin():
    return hp(one(RPG, "item_coins_03.ogg", 0, 0.3, 0.1), 500)


def s_coin_stack():
    return one(K_RPG, "handleCoins2.ogg", 0, 0.8, 0.15)


def s_build():
    out = one(WM, "hammer_01.ogg", 0, 0.25)
    out = at(out, 0.16, gain(one(WM, "wood_hit_01.ogg", 0, 0.25), -3))
    out = at(out, 0.32, one(WM, "hammer_03.ogg", 0, 0.25))
    return reverb(out, 0.12)


def s_gate():
    return mix(one(K_RPG, "doorOpen_1.ogg", 0, 0.9, 0.2), gain(at(np.zeros(1), 0.1, one(K_RPG, "creak1.ogg", 0, 0.6, 0.2)), -8))


def s_wave_horn():
    """War horn: two bass-recorder notes a fifth apart, swelled, with a frame-drum double hit."""
    low = env(brec("G2", "SusVib", 1.6), 0.12, 0.9, 0.45)
    fifth = env(brec("D3", "SusVib", 1.6), 0.16, 0.86, 0.45)
    horn = lp_fast(mix(low, gain(fifth, -4)), 3200)
    out = reverb(horn, 0.3, (0.047, 0.071, 0.113), 0.5)
    out = at(out, 0.0, gain(one(FRAME, "HDrumL_Hit_v3_rr1_Sum.wav", 0, 0.5), -6))
    out = at(out, 0.42, gain(one(FRAME, "HDrumL_Hit_v3_rr2_Sum.wav", 0, 0.5), -8))
    return out


def s_boss_roar():
    out = one(RPG, "creature_roar_02.ogg", 0, 1.1, 0.2)
    out = at(out, 0.05, gain(pitch(one(CRE2, "roar_04.ogg", 0, 0.9, 0.2), -3), -4))
    out = at(out, 0.0, gain(one(BDRUM, "BDrumNew_hit_v5_rr1_Sum.wav", 0, 0.8, 0.3), -4))
    out = at(out, 0.04, gain(one(CRE2, "stomp_01.ogg", 0, 0.4), -6))
    return reverb(lp_fast(out, 6000), 0.18)


def s_ability():  # warrior whirlwind
    out = one(WOO, "Twirl 2.wav", 0, 1.3, 0.25)
    out = at(out, 0.04, gain(one(WOO, "Slash.wav"), -4))
    out = at(out, 0.5, gain(one(WOO, "Classic Swish 2.wav", 0, 0.35), -5))
    return out


def s_ability_ranger():  # arrow rain
    out = one(WOO, "Combo 1.wav", 0, 0.6, 0.1)
    out = at(out, 0.22, gain(one(WOO, "Whistle 1.wav", 0, 0.6, 0.15), -7))
    out = at(out, 0.4, gain(one(WOO, "Whistle 3.wav", 0, 0.6, 0.15), -9))
    out = at(out, 0.1, gain(cut(fharp("A2", 0.12), 0, 0.12, 0.001, 0.08), -10))
    return out


def s_ability_builder():  # repair hammer + chime
    out = one(WM, "hammer_02.ogg", 0, 0.25)
    out = at(out, 0.2, gain(one(ANVIL, "Anvil_Hit2_v2_rr1_Mid.wav", 0, 0.5, 0.2), -6))
    out = at(out, 0.32, gain(chime("C5", 0.9), -8))
    out = at(out, 0.3, gain(one(WIND, "windchimes_fastAsc1.wav", 0, 0.8, 0.3), -12))
    return reverb(out, 0.12)


def s_victory():
    out = env(one(TIMP, "Roll/Timpani2_Roll_v3_rr1_Sum.wav", 0, 0.7, 0.1), 0.05, 0.45, 0.2)
    out = at(out, 0.55, gain(one(BDRUM, "BDrumNew_hit_v5_rr1_Sum.wav", 0, 0.9, 0.3), -3))
    out = at(out, 0.55, gain(one(CRASH, "cymbal_crash1_mf1.wav", 0, 1.6, 0.6), -8))
    for i, (nt, t) in enumerate((("C5", 0.6), ("E5", 0.75), ("G5", 0.9), ("C6", 1.1))):
        out = at(out, t, gain(glock(nt, "loud", 1.4), -4))
    gl = arpeggio(harp, ["C4", "E4", "G4", "C5", "E5", "G5", "C6"], 0.045, 0.8, 0.5)
    out = at(out, 1.1, gain(gl, -6))
    out = at(out, 1.5, gain(tub("C4", "ff", 1.8), -8))
    return reverb(out, 0.3, (0.047, 0.071, 0.113), 0.5)


def s_defeat():
    out = gain(one(TIMP, "Hit/Timpani1_Hit_v4_rr1_Sum.wav", 0, 1.2, 0.4), -2)
    out = at(out, 0.7, gain(one(TIMP, "Hit/Timpani1_Hit_v3_rr3_Sum.wav", 0, 1.2, 0.4), -5))
    out = at(out, 0.15, gain(tub("D3", "ff", 2.0), -9))
    out = at(out, 0.9, gain(tub("A#3", "ff", 1.6), -13))
    out = at(out, 0.0, gain(env(one(SUS, "susCymb1_roll_mp1_nloop.wav", 0, 1.6, 0.5), 0.3, 0.6, 0.6), -14))
    return reverb(lp_fast(out, 5000), 0.35, (0.053, 0.079, 0.127), 0.55)


def s_click():
    return hp(one(K_UI2, "click1.ogg", 0, 0.12, 0.04), 250)


def s_level_up():
    notes = ["C5", "E5", "G5", "C6", "E6", "G6"]
    out = arpeggio(lambda nt, d: glock(nt, "medium", d), notes, 0.075, 1.2, 0.8)
    out = at(out, 0.25, gain(one(WIND, "windchimes_fastAsc1.wav", 0, 1.2, 0.4), -10))
    out = at(out, 0.45, gain(chime("C6", 1.4), -8))
    return reverb(out, 0.25)


def s_king_hurt():
    return mix(one(K_IMP, "impactPunch_heavy_000.ogg", 0, 0.5), gain(hp(one(CRE2, "human_02.ogg", 0, 0.6, 0.15), 200), -7))


def s_wall_break():
    out = one(WM, "wood_breaking_01.ogg", 0, 0.6, 0.15)
    out = at(out, 0.08, gain(one(BFH, "bfh1_wood_breaking_02.ogg", 0, 0.6, 0.15), -3))
    out = at(out, 0.2, gain(one(BFH, "bfh1_rock_falling_01.ogg", 0, 0.6, 0.2), -9))
    return out


def s_recruit():
    out = one(K_RPG, "cloth2.ogg", 0, 0.42, 0.1)
    out = at(out, 0.05, gain(one(K_IMP, "footstep_grass_000.ogg", 0, 0.3, 0.1), -6))
    out = at(out, 0.22, gain(glock("G5", "soft", 0.7), -9))
    out = at(out, 0.34, gain(glock("C6", "soft", 0.8), -9))
    return out


def s_forge():
    out = one(ANVIL, "Anvil_Hit1_v2_rr1_Mid.wav", 0, 0.5, 0.2)
    out = at(out, 0.28, gain(one(ANVIL, "Anvil_Hit3_v2_rr1_Mid.wav", 0, 0.6, 0.25), -1))
    return reverb(out, 0.15)


def s_repair():
    out = one(WM, "hammer_02.ogg", 0, 0.25)
    out = at(out, 0.2, one(WM, "wood_hammer_01.ogg", 0, 0.3))
    out = at(out, 0.4, one(WM, "hammer_04.ogg", 0, 0.26))
    out = at(out, 0.55, gain(chime("E5", 0.9), -9))
    return reverb(out, 0.1)


def s_banner():
    out = one(WM, "wood_hit_02.ogg", 0, 0.35)
    out = at(out, 0.07, gain(one(K_RPG, "cloth4.ogg", 0, 0.38, 0.1), -2))
    out = at(out, 0.34, gain(one(K_RPG, "cloth1.ogg", 0, 0.5, 0.15), -6))
    out = at(out, 0.28, gain(glock("C6", "soft", 0.9), -12))
    return out


def s_burn():
    whoomp = one(RPG, "spell_fire_06.ogg", 0, 0.8, 0.25)
    crackle = lp_fast(cut(load(f(FUZZ, "Big Crackle.wav")), 2.0, 0.7, 0.05, 0.2), 3500)
    return mix(whoomp, gain(crackle, -4))


def s_extinguish():
    return hp(one(WAT, "splash_02.ogg", 0, 0.5, 0.15), 1200)


def s_charge():
    out = one(WOO, "Classic Swish 2.wav", 0, 0.37, 0.08)
    out = at(out, 0.02, gain(one(WOO, "Slash.wav"), -6))
    hoof = pitch(one(K_IMP, "footstep_grass_000.ogg", 0, 0.16, 0.05), -5)
    for t in (0.08, 0.2, 0.32):
        out = at(out, t, gain(hoof, -5))
    return out


def s_relic():
    out = gain(one(BTREE, "Stroke/BellTree_Stroke_3_Mid.wav", 0, 1.3, 0.4), -6)
    out = at(out, 0.1, arpeggio(harp, ["C4", "G4", "C5", "E5"], 0.1, 0.9, 0.7))
    out = at(out, 0.5, gain(chime("C6", 1.2), -8))
    return reverb(out, 0.25)


def s_combo_break():
    out = one(BDRUM, "BDrumNew_hit_v5_rr2_Sum.wav", 0, 0.5, 0.2)
    out = at(out, 0.0, gain(one(WOO, "Thunk 2.wav", 0, 0.3), -4))
    out = at(out, 0.02, gain(one(WM, "wood_slam_01.ogg", 0, 0.3), -8))
    return lp_fast(out, 3000)


def s_combo_up():
    return hp(one(FING, "Fing_Cymb.wav", 0, 0.28, 0.1), 1500)


def s_achievement():
    out = arpeggio(lambda nt, d: glock(nt, "medium", d), ["G5", "C6", "G6"], 0.09, 1.0, 0.8)
    out = at(out, 0.15, gain(one(WIND, "windchimes_asc1.wav", 0, 1.0, 0.3), -12))
    out = at(out, 0.3, gain(chime("C6", 1.0), -9))
    return reverb(out, 0.2)


def s_night():
    out = gain(tub("E3", "pp", 1.7), 0)
    drone = lp_fast(cut(load(f(DIDG, "Didgeridoo1_Sus2_Main.wav")), 0.6, 1.5, 0.3, 0.5), 450)
    out = at(out, 0.0, gain(drone, -10))
    return reverb(out, 0.3, (0.053, 0.079, 0.127), 0.5)


def s_rain_start():
    return cut(load(f(LOOPS, "rain.ogg")), 0.0, 1.2, 0.35, 0.35)


def s_rain_loop():
    x = load(f(LOOPS, "rain.ogg"))
    k = n(0.2)
    body = x[: len(x) - k].copy()
    body[:k] = body[:k] * np.linspace(0, 1, k) + x[len(x) - k:] * np.linspace(1, 0, k)  # seamless loop
    return body


def s_boss_down():
    out = one(BDRUM, "BDrumNew_hit_v7_rr1_Sum.wav", 0, 1.0, 0.3)
    out = at(out, 0.0, gain(one(CRASH, "cymbal_crash1_ff2.wav", 0, 1.8, 0.6), -7))
    out = at(out, 0.02, gain(one(CRE2, "die_02.ogg", 0, 0.66, 0.15), -3))
    out = at(out, 0.05, gain(one(TIMP, "Hit/Timpani1_Hit_v4_rr2_Sum.wav", 0, 1.0, 0.3), -5))
    out = at(out, 0.4, gain(arpeggio(fharp, ["C3", "E3", "G3", "C4", "E4", "G4", "C5"], 0.05, 0.8, 0.6), -4))
    out = at(out, 0.8, gain(glock("C6", "loud", 1.2), -6))
    return reverb(out, 0.3, (0.047, 0.071, 0.113), 0.5)


def s_star():
    return mix(glock("C7", "loud", 0.6), gain(one(FING, "Fing_Cymb.wav", 0, 0.4, 0.15), -10))


def s_crown():
    out = env(one(TIMP, "Roll/Timpani3_Roll_v5_rr1_Sum.wav", 0, 0.6, 0.1), 0.05, 0.35, 0.2)
    out = at(out, 0.45, gain(one(BDRUM, "BDrumNew_hit_v5_rr1_Sum.wav", 0, 0.9, 0.3), -3))
    out = at(out, 0.45, gain(one(CRASH, "cymbal_crash1_mf2.wav", 0, 1.6, 0.6), -8))
    for nt, t in (("G5", 0.45), ("C6", 0.6), ("E6", 0.75), ("G6", 0.95)):
        out = at(out, t, gain(glock(nt, "loud", 1.3), -4))
    out = at(out, 0.95, gain(tub("C4", "ff", 1.6), -8))
    out = at(out, 1.0, gain(one(WIND, "windchimes_asc1.wav", 0, 1.0, 0.3), -12))
    return reverb(out, 0.3, (0.047, 0.071, 0.113), 0.5)


def s_tab():
    return hp(one(K_UI, "tick_001.ogg", 0, 0.1, 0.03), 300)


def s_swipe():
    return lp_fast(one(WOO, "Gentle Swish.wav", 0, 0.22, 0.06), 6000)


SOUNDS = [
    ("shoot", -16, s_shoot), ("hit", -13, s_hit), ("hit_flesh", -13, s_hit_flesh), ("coin", -16, s_coin),
    ("coin_stack", -15, s_coin_stack), ("build", -13, s_build), ("gate", -15, s_gate), ("wave_horn", -13, s_wave_horn),
    ("boss_roar", -11, s_boss_roar), ("ability", -13, s_ability), ("ability_ranger", -14, s_ability_ranger),
    ("ability_builder", -13, s_ability_builder), ("victory", -12, s_victory), ("defeat", -13, s_defeat),
    ("click", -19, s_click), ("level_up", -13, s_level_up), ("king_hurt", -12, s_king_hurt),
    ("wall_break", -12, s_wall_break), ("recruit", -15, s_recruit), ("forge", -13, s_forge), ("repair", -13, s_repair),
    ("banner", -14, s_banner), ("burn", -15, s_burn), ("extinguish", -16, s_extinguish), ("charge", -13, s_charge),
    ("relic", -13, s_relic), ("combo_break", -12, s_combo_break), ("combo_up", -17, s_combo_up),
    ("achievement", -13, s_achievement), ("night", -15, s_night), ("rain_start", -19, s_rain_start),
    ("rain_loop", -19, s_rain_loop), ("boss_down", -11, s_boss_down), ("star", -14, s_star), ("crown", -12, s_crown),
    ("tab", -21, s_tab), ("swipe", -19, s_swipe),
]


def save_wav(name, x):
    y = np.asarray(x, dtype=np.float64)
    y = y - y.mean()  # no DC offset (drones)
    if not name.endswith("_loop"):
        # at most 5 ms of leading silence so the sound lands on the frame that triggers it
        thr = 10 ** (-50 / 20)
        idx = np.where(np.abs(y) > thr)[0]
        if len(idx) and idx[0] > n(0.005):
            y = y[idx[0] - n(0.005):]
    y = np.clip(y, -1, 1)
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((y * 32767).astype("<i2").tobytes())


if __name__ == "__main__":
    preview = np.zeros(1)
    t = 0.0
    for name, db, fn in SOUNDS:
        if ONLY and name not in ONLY:
            continue
        CURRENT[0] = name
        x = level(fn(), db)
        save_wav(name, x)
        print("wav %-16s %.2fs" % (name, len(x) / SR))
        if PREVIEW:
            preview = at(preview, t, x)
            t += len(x) / SR + 0.6
    if not ONLY:
        with open(os.path.join(ROOT, "art/audio/sfx_sources.md"), "w") as f:
            f.write("# Sources des effets sonores (généré par make_sfx_natural.py)\n\n")
            f.write("Toutes les sources sont CC0. `lav/` = collection lavenderdotpet "
                    "(https://github.com/lavenderdotpet/CC0-Public-Domain-Sounds), "
                    "`vcsl/` = Versilian Community Sample Library (https://github.com/sgossner/VCSL).\n\n")
            f.write("| Fichier du jeu | Sources |\n|---|---|\n")
            for name, _, _ in SOUNDS:
                srcs = sorted(USED.get(name, ()))
                f.write("| `%s.wav` | %s |\n" % (name, "<br>".join("`%s`" % x for x in srcs)))
    if PREVIEW:
        tmp = PREVIEW + ".wav"
        OUT_ = OUT
        with wave.open(tmp, "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
            w.writeframes((np.clip(preview, -1, 1) * 32767).astype("<i2").tobytes())
        subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", tmp, "-codec:a", "libmp3lame", "-b:a", "128k", PREVIEW], check=True)
        os.remove(tmp)
        print("preview", PREVIEW)
