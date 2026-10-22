"""
Synthesises TapX's sound effects: soft, round, "bubbly" sounds (sine bloops with pitch slides,
gentle plinks, chimes and fizz), written as mono OGG Vorbis into src/main/resources/sounds/.
They go into the TapX resource pack as tapx:<name> (see ScreenPack / Session.sfx).

Run from the repo root (needs numpy and ffmpeg):  python3 tools/make_sounds.py
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

SR = 44100
OUT = os.path.join("src", "main", "resources", "sounds")
rng = np.random.default_rng(7)


def t_of(d):
    return np.arange(int(SR * d)) / SR


def env(n, attack=0.004, decay=0.08):
    """Soft attack, exponential decay."""
    t = np.arange(n) / SR
    a = np.clip(t / attack, 0, 1) if attack > 0 else np.ones(n)
    return a * np.exp(-t / decay)


def sweep(f0, f1, d, curve=3.0):
    """Sine whose pitch glides from f0 to f1 (exponential-ish), phase-continuous."""
    t = t_of(d)
    k = 1 - np.exp(-curve * t / d)
    k /= k[-1] if k[-1] else 1
    f = f0 + (f1 - f0) * k
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def tone(f, d, harmonics=(1.0, 0.25, 0.08)):
    t = t_of(d)
    return sum(a * np.sin(2 * np.pi * f * (i + 1) * t) for i, a in enumerate(harmonics))


def bloop(f0, f1, d=0.12, decay=0.05, attack=0.003):
    """The basic bubble: a quick pitch slide with a soft envelope."""
    s = sweep(f0, f1, d)
    return s * env(len(s), attack, decay)


def plink(f, d=0.35, decay=0.09):
    """A soft bell-ish plink (sine + a gentle inharmonic partial)."""
    t = t_of(d)
    s = np.sin(2 * np.pi * f * t) + 0.3 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t / (decay * 0.4))
    return s * env(len(s), 0.002, decay)


def fizz(d=0.3, decay=0.12, lo=2000, hi=7000):
    """Soft sparkly noise, band-limited."""
    n = int(SR * d)
    x = rng.standard_normal(n)
    spec = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(n, 1 / SR)
    spec[(freqs < lo) | (freqs > hi)] = 0
    x = np.fft.irfft(spec, n)
    x /= np.max(np.abs(x)) + 1e-9
    return x * env(n, 0.01, decay)


def mix(length, *parts):
    """parts: (offset_seconds, signal, gain)."""
    out = np.zeros(int(SR * length))
    for off, s, g in parts:
        i = int(off * SR)
        j = min(len(out), i + len(s))
        out[i:j] += g * s[: j - i]
    return out


def lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.zeros_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def space(x, delay=0.045, fb=0.28, wet=0.25):
    """A tiny room: a couple of soft echoes so sounds feel round, not dry."""
    d = int(delay * SR)
    y = np.concatenate([x, np.zeros(int(SR * 0.25))])
    out = y.copy()
    for k in range(1, 6):
        if d * k >= len(y):
            break
        out[d * k:] += (fb ** k) * wet * y[: len(y) - d * k]
    return out


def finish(x, peak=0.7):
    x = space(x)
    x = lowpass(x, 9000)
    fade = int(0.01 * SR)
    x[-fade:] *= np.linspace(1, 0, fade)
    # trim the silent tail
    nz = np.nonzero(np.abs(x) > 1e-3)[0]
    if len(nz):
        x = x[: nz[-1] + int(0.02 * SR)]
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def arp(notes, step, each, kind=plink, gain=1.0):
    return [(i * step, kind(f, each), gain) for i, f in enumerate(notes)]


C5, D5, E5, G5, A5, C6, E6, G6, C7 = 523.25, 587.33, 659.25, 783.99, 880.0, 1046.5, 1318.5, 1568.0, 2093.0

SOUNDS = {
    # tapping a monster: a soft bubble pop
    "hit": lambda: finish(mix(0.2, (0, bloop(520, 300, 0.12, 0.045), 1.0), (0.004, bloop(1040, 700, 0.06, 0.02), 0.25)), 0.55),
    # critical: a bigger double pop with a sparkle
    "crit": lambda: finish(mix(0.4, (0, bloop(700, 330, 0.14, 0.06), 1.0), (0.05, bloop(980, 520, 0.12, 0.05), 0.8),
                               (0.03, fizz(0.25, 0.08), 0.18))),
    # monster defeated: a little burst of rising bubbles
    "kill": lambda: finish(mix(0.5, (0.0, bloop(380, 620, 0.1, 0.05), 0.9), (0.06, bloop(520, 840, 0.1, 0.05), 0.8),
                               (0.12, bloop(680, 1100, 0.12, 0.06), 0.7), (0.1, fizz(0.3, 0.1), 0.12))),
    # coin collected: a soft round plink
    "coin": lambda: finish(mix(0.35, (0, plink(1760, 0.3, 0.06), 0.7), (0.035, plink(2349, 0.3, 0.07), 0.6)), 0.45),
    # boss appears: a deep wobbly bubble rumble
    "boss": lambda: finish(mix(1.1, (0, sweep(110, 70, 1.0) * env(int(SR), 0.05, 0.45) * (1 + 0.35 * np.sin(2 * np.pi * 9 * t_of(1.0))), 1.0),
                               (0.05, bloop(220, 140, 0.4, 0.2), 0.5), (0.25, bloop(180, 110, 0.4, 0.2), 0.4))),
    # boss defeated: a happy bubbly arpeggio with bells
    "boss_kill": lambda: finish(mix(1.2, *arp([C5, E5, G5, C6, E6], 0.08, 0.6), (0.0, bloop(300, 700, 0.15, 0.08), 0.6), (0.32, fizz(0.6, 0.25), 0.15))),
    # level up: a bubble sweeping up into a chime
    "level_up": lambda: finish(mix(0.9, (0, sweep(300, 1200, 0.3) * env(int(0.3 * SR), 0.01, 0.2), 0.7),
                                   (0.22, plink(C6, 0.6, 0.18), 0.8), (0.3, plink(G6, 0.6, 0.2), 0.6), (0.25, fizz(0.4, 0.15), 0.12))),
    # bought something: pop + coin plink
    "buy": lambda: finish(mix(0.5, (0, bloop(420, 640, 0.09, 0.04), 0.9), (0.06, plink(1568, 0.35, 0.08), 0.6), (0.1, plink(2093, 0.35, 0.08), 0.5)), 0.55),
    # UI click: a tiny soft blip
    "click": lambda: finish(mix(0.1, (0, bloop(900, 1200, 0.05, 0.015, 0.001), 1.0)), 0.35),
    # not allowed: two low, gentle bonks
    "error": lambda: finish(mix(0.4, (0, bloop(330, 220, 0.12, 0.05), 1.0), (0.13, bloop(280, 180, 0.14, 0.06), 0.9)), 0.5),
    # thunder spell: a soft whoosh and a round boom
    "thunder": lambda: finish(mix(1.0, (0, fizz(0.6, 0.18, 300, 3000), 0.6), (0.04, bloop(160, 55, 0.7, 0.3, 0.005), 1.0))),
    # any spell: a shimmering rising bubble
    "spell": lambda: finish(mix(0.8, (0, sweep(400, 1300, 0.5) * env(int(0.5 * SR), 0.02, 0.25) * (1 + 0.3 * np.sin(2 * np.pi * 14 * t_of(0.5))), 0.7),
                                (0.15, fizz(0.5, 0.2), 0.2), (0.3, plink(E6, 0.4, 0.12), 0.4))),
    # pet attacks: a cute squeaky chirp
    "pet": lambda: finish(mix(0.3, (0, bloop(900, 1700, 0.09, 0.04), 1.0), (0.08, bloop(1100, 1900, 0.08, 0.035), 0.8)), 0.5),
    # chest opens: a woody pop and a sparkle run
    "chest": lambda: finish(mix(0.9, (0, bloop(240, 160, 0.12, 0.05), 0.9), *arp([G5, C6, E6, G6], 0.06, 0.4, gain=0.5), (0.1, fizz(0.5, 0.2), 0.18))),
    # legendary drop: a long magical bell arpeggio
    "legendary": lambda: finish(mix(1.6, *arp([C5, G5, C6, E6, G6, C7], 0.09, 0.9, gain=0.8), (0.0, sweep(200, 800, 0.5) * env(int(0.5 * SR), 0.02, 0.3), 0.4),
                                    (0.3, fizz(1.0, 0.4), 0.2))),
    # gear equipped: a soft, rounded clink
    "equip": lambda: finish(mix(0.4, (0, plink(1200, 0.35, 0.07), 0.8), (0.012, plink(1610, 0.35, 0.06), 0.5), (0, bloop(300, 220, 0.08, 0.03), 0.4)), 0.5),
    # the wisp: an airy twinkle
    "wisp": lambda: finish(mix(0.8, *arp([E6, G6, C7], 0.07, 0.5, gain=0.5), (0.0, fizz(0.6, 0.25, 4000, 9000), 0.3))),
    # boss escaped: descending wobbly bubbles
    "fail": lambda: finish(mix(0.9, (0, bloop(500, 300, 0.25, 0.12), 0.9), (0.2, bloop(400, 220, 0.25, 0.12), 0.8), (0.4, bloop(300, 140, 0.4, 0.2), 0.8)), 0.6),
    # achievement / quest done: two bright chimes and a sparkle
    "achievement": lambda: finish(mix(0.9, (0, plink(E6, 0.5, 0.14), 0.8), (0.11, plink(C7 * 0.75 * 1.0, 0.6, 0.18), 0.8), (0.0, bloop(400, 800, 0.1, 0.05), 0.5),
                                      (0.12, fizz(0.4, 0.15), 0.15))),
}


def write_ogg(name, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "s.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        out = os.path.join(OUT, name + ".ogg")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", "-map_metadata", "-1",
                        "-fflags", "+bitexact", "-flags:a", "+bitexact", out], check=True)
        return out


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    names = sys.argv[1:] or list(SOUNDS)
    for n in names:
        path = write_ogg(n, SOUNDS[n]())
        print(f"{path}: {os.path.getsize(path)} bytes")
