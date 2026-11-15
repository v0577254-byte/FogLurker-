"""Draws the Fog Lurker split head: the creature's face on the left, a Minecraft-style character on the
right, torn apart down the middle. Also used as the mod logo.

Usage: python3 tools/make_split_head.py out.png
"""
import random
import sys
from PIL import Image, ImageDraw, ImageFilter

random.seed(7)
OUT = sys.argv[1]
SIZE = 512
CELL = 22
OX, OY = 80, 30          # where the 16x16 face starts on the canvas
COLS, ROWS = 16, 20      # the creature's jaw hangs 4 rows below the chin

# palette
PALE = (226, 221, 212)
PALE_SHADE = (198, 193, 186)
GRAY_L = (168, 165, 160)
GRAY = (128, 126, 122)
GRAY_D = (82, 81, 79)
BLOOD = (150, 8, 14)
BLOOD_B = (192, 16, 24)
BLOOD_D = (86, 3, 7)
TOOTH = (242, 240, 230)
WHITE = (255, 255, 255)
SKIN = (222, 170, 132)
SKIN_SHADE = (198, 146, 110)
SKIN_HI = (234, 186, 150)
HAIR = (48, 33, 24)
HAIR_HI = (78, 54, 38)
IRIS = (54, 132, 72)
PUPIL = (26, 52, 30)
LIP = (158, 84, 74)
BLUSH = (232, 150, 128)
BROW = (40, 28, 20)

grid = [[None] * COLS for _ in range(ROWS)]


def jitter(c, n=6):
    d = random.randint(-n, n)
    return tuple(max(0, min(255, v + d)) for v in c)


# ---------------------------------------------------------------- right half: an ordinary player
for r in range(16):
    for c in range(8, 16):
        grid[r][c] = jitter(SKIN, 5)
for r in range(0, 4):
    for c in range(8, 16):
        grid[r][c] = jitter(HAIR_HI if random.random() < 0.25 else HAIR, 5)
for c in (12, 13, 14, 15):                    # a side-swept fringe
    grid[4][c] = jitter(HAIR, 4)
grid[5][15] = jitter(HAIR, 4)
grid[5][14] = jitter(HAIR, 4)
for r in range(4, 9):
    grid[r][15] = jitter(HAIR, 4)              # sideburn
grid[7][11] = BROW
grid[7][12] = BROW
grid[8][11] = PUPIL                            # eye: iris toward the nose, white outside
grid[8][12] = WHITE
grid[9][11] = IRIS
grid[9][12] = WHITE
grid[10][8] = SKIN_SHADE                       # nose shadow
grid[11][8] = SKIN_SHADE
grid[10][9] = SKIN_HI
grid[11][13] = BLUSH
for c in (8, 9, 10):
    grid[13][c] = LIP                          # mouth
grid[13][11] = SKIN_SHADE
for c in range(8, 15):
    grid[15][c] = jitter(SKIN_SHADE, 4)        # chin shadow

# ---------------------------------------------------------------- left half: the thing underneath
for r in range(16):
    for c in range(0, 8):
        grid[r][c] = jitter(PALE, 6)
for r in range(0, 4):                          # bald scalp with grey veins
    for c in range(0, 8):
        if random.random() < 0.18:
            grid[r][c] = jitter(GRAY_L, 5)
grid[1][2] = GRAY
grid[2][3] = GRAY
grid[3][3] = GRAY_L
grid[2][6] = GRAY_L
for c in range(1, 7):
    grid[4][c] = jitter(GRAY_L, 4)             # heavy brow
for r in range(5, 11):                         # sunken grey socket...
    for c in range(2, 7):
        grid[r][c] = jitter(GRAY_D, 4)
for r in range(6, 10):                         # ...and a huge, pure white eye
    for c in range(3, 6):
        grid[r][c] = WHITE
for r in (10, 11):
    grid[r][1] = GRAY                          # cheekbone
    grid[r][7] = GRAY_L

# The mouth: a vertical maw that splits the face and keeps going past the chin.
for r in range(12, ROWS):
    for c in range(0, 8):
        grid[r][c] = jitter(PALE, 6) if r < 19 else None
for r in range(12, 19):
    for c in range(2, 8):
        grid[r][c] = jitter(BLOOD if c > 3 else BLOOD_D, 8)
for r in range(13, 18):
    grid[r][5] = jitter(BLOOD_D, 4)            # throat
    grid[r][6] = jitter(BLOOD_D, 4)
for c in range(2, 8):                          # upper teeth, tips bloody
    grid[12][c] = TOOTH if c % 2 == 0 else jitter(BLOOD_B, 6)
for r in range(13, 18):                        # teeth down the side of the jaw
    grid[r][2] = TOOTH if r % 2 == 1 else jitter(BLOOD_B, 6)
for c in range(2, 8):                          # lower teeth
    grid[18][c] = TOOTH if c % 2 == 1 else jitter(BLOOD, 6)
for r in range(12, 19):
    grid[r][0] = jitter(PALE_SHADE, 5)         # jaw outline
    grid[r][1] = jitter(GRAY_L if r % 3 == 0 else PALE, 5)
for c in range(1, 8):
    grid[19][c] = jitter(PALE_SHADE, 5)        # bottom of the hanging jaw
grid[19][0] = None

# ---------------------------------------------------------------- the tear down the middle
seam = [8, 7, 8, 8, 7, 9, 8, 7, 8, 8, 9, 8, 8, 8, 7, 8]
for r in range(16):
    s = seam[r]
    grid[r][s] = jitter(BLOOD_D if r % 3 else BLOOD, 6)      # torn flesh
    if s - 1 >= 0 and random.random() < 0.6:
        grid[r][s - 1] = jitter(GRAY, 5)                     # grey crack on the creature side
    if s + 1 < 16 and random.random() < 0.5 and grid[r][s + 1] not in (PUPIL, WHITE, IRIS):
        # pale skin creeping into the human side
        grid[r][s + 1] = tuple((a + b) // 2 for a, b in zip(PALE, grid[r][s + 1]))
for (r, c) in [(2, 10), (5, 9), (9, 10), (14, 10)]:
    grid[r][c] = jitter(PALE, 4)                             # infection spots
for (r, c) in [(1, 6), (6, 7), (8, 6), (15, 6)]:
    if grid[r][c] not in (WHITE,):
        grid[r][c] = jitter(SKIN_SHADE, 4)                   # flaps of the stolen skin still clinging on
for (r, c) in [(3, 10), (7, 10), (11, 9), (12, 11)]:
    grid[r][c] = tuple((a * 2 + b) // 3 for a, b in zip(grid[r][c], GRAY_L))   # grey veins spreading
grid[13][7] = BLOOD_B                                        # the human mouth tears into the maw
grid[13][8] = BLOOD
grid[14][9] = BLOOD_D                                        # blood running down the chin
grid[15][9] = BLOOD
grid[16][9] = BLOOD_B                                        # dripping off the chin

# ---------------------------------------------------------------- render
img = Image.new("RGB", (SIZE, SIZE))
draw = ImageDraw.Draw(img)
for y in range(SIZE):                                        # foggy night background
    t = y / SIZE
    col = tuple(int(a + (b - a) * t) for a, b in zip((44, 48, 58), (12, 13, 16)))
    draw.line([(0, y), (SIZE, y)], fill=col)
fog = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
fd = ImageDraw.Draw(fog)
for _ in range(26):
    x, y = random.randint(-60, SIZE), random.randint(SIZE // 3, SIZE)
    w, h = random.randint(120, 260), random.randint(30, 70)
    fd.ellipse([x, y, x + w, y + h], fill=(210, 215, 225, random.randint(14, 34)))
fog = fog.filter(ImageFilter.GaussianBlur(18))
img.paste(fog, (0, 0), fog)

shadow = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
sd = ImageDraw.Draw(shadow)
sd.rectangle([OX + 10, OY + 14, OX + COLS * CELL + 10, OY + 16 * CELL + 14], fill=(0, 0, 0, 150))
sd.rectangle([OX + 10, OY + 14, OX + 8 * CELL + 10, OY + ROWS * CELL + 14], fill=(0, 0, 0, 150))
shadow = shadow.filter(ImageFilter.GaussianBlur(10))
img.paste(shadow, (0, 0), shadow)

for r in range(ROWS):
    for c in range(COLS):
        col = grid[r][c]
        if col is None:
            continue
        x0, y0 = OX + c * CELL, OY + r * CELL
        draw.rectangle([x0, y0, x0 + CELL - 1, y0 + CELL - 1], fill=col)
        # a little bevel so it reads as blocky pixels
        hi = tuple(min(255, v + 10) for v in col)
        lo = tuple(max(0, v - 14) for v in col)
        draw.line([(x0, y0), (x0 + CELL - 1, y0)], fill=hi)
        draw.line([(x0, y0 + CELL - 1), (x0 + CELL - 1, y0 + CELL - 1)], fill=lo)

img.save(OUT)
print("ok")
