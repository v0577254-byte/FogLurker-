"""The diamond-made Armour of the Fog as worn: the same textures as the netherite-made one (and its 32 drifting-mist
frames), with the light-blue outline its item icons have, drawn round the edge of every face of the armour.

    python3 tools/diamond_armor_outline.py <armour texture folder>

Reads fog_layer_1.png / fog_layer_2.png (and fog_layer_N/frame_0..31.png) and writes fog_diamond_layer_N... beside them.
Needs Pillow.
"""
import os
import sys

from PIL import Image

LIGHT = (120, 215, 255, 255)  # the two blues of the diamond pieces' item outline
DARK = (70, 170, 230, 255)


def box(u, v, w, h, d):
    """The six faces of a model box on its texture (as Minecraft lays them out), and whether the face's own border is
    outlined: only the front and back are (so seen from in front or behind, each piece has an outline round it,
    without every edge of every box turning blue). Gaps in any face are outlined."""
    return [((u + d, v, u + d + w, v + d), False), ((u + d + w, v, u + d + 2 * w, v + d), False),  # top, bottom
            ((u, v + d, u + d, v + d + h), False), ((u + d, v + d, u + d + w, v + d + h), True),  # right, front
            ((u + d + w, v + d, u + 2 * d + w, v + d + h), False),
            ((u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h), True)]  # left, back


# The armour model: head, body, arm (both arms use it), leg (both legs use it).
FACES = box(0, 0, 8, 8, 8) + box(16, 16, 8, 12, 4) + box(40, 16, 4, 12, 4) + box(0, 16, 4, 12, 4)


def outline_mask(base):
    """The pixels on the edge of each face: on its border (front and back), or next to a gap in it."""
    w, h = base.size
    alpha = base.getchannel("A").load()
    mask = set()
    for (x0, y0, x1, y1), border in FACES:
        for y in range(y0, min(y1, h)):
            for x in range(x0, min(x1, w)):
                if alpha[x, y] == 0:
                    continue
                edge = border and (x in (x0, x1 - 1) or y in (y0, y1 - 1))
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if x0 <= nx < x1 and y0 <= ny < y1 and alpha[nx, ny] == 0:
                        edge = True
                if edge:
                    mask.add((x, y))
    return mask


def outlined(image, mask):
    out = image.convert("RGBA").copy()
    px = out.load()
    for x, y in mask:
        px[x, y] = DARK if (x + y) % 3 == 0 else LIGHT
    return out


def main(folder):
    for layer in (1, 2):
        base = Image.open(os.path.join(folder, f"fog_layer_{layer}.png")).convert("RGBA")
        mask = outline_mask(base)  # (one outline for every frame, so it does not flicker with the mist)
        outlined(base, mask).save(os.path.join(folder, f"fog_diamond_layer_{layer}.png"))
        frames = os.path.join(folder, f"fog_layer_{layer}")
        target = os.path.join(folder, f"fog_diamond_layer_{layer}")
        os.makedirs(target, exist_ok=True)
        n = 0
        while os.path.exists(os.path.join(frames, f"frame_{n}.png")):
            outlined(Image.open(os.path.join(frames, f"frame_{n}.png")), mask).save(os.path.join(target, f"frame_{n}.png"))
            n += 1
        print(f"layer {layer}: {len(mask)} outline pixels, {n} frames")


if __name__ == "__main__":
    main(sys.argv[1])
