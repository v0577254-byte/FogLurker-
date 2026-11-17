"""Draws the Fog Lurker item textures (16x16, the mist ones animated). Run: python3 tools/make_item_textures.py"""
import json
import math
import os
import random

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "foglurker", "textures", "item")
FRAMES = 8


def blank():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def put(im, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        im.putpixel((x, y), c)


def save_animated(name, frames, frametime=3):
    sheet = Image.new("RGBA", (16, 16 * len(frames)), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        sheet.paste(f, (0, 16 * i))
    sheet.save(os.path.join(OUT, name + ".png"))
    with open(os.path.join(OUT, name + ".png.mcmeta"), "w") as fh:
        json.dump({"animation": {"frametime": frametime, "interpolate": True}}, fh, indent=2)


def mist(im, solid, frame, color, strength, reach=2, seed=0):
    """Soft, drifting mist on the empty pixels near the solid ones."""
    rnd = random.Random(seed)
    jitter = {(x, y): rnd.random() * 6.28 for x in range(16) for y in range(16)}
    t = frame / FRAMES * 2 * math.pi
    for y in range(16):
        for x in range(16):
            if (x, y) in solid:
                continue
            d = min((abs(x - sx) + abs(y - sy) for sx, sy in solid), default=99)
            if d > reach:
                continue
            wave = 0.5 + 0.5 * math.sin(t + jitter[(x, y)] + (x - y) * 0.7)
            a = int(strength * wave * (1.0 if d == 1 else 0.55))
            if a > 18:
                put(im, x, y, color + (a,))


# ---------------------------------------------------------------- The Protector of the Fog
def sword_pixels():
    """A spiky black, grey and white sword along the anti-diagonal, tip top-right.

    Works in (s, u) = (x + y, x - y): the blade lies along s = 15, u runs from the pommel (-15) to the tip (15).
    """
    K, D, G, L, W = (16, 16, 20, 255), (56, 58, 66, 255), (116, 120, 130, 255), (188, 192, 202, 255), (246, 247, 251, 255)
    up_spikes = {-1, 3, 7, 11}  # saw teeth on the upper edge
    down_spikes = {1, 5, 9}     # and on the lower edge, staggered
    px = {}
    for y in range(16):
        for x in range(16):
            s_, u = x + y, x - y
            d = s_ - 15            # across the blade: <0 upper-left edge, >0 lower-right edge
            c = None
            if -3 <= u <= 15:      # blade
                half = 1 if u <= 10 else (1 if u == 11 else 0)
                if u >= 14 and d == 0:
                    c = W          # the point
                elif abs(d) <= half:
                    c = {-1: L, 0: W, 1: G}[d]
                elif abs(d) == half + 1 and u <= 12:
                    c = K          # outline
                # Barbs: a base on the edge and a white point leaning toward the tip. (On a diagonal only
                # every other pixel lines up, so the point sits one step further along.)
                if (u in up_spikes and d == -2) or (u - 1 in up_spikes and d == -3):
                    c = L if d == -2 else W
                if (u in down_spikes and d == 2) or (u - 1 in down_spikes and d == 3):
                    c = G if d == 2 else W
            elif u in (-4, -5):    # a jagged guard of teeth across the blade
                if abs(d) <= 5:
                    c = W if abs(d) >= 4 else (D if u == -4 else K)
            elif -13 <= u <= -6:   # wrapped grip
                if d == 0:
                    c = G if u % 4 == 0 else D
                elif abs(d) == 1:
                    c = K
            elif u <= -14 and d == 0:
                c = W              # spiked pommel
            if c:
                px[(x, y)] = c
    return px


def make_sword():
    base = sword_pixels()
    frames = []
    for f in range(FRAMES):
        im = blank()
        mist(im, base, f, (206, 214, 228), 120, reach=1, seed=3)
        for (x, y), c in base.items():
            put(im, x, y, c)
        frames.append(im)
    save_animated("protector_of_the_fog", frames, frametime=4)


# ---------------------------------------------------------------- The Fog of the Day / Night
def make_fog(name, base, deep, glint, glint_pos, seed):
    frames = []
    for f in range(FRAMES):
        im = blank()
        t = f / FRAMES * 2 * math.pi
        blobs = [(5 + math.cos(t) * 0.8, 9 + math.sin(t) * 0.6, 4.0), (9 + math.sin(t) * 0.7, 7, 4.6),
                 (11, 10 + math.cos(t) * 0.7, 3.5), (7, 11, 3.3)]
        rnd = random.Random(seed + f)
        for y in range(16):
            for x in range(16):
                d = min(math.hypot(x - cx, y - cy) / r for cx, cy, r in blobs)
                if d < 1:
                    k = 1 - d
                    swirl = 0.5 + 0.5 * math.sin(t * 2 + x * 0.9 - y * 0.6)
                    c = tuple(int(deep[i] + (base[i] - deep[i]) * (0.35 + 0.65 * k * (0.7 + 0.3 * swirl))) for i in range(3))
                    a = int(110 + 140 * k)
                    if rnd.random() < 0.06:
                        c = tuple(min(255, v + 24) for v in c)
                    put(im, x, y, c + (a,))
        tw = 0.5 + 0.5 * math.sin(t * 2)
        for (x, y) in glint_pos:
            put(im, x, y, glint + (int(150 + 105 * tw),))
        frames.append(im)
    save_animated(name, frames, frametime=3)


# ---------------------------------------------------------------- the rest
def make_teeth():
    im = blank()
    gum, gum_dark = (132, 24, 34, 255), (88, 12, 22, 255)
    for x in range(1, 15):
        put(im, x, 3, gum_dark)
        put(im, x, 4, gum)
        put(im, x, 5, gum if x % 3 else gum_dark)
    for i, x in enumerate(range(2, 14, 2)):
        length = [8, 5, 9, 6, 8, 5][i]
        for y in range(6, 6 + length):
            taper = y > 6 + length - 3
            put(im, x, y, (238, 234, 218, 255))
            if not taper:
                put(im, x + 1, y, (196, 190, 172, 255))
        put(im, x, 6 + length, (160, 154, 138, 255))
    put(im, 5, 9, (120, 16, 24, 255))  # a fleck of something
    im.save(os.path.join(OUT, "teeth_of_the_lurker.png"))


def make_ingot():
    im = blank()
    rows = [(4, 5, 11), (3, 6, 12), (2, 7, 13), (2, 8, 13), (2, 9, 13), (3, 10, 12)]
    for x0, y, x1 in rows:
        for x in range(x0, x1 + 1):
            c = (96, 100, 110, 255) if y < 8 else (64, 68, 78, 255)
            if x in (x0, x1) or y == 10:
                c = (30, 32, 38, 255)
            put(im, x, y, c)
    for x, y in [(4, 6), (5, 6), (6, 6), (5, 5), (3, 7)]:
        put(im, x, y, (214, 222, 234, 255))
    for x, y in [(10, 3), (11, 2), (12, 3), (13, 2), (9, 2), (12, 11), (13, 12)]:
        put(im, x, y, (206, 214, 226, 120))
    im.save(os.path.join(OUT, "ingot_of_the_fog.png"))


def make_stick():
    im = blank()
    for i in range(11):
        x, y = 3 + i, 13 - i
        put(im, x, y, (78, 80, 88, 255))
        put(im, x + 1, y, (44, 46, 52, 255))
    for x, y in [(4, 10), (5, 9), (9, 5), (10, 4), (12, 2), (13, 3), (7, 10), (8, 11), (2, 12)]:
        put(im, x, y, (210, 216, 226, 150))
    im.save(os.path.join(OUT, "foggy_stick.png"))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    make_sword()
    make_fog("fog_of_the_day", (236, 238, 232), (176, 184, 194), (255, 214, 96), [(12, 4), (11, 5), (12, 5), (13, 5), (12, 6)], 11)
    make_fog("fog_of_the_night", (92, 104, 148), (34, 38, 64), (232, 238, 255), [(12, 3), (13, 4), (12, 5), (5, 7)], 29)
    make_teeth()
    make_ingot()
    make_stick()
    print("item textures written to", os.path.normpath(OUT))
