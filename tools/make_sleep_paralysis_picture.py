"""Cuts the crawler out of a showcase portrait (run/screenshots/foglurker_portrait.png, taken with
./gradlew runClient -PfoglurkerShowcase -PfoglurkerScene=portrait) for the faint picture shown in bed.
Run: python3 tools/make_sleep_paralysis_picture.py"""
import os

from PIL import Image, ImageFilter

HERE = os.path.dirname(__file__)
SRC = os.path.join(HERE, "..", "run", "screenshots", "foglurker_portrait.png")
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "foglurker", "textures", "gui", "sleep_paralysis.png")

im = Image.open(SRC).convert("RGB")
w, h = im.size
px = im.load()
cut = Image.new("RGBA", (w, h), (0, 0, 0, 0))
out = cut.load()
for y in range(h):
    sky = px[8, y]  # the sky at this height (it changes from top to bottom)
    for x in range(w):
        r, g, b = px[x, y]
        diff = abs(r - sky[0]) + abs(g - sky[1]) + abs(b - sky[2])
        a = max(0, min(255, (diff - 18) * 4))
        out[x, y] = (r, g, b, a)
box = cut.getbbox()
pad = 24
box = (max(0, box[0] - pad), max(0, box[1] - pad), min(w, box[2] + pad), min(h, box[3] + pad))
cut = cut.crop(box)
side = max(cut.size)
square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
square.paste(cut, ((side - cut.width) // 2, (side - cut.height) // 2))
square = square.resize((256, 256), Image.LANCZOS)
# A slightly soft, dream-like edge.
alpha = square.split()[3].filter(ImageFilter.GaussianBlur(1.2))
square.putalpha(alpha)
square.save(OUT)
print("wrote", OUT)
