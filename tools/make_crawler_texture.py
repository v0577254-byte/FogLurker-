"""Generates the crawler (attack form) texture. Layout matches CrawlerModel (128x64).

Usage: python3 tools/make_crawler_texture.py src/main/resources/assets/foglurker/textures/entity
"""
import random
import sys
from PIL import Image

OUT = sys.argv[1]
random.seed(4242)
W, H = 128, 64
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
eyes = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px, ex = img.load(), eyes.load()

PALE = (226, 221, 212)
LIGHT_GRAY = (172, 169, 164)
GRAY = (132, 130, 126)
DARK_GRAY = (88, 87, 85)
CLAW = (44, 42, 42)
BLOOD = (150, 8, 14)
BLOOD_BRIGHT = (186, 14, 22)
BLOOD_DARK = (92, 4, 8)
TOOTH = (240, 238, 228)
EYE = (255, 255, 255)


def jitter(c, amount=7):
    d = random.randint(-amount, amount)
    return tuple(max(0, min(255, v + d)) for v in c) + (255,)


def fill(x, y, w, h, color, noise=7):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            px[xx, yy] = jitter(color, noise)


def faces(u, v, w, h, d):
    """Box UV faces: name -> (x, y, width, height)."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "side_a": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "side_b": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def paint_box(u, v, w, h, d, color=PALE, noise=7):
    for (x, y, fw, fh) in faces(u, v, w, h, d).values():
        fill(x, y, fw, fh, color, noise)


def gradient_bottom(u, v, w, h, d, rows, color):
    """Grey accents creeping up from the joint end of a limb."""
    f = faces(u, v, w, h, d)
    for name in ("side_a", "front", "side_b", "back"):
        x, y, fw, fh = f[name]
        for i in range(rows):
            for xx in range(x, x + fw):
                if random.random() < 0.85 - i * 0.15:
                    px[xx, y + fh - 1 - i] = jitter(color, 6)
    x, y, fw, fh = f["bottom"]
    fill(x, y, fw, fh, color, 6)


# ---- body 9x6x16 @ (0,0)
paint_box(0, 0, 9, 6, 16)
f = faces(0, 0, 9, 6, 16)
x, y, fw, fh = f["top"]
for yy in range(y, y + fh):
    px[x + 4, yy] = jitter(GRAY)                      # spine shadow
    if (yy - y) % 3 == 0:                              # ribs showing through
        for xx in (x, x + 1, x + fw - 2, x + fw - 1):
            px[xx, yy] = jitter(LIGHT_GRAY)
for side in ("side_a", "side_b"):
    x, y, fw, fh = f[side]
    for xx in range(x, x + fw):
        if (xx - x) % 3 == 1:
            for yy in range(y, y + 4):
                px[xx, yy] = jitter(LIGHT_GRAY if yy - y < 3 else GRAY)
x, y, fw, fh = f["bottom"]
fill(x, y, fw, fh, (234, 230, 222), 5)
for yy in range(y, y + fh):
    px[x + 4, yy] = jitter(LIGHT_GRAY)
x, y, fw, fh = f["front"]
for xx in range(x + 1, x + fw - 1):
    px[xx, y + 1] = jitter(GRAY)                       # collarbones

# ---- spine bumps 2x2x3 @ (50,0)
paint_box(50, 0, 2, 2, 3, GRAY, 6)
x, y, fw, fh = faces(50, 0, 2, 2, 3)["top"]
fill(x, y, fw, fh, LIGHT_GRAY, 6)

# ---- neck 5x5x6 @ (0,22)
paint_box(0, 22, 5, 5, 6)
for name, (x, y, fw, fh) in faces(0, 22, 5, 5, 6).items():
    if name in ("side_a", "side_b", "bottom"):
        for yy in range(y, y + fh):
            px[x + fw // 2, yy] = jitter(LIGHT_GRAY)   # tendons

# ---- skull 8x7x9 @ (64,0)
paint_box(64, 0, 8, 7, 9)
f = faces(64, 0, 8, 7, 9)
x, y, fw, fh = f["top"]
for yy in range(y, y + fh):
    px[x + 3, yy] = jitter(LIGHT_GRAY)
for _ in range(10):                                    # veins
    vx, vy = random.randint(x, x + fw - 1), random.randint(y, y + fh - 1)
    px[vx, vy] = jitter(GRAY)
x, y, fw, fh = f["front"]
for xx in range(x, x + fw):
    px[xx, y] = jitter(GRAY)                           # heavy brow
for cy in (1, 2, 3):
    for cx in (1, 2, 5, 6):
        px[x + cx, y + cy] = EYE + (255,)              # pure white eyes, too tall
        ex[x + cx, y + cy] = EYE + (255,)
    for cx in (0, 3, 4, 7):
        px[x + cx, y + cy] = jitter(DARK_GRAY, 5)      # sunken grey sockets
for yy in range(y + 4, y + fh):
    for xx in range(x, x + fw):
        px[xx, yy] = jitter(BLOOD_DARK if yy >= y + 5 else BLOOD, 6)   # throat behind the teeth
for side in ("side_a", "side_b"):
    x, y, fw, fh = f[side]
    for xx in range(x + 2, x + 6):
        px[xx, y + 1] = jitter(LIGHT_GRAY)             # temples
    for xx in range(x, x + fw):
        px[xx, y + fh - 1] = jitter(GRAY)
x, y, fw, fh = f["bottom"]
fill(x, y, fw, fh, BLOOD_DARK, 6)

# ---- snout / upper jaw 6x3x7 @ (64,16)
paint_box(64, 16, 6, 3, 7)
f = faces(64, 16, 6, 3, 7)
x, y, fw, fh = f["top"]
for yy in (y + fh - 2, y + fh - 1):
    px[x + 1, yy] = jitter(DARK_GRAY, 4)               # nostril slits
    px[x + fw - 2, yy] = jitter(DARK_GRAY, 4)
for xx in range(x, x + fw):
    px[xx, y] = jitter(LIGHT_GRAY)
x, y, fw, fh = f["bottom"]
fill(x, y, fw, fh, BLOOD, 8)                           # roof of the mouth
for yy in range(y, y + fh, 2):
    for xx in range(x + 1, x + fw - 1):
        px[xx, yy] = jitter(BLOOD_DARK, 5)
for name in ("front", "side_a", "side_b"):
    x, y, fw, fh = f[name]
    for xx in range(x, x + fw):
        px[xx, y + fh - 1] = jitter(BLOOD_BRIGHT, 8)   # bloody gums
        px[xx, y + 1] = jitter(LIGHT_GRAY, 6)

# ---- lower jaw 6x2x10 @ (0,34)
paint_box(0, 34, 6, 2, 10)
f = faces(0, 34, 6, 2, 10)
x, y, fw, fh = f["top"]
fill(x, y, fw, fh, BLOOD, 8)
for yy in range(y, y + fh):
    px[x + 2, yy] = jitter(BLOOD_DARK, 5)              # tongue groove
    px[x + 3, yy] = jitter(BLOOD_DARK, 5)
for _ in range(6):
    px[random.randint(x, x + fw - 1), random.randint(y, y + fh - 1)] = jitter(BLOOD_BRIGHT, 6)
for name in ("front", "side_a", "side_b"):
    x, y, fw, fh = f[name]
    for xx in range(x, x + fw):
        px[xx, y] = jitter(BLOOD_BRIGHT, 8)
        px[xx, y + 1] = jitter(LIGHT_GRAY, 6)
x, y, fw, fh = f["back"]
fill(x, y, fw, fh, BLOOD_DARK, 5)
x, y, fw, fh = f["bottom"]
for xx in range(x, x + fw):
    px[xx, y + fh // 2] = jitter(GRAY)


# ---- teeth
def tooth(u, v, h, bloody, tip_down):
    """1 x h x 1 tooth. Upper teeth point down (tip = last row), lower teeth point up (tip = first row)."""
    f = faces(u, v, 1, h, 1)
    for name in ("side_a", "front", "side_b", "back"):
        x, y, _, fh = f[name]
        for i in range(fh):
            tip = (i == fh - 1) if tip_down else (i == 0)
            px[x, y + i] = jitter(BLOOD_BRIGHT if bloody and tip else TOOTH, 4)
    for name in ("top", "bottom"):
        x, y, _, _ = f[name]
        tip_face = (name == "bottom") if tip_down else (name == "top")
        px[x, y] = jitter(BLOOD if bloody and tip_face else TOOTH, 4)


tooth(100, 0, 2, False, True)    # upper, clean
tooth(104, 0, 2, True, True)     # upper, bloody tip
tooth(108, 0, 3, True, True)     # fang, bloody
tooth(100, 4, 2, False, False)   # lower, clean
tooth(104, 4, 2, True, False)    # lower, bloody tip
# a drip running down the fang
px[109, 2] = jitter(BLOOD, 4)

# ---- limbs
paint_box(32, 34, 3, 13, 3)                            # upper arm
gradient_bottom(32, 34, 3, 13, 3, 3, GRAY)             # elbow
for name, (x, y, fw, fh) in faces(32, 34, 3, 13, 3).items():
    if name == "top":
        fill(x, y, fw, fh, LIGHT_GRAY, 5)
paint_box(44, 34, 2, 13, 2)                            # forearm
gradient_bottom(44, 34, 2, 13, 2, 5, GRAY)             # wrist
for name, (x, y, fw, fh) in faces(44, 34, 2, 13, 2).items():
    if name not in ("top", "bottom"):
        for xx in range(x, x + fw):
            px[xx, y] = jitter(GRAY)                   # knobbly elbow
paint_box(52, 34, 3, 11, 4)                            # thigh
gradient_bottom(52, 34, 3, 11, 4, 2, GRAY)             # knee
paint_box(66, 34, 2, 13, 2)                            # shin
gradient_bottom(66, 34, 2, 13, 2, 5, GRAY)             # ankle
paint_box(22, 22, 3, 1, 4, GRAY, 6)                    # hands / feet
x, y, fw, fh = faces(22, 22, 3, 1, 4)["bottom"]
fill(x, y, fw, fh, DARK_GRAY, 5)
paint_box(36, 22, 1, 1, 4, DARK_GRAY, 5)               # fingers / toes
fx, fy, _, _ = faces(36, 22, 1, 1, 4)["front"]
px[fx, fy] = CLAW + (255,)                             # claw tips

img.save(OUT + "/crawler.png")
eyes.save(OUT + "/crawler_eyes.png")
print("ok")
