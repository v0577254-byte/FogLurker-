import random, sys
from PIL import Image

out = sys.argv[1]
random.seed(1337)
W = H = 64
skin = Image.new("RGBA", (W, H), (0, 0, 0, 0))
eyes = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = skin.load()
ex = eyes.load()

def shade(base=16, spread=10):
    v = base + random.randint(-spread // 2, spread)
    return (v, v, v + 3, 255)

# Base (non-overlay) regions of the 64x64 player skin layout.
base_regions = [
    (0, 0, 32, 16),    # head
    (0, 16, 56, 32),   # right leg, body, right arm
    (16, 48, 48, 64),  # left leg, left arm
]
for x0, y0, x1, y1 in base_regions:
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[x, y] = shade()

# Head layout cells are 8x8; the front face is (8,8)-(16,16).
# Bottom of the head / top unchanged. Face: hollow, too-tall eyes and a long thin mouth.
face = [(x, y) for x in range(8, 16) for y in range(8, 16)]
for x, y in face:
    px[x, y] = shade(9, 6)

eye_px = [(9, 10), (9, 11), (9, 12), (10, 11), (10, 12),
          (14, 10), (14, 11), (14, 12), (13, 11), (13, 12)]
for x, y in eye_px:
    px[x, y] = (205, 210, 220, 255)
    ex[x, y] = (235, 240, 255, 255)
# pupils: pin points
for x, y in [(9, 12), (14, 12)]:
    px[x, y] = (255, 255, 255, 255)
    ex[x, y] = (255, 255, 255, 255)
# mouth: stretched slit
for x in range(9, 15):
    px[x, 14] = (38, 4, 6, 255)
px[8, 13] = (30, 4, 5, 255)
px[15, 13] = (30, 4, 5, 255)

# Ribs on the body front (20,20)-(28,32): faint lighter streaks.
for y in (22, 24, 26):
    for x in range(21, 27):
        px[x, y] = (30, 30, 34, 255)
# Long dark fingers at the bottom of the arms.
for x in range(44, 48):
    for y in range(29, 32):
        px[x, y] = (6, 6, 7, 255)
for x in range(36, 40):
    for y in range(61, 64):
        px[x, y] = (6, 6, 7, 255)

# Wisps of fog on the jacket overlay (16,32)-(40,48), mostly transparent.
for _ in range(40):
    x = random.randint(16, 39)
    y = random.randint(36, 47)
    g = random.randint(40, 70)
    px[x, y] = (g, g, g + 6, random.randint(60, 120))

skin.save(out + "/fog_lurker.png")
eyes.save(out + "/fog_lurker_eyes.png")
print("ok")
