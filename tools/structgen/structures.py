"""The six Fog Lurker structures, built block by block. Coordinates: x east, y up, z south."""
from builder import Template

LOOT = "foglurker:chests/"


def pick(t, *weighted):
    """weighted: (name, weight) pairs."""
    total = sum(w for _, w in weighted)
    r = t.rng.uniform(0, total)
    for name, w in weighted:
        r -= w
        if r <= 0:
            return name
    return weighted[-1][0]


# ===================================================================== lookout tower
def lookout_tower():
    """A tall, spindly ranger's tower: legs splayed at the foot, beams and rails between them, a landing halfway up,
    a cabin on a deck with lanterns hung under it, a gabled roof with an aerial on the ridge."""
    t = Template(9, 29, 9, seed=11)
    log_y = {"axis": "y"}
    stair = lambda f, half="bottom": {"facing": f, "half": half, "shape": "straight", "waterlogged": "false"}
    deck = 20
    # legs: splayed at the bottom (corner blocks), stepping in, then straight up to the deck
    for (x0, z0, x1, z1) in ((0, 0, 1, 1), (8, 0, 7, 1), (0, 8, 1, 7), (8, 8, 7, 7)):
        for y in range(0, 4):
            t.set(x0, y, z0, "spruce_log", log_y)
        t.set(x0, 4, z0, "spruce_log", log_y)
        t.set(x1, 4, z1, "spruce_log", log_y)
        for y in range(5, deck):
            t.set(x1, y, z1, "spruce_log", log_y)
        for y in (2, 9, 15):                                   # moss creeping up the legs
            side = "west" if x1 == 1 else "east"
            vx = x1 + (1 if x1 == 1 else -1)
            if t.get(vx, y, z1) is None:
                t.set(vx, y, z1, "vine", {"north": "false", "south": "false", "east": str(x1 == 7).lower(),
                                          "west": str(x1 == 1).lower(), "up": "false"})
    for y in range(0, deck):
        t.set(4, y, 4, "stripped_spruce_log", log_y)            # the ladder pole
    for y in range(1, deck + 1):
        t.set(4, y, 3, "ladder", {"facing": "north", "waterlogged": "false"})
    for y in (8, 15):                                           # beams between the legs
        for i in range(2, 7):
            t.set(i, y, 1, "stripped_spruce_log", {"axis": "x"})
            t.set(i, y, 7, "stripped_spruce_log", {"axis": "x"})
            t.set(1, y, i, "stripped_spruce_log", {"axis": "z"})
            t.set(7, y, i, "stripped_spruce_log", {"axis": "z"})
    for y in (5, 12, 18):                                       # rails
        for i in range(2, 7):
            for (x, z) in ((i, 1), (i, 7), (1, i), (7, i)):
                t.set(x, y, z, "spruce_fence")
    for i in range(1, 8):                                       # a low rail between the splayed feet
        for (x, z) in ((i, 0), (i, 8), (0, i), (8, i)):
            if t.get(x, 2, z) is None:
                t.set(x, 2, z, "spruce_fence")
    # a landing halfway up, round the ladder
    for x in range(3, 6):
        for z in range(2, 6):
            if (x, z) != (4, 3) and (x, z) != (4, 4):
                t.set(x, 11, z, "spruce_slab", {"type": "top", "waterlogged": "false"})
    for (x, z) in ((3, 2), (5, 2), (3, 5), (5, 5)):
        t.set(x, 12, z, "spruce_fence")
    # deck (a few planks rotted away), railing, lanterns hung beneath its corners
    for x in range(9):
        for z in range(9):
            if (x, z) == (4, 3):
                continue  # the ladder hole
            inner = 2 <= x <= 6 and 2 <= z <= 6
            corner = x in (0, 8) and z in (0, 8)
            if inner or corner or t.rng.random() < 0.75:
                t.set(x, deck, z, "spruce_planks")
    for (x, z) in ((0, 0), (8, 0), (0, 8), (8, 8)):
        t.set(x, deck - 1, z, "lantern", {"hanging": "true", "waterlogged": "false"})
    for i in range(9):
        for (x, z) in ((i, 0), (i, 8), (0, i), (8, i)):
            t.set(x, deck + 1, z, "spruce_fence")
    t.remove(4, deck + 1, 8)                                    # the gap in the railing by the door
    c = deck + 1
    for x, z in ((2, 2), (6, 2), (2, 6), (6, 6)):
        for y in range(c, c + 3):
            t.set(x, y, z, "stripped_spruce_log", log_y)
    windows = []
    for i in range(3, 6):
        for (x, z) in ((i, 2), (i, 6), (2, i), (6, i)):
            for y in range(c, c + 3):
                if y == c + 1:
                    windows.append((x, y, z))
                else:
                    t.set(x, y, z, "spruce_planks")
    t.rng.shuffle(windows)
    for k, (x, y, z) in enumerate(windows):
        t.set(x, y, z, "air" if k < 2 else "iron_bars" if k < 5 else "glass_pane")
    t.air(3, c, 3, 5, c + 2, 5)
    t.set(4, c, 6, "spruce_door", {"facing": "south", "half": "lower", "hinge": "left", "open": "false", "powered": "false"})
    t.set(4, c + 1, 6, "spruce_door", {"facing": "south", "half": "upper", "hinge": "left", "open": "false", "powered": "false"})
    for (x, z, f) in ((3, 1, "north"), (5, 1, "north"), (1, 3, "west"), (7, 5, "east"), (1, 5, "west"), (7, 3, "east")):
        t.set(x, c + 1, z, "spruce_trapdoor", {"facing": f, "half": "top", "open": "true", "powered": "false", "waterlogged": "false"})
    r = c + 3                                                   # the roof starts here
    for x in range(3, 6):                                       # flat ceiling inside, to hang the lantern
        for z in range(3, 6):
            t.set(x, r, z, "spruce_planks")
    for x in range(1, 8):
        t.set(x, r, 1, "dark_oak_stairs", stair("south"))
        t.set(x, r, 7, "dark_oak_stairs", stair("north"))
        t.set(x, r + 1, 2, "dark_oak_stairs", stair("south"))
        t.set(x, r + 1, 6, "dark_oak_stairs", stair("north"))
        t.set(x, r + 2, 3, "dark_oak_stairs", stair("south"))
        t.set(x, r + 2, 5, "dark_oak_stairs", stair("north"))
        t.set(x, r + 3, 4, "dark_oak_slab", {"type": "bottom", "waterlogged": "false"})
    for x in (2, 6):
        for (y, zs) in ((r, range(2, 7)), (r + 1, range(3, 6)), (r + 2, (4,))):
            for z in zs:
                if t.get(x, y, z) is None:
                    t.set(x, y, z, "dark_oak_planks")
    for x in (0, 8):                                            # eaves
        t.set(x, r, 1, "dark_oak_slab", {"type": "top", "waterlogged": "false"})
        t.set(x, r, 7, "dark_oak_slab", {"type": "top", "waterlogged": "false"})
    t.set(4, r + 4, 4, "lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})   # the aerial
    t.set(1, r + 4, 4, "iron_bars")
    # inside: a bed, the chest, the ranger's maps, the "radio", a lantern, webs in the corners
    t.bed(3, c, 4, "south", "red")
    t.chest(5, c, 5, LOOT + "lookout_tower", facing="west")
    t.set(3, c, 3, "cartography_table")
    t.set(5, c, 3, "note_block", {"instrument": "harp", "note": "0", "powered": "false"})
    t.set(5, c + 1, 3, "observer", {"facing": "west", "powered": "false"})
    t.set(4, c + 2, 4, "lantern", {"hanging": "true", "waterlogged": "false"})
    t.set(3, c + 2, 5, "cobweb")
    t.set(5, c + 2, 3, "cobweb")
    return t


# ===================================================================== abandoned campfire
STAIR = {"half": "bottom", "shape": "straight", "waterlogged": "false"}
SLAB = {"type": "bottom", "waterlogged": "false"}


def fence(**sides):
    p = {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"}
    p.update({k: "true" for k, v in sides.items() if v})
    return p


def abandoned_campfire():
    """A camp in a clearing: the fire, log benches, one tent still up and one fallen in, a drying rack, a lamp post
    left burning, a path in, and the bones of whoever was in the fallen tent."""
    t = Template(13, 6, 13, seed=22)
    for x in range(13):
        for z in range(13):
            d = ((x - 6) ** 2 + (z - 6) ** 2) ** 0.5
            if d <= 6.3:
                t.air(x, 1, z, x, 5, z)                        # the clearing
                # trampled ground, mostly path and gravel: trees can't take root in it, so the clearing stays one
                t.set(x, 0, z, pick(t, ("dirt_path", 6), ("gravel", 2), ("coarse_dirt", 1), ("packed_mud", 1))
                      if d > 2.2 else pick(t, ("dirt_path", 1), ("packed_mud", 1)))
    # the fire in a ring of stones, and the path to it from the south
    for (x, z) in ring(5, 5, 7, 7):
        t.set(x, 0, z, pick(t, ("cobblestone", 2), ("mossy_cobblestone", 1)))
    for (x, z) in ((5, 5), (7, 5), (5, 7), (7, 7)):
        t.set(x, 1, z, "cobblestone_slab" if t.rng.random() < 0.6 else "mossy_cobblestone_slab", SLAB)
    t.set(6, 0, 6, "coarse_dirt")
    t.set(6, 1, 6, "campfire", {"facing": "north", "lit": "false", "signal_fire": "false", "waterlogged": "false"})
    for z in range(8, 13):
        t.set(6, 0, z, pick(t, ("dirt_path", 3), ("gravel", 1), ("cobblestone", 1)))
        if t.rng.random() < 0.5:
            t.set(5 if z % 2 else 7, 0, z, pick(t, ("dirt_path", 2), ("gravel", 1)))
    # log benches round the fire, a stump to sit on, the pot
    for z in (5, 6, 7):
        t.set(3, 1, z, "spruce_log", {"axis": "z"})
    for x in (5, 6, 7):
        t.set(x, 1, 3, "spruce_log", {"axis": "x"})
    t.set(9, 1, 6, "stripped_spruce_log", {"axis": "y"})
    t.set(8, 1, 4, "cauldron")
    t.set(4, 1, 4, "decorated_pot", {"facing": "south", "cracked": "true", "waterlogged": "false"})
    # the tent still standing (south-east): an A-frame, closed at the back, the bed and the barrel inside
    for z in range(8, 13):
        for (x, y) in ((8, 1), (12, 1), (9, 2), (11, 2), (10, 3)):
            if (x, y, z) != (11, 2, 10):                       # torn
                t.set(x, y, z, pick(t, ("gray_wool", 3), ("brown_wool", 1), ("light_gray_wool", 1)))
    for (x, y) in ((9, 1), (10, 1), (11, 1), (10, 2)):
        t.set(x, y, 12, pick(t, ("gray_wool", 2), ("brown_wool", 1)))
    t.set(10, 1, 7, "spruce_fence", fence())                  # tent pegs and the front pole
    t.set(10, 2, 7, "spruce_fence", fence())
    t.set(10, 3, 7, "gray_wool")                               # the ridge runs out over it
    t.set(7, 1, 8, "tripwire_hook", {"facing": "west", "attached": "false", "powered": "false"})
    t.bed(10, 1, 9, "south", "brown")
    t.chest(9, 1, 11, LOOT + "abandoned_campfire", barrel=True)
    t.set(11, 1, 11, "brown_carpet")
    t.set(11, 1, 9, "brown_carpet")
    # the fallen tent (south-west): its canvas on the ground over what is left of someone
    for (x, z) in ((1, 8), (2, 8), (3, 8), (1, 9), (2, 9), (3, 9), (4, 9), (1, 10), (3, 10), (2, 11), (3, 11), (1, 11)):
        t.set(x, 1, z, pick(t, ("gray_carpet", 3), ("brown_carpet", 1), ("light_gray_carpet", 1)))
    t.set(2, 1, 10, "gray_wool")
    t.set(4, 1, 8, "spruce_fence", fence())                   # the pole that still stands
    t.set(4, 2, 8, "spruce_fence", fence())
    t.set(0, 1, 9, "spruce_log", {"axis": "z"})               # and the one that broke
    t.set(0, 1, 10, "spruce_log", {"axis": "z"})
    t.set(2, 1, 12, "bone_block", {"axis": "x"})
    t.set(4, 1, 11, "bone_block", {"axis": "z"})
    t.set(4, 1, 10, "skeleton_skull", {"rotation": "4"})
    # a drying rack (north-west), hides hung on it
    for x in (1, 4):
        t.set(x, 1, 1, "spruce_fence", fence())
        t.set(x, 2, 1, "spruce_fence", fence())
    for x in (1, 2, 3, 4):
        t.set(x, 3, 1, "spruce_slab", SLAB)
    for x in (2, 3):
        t.set(x, 2, 1, "spruce_trapdoor", {"facing": "north", "half": "top", "open": "true", "powered": "false", "waterlogged": "false"})
    t.set(2, 1, 1, "spruce_trapdoor", {"facing": "north", "half": "top", "open": "true", "powered": "false", "waterlogged": "false"})
    # a lamp post someone keeps lit, the woodpile and the chopping block (north-east)
    for y in (1, 2, 3):
        t.set(10, y, 3, "spruce_fence", fence())
    t.set(10, 4, 3, "lantern", {"hanging": "false", "waterlogged": "false"})
    for (x, y, z) in ((11, 1, 0), (12, 1, 0), (11, 1, 1), (12, 1, 1), (12, 2, 0), (12, 2, 1), (11, 2, 0)):
        t.set(x, y, z, "spruce_log", {"axis": "x"})
    t.set(9, 1, 1, "stripped_spruce_log", {"axis": "y"})
    t.set(8, 1, 1, "spruce_slab", SLAB)
    # the ward someone never finished
    t.set(0, 1, 6, "soul_lantern", {"hanging": "false", "waterlogged": "false"})
    t.set(1, 1, 3, "soul_torch")
    t.set(12, 1, 6, "soul_torch")
    for (x, z) in ((2, 5), (8, 9), (5, 2), (11, 5), (1, 7), (7, 11), (9, 4)):
        if t.get(x, 1, z) in (None, "minecraft:air"):
            t.set(x, 1, z, "moss_carpet")
    t.set(11, 2, 1, "cobweb")
    t.set(4, 3, 1, "cobweb")
    return t


# ===================================================================== hunters' bunker
def hunters_bunker():
    """A hunter's dugout, deep under the ground (13 blocks down from the grass, so burying it fills the earth in
    round it without heaping a hill over the way in): a stone-lined trench of twelve steps down to an arched door, a
    hearth whose chimney pokes out of the grass, and inside the bunk, the bench, the books - and the hole something
    tore in the wall. Template y 13 is the first air above the ground."""
    t = Template(11, 17, 23, seed=33)
    top = 12                                                   # the grass block's layer
    t.air(1, 1, 1, 9, 4, 9)
    for x in range(1, 10):
        for z in range(1, 10):
            t.set(x, 0, z, "spruce_planks" if t.rng.random() < 0.72 else pick(t, ("stone", 1), ("andesite", 1)))
            t.set(x, 5, z, pick(t, ("stone", 2), ("andesite", 1)) if t.rng.random() < 0.8 else "spruce_planks")
    for i in range(11):
        for (x, z) in ((i, 0), (i, 10), (0, i), (10, i)):
            for y in range(0, 5):
                t.set(x, y, z, pick(t, ("cobblestone", 3), ("mossy_cobblestone", 2)))
    for z in (4, 5):                                           # the claw hole: something tore through the west wall
        for y in (1, 2, 3):
            t.remove(0, y, z)
    t.set(1, 3, 4, "cobweb")
    for (x, z) in ((2, 2), (8, 2), (2, 8), (8, 8)):
        for y in range(1, 5):
            t.set(x, y, z, "stripped_spruce_log", {"axis": "y"})
    for z in range(3, 8):
        t.set(9, 2, z, "spruce_slab", {"type": "top", "waterlogged": "false"})
        t.set(9, 3, z, "spruce_slab", SLAB)
    for (x, y, z) in ((1, 4, 1), (9, 4, 1), (1, 4, 9), (9, 4, 9), (3, 4, 5), (7, 4, 3), (5, 4, 8), (1, 1, 9), (9, 1, 9)):
        t.set(x, y, z, "cobweb")
    t.set(8, 1, 5, "damaged_anvil", {"facing": "east"})
    t.set(8, 1, 7, "crafting_table")
    t.set(8, 2, 7, "lantern", {"hanging": "false", "waterlogged": "false"})
    t.bed(2, 1, 5, "south", "brown")
    t.chest(5, 1, 1, LOOT + "hunters_bunker", barrel=True)
    for x in range(1, 10):                                     # beams under the ceiling, a ward lantern
        t.set(x, 4, 5, "stripped_spruce_log", {"axis": "x"})
    t.set(5, 3, 5, "soul_lantern", {"hanging": "true", "waterlogged": "false"})
    for (x, y, z) in ((1, 1, 7), (1, 2, 7), (1, 1, 8), (1, 2, 8)):
        t.set(x, y, z, "bookshelf")
    t.set(9, 4, 4, "skeleton_skull", {"rotation": "12"})
    t.set(9, 1, 3, "barrel", {"facing": "up", "open": "false"})
    t.set(9, 1, 4, "barrel", {"facing": "west", "open": "false"})
    t.set(4, 1, 1, "hay_block", {"axis": "y"})
    t.set(3, 1, 1, "composter", {"level": "0"})
    # the hearth against the north wall, its chimney up through the earth and out of the grass
    t.set(7, 1, 1, "campfire", {"facing": "south", "lit": "false", "signal_fire": "false", "waterlogged": "false"})
    for (x, y) in ((6, 1), (8, 1), (6, 2), (8, 2), (6, 3), (7, 3), (8, 3), (7, 4)):
        t.set(x, y, 1, pick(t, ("bricks", 3), ("cobblestone", 1)))
    for y in range(5, top + 1):
        t.set(7, y, 1, pick(t, ("bricks", 2), ("mossy_cobblestone", 1)))
    t.set(7, top + 1, 1, "bricks")
    t.set(7, top + 2, 1, "cobblestone_wall")
    # the front: a stone face round the door, windows either side, a keystone over it
    for x in range(3, 8):
        for y in range(3, top + 1):
            t.set(x, y, 10, pick(t, ("stone_bricks", 4), ("mossy_stone_bricks", 2), ("cracked_stone_bricks", 1)))
    t.set(5, 3, 10, "chiseled_stone_bricks")
    t.set(4, 2, 10, "iron_bars")
    t.set(6, 2, 10, "iron_bars")
    t.set(5, 1, 10, "spruce_door", {"facing": "south", "half": "lower", "hinge": "left", "open": "true", "powered": "false"})
    t.set(5, 2, 10, "spruce_door", {"facing": "south", "half": "upper", "hinge": "left", "open": "true", "powered": "false"})
    # the way in: a trench of twelve steps up to the grass, lined with stone, railed along the top
    for z in range(11, 23):
        k = z - 10                                             # 1..12: the step's height
        for x in (4, 5, 6):
            t.set(x, k, z, "cobblestone_stairs", dict(STAIR, facing="south"))
            if k >= 2:
                t.set(x, k - 1, z, "cobblestone")
            t.air(x, k + 1, z, x, 16, z)
        for x in (3, 7):
            for y in range(max(0, k - 2), top + 1):
                t.set(x, y, z, pick(t, ("stone_bricks", 3), ("mossy_stone_bricks", 2), ("cobblestone", 2)))
            if z <= 21:
                t.set(x, top + 1, z, "spruce_fence", fence(north=z > 11, south=z < 21))
    for x in (4, 5, 6):
        t.set(x, 0, 11, "cobblestone")
    for (x, z) in ((3, 10), (7, 10)):                          # the railing turns along the front
        t.set(x, top + 1, z, "spruce_fence", fence(south=True, east=x == 3, west=x == 7))
    for x in (4, 5, 6):
        t.set(x, top + 1, 10, "spruce_fence", fence(east=True, west=True))
    # an arch over the bottom of the steps with a lantern under it, torches down the wall
    t.set(4, 4, 11, "stone_brick_stairs", dict(STAIR, facing="west", half="top"))
    t.set(6, 4, 11, "stone_brick_stairs", dict(STAIR, facing="east", half="top"))
    for x in (4, 5, 6):
        for y in (5, 6):
            t.set(x, y, 11, "chiseled_stone_bricks" if (x, y) == (5, 5) else pick(t, ("stone_bricks", 3), ("mossy_stone_bricks", 1)))
        t.set(x, 7, 11, "stone_brick_slab", SLAB)
    t.set(5, 4, 11, "lantern", {"hanging": "true", "waterlogged": "false"})
    for (z, x, f) in ((15, 4, "east"), (19, 6, "west")):
        t.set(x, z - 10 + 3, z, "soul_wall_torch", {"facing": f})
    # at the top: a lantern post, and a skull on a stake to warn you off
    t.set(8, top, 22, "cobblestone")
    t.set(8, top + 1, 22, "spruce_fence", fence())
    t.set(8, top + 2, 22, "spruce_fence", fence())
    t.set(8, top + 3, 22, "lantern", {"hanging": "false", "waterlogged": "false"})
    t.set(1, top + 1, 20, "spruce_fence", fence())
    t.set(1, top + 2, 20, "skeleton_skull", {"rotation": "8"})
    return t


# ===================================================================== fog well
def fog_well(village=False):
    t = Template(7, 19, 7, seed=44)
    # the hidden chamber at the bottom
    t.air(1, 1, 1, 5, 3, 5)
    for x in range(1, 6):
        for z in range(1, 6):
            if t.rng.random() < 0.85:
                t.set(x, 0, z, pick(t, ("tuff", 2), ("cobbled_deepslate", 1)))
            if (x, z) != (3, 3) and t.rng.random() < 0.45:
                t.set(x, 4, z, pick(t, ("tuff", 1), ("cobbled_deepslate", 1)))
    for (x, z) in ((1, 1), (5, 1), (1, 5), (5, 5)):
        for y in (1, 2):
            t.set(x, y, z, pick(t, ("tuff", 1), ("cobbled_deepslate", 2)))
    t.set(3, 4, 3, "air")
    for (x, z) in ((1, 2), (5, 4), (2, 5), (4, 1)):
        t.set(x, 3, z, "cobweb")
    for (x, z) in ((2, 1), (4, 5), (5, 2)):
        t.set(x, 1, z, "gray_concrete_powder")
    t.chest(3, 1, 5, LOOT + "fog_well", barrel=True)
    t.set(2, 1, 4, "skeleton_skull", {"rotation": "10"})
    t.set(4, 1, 2, "bone_block", {"axis": "x"})
    # the shaft: lined with cobblestone above, tuff and deepslate below, choked with webs and grey powder
    ring = [(2, 2), (3, 2), (4, 2), (2, 3), (4, 3), (2, 4), (3, 4), (4, 4)]
    for y in range(5, 13):
        for (x, z) in ring:
            if y <= 7:
                t.set(x, y, z, pick(t, ("tuff", 2), ("cobbled_deepslate", 1)))
            else:
                t.set(x, y, z, pick(t, ("cobblestone", 3), ("mossy_cobblestone", 2)))
    choke = {12: "cobweb", 11: "gray_concrete_powder", 10: "cobweb", 9: "gray_concrete_powder", 8: "cobweb",
             7: "gray_concrete_powder", 6: "cobweb", 5: "air"}
    for y, b in choke.items():
        t.set(3, y, 3, b)
    t.set(3, 13, 3, "cobweb")
    t.set(3, 14, 3, "air")
    # the well at the surface: a stone rim with a slab lip
    for (x, z) in ring:
        t.set(x, 13, z, pick(t, ("cobblestone", 3), ("mossy_cobblestone", 2)))
        if (x, z) in ((3, 2), (2, 3), (4, 3), (3, 4)):
            t.set(x, 14, z, "mossy_cobblestone_slab" if t.rng.random() < 0.5 else "cobblestone_slab", SLAB)
        else:
            t.set(x, 14, z, pick(t, ("cobblestone", 2), ("mossy_cobblestone", 2)))
    t.remove(3, 14, 2)                                         # a stone has fallen in
    for (x, z) in ((2, 2), (4, 2), (2, 4), (4, 4)):
        t.set(x, 15, z, "spruce_fence", fence())
        t.set(x, 16, z, "spruce_fence", fence())
    for x in range(1, 6):                                      # a little pitched roof, one shingle gone
        t.set(x, 17, 1, "spruce_stairs", dict(STAIR, facing="south"))
        t.set(x, 17, 5, "spruce_stairs", dict(STAIR, facing="north"))
        t.set(x, 17, 2, "spruce_planks")
        t.set(x, 17, 4, "spruce_planks")
        t.set(x, 18, 3, "spruce_slab", SLAB)
    t.set(3, 17, 3, "spruce_planks")
    t.remove(4, 17, 1)
    t.set(4, 18, 3, "air")
    # the winch: a beam across under the roof, its crank, and the rope going down into the dark
    for x in (1, 2, 3, 4, 5):
        t.set(x, 16, 3, "stripped_spruce_log", {"axis": "x"})
    t.set(0, 16, 3, "lever", {"face": "wall", "facing": "west", "powered": "false"})
    t.set(3, 15, 3, "spruce_fence", fence())
    for x in range(7):                                         # a stone apron round it, mossy
        for z in range(7):
            if (x in (0, 6) or z in (0, 6) or x in (1, 5) or z in (1, 5)) and not (2 <= x <= 4 and 2 <= z <= 4):
                if t.rng.random() < 0.8:
                    t.set(x, 12, z, pick(t, ("cobblestone", 2), ("mossy_cobblestone", 2), ("gravel", 1)))
    # lantern posts at two corners, a bench to sit by it, moss
    for (x, z) in ((0, 0), (6, 6)):
        t.set(x, 12, z, "cobblestone")
        t.set(x, 13, z, "spruce_fence", fence())
        t.set(x, 14, z, "spruce_fence", fence())
        t.set(x, 15, z, "lantern", {"hanging": "false", "waterlogged": "false"})
    for x in (1, 2):
        t.set(x, 12, 6, "cobblestone")
        t.set(x, 13, 6, "spruce_stairs", dict(STAIR, facing="south"))
    t.set(0, 13, 6, "spruce_trapdoor", {"facing": "west", "half": "bottom", "open": "true", "powered": "false", "waterlogged": "false"})
    t.set(3, 13, 6, "spruce_trapdoor", {"facing": "east", "half": "bottom", "open": "true", "powered": "false", "waterlogged": "false"})
    for (x, z) in ((1, 1), (5, 2), (0, 4), (6, 3), (5, 6)):
        if t.get(x, 13, z) in (None, "minecraft:air"):
            t.set(x, 13, z, "moss_carpet")
    t.set(2, 16, 4, "cobweb")
    t.set(4, 15, 4, "cobweb")
    if village:
        t.jigsaw(3, 13, 0, "north_up", "minecraft:building_entrance", "minecraft:building_entrance",
                 "minecraft:empty", "minecraft:air")
    return t


# ===================================================================== castles (jigsaw pieces)
class Mats:
    """What a castle is made of: ruined and overgrown, or kept up and warded."""

    def __init__(self, fortified, t):
        self.f = fortified
        self.t = t

    def wall(self):
        if self.f:
            return pick(self.t, ("stone_bricks", 7), ("chiseled_stone_bricks", 1))
        return pick(self.t, ("stone_bricks", 50), ("cracked_stone_bricks", 22), ("mossy_stone_bricks", 22), ("cobblestone", 6))

    def found(self):
        if self.f:
            return pick(self.t, ("cobblestone", 1), ("polished_andesite", 1))
        return pick(self.t, ("cobblestone", 3), ("mossy_cobblestone", 2))

    def floor(self):
        if self.f:
            return pick(self.t, ("spruce_planks", 2), ("oak_planks", 1))
        return pick(self.t, ("dark_oak_planks", 2), ("spruce_planks", 1))

    def keep(self, p):
        """True if this block survives (ruins lose some)."""
        return self.f or self.t.rng.random() > p


def crenels(t, m, coords, y):
    for k, (x, z) in enumerate(coords):
        if k % 2 == 0 and m.keep(0.25):
            t.set(x, y, z, "stone_brick_wall")


def ring(x1, z1, x2, z2):
    out = []
    for x in range(x1, x2 + 1):
        out += [(x, z1), (x, z2)]
    for z in range(z1 + 1, z2):
        out += [(x1, z), (x2, z)]
    return out




def pyramid(t, x1, z1, x2, z2, y, stairs, top, keep=lambda: True, rings=99):
    """A stepped roof: a ring of stairs (rising inwards) on each layer, one block in and one up, a slab on top."""
    k = 0
    while k < rings:
        xa, xb, za, zb = x1 + k, x2 - k, z1 + k, z2 - k
        yy = y + k
        if xa > xb or za > zb:
            return
        if xa == xb or za == zb:
            for x in range(xa, xb + 1):
                for z in range(za, zb + 1):
                    if keep():
                        t.set(x, yy, z, top, {"type": "bottom", "waterlogged": "false"})
            return
        for x in range(xa, xb + 1):
            if keep():
                t.set(x, yy, za, stairs, dict(STAIR, facing="south"))
            if keep():
                t.set(x, yy, zb, stairs, dict(STAIR, facing="north"))
        for z in range(za + 1, zb):
            if keep():
                t.set(xa, yy, z, stairs, dict(STAIR, facing="east"))
            if keep():
                t.set(xb, yy, z, stairs, dict(STAIR, facing="west"))
        k += 1


def castle_keep(kind, fortified):
    t = Template(15, 22, 15, seed=55 if fortified else 66)
    m = Mats(fortified, t)
    base = "foglurker:" + kind
    # crypt / treasury (y 0-3) under the middle, deepslate
    t.air(5, 1, 5, 9, 3, 9)
    for x in range(4, 11):
        for z in range(4, 11):
            t.set(x, 0, z, "deepslate_bricks")
    for (x, z) in ring(4, 4, 10, 10):
        for y in (1, 2, 3):
            t.set(x, y, z, pick(t, ("cobbled_deepslate", 2), ("deepslate_bricks", 1)))
    for (x, z) in ((6, 6), (8, 6), (6, 8), (8, 8)):
        for y in (1, 2, 3):
            t.set(x, y, z, "deepslate_bricks")
    t.set(5, 1, 9, "deepslate_brick_stairs", dict(STAIR, facing="east"))
    t.set(9, 1, 5, "deepslate_brick_stairs", dict(STAIR, facing="west"))
    t.set(7, 1, 6, "polished_deepslate")                          # a tomb down the middle
    t.set(7, 1, 7, "polished_deepslate")
    t.set(7, 2, 6, "deepslate_tile_slab", {"type": "bottom", "waterlogged": "false"})
    t.set(7, 2, 7, "deepslate_tile_slab", {"type": "bottom", "waterlogged": "false"})
    for (x, z, n) in ((9, 9, "3"), (5, 6, "2"), (9, 7, "1"), (5, 8, "4")):
        t.set(x, 1, z, "candle", {"candles": n, "lit": "true" if fortified else "false", "waterlogged": "false"})
    # ground floor (y 4): cobble/andesite, deepslate tiles over the crypt; the crypt hatch with its ladder
    for x in range(1, 14):
        for z in range(1, 14):
            hall = m.found() if fortified else pick(t, ("cobblestone", 3), ("mossy_cobblestone", 2), ("stone_bricks", 2), ("cracked_stone_bricks", 1))
            t.set(x, 4, z, "deepslate_tiles" if 5 <= x <= 9 and 5 <= z <= 9 else hall)
    t.set(6, 4, 5, "air")
    for y in (1, 2, 3):
        t.set(6, y, 5, "ladder", {"facing": "south", "waterlogged": "false"})
    # walls (y 4-11) with a darker plinth, interior air
    t.air(1, 5, 1, 13, 11, 13)
    for (x, z) in ring(0, 0, 14, 14):
        t.set(x, 4, z, m.found())
        for y in range(5, 12):
            if not fortified and y > 9 and t.rng.random() < 0.25:
                continue  # crumbled
            t.set(x, y, z, "stone_bricks" if y == 5 and fortified else m.wall())
    for side in range(4):                                      # tall windows
        for i in (3, 11):
            for y in ((6, 7, 9, 10) if fortified else (7, 10)):
                x, z = [(i, 0), (14, i), (i, 14), (0, i)][side]
                t.set(x, y, z, "iron_bars" if fortified or t.rng.random() < 0.45 else "air")
    crenels(t, m, ring(0, 0, 14, 14), 12)
    # wide arched doors with jigsaw connectors: the gatehouse south, curtain walls east and west
    t.jigsaw(7, 5, 14, "south_up", "foglurker:castle_keep", "foglurker:castle_gate", base + "/gatehouse", "minecraft:air")
    t.jigsaw(14, 5, 7, "east_up", "foglurker:castle_keep", "foglurker:castle_wing", base + "/wings", "minecraft:air")
    t.jigsaw(0, 5, 7, "west_up", "foglurker:castle_keep", "foglurker:castle_wing", base + "/wings", "minecraft:air")
    for d in (-1, 0, 1):
        for y in (5, 6, 7):
            for (x, z) in ((7 + d, 14), (14, 7 + d), (0, 7 + d)):
                if not (y == 5 and d == 0):
                    t.set(x, y, z, "air")
    for (x, z, f) in ((6, 14, "west"), (8, 14, "east")):
        t.set(x, 7, z, "stone_brick_stairs", dict(STAIR, facing=f, half="top"))
    for (x, z, f) in ((14, 6, "north"), (14, 8, "south"), (0, 6, "north"), (0, 8, "south")):
        t.set(x, 7, z, "stone_brick_stairs", dict(STAIR, facing=f, half="top"))
    # upper floor (y 8): a gallery round the walls when kept up, rotted through in the ruin
    for x in range(1, 14):
        for z in range(1, 14):
            if 2 <= x <= 3 and 10 <= z <= 12:
                continue
            gallery = not (4 <= x <= 10 and 4 <= z <= 10)
            if (fortified and gallery) or (not fortified and t.rng.random() < 0.5):
                t.set(x, 8, z, m.floor())
    for y in range(5, 9):                                      # stairs up in the corner
        t.set(2, y, 13 - (y - 5), "stone_brick_stairs", dict(STAIR, facing="north"))
        t.air(2, y + 1, 13 - (y - 5), 2, 7, 13 - (y - 5))
    if fortified:
        # a stepped roof over the hall, inside the battlements
        pyramid(t, 1, 1, 13, 13, 12, "dark_oak_stairs", "dark_oak_slab")
        t.set(7, 19, 7, "dark_oak_fence")
        t.set(7, 20, 7, "dark_oak_fence")
        t.set(7, 21, 7, "lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    else:
        # the roof fell in: only the beams are left, and bits of it
        for x in (4, 7, 10):
            for z in range(1, 14):
                if t.rng.random() < 0.7:
                    t.set(x, 12, z, "stripped_dark_oak_log", {"axis": "z"})
        pyramid(t, 1, 1, 13, 13, 12, "dark_oak_stairs", "dark_oak_slab", keep=lambda: t.rng.random() < 0.4, rings=2)
    # corner turrets rising above the walls
    for (cx, cz) in ((0, 0), (12, 0), (0, 12), (12, 12)):
        top = 15 if fortified else 12 + t.rng.randint(0, 3)
        for y in range(12, top):
            for (x, z) in ring(cx, cz, cx + 2, cz + 2):
                if fortified or t.rng.random() > 0.15:
                    t.set(x, y, z, m.wall())
            t.set(cx + 1, y, cz + 1, "air")
        if fortified:
            pyramid(t, cx, cz, cx + 2, cz + 2, 15, "dark_oak_stairs", "dark_oak_slab")
            t.set(cx + 1, 17, cz + 1, "lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
        else:
            crenels(t, m, ring(cx, cz, cx + 2, cz + 2), top)
    if fortified:
        t.set(7, 5, 2, "oak_stairs", dict(STAIR, facing="north"))      # the lord's seat, facing the door
        t.set(6, 5, 2, "gold_block")
        t.set(8, 5, 2, "gold_block")
        t.set(7, 7, 1, "red_wall_banner", {"facing": "south"})
        for z in range(3, 14):
            t.set(7, 5, z, "red_carpet")                                 # the runner from the door
        for (tx, cw, ce) in ((4, 3, 5), (10, 9, 11)):                   # two long tables with chairs
            for z in range(5, 10):
                t.set(tx, 5, z, "oak_fence")
                t.set(tx, 6, z, "white_carpet")
                if z % 2 == 1:
                    t.set(cw, 5, z, "oak_stairs", dict(STAIR, facing="west"))
                    t.set(ce, 5, z, "oak_stairs", dict(STAIR, facing="east"))
        for (bx, bz) in ((2, 3), (12, 3), (2, 7), (12, 7)):              # beds on the gallery
            t.bed(bx, 9, bz, "south", "red")
        for (x, z) in ((3, 7), (11, 7), (3, 3), (11, 11)):               # lanterns hung under the gallery
            t.set(x, 7, z, "lantern", {"hanging": "true", "waterlogged": "false"})
        for (x, y, z) in ((12, 5, 12), (12, 6, 12), (11, 5, 12), (11, 6, 12), (12, 5, 11), (12, 6, 11)):
            t.set(x, y, z, "bookshelf")
        t.chest(12, 5, 2, LOOT + "fortified_castle", facing="south")   # armory
        t.chest(7, 1, 9, LOOT + "fortified_castle", facing="north")    # treasury
        for (x, y, z) in ((3, 5, 3), (11, 5, 3), (3, 5, 11), (5, 1, 5)):
            t.set(x, y, z, "lantern", {"hanging": "false", "waterlogged": "false"})
        for (x, z) in ((0, 4), (0, 10), (14, 4), (14, 10), (4, 0), (10, 0), (4, 14), (10, 14)):
            t.set(x, 12, z, m.wall())
            t.set(x, 13, z, "soul_lantern", {"hanging": "false", "waterlogged": "false"})
        for (x, z) in ((5, 13), (9, 13)):
            t.set(x, 7, z, "red_wall_banner", {"facing": "north"})
        villagers = [("minecraft:armorer", 4, 4), ("minecraft:cleric", 10, 4), ("minecraft:librarian", 4, 10),
                     ("minecraft:weaponsmith", 10, 10)]
        for prof, x, z in villagers:
            t.entity(x, 5, z, {"id": "minecraft:villager", "PersistenceRequired": True,
                               "VillagerData": {"profession": prof, "type": "minecraft:plains", "level": 2}})
        for (x, z) in ((12, 4), (12, 6)):
            t.entity(x, 5, z, {"id": "minecraft:armor_stand", "_armor": ["iron_boots", "iron_leggings", "iron_chestplate", "iron_helmet"]})
    else:
        t.chest(12, 5, 12, LOOT + "abandoned_castle", barrel=True)
        t.chest(7, 1, 9, LOOT + "abandoned_castle", facing="north")
        for (x, y, z) in ((1, 7, 1), (13, 7, 1), (1, 6, 13), (5, 2, 7), (9, 3, 7), (12, 10, 12), (7, 7, 7), (3, 11, 3),
                          (13, 10, 6), (1, 9, 9), (7, 3, 5)):
            t.set(x, y, z, "cobweb")
        for (x, z) in ((9, 3), (10, 3), (9, 2), (3, 9), (11, 11)):          # rubble
            t.set(x, 5, z, m.found())
        for _ in range(18):                                    # moss creeping over the floor
            x, z = t.rng.randint(1, 13), t.rng.randint(1, 13)
            if t.get(x, 5, z) == "minecraft:air":
                t.set(x, 5, z, "moss_carpet")
        for _ in range(8):                                     # planks fallen from the floor above
            x, z = t.rng.randint(2, 12), t.rng.randint(2, 12)
            if t.get(x, 5, z) in ("minecraft:air", "minecraft:moss_carpet"):
                t.set(x, 5, z, "dark_oak_slab", {"type": "bottom", "waterlogged": "false"})
        t.set(7, 5, 2, "oak_stairs", dict(STAIR, facing="east"))       # the seat, knocked over
        t.set(8, 5, 2, "cracked_stone_bricks")
        t.set(6, 5, 3, "skeleton_skull", {"rotation": "6"})
        t.set(5, 5, 9, "bone_block", {"axis": "x"})
        for (x, y, z, side) in ((1, 9, 4, "west"), (1, 8, 4, "west"), (13, 9, 9, "east"), (13, 8, 9, "east"),
                                (13, 7, 9, "east"), (5, 9, 1, "north"), (5, 8, 1, "north"), (9, 10, 13, "south")):
            if t.get(x, y, z) in (None, "minecraft:air"):
                t.set(x, y, z, "vine", {"north": str(side == "north").lower(), "south": str(side == "south").lower(),
                                        "east": str(side == "east").lower(), "west": str(side == "west").lower(), "up": "false"})
    return t


def castle_wing(kind, fortified):
    """A curtain wall: two walls with a passage between and a wall-walk on top."""
    t = Template(5, 8, 9, seed=77 if fortified else 88)
    m = Mats(fortified, t)
    base = "foglurker:" + kind
    t.air(1, 1, 0, 3, 4, 8)
    for z in range(9):
        for x in (1, 2, 3):
            t.set(x, 0, z, m.found())
        for x in (0, 4):
            for y in range(1, 5):
                if not fortified and y >= 3 and t.rng.random() < 0.3:
                    continue
                t.set(x, y, z, "stone_bricks" if y == 1 and fortified else m.wall())
        for x in (1, 2, 3):                                    # wall-walk
            if m.keep(0.4):
                t.set(x, 5, z, m.floor())
    crenels(t, m, [(0, z) for z in range(9)], 5)
    crenels(t, m, [(4, z) for z in range(9)], 5)
    for z in (0, 8):                                           # the openings at each end are arched
        t.set(1, 4, z, "stone_brick_stairs", dict(STAIR, facing="west", half="top"))
        t.set(3, 4, z, "stone_brick_stairs", dict(STAIR, facing="east", half="top"))
    if fortified:
        for (x, z) in ((0, 2), (0, 6), (4, 2), (4, 6)):
            t.set(x, 5, z, m.wall())
            t.set(x, 6, z, "soul_lantern", {"hanging": "false", "waterlogged": "false"})
        for z in (2, 4, 6):
            t.set(0, 3, z, "iron_bars")
            t.set(4, 3, z, "iron_bars")
        t.set(2, 4, 4, "lantern", {"hanging": "true", "waterlogged": "false"})
        t.set(1, 2, 3, "red_wall_banner", {"facing": "east"})
        t.set(3, 2, 5, "red_wall_banner", {"facing": "west"})
    else:
        for (x, z) in ((1, 2), (3, 6), (2, 7)):
            t.set(x, 1, z, "moss_carpet")
        for (x, y, z) in ((1, 3, 3), (3, 2, 6), (3, 3, 1)):
            t.set(x, y, z, "cobweb")
        t.set(2, 1, 5, "mossy_cobblestone")
    t.jigsaw(2, 1, 0, "north_up", "foglurker:castle_wing", "foglurker:castle_wing", "minecraft:empty", "minecraft:air")
    t.jigsaw(2, 1, 8, "south_up", "foglurker:castle_wing_end", "foglurker:castle_tower", base + "/towers", "minecraft:air")
    t.set(2, 2, 8, "air")
    return t


def castle_tower(kind, fortified):
    """A round-ish corner tower: a pointed roof and a spire when kept up, a jagged broken top in the ruin."""
    t = Template(9, 18, 9, seed=99 if fortified else 111)
    m = Mats(fortified, t)
    t.air(2, 1, 2, 6, 10, 6)
    for x in range(2, 7):
        for z in range(2, 7):
            t.set(x, 0, z, m.found())
    corners = {(1, 1), (7, 1), (1, 7), (7, 7)}
    for (x, z) in ring(1, 1, 7, 7):
        top = 10 if fortified else 5 + t.rng.randint(0, 5)
        for y in range(1, top + 1):
            t.set(x, y, z, "stone_bricks" if y == 1 and fortified else m.wall())
    if fortified:
        for (x, z) in ring(1, 1, 7, 7):                        # a timber frieze under the eaves
            t.set(x, 11, z, "stripped_dark_oak_log" if (x, z) in corners else "dark_oak_planks",
                  {"axis": "y"} if (x, z) in corners else None)
        pyramid(t, 0, 0, 8, 8, 11, "dark_oak_stairs", "dark_oak_slab")   # overhanging one block all round
        t.set(4, 15, 4, "dark_oak_planks")
        t.set(4, 16, 4, "dark_oak_fence")
        t.set(4, 17, 4, "lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
        for (x, z) in ((0, 0), (8, 0), (0, 8), (8, 8)):
            t.set(x, 10, z, "soul_lantern", {"hanging": "true", "waterlogged": "false"})
    else:
        crenels(t, m, ring(1, 1, 7, 7), 11)
        for (x, z) in ring(1, 1, 7, 7):                        # nothing floats: crenels only on walls that reach
            if t.get(x, 11, z) and not t.get(x, 10, z):
                t.remove(x, 11, z)
    for x in range(2, 7):                                      # floors halfway and at the top
        for z in range(2, 7):
            if (x, z) != (6, 3) and m.keep(0.5):
                t.set(x, 5, z, m.floor())
            if fortified and (x, z) != (6, 3):
                t.set(x, 10, z, m.floor())
    for (x, y, z) in ((4, 3, 7), (1, 3, 4), (7, 3, 4), (4, 7, 7), (1, 7, 4), (7, 7, 4), (1, 7, 2), (7, 7, 6), (4, 8, 1)):
        if t.get(x, y, z):
            t.set(x, y, z, "iron_bars" if fortified or t.rng.random() < 0.4 else "air")
    t.set(4, 1, 1, "air")
    t.set(4, 2, 1, "air")
    t.set(4, 3, 1, "stone_brick_stairs", dict(STAIR, facing="south", half="top"))
    for y in range(1, 10 if fortified else 5):
        t.set(6, y, 3, "ladder", {"facing": "west", "waterlogged": "false"})
    t.set(6, 5, 3, "air" if not fortified else "ladder")
    if fortified:
        t.set(6, 5, 3, "ladder", {"facing": "west", "waterlogged": "false"})
        t.set(6, 10, 3, "air")
    t.jigsaw(4, 1, 0, "north_up", "foglurker:castle_tower", "foglurker:castle_tower", "minecraft:empty", "minecraft:air")
    for y in range(1, 5):                                      # the walls of the passage in from the curtain wall
        for x in (2, 6):
            if fortified or y < 4 or t.rng.random() < 0.5:
                t.set(x, y, 0, m.wall())
    for x in (3, 4, 5):
        t.set(x, 0, 0, m.found())
        if m.keep(0.4):
            t.set(x, 5, 0, m.floor())
    if fortified:
        for (x, y, z) in ((3, 1, 3), (5, 6, 5), (3, 6, 5)):
            t.set(x, y, z, "lantern", {"hanging": "false", "waterlogged": "false"})
        t.set(2, 1, 5, "stone_brick_stairs", dict(STAIR, facing="west"))
        t.set(2, 1, 4, "stone_brick_stairs", dict(STAIR, facing="west"))
        t.chest(5, 1, 5, LOOT + "fortified_castle", facing="north")
        t.entity(5, 1, 3, {"id": "minecraft:armor_stand", "_armor": ["iron_boots", "iron_leggings", "iron_chestplate", "iron_helmet"]})
        t.set(3, 1, 5, "barrel", {"facing": "up", "open": "false"})
    else:
        t.chest(5, 1, 5, LOOT + "abandoned_castle", barrel=True)
        for (x, z) in ((2, 2), (3, 5), (5, 2)):
            if t.get(x, 1, z) in (None, "minecraft:air"):
                t.set(x, 1, z, "moss_carpet")
        t.set(2, 1, 4, "bone_block", {"axis": "z"})
        for (x, y, z) in ((2, 3, 2), (6, 4, 6), (2, 2, 6), (5, 3, 2)):
            if t.get(x, y, z) == "minecraft:air":
                t.set(x, y, z, "cobweb")
    return t


def castle_gatehouse(kind, fortified):
    """The way in: a passage between two walls, a portcullis under an arch, and a squat tower either side."""
    t = Template(11, 12, 9, seed=121 if fortified else 131)
    m = Mats(fortified, t)
    t.air(4, 1, 0, 6, 5, 8)
    for z in range(9):
        for x in (4, 5, 6):
            t.set(x, 0, z, m.found())
        for x in (3, 7):                                       # passage walls
            for y in range(1, 7):
                if not fortified and y >= 5 and t.rng.random() < 0.3:
                    continue
                t.set(x, y, z, m.wall())
        for x in (4, 5, 6):                                    # walkway over the passage
            if m.keep(0.35):
                t.set(x, 6, z, m.floor())
    crenels(t, m, [(3, z) for z in range(9)], 7)
    crenels(t, m, [(7, z) for z in range(9)], 7)
    for (x1, x2) in ((0, 2), (8, 10)):                         # flanking towers
        top = 8 if fortified else 5 + t.rng.randint(0, 2)
        for x in range(x1, x2 + 1):
            for z in range(5, 9):
                for y in range(1, top + 1):
                    if x in (x1, x2) or z in (5, 8):
                        t.set(x, y, z, m.wall())
                    else:
                        t.set(x, y, z, "air")
        if fortified:
            pyramid(t, x1, 5, x2, 8, top + 1, "dark_oak_stairs", "dark_oak_slab")
        else:
            crenels(t, m, [(x1, 5), (x2, 5), (x1, 8), (x2, 8), (x1 + 1, 8), (x1 + 1, 5)], top + 1)
        for y in (3, 6):                                       # arrow slits
            if t.get(x1 + 1, y, 8):
                t.set(x1 + 1, y, 8, "iron_bars" if fortified or t.rng.random() < 0.5 else "air")
    for x in (4, 5, 6):                                        # the gate: portcullis under an arch
        for y in (3, 4):
            if fortified or t.rng.random() < 0.6:
                t.set(x, y, 8, "iron_bars")
        t.set(x, 5, 8, m.wall())
    t.set(4, 4, 8, "stone_brick_stairs", dict(STAIR, facing="west", half="top"))
    t.set(6, 4, 8, "stone_brick_stairs", dict(STAIR, facing="east", half="top"))
    t.jigsaw(5, 1, 0, "north_up", "foglurker:castle_gate", "foglurker:castle_gate", "minecraft:empty", "minecraft:air")
    if fortified:
        for (x, z) in ((3, 2), (7, 2), (3, 6), (7, 6)):
            t.set(x, 7, z, m.wall())
            t.set(x, 8, z, "soul_lantern", {"hanging": "false", "waterlogged": "false"})
        for (x, z) in ((0, 4), (2, 4), (8, 4), (10, 4)):          # hung under the eaves
            t.set(x, 8, z, "soul_lantern", {"hanging": "true", "waterlogged": "false"})
        for (x, y, z) in ((1, 1, 6), (9, 1, 6)):
            t.set(x, y, z, "lantern", {"hanging": "false", "waterlogged": "false"})
        t.set(5, 5, 4, "lantern", {"hanging": "true", "waterlogged": "false"})
        t.set(4, 3, 4, "red_wall_banner", {"facing": "east"})
        t.set(6, 3, 4, "red_wall_banner", {"facing": "west"})
        t.chest(1, 1, 7, LOOT + "fortified_castle", facing="north")
        t.set(9, 1, 7, "barrel", {"facing": "up", "open": "false"})
        t.set(9, 2, 7, "hay_block", {"axis": "y"})
        t.entity(5, 1, 4, {"id": "minecraft:iron_golem", "PlayerCreated": True, "PersistenceRequired": True})
    else:
        t.set(4, 1, 3, "cobweb")
        t.set(6, 2, 5, "cobweb")
        t.set(5, 1, 6, "mossy_cobblestone")
        t.set(5, 1, 7, "iron_bars")                            # bars fallen from the portcullis
    return t
