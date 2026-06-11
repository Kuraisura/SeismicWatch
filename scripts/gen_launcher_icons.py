"""
Regenerate the legacy launcher PNGs (ic_launcher / ic_launcher_round) for every
mipmap density from the Gising! teal emblem. Modern devices use the adaptive vector
icon (mipmap-anydpi-v26); these PNGs are the pre-API-26 fallback.

Emblem: obsidian field, teal glow disc, teal outer ring, ashen-white seismic wave,
Epicenter-Crimson location dot. Rendered at 4x then downsampled for clean antialiasing.
"""
import math
import os
from PIL import Image, ImageDraw

OBSIDIAN = (0x12, 0x14, 0x1C, 255)
TEAL = (0x00, 0xC7, 0xBE, 255)
TEAL_GLOW = (0x00, 0xC7, 0xBE, 0x22)
CHALK = (0xF5, 0xF6, 0xF9, 255)
CRIMSON = (0xFF, 0x3B, 0x30, 255)

# density -> launcher icon edge in px (standard Android sizes)
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

SS = 4  # supersample factor
RES_ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")


def circle(draw, cx, cy, r, **kw):
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], **kw)


def render(size, round_icon):
    s = size * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # Background plate (full-bleed square, or disc for the round variant).
    if round_icon:
        circle(d, s / 2, s / 2, s / 2, fill=OBSIDIAN)
    else:
        d.rectangle([0, 0, s, s], fill=OBSIDIAN)

    cx = cy = s / 2
    unit = s / 108.0  # vector was authored on a 108 viewport

    # Teal glow disc.
    circle(d, cx, cy, 30 * unit, fill=TEAL_GLOW)
    # Outer ring (stroke).
    circle(d, cx, cy, 28 * unit, outline=TEAL, width=max(1, round(5 * unit)))

    # Seismic wave polyline (chalk), mapped from the vector path points.
    pts = [(30, 54), (40, 54), (45, 38), (51, 72), (57, 44),
           (62, 62), (67, 52), (72, 54), (78, 54)]
    wave = [(p[0] * unit, p[1] * unit) for p in pts]
    d.line(wave, fill=CHALK, width=max(1, round(4.5 * unit)), joint="curve")

    # Location pin dot (crimson).
    circle(d, cx, 82 * unit, 4 * unit, fill=CRIMSON)

    return img.resize((size, size), Image.LANCZOS)


def main():
    for density, edge in DENSITIES.items():
        out_dir = os.path.join(RES_ROOT, f"mipmap-{density}")
        os.makedirs(out_dir, exist_ok=True)
        render(edge, round_icon=False).save(os.path.join(out_dir, "ic_launcher.png"))
        render(edge, round_icon=True).save(os.path.join(out_dir, "ic_launcher_round.png"))
        print(f"  {density}: {edge}x{edge} ic_launcher + ic_launcher_round")


if __name__ == "__main__":
    main()
