#!/usr/bin/env python3
"""
Generates the particle sprite textures used by the companion's visual effects
(app/src/main/res/drawable-nodpi/vfx_*.png). White sprites on transparent; the app tints and
blends them additively, which is how real-time engines make glow, sparks, fire and dust.

  python3 tools/make_vfx_textures.py
"""
import math
import os

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "drawable-nodpi")
N = 128


def grid(n=N):
    y, x = np.mgrid[0:n, 0:n].astype(np.float32)
    c = (n - 1) / 2
    return (x - c) / c, (y - c) / c


def save(name, alpha, rgb=(1.0, 1.0, 1.0)):
    a = np.clip(alpha, 0, 1)
    img = np.zeros((a.shape[0], a.shape[1], 4), np.uint8)
    for i, v in enumerate(rgb):
        img[..., i] = int(255 * v)
    img[..., 3] = (a * 255).astype(np.uint8)
    Image.fromarray(img, "RGBA").save(os.path.join(OUT, name), optimize=True)
    print("wrote", name)


def fbm(n, octaves=5, seed=0):
    rng = np.random.default_rng(seed)
    total = np.zeros((n, n), np.float32)
    amp = 1.0
    for o in range(octaves):
        size = 2 ** (o + 2)
        coarse = rng.random((size, size)).astype(np.float32)
        layer = np.array(Image.fromarray((coarse * 255).astype(np.uint8)).resize((n, n), Image.BICUBIC), np.float32) / 255
        total += layer * amp
        amp *= 0.5
    return (total - total.min()) / (total.max() - total.min() + 1e-6)


def main():
    os.makedirs(OUT, exist_ok=True)
    x, y = grid()
    r = np.sqrt(x * x + y * y)

    # Soft glow: gaussian core + wide falloff (fire, embers, bokeh, motes).
    save("vfx_glow.png", np.exp(-(r ** 2) / 0.08) * 0.85 + np.exp(-(r ** 2) / 0.45) * 0.25 * (r < 1))

    # Star sparkle: hot core, 4 diffraction spikes, 4 faint diagonal spikes, halo.
    core = np.exp(-(r ** 2) / 0.004)
    spikes = np.exp(-np.abs(x) / 0.025) * np.exp(-np.abs(y) / 0.55) + np.exp(-np.abs(y) / 0.025) * np.exp(-np.abs(x) / 0.55)
    u, v = (x + y) / math.sqrt(2), (x - y) / math.sqrt(2)
    diag = (np.exp(-np.abs(u) / 0.02) * np.exp(-np.abs(v) / 0.25) + np.exp(-np.abs(v) / 0.02) * np.exp(-np.abs(u) / 0.25)) * 0.45
    halo = np.exp(-(r ** 2) / 0.06) * 0.35
    save("vfx_star.png", (core + spikes + diag + halo) * (r < 1))

    # Smoke / mist puff: soft-edged turbulent blob.
    noise = fbm(N, seed=3)
    blob = np.clip(1 - r, 0, 1) ** 1.6
    save("vfx_smoke.png", np.clip(blob * (0.35 + 0.9 * noise) - 0.08, 0, 1) * 0.9)

    # Flame lick: teardrop with noisy edge (rises upward).
    fy = (y + 1) / 2  # 0 top .. 1 bottom
    width = 0.15 + 0.55 * np.sin(np.clip(fy, 0, 1) * math.pi * 0.85) ** 1.3
    shape = np.clip(1 - np.abs(x) / (width + 1e-3), 0, 1) ** 1.2
    fn = fbm(N, seed=7)
    flame = shape * np.clip(fy * 1.4, 0, 1) * (0.6 + 0.6 * fn)
    save("vfx_flame.png", np.clip(flame - 0.05, 0, 1))

    # Spark streak (motion-stretched, for firework trails and lightning sparks).
    save("vfx_streak.png", np.exp(-(x ** 2) / 0.003) * np.exp(-(y ** 2) / 0.5) * (r < 1.2))

    # Dust grain: tiny crisp dot with faint glow (sand, stardust).
    save("vfx_dust.png", np.exp(-(r ** 2) / 0.02) + np.exp(-(r ** 2) / 0.2) * 0.2)

    # Ring: soft circle outline (shockwaves, listening pulses).
    save("vfx_ring.png", np.exp(-((r - 0.82) ** 2) / 0.004))


if __name__ == "__main__":
    main()
