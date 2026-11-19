"""Draws the fog wisp texture: four soft, ragged wisps on one 128x128 sheet. Run: python3 tools/make_fog_particles.py"""
import math
import os
import random

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "foglurker", "textures", "misc")
SIZE = 64


def wisp(seed):
    rnd = random.Random(seed)
    blobs = [(rnd.uniform(18, 46), rnd.uniform(22, 42), rnd.uniform(8, 16)) for _ in range(rnd.randint(4, 7))]
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = im.load()
    for y in range(SIZE):
        for x in range(SIZE):
            d = 0.0
            for bx, by, r in blobs:
                d += math.exp(-((x - bx) ** 2 + (y - by) ** 2) / (2 * r * r))
            # Soft edge toward the border so no square ever shows.
            edge = min(x, y, SIZE - 1 - x, SIZE - 1 - y) / 10.0
            a = min(1.0, d * 0.55) * min(1.0, edge) * (0.85 + 0.15 * rnd.random())
            px[x, y] = (255, 255, 255, int(max(0.0, min(1.0, a)) * 255))
    return im


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    sheet = Image.new("RGBA", (SIZE * 2, SIZE * 2), (0, 0, 0, 0))
    for i in range(4):
        sheet.paste(wisp(1000 + i), ((i % 2) * SIZE, (i // 2) * SIZE))
    sheet.save(os.path.join(OUT, "fog_wisps.png"))
    print("wrote", os.path.join(OUT, "fog_wisps.png"))
