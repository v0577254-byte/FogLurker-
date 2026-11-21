"""Ingot of the Fog: a diagonal bar like a netherite ingot, purple with grey accents, in a little fog."""
import math
from PIL import Image

W = H = 16
# palette
OUT = (34, 32, 40, 255)        # dark grey outline
G1 = (88, 86, 98, 255)         # grey accent (ends, under-rim)
G2 = (138, 136, 150, 255)      # light grey accent (bevel rim)
P0 = (52, 24, 74, 255)         # deepest purple (shadow)
P1 = (78, 38, 110, 255)        # side face purple
P2 = (112, 58, 156, 255)       # top face purple
P3 = (146, 92, 196, 255)       # top face light
P4 = (186, 140, 228, 255)      # highlight
FOG = (210, 216, 226, 150)     # like the Foggy Stick
FOG2 = (226, 230, 240, 90)

a = (3.2, 10.6)   # bar start (lower left)
b = (12.4, 4.4)   # bar end (upper right)
half = 2.15        # half width of the top face
drop = 2           # how far the side face shows below


def seg_dist(px, py):
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)
    tc = max(0.0, min(1.0, t))
    cx, cy = ax + tc * dx, ay + tc * dy
    return math.hypot(px - cx, py - cy), t


def top(x, y):
    d, t = seg_dist(x + 0.5, y + 0.5)
    return d <= half and -0.02 <= t <= 1.02


def side(x, y):
    return not top(x, y) and any(top(x, y - k) for k in range(1, drop + 1))


img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()
shape = {}
for y in range(H):
    for x in range(W):
        if top(x, y):
            shape[(x, y)] = "top"
        elif side(x, y):
            shape[(x, y)] = "side"

for (x, y), kind in shape.items():
    _, t = seg_dist(x + 0.5, y + 0.5)
    if kind == "top":
        below_is_side = shape.get((x, y + 1)) == "side"
        above_empty = (x, y - 1) not in shape
        if below_is_side:
            c = G2                       # the grey bevel rim along the front edge
        elif above_empty:
            c = P4 if 0.25 < t < 0.75 else P3   # light catching the back edge
        else:
            c = P2 if t < 0.55 else P3
    else:
        below_empty = (x, y + 1) not in shape
        c = P0 if below_empty else P1
    if t < 0.03 or t > 0.97:
        c = G1 if kind == "side" else G2  # grey end caps
    px[x, y] = c

# a dark grey outline round the whole bar
for y in range(H):
    for x in range(W):
        if (x, y) in shape:
            continue
        if any((x + dx, y + dy) in shape for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            px[x, y] = OUT

# fog, like the Foggy Stick: pale see-through wisps round it, and a little haze on it
for (x, y) in ((1, 5), (3, 3), (14, 9), (13, 12), (7, 14), (11, 1), (0, 10), (15, 6)):
    if px[x, y][3] == 0:
        px[x, y] = FOG
for (x, y) in ((7, 6), (10, 5), (5, 9)):
    r, g, b2, a2 = px[x, y]
    if a2:
        px[x, y] = tuple(int(c * 0.65 + f * 0.35) for c, f in zip((r, g, b2), FOG2[:3])) + (255,)

import sys
img.save(sys.argv[1])
img.resize((256, 256), Image.NEAREST).save(sys.argv[2])
