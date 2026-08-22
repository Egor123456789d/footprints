#!/usr/bin/env python3
"""Draws the two footprint glyph textures from the ASCII art below.

The art is white and fully opaque. Colour comes from the plugin (text colour) and
transparency from text opacity, so painting alpha into the PNG only fights the font
renderer.

One shape mirrored across the vertical axis gives both feet. The widest rows have to
touch columns 4 and 11 exactly: a bitmap glyph advances to its rightmost lit column,
so a shape whose mirror image ends one column short gets centred differently and the
left print drifts away from the right one.

    python3 tools/make_glyphs.py
"""
import pathlib

from PIL import Image

RIGHT_FOOT = (
    "................",
    "......####......",
    ".....######.....",
    "....########....",
    "....########....",
    "....########....",
    "....#######.....",
    ".....######.....",
    "......#####.....",
    "................",
    ".....#####......",
    "....#######.....",
    "....#######.....",
    ".....#####......",
    "................",
    "................",
)

OUT = pathlib.Path(__file__).resolve().parents[1] / "resourcepack/assets/footprints/textures/font"


def render(art):
    img = Image.new("RGBA", (len(art[0]), len(art)), (0, 0, 0, 0))
    for y, row in enumerate(art):
        for x, cell in enumerate(row):
            if cell == "#":
                img.putpixel((x, y), (255, 255, 255, 255))
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    right = render(RIGHT_FOOT)
    feet = (("footprint_right", right), ("footprint_left", right.transpose(Image.FLIP_LEFT_RIGHT)))
    for name, img in feet:
        img.save(OUT / f"{name}.png")
        lit = [x for x in range(img.width) for y in range(img.height) if img.getpixel((x, y))[3]]
        print(f"{name}.png {img.width}x{img.height}, columns {min(lit)}..{max(lit)}, advance {max(lit) + 2}")


if __name__ == "__main__":
    main()
