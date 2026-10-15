"""Synthesises the toggle sounds: a bright rising "plim" (on) and a soft falling "toom" (off)."""
import numpy as np, wave, sys
SR = 44100

def bell(freq, dur, decay, partials=((1, 1.0), (2.0, 0.28), (3.01, 0.10), (4.2, 0.04)), attack=0.004):
    t = np.arange(int(SR * dur)) / SR
    s = sum(a * np.sin(2 * np.pi * freq * m * t) * np.exp(-t * decay * (1 + 0.6 * (m - 1))) for m, a in partials)
    env = np.minimum(1.0, t / attack)
    return s * env

def place(total, sig, at):
    i = int(at * SR)
    total[i:i + len(sig)] += sig[:max(0, len(total) - i)]

def render(notes, dur, gain, path):
    out = np.zeros(int(SR * dur))
    for freq, at, decay, amp in notes:
        place(out, amp * bell(freq, dur - at, decay), at)
    # tiny soft "body" thump for weight, then normalise and fade the tail
    out *= gain / np.max(np.abs(out))
    fade = int(0.03 * SR)
    out[-fade:] *= np.linspace(1, 0, fade)
    with wave.open(path, 'w') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((out * 32767).astype('<i2').tobytes())

# ON: "plim" - two quick rising notes (E6 -> B6), bright and short
render([(1318.5, 0.000, 14.0, 0.85), (1975.5, 0.055, 11.0, 1.0)], 0.42, 0.55, 'toggle_on.wav')
# OFF: "toom" - two falling, rounder notes (B5 -> E5), slightly darker
render([(987.8, 0.000, 16.0, 0.8), (659.3, 0.060, 12.0, 1.0)], 0.42, 0.5, 'toggle_off.wav')
