#!/usr/bin/env python3
"""Draws the application icon and exports every size the packaging and the window need.

Run from the repository root:  python3 design/generate-icon.py
Needs Pillow. The geometry lives here so the icon can be tweaked and regenerated, not edited by hand.
"""
import math
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

SIZE = 1024
SCALE = 2  # drawn at twice the size and scaled down, for smooth edges
OUT_ICONS = Path("app-desktop/icons")
OUT_RESOURCES = Path("app-desktop/src/jvmMain/resources")

SKY_TOP = (14, 52, 140)
SKY_BOTTOM = (120, 200, 248)
CANOPY_LEFT = (255, 150, 40)
CANOPY_RIGHT = (255, 92, 36)
WHITE = (255, 255, 255)


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def vertical_gradient(size, top, bottom):
    image = Image.new("RGB", (size, size))
    draw = ImageDraw.Draw(image)
    for y in range(size):
        draw.line([(0, y), (size, y)], fill=lerp(top, bottom, y / (size - 1)))
    return image


def horizontal_gradient(size, left, right):
    image = Image.new("RGB", (size, size))
    draw = ImageDraw.Draw(image)
    for x in range(size):
        draw.line([(x, 0), (x, size)], fill=lerp(left, right, x / (size - 1)))
    return image


def ellipse_box(cx, cy, rx, ry, s):
    return [(cx - rx) * s, (cy - ry) * s, (cx + rx) * s, (cy + ry) * s]


def canopy_mask(s):
    """An arched crescent: a big ellipse with a lower, smaller one cut out, kept above the wing tips."""
    mask = Image.new("L", (SIZE * s, SIZE * s), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse(ellipse_box(512, 440, 400, 270, s), fill=255)
    draw.ellipse(ellipse_box(512, 520, 372, 205, s), fill=0)
    draw.rectangle([0, 520 * s, SIZE * s, SIZE * s], fill=0)
    return mask


def cell_lines(s):
    """Thin lines fanning out from below the canopy, like the cell walls of a wing."""
    lines = Image.new("L", (SIZE * s, SIZE * s), 0)
    draw = ImageDraw.Draw(lines)
    origin = (512 * s, 760 * s)
    for degrees in range(-62, -117, -11):
        angle = math.radians(degrees)
        end = (origin[0] + math.cos(angle) * 700 * s, origin[1] + math.sin(angle) * 700 * s)
        draw.line([origin, end], fill=255, width=9 * s)
    return lines


def draw_icon():
    s = SCALE
    big = SIZE * s
    background = vertical_gradient(big, SKY_TOP, SKY_BOTTOM)

    canopy = horizontal_gradient(big, CANOPY_LEFT, CANOPY_RIGHT)
    mask = canopy_mask(s)
    background.paste(canopy, (0, 0), mask)

    # Cell walls: slightly darker lines, only where the canopy is.
    walls = ImageChops.multiply(cell_lines(s), mask)
    darker = Image.new("RGB", (big, big), (200, 60, 20))
    background.paste(darker, (0, 0), walls.point(lambda v: int(v * 0.55)))

    # Leading edge: a light rim along the top of the canopy.
    rim = Image.new("L", (big, big), 0)
    rim_draw = ImageDraw.Draw(rim)
    rim_draw.ellipse(ellipse_box(512, 440, 400, 270, s), outline=255, width=16 * s)
    rim = ImageChops.multiply(rim, mask)
    background.paste(Image.new("RGB", (big, big), (255, 214, 150)), (0, 0), rim.point(lambda v: int(v * 0.85)))

    # Suspension lines and the pilot.
    draw = ImageDraw.Draw(background)
    pilot = (512 * s, 800 * s)
    # The lines start on the underside of the canopy (the lower ellipse), just inside it so no gap shows.
    for x, y in [(300, 346), (512, 310), (724, 346)]:
        draw.line([(x * s, y * s), pilot], fill=WHITE, width=7 * s)
    draw.ellipse(ellipse_box(512, 800, 40, 40, s), fill=WHITE)
    draw.rounded_rectangle([(512 - 24) * s, 826 * s, (512 + 24) * s, 880 * s], radius=14 * s, fill=WHITE)

    # Rounded-square outline, so the corners are transparent.
    corner = Image.new("L", (big, big), 0)
    ImageDraw.Draw(corner).rounded_rectangle([40 * s, 40 * s, (SIZE - 40) * s, (SIZE - 40) * s], radius=210 * s, fill=255)
    icon = background.convert("RGBA")
    icon.putalpha(corner)
    return icon.resize((SIZE, SIZE), Image.LANCZOS)


def main():
    icon = draw_icon()
    OUT_ICONS.mkdir(parents=True, exist_ok=True)
    OUT_RESOURCES.mkdir(parents=True, exist_ok=True)
    icon.save(OUT_ICONS / "icon.png")
    icon.resize((512, 512), Image.LANCZOS).save(OUT_RESOURCES / "icon.png")
    icon.resize((32, 32), Image.LANCZOS).save(OUT_RESOURCES / "tray-icon.png")
    icon.save(OUT_ICONS / "icon.ico", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    icon.save(OUT_ICONS / "icon.icns")
    for size in (16, 32, 64, 128, 256):
        icon.resize((size, size), Image.LANCZOS).save(Path("design") / f"preview-{size}.png")
    print("icons written")


if __name__ == "__main__":
    main()
