#!/usr/bin/env python3
"""Generate GN Reader launcher icon: pixel-art Earth from the Moon (deterministic).

Usage:
  python3 scripts/generate_app_icon.py

Writes mipmap-*/ic_launcher(.png|_round.png) and drawable/ic_launcher_foreground.png
"""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
ART = ROOT / "scripts/icon_src"
ART.mkdir(parents=True, exist_ok=True)

W, H = 32, 32

SPACE = (12, 14, 28, 255)
STAR = (230, 235, 255, 255)
STAR_DIM = (120, 130, 170, 255)
MOON_LT = (210, 208, 200, 255)
MOON_MD = (168, 166, 158, 255)
MOON_DK = (120, 118, 112, 255)
MOON_CR = (90, 88, 84, 255)
EARTH_OCEAN = (30, 110, 210, 255)
EARTH_OCEAN2 = (50, 140, 230, 255)
EARTH_LAND = (55, 170, 75, 255)
EARTH_LAND2 = (40, 140, 60, 255)
EARTH_CLOUD = (220, 230, 245, 255)
EARTH_ICE = (200, 220, 240, 255)


def hash_xy(x: int, y: int, salt: int = 0) -> int:
    n = (x * 374761393 + y * 668265263 + salt * 982451653) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return n


def paint() -> list[list[tuple[int, int, int, int]]]:
    """Clearer Earth (blue ocean + green land bands) + soft moon without crater clutter."""
    pixels = [[SPACE for _ in range(W)] for _ in range(H)]
    for y in range(H):
        for x in range(W):
            h = hash_xy(x, y, 7)
            if h % 41 == 0:
                pixels[y][x] = STAR
            elif h % 61 == 0:
                pixels[y][x] = STAR_DIM

    # Earth: upper-right sphere — recognizable ocean/land, soft terminator
    ex, ey, er = 22.0, 9.5, 7.2
    for y in range(H):
        for x in range(W):
            dx, dy = x - ex + 0.5, y - ey + 0.5
            d = math.hypot(dx, dy)
            if d > er + 0.4:
                continue
            nx, ny = dx / er, dy / er
            lit = max(0.0, min(1.0, (-nx * 0.35 - ny * 0.5 + 0.78)))
            # Simple continents: two green lobes + polar ice (reads as “planet Earth”)
            land = (
                (nx > -0.15 and nx < 0.55 and ny > -0.35 and ny < 0.45 and (nx + ny * 0.4) > -0.05)
                or (nx < -0.25 and ny > -0.1 and ny < 0.55 and nx > -0.85)
                or (abs(nx) < 0.35 and ny > 0.35 and ny < 0.7)
            )
            ice = ny < -0.68 or ny > 0.75
            cloud = (not land) and (math.sin(nx * 6 + ny * 3) > 0.72) and abs(ny) < 0.55
            if d > er - 0.2:
                base = (45, 100, 175, 255)  # atmosphere rim
            elif ice:
                base = EARTH_ICE
            elif land:
                base = EARTH_LAND if lit > 0.5 else EARTH_LAND2
            elif cloud:
                base = EARTH_CLOUD
            else:
                base = EARTH_OCEAN if lit > 0.42 else EARTH_OCEAN2
            shade = 0.5 + 0.5 * lit
            c = tuple(max(0, min(255, int(ch * shade))) for ch in base[:3]) + (255,)
            if lit < 0.22:
                c = (max(6, c[0] // 3), max(10, c[1] // 3), max(28, c[2] // 2), 255)
            pixels[y][x] = c

    # Moon foreground: smooth shading bands, no crater rings
    for y in range(H):
        for x in range(W):
            horizon = 19 + int(0.8 * math.sin(x / 6.0)) + (x - 16) ** 2 // 110
            if y < horizon:
                continue
            depth = y - horizon
            # Soft left→right lighting on lunar surface
            lit = 0.55 + 0.35 * (x / max(1, W - 1)) - 0.08 * (depth / 12.0)
            lit = max(0.25, min(1.0, lit))
            if depth < 2:
                base = MOON_LT
            elif depth < 5:
                base = MOON_MD
            else:
                base = MOON_DK
            # Very subtle mottling (not mountains/craters)
            if hash_xy(x, y, 19) % 17 == 0 and depth > 2:
                base = MOON_MD if base == MOON_DK else MOON_DK
            c = tuple(max(0, min(255, int(ch * lit))) for ch in base[:3]) + (255,)
            pixels[y][x] = c

    # Soft dark limb near left for depth (no rocky silhouette)
    for y in range(18, 23):
        for x in range(2, 7):
            if (x - 4) ** 2 + (y - 20) ** 2 <= 8:
                prev = pixels[y][x]
                pixels[y][x] = (
                    max(40, prev[0] * 2 // 3),
                    max(40, prev[1] * 2 // 3),
                    max(38, prev[2] * 2 // 3),
                    255,
                )
    return pixels


def to_image(pixels, scale: int) -> Image.Image:
    img = Image.new("RGBA", (W * scale, H * scale))
    px = img.load()
    for y in range(H):
        for x in range(W):
            c = pixels[y][x]
            for dy in range(scale):
                for dx in range(scale):
                    px[x * scale + dx, y * scale + dy] = c
    return img


def main() -> None:
    pixels = paint()
    master = to_image(pixels, 16)
    master.save(ART / "gn_reader_icon_master.png")
    to_image(pixels, 1).save(ART / "gn_reader_icon_32.png")

    fg_size = 432
    pad_ratio = 0.18
    content = to_image(pixels, 10)
    fg = Image.new("RGBA", (fg_size, fg_size), (0, 0, 0, 0))
    target = int(fg_size * (1 - 2 * pad_ratio))
    content_r = content.resize((target, target), Image.NEAREST)
    ox = (fg_size - target) // 2
    oy = (fg_size - target) // 2
    fg.paste(content_r, (ox, oy), content_r)
    (RES / "drawable").mkdir(parents=True, exist_ok=True)
    fg.save(RES / "drawable/ic_launcher_foreground.png")

    bg_color = (12, 14, 28, 255)
    legacy = Image.new("RGBA", (512, 512), bg_color)
    full = content.resize((512, 512), Image.NEAREST)
    legacy.paste(full, (0, 0), full)

    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in densities.items():
        d = RES / folder
        d.mkdir(parents=True, exist_ok=True)
        icon = legacy.resize((size, size), Image.NEAREST)
        icon.save(d / "ic_launcher.png")
        icon.save(d / "ic_launcher_round.png")
        print("wrote", d)

    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        (RES / "mipmap-anydpi-v26" / name).write_text(
            """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_bg" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
"""
        )
    print("ok")


if __name__ == "__main__":
    main()
