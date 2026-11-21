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
    """A spindly ranger's tower: legs splayed at the foot, cross-braced, a ladder up the middle, a cabin on a deck."""
    t = Template(9, 23, 9, seed=11)
    log_y = {"axis": "y"}
    stair = lambda f: {"facing": f, "half": "bottom", "shape": "straight", "waterlogged": "false"}
    # legs: splayed out at the bottom, straight from y 5
    for (x0, z0, x1, z1) in ((0, 0, 1, 1), (8, 0, 7, 1), (0, 8, 1, 7), (8, 8, 7, 7)):
        for y in range(0, 5):
            t.set(x0, y, z0, "spruce_log", log_y)
        t.set(x0 + (1 if x1 > x0 else -1) * 0, 4, z0, "spruce_log", log_y)
        t.set(x1, 4, z0 if False else z1, "spruce_log", log_y)
        for y in range(5, 15):
            t.set(x1, y, z1, "spruce_log", log_y)
    for y in range(0, 14):
        t.set(4, y, 4, "stripped_spruce_log", log_y)          # the ladder pole
    for y in range(1, 15):
        t.set(4, y, 3, "ladder", {"facing": "north", "waterlogged": "false"})
    for y in (6, 11):                                          # cross-bracing rails
        for i in range(2, 7):
            for (x, z) in ((i, 1), (i, 7), (1, i), (7, i)):
                t.set(x, y, z, "spruce_fence")
    for y in (3,):                                             # a low rail between the splayed feet
        for i in range(1, 8):
            for (x, z) in ((i, 0), (i, 8), (0, i), (8, i)):
                if t.get(x, y, z) is None:
                    t.set(x, y, z, "spruce_fence")
    # deck (some planks rotted away), railing, and the cabin
    for x in range(9):
        for z in range(9):
            if (x, z) == (4, 3):
                continue  # the ladder hole
            inner = 2 <= x <= 6 and 2 <= z <= 6
            if inner or t.rng.random() < 0.7:
                t.set(x, 14, z, "spruce_planks")
    for i in range(9):
        for (x, z) in ((i, 0), (i, 8), (0, i), (8, i)):
            t.set(x, 15, z, "spruce_fence")
    t.remove(4, 15, 8)                                         # the gap in the railing by the door
    for x, z in ((2, 2), (6, 2), (2, 6), (6, 6)):
        for y in range(15, 18):
            t.set(x, y, z, "stripped_spruce_log", log_y)
    windows = []
    for i in range(3, 6):
        for (x, z) in ((i, 2), (i, 6), (2, i), (6, i)):
            for y in range(15, 18):
                if y == 16:
                    windows.append((x, y, z))
                else:
                    t.set(x, y, z, "spruce_planks")
    t.rng.shuffle(windows)
    for k, (x, y, z) in enumerate(windows):
        t.set(x, y, z, "air" if k < 2 else "iron_bars" if k < 5 else "glass_pane")
    t.air(3, 15, 3, 5, 17, 5)
    t.set(4, 15, 6, "spruce_door", {"facing": "south", "half": "lower", "hinge": "left", "open": "false", "powered": "false"})
    t.set(4, 16, 6, "spruce_door", {"facing": "south", "half": "upper", "hinge": "left", "open": "false", "powered": "false"})
    for (x, z, f) in ((3, 1, "north"), (5, 1, "north"), (1, 3, "west"), (7, 5, "east"), (1, 5, "west")):   # shutters
        t.set(x, 16, z, "spruce_trapdoor", {"facing": f, "half": "top", "open": "true", "powered": "false", "waterlogged": "false"})
    for x in range(3, 6):                                      # flat ceiling inside, to hang the lantern
        for z in range(3, 6):
            t.set(x, 18, z, "spruce_planks")
    # pitched roof, dark oak, ridge running east-west, gables filled
    for x in range(1, 8):
        t.set(x, 18, 1, "dark_oak_stairs", stair("south"))
        t.set(x, 18, 7, "dark_oak_stairs", stair("north"))
        t.set(x, 19, 2, "dark_oak_stairs", stair("south"))
        t.set(x, 19, 6, "dark_oak_stairs", stair("north"))
        t.set(x, 20, 3, "dark_oak_stairs", stair("south"))
        t.set(x, 20, 5, "dark_oak_stairs", stair("north"))
        t.set(x, 21, 4, "dark_oak_slab", {"type": "bottom", "waterlogged": "false"})
    for x in (2, 6):
        for (y, zs) in ((18, range(2, 7)), (19, range(3, 6)), (20, (4,))):
            for z in zs:
                if t.get(x, y, z) is None:
                    t.set(x, y, z, "dark_oak_planks")
    for x in (0, 8):                                           # eaves
        t.set(x, 18, 1, "dark_oak_slab", {"type": "top", "waterlogged": "false"})
        t.set(x, 18, 7, "dark_oak_slab", {"type": "top", "waterlogged": "false"})
    # inside: a bed, the chest, the ranger's maps, the "radio", a lantern, a web in the corner
    t.bed(3, 15, 4, "south", "red")
    t.chest(5, 15, 5, LOOT + "lookout_tower", facing="west")
    t.set(3, 15, 3, "cartography_table")
    t.set(5, 15, 3, "note_block", {"instrument": "harp", "note": "0", "powered": "false"})
    t.set(5, 16, 3, "observer", {"facing": "west", "powered": "false"})
    t.set(4, 17, 4, "lantern", {"hanging": "true", "waterlogged": "false"})
    t.set(3, 17, 5, "cobweb")
    t.set(5, 17, 3, "cobweb")
    return t


# ===================================================================== abandoned campfire
def abandoned_campfire():
    t = Template(9, 4, 9, seed=22)
    for x in range(9):
        for z in range(9):
            d = ((x - 4) ** 2 + (z - 4) ** 2) ** 0.5
            if d <= 4.3:
                if t.rng.random() < 0.55 or d < 2:
                    t.set(x, 0, z, pick(t, ("coarse_dirt", 3), ("podzol", 2)))
                t.air(x, 1, z, x, 3, z)                        # a clearing
    t.set(4, 1, 4, "campfire", {"facing": "north", "lit": "false", "signal_fire": "false", "waterlogged": "false"})
    t.set(4, 0, 4, "coarse_dirt")
    for (x, z) in ((3, 3), (4, 3), (5, 3), (3, 4), (5, 4), (3, 5), (4, 5), (5, 5)):
        if (x, z) != (4, 5):
            t.set(x, 0, z, pick(t, ("cobblestone", 2), ("mossy_cobblestone", 1)))
    for (x, z) in ((2, 6), (6, 2), (1, 1), (0, 6)):
        t.set(x, 1, z, pick(t, ("cobblestone", 1), ("mossy_cobblestone", 2)))
    for x in range(1, 4):                                      # fallen-log benches
        t.set(x, 1, 7, "spruce_log", {"axis": "x"})
    for z in range(2, 5):
        t.set(7, 1, z, "spruce_log", {"axis": "z"})
    for (x, z) in ((2, 2), (6, 6), (1, 5), (8, 1)):
        t.set(x, 1, z, "spruce_slab", {"type": "bottom", "waterlogged": "false"})
    # a tattered A-frame tent, open at the front
    for z in range(5, 9):
        t.set(5, 1, z, pick(t, ("gray_wool", 2), ("brown_wool", 1)))
        t.set(7, 1, z, pick(t, ("gray_wool", 2), ("brown_wool", 1)))
        if z != 6 or True:
            t.set(6, 2, z, pick(t, ("gray_wool", 2), ("brown_wool", 1)))
    t.remove(7, 1, 5)                                          # torn
    t.set(6, 1, 8, "oak_fence")
    t.set(4, 1, 8, "oak_fence")
    t.set(8, 1, 8, "oak_fence")
    t.bed(6, 1, 6, "south", "brown")
    t.chest(1, 1, 3, LOOT + "abandoned_campfire", barrel=True)
    # the ward ring someone never finished
    t.set(2, 1, 1, "soul_lantern", {"hanging": "false", "waterlogged": "false"})
    t.set(0, 1, 3, "soul_torch")
    # a woodpile, a pot left by the fire, a chopping block, a cracked jar, moss creeping in
    for (x, y, z) in ((0, 1, 7), (0, 1, 8), (1, 1, 8), (0, 2, 8)):
        t.set(x, y, z, "spruce_log", {"axis": "z"})
    t.set(3, 1, 4, "cauldron")
    t.set(6, 1, 1, "stripped_spruce_log", {"axis": "y"})
    t.set(2, 1, 5, "decorated_pot", {"facing": "north", "cracked": "true", "waterlogged": "false"})
    for (x, z) in ((1, 2), (7, 6), (2, 7), (6, 0), (8, 4), (3, 7)):
        if t.get(x, 1, z) in (None, "minecraft:air"):
            t.set(x, 1, z, "moss_carpet")
    t.set(6, 3, 6, "cobweb")
    return t


# ===================================================================== hunters' bunker
def hunters_bunker():
    t = Template(11, 9, 17, seed=33)
    t.air(1, 1, 1, 9, 4, 9)
    for x in range(1, 10):
        for z in range(1, 10):
            t.set(x, 0, z, "spruce_planks" if t.rng.random() < 0.72 else pick(t, ("stone", 1), ("andesite", 1)))
            if t.rng.random() < 0.72:
                t.set(x, 5, z, pick(t, ("stone", 2), ("andesite", 1)))
    for i in range(11):
        for (x, z) in ((i, 0), (i, 10), (0, i), (10, i)):
            for y in range(0, 4):
                t.set(x, y, z, pick(t, ("cobblestone", 3), ("mossy_cobblestone", 2)))
            if t.rng.random() < 0.3:
                t.set(x, 4, z, "cobblestone")
    for z in (4, 5):                                           # the claw hole: something tore through the west wall
        for y in (1, 2, 3):
            t.remove(0, y, z)
    t.set(1, 3, 4, "cobweb")
    for (x, z) in ((2, 2), (8, 2), (2, 8), (8, 8)):
        for y in range(1, 5):
            t.set(x, y, z, "stripped_spruce_log", {"axis": "y"})
    for z in range(3, 8):
        t.set(9, 2, z, "spruce_slab", {"type": "top", "waterlogged": "false"})
        t.set(9, 3, z, "spruce_slab", {"type": "bottom", "waterlogged": "false"})
    t.set(5, 1, 10, "air")
    t.set(5, 2, 10, "air")
    t.set(4, 2, 10, "iron_bars")
    t.set(6, 2, 10, "iron_bars")
    t.set(3, 2, 10, "iron_bars")
    t.set(7, 2, 10, "iron_bars")
    for (x, y, z) in ((1, 4, 1), (9, 4, 1), (1, 4, 9), (9, 4, 9), (3, 4, 5), (7, 4, 3), (5, 4, 8), (1, 1, 9), (9, 1, 9)):
        t.set(x, y, z, "cobweb")
    t.set(8, 1, 5, "damaged_anvil", {"facing": "east"})
    t.set(8, 1, 7, "crafting_table")
    t.set(8, 2, 7, "lantern", {"hanging": "false", "waterlogged": "false"})
    t.bed(2, 1, 5, "south", "brown")
    t.chest(5, 1, 1, LOOT + "hunters_bunker", barrel=True)
    # beams under the ceiling, a ward lantern, books, a trophy, supplies, a door
    for x in range(1, 10):
        t.set(x, 4, 5, "stripped_spruce_log", {"axis": "x"})
    t.set(5, 3, 5, "soul_lantern", {"hanging": "true", "waterlogged": "false"})
    for (x, y, z) in ((1, 1, 7), (1, 2, 7), (1, 1, 8), (1, 2, 8)):
        t.set(x, y, z, "bookshelf")
    t.set(9, 4, 4, "skeleton_skull", {"rotation": "12"})
    t.set(9, 1, 3, "barrel", {"facing": "up", "open": "false"})
    t.set(9, 1, 4, "barrel", {"facing": "west", "open": "false"})
    t.set(4, 1, 1, "hay_block", {"axis": "y"})
    t.set(6, 1, 1, "composter", {"level": "0"})
    t.set(5, 1, 10, "spruce_door", {"facing": "south", "half": "lower", "hinge": "left", "open": "true", "powered": "false"})
    t.set(5, 2, 10, "spruce_door", {"facing": "south", "half": "upper", "hinge": "left", "open": "true", "powered": "false"})
    # the way in: a trench of steps up to the surface (the hillside covers the rest)
    for k in range(1, 7):
        z = 10 + k
        t.set(5, k, z, "cobblestone_stairs", {"facing": "north", "half": "bottom", "shape": "straight", "waterlogged": "false"})
        t.air(5, k + 1, z, 5, 8, z)
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
    # the well at the surface
    for (x, z) in ring:
        t.set(x, 13, z, pick(t, ("cobblestone", 3), ("mossy_cobblestone", 2)))
        t.set(x, 14, z, pick(t, ("cobblestone", 2), ("mossy_cobblestone", 2)))
    t.remove(3, 14, 2)                                         # a stone has fallen in
    for (x, z) in ((2, 2), (4, 2), (2, 4), (4, 4)):
        t.set(x, 15, z, "spruce_fence")
        t.set(x, 16, z, "spruce_fence")
    for x in range(1, 6):                                      # a little pitched roof, one shingle gone
        t.set(x, 17, 1, "spruce_stairs", {"facing": "south", "half": "bottom", "shape": "straight", "waterlogged": "false"})
        t.set(x, 17, 5, "spruce_stairs", {"facing": "north", "half": "bottom", "shape": "straight", "waterlogged": "false"})
        t.set(x, 17, 2, "spruce_planks")
        t.set(x, 17, 4, "spruce_planks")
        t.set(x, 18, 3, "spruce_slab", {"type": "bottom", "waterlogged": "false"})
    t.set(3, 17, 3, "spruce_planks")
    t.remove(4, 17, 1)
    t.set(4, 18, 3, "air")
    for x in range(7):                                         # a stone apron round it, mossy
        for z in range(7):
            if (x in (0, 6) or z in (0, 6) or x in (1, 5) or z in (1, 5)) and not (2 <= x <= 4 and 2 <= z <= 4):
                if t.rng.random() < 0.8:
                    t.set(x, 12, z, pick(t, ("cobblestone", 2), ("mossy_cobblestone", 2), ("gravel", 1)))
    for (x, z) in ((1, 1), (5, 2), (0, 4), (6, 5), (2, 6)):
        t.set(x, 13, z, "moss_carpet")
    t.set(2, 16, 3, "cobweb")
    t.set(4, 16, 4, "cobweb")
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


STAIR = {"half": "bottom", "shape": "straight", "waterlogged": "false"}


def castle_keep(kind, fortified):
    t = Template(15, 15, 15, seed=55 if fortified else 66)
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
    # walls (y 4-11), interior air
    t.air(1, 5, 1, 13, 11, 13)
    for (x, z) in ring(0, 0, 14, 14):
        t.set(x, 4, z, m.found())                             # footing
        for y in range(5, 12):
            if not fortified and y > 9 and t.rng.random() < 0.25:
                continue  # crumbled
            t.set(x, y, z, m.wall())
    for side in range(4):                                      # windows
        for i in (3, 11):
            for y in ((6, 7, 9, 10) if fortified else (7, 10)):
                x, z = [(i, 0), (14, i), (i, 14), (0, i)][side]
                t.set(x, y, z, "iron_bars" if fortified or t.rng.random() < 0.45 else "air")
    crenels(t, m, ring(0, 0, 14, 14), 12)
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
    # doors with jigsaw connectors: the gatehouse south, curtain walls east and west
    t.jigsaw(7, 5, 14, "south_up", "foglurker:castle_keep", "foglurker:castle_gate", base + "/gatehouse", "minecraft:air")
    t.jigsaw(14, 5, 7, "east_up", "foglurker:castle_keep", "foglurker:castle_wing", base + "/wings", "minecraft:air")
    t.jigsaw(0, 5, 7, "west_up", "foglurker:castle_keep", "foglurker:castle_wing", base + "/wings", "minecraft:air")
    for (x, z) in ((7, 14), (14, 7), (0, 7)):
        t.set(x, 6, z, "air")
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
        for (bx, bz) in ((2, 3), (12, 3), (2, 7), (12, 7)):              # beds on the gallery for the people who live here
            t.bed(bx, 9, bz, "south", "red")
        for (x, z) in ((3, 7), (11, 7), (3, 3), (11, 11)):               # lanterns hung under the gallery
            t.set(x, 7, z, "lantern", {"hanging": "true", "waterlogged": "false"})
        for (x, y, z) in ((12, 5, 12), (12, 6, 12), (11, 5, 12), (11, 6, 12), (12, 5, 11), (12, 6, 11)):
            t.set(x, y, z, "bookshelf")
        for z in range(3, 12):                                 # benches along the hall
            if z != 7 and t.get(1, 5, z) is None or t.get(1, 5, z) == "minecraft:air":
                t.set(1, 5, z, "stone_brick_stairs", dict(STAIR, facing="west"))
                t.set(13, 5, z, "stone_brick_stairs", dict(STAIR, facing="east"))
        t.chest(12, 5, 2, LOOT + "fortified_castle", facing="south")   # armory
        t.chest(7, 1, 9, LOOT + "fortified_castle", facing="north")    # treasury
        for (x, y, z) in ((3, 5, 3), (11, 5, 3), (3, 5, 11), (11, 5, 11), (2, 9, 2), (12, 9, 12), (12, 9, 2), (5, 1, 5)):
            t.set(x, y, z, "lantern", {"hanging": "false", "waterlogged": "false"})
        for (x, z) in ((0, 2), (0, 12), (14, 2), (14, 12), (2, 0), (12, 0), (4, 14), (10, 14)):
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
        t.set(5, 5, 8, "bone_block", {"axis": "x"})
        for (x, y, z, side) in ((1, 9, 4, "west"), (1, 8, 4, "west"), (13, 9, 9, "east"), (13, 8, 9, "east"),
                                (13, 7, 9, "east"), (5, 9, 1, "north"), (5, 8, 1, "north"), (9, 10, 13, "south")):
            if t.get(x, y, z) in (None, "minecraft:air"):
                t.set(x, y, z, "vine", {"north": str(side == "north").lower(), "south": str(side == "south").lower(),
                                        "east": str(side == "east").lower(), "west": str(side == "west").lower(), "up": "false"})
    return t


def castle_wing(kind, fortified):
    t = Template(5, 7, 9, seed=77 if fortified else 88)
    m = Mats(fortified, t)
    base = "foglurker:" + kind
    t.air(1, 1, 0, 3, 3, 8)
    for z in range(9):
        for x in (1, 2, 3):
            t.set(x, 0, z, m.found())
        for x in (0, 4):
            for y in range(1, 4):
                if not fortified and y == 3 and t.rng.random() < 0.3:
                    continue
                t.set(x, y, z, m.wall())
        for x in (1, 2, 3):                                    # wall-walk
            if m.keep(0.4):
                t.set(x, 4, z, m.floor())
    crenels(t, m, [(0, z) for z in range(9)], 4)
    crenels(t, m, [(4, z) for z in range(9)], 4)
    if fortified:
        for (x, z) in ((0, 1), (0, 7), (4, 1), (4, 7)):
            t.set(x, 4, z, m.wall())
            t.set(x, 5, z, "soul_lantern", {"hanging": "false", "waterlogged": "false"})
        for z in (2, 4, 6):
            t.set(0, 2, z, "iron_bars")
            t.set(4, 2, z, "iron_bars")
        t.set(2, 1, 4, "lantern", {"hanging": "false", "waterlogged": "false"})
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
    t = Template(9, 11, 9, seed=99 if fortified else 111)
    m = Mats(fortified, t)
    t.air(2, 1, 2, 6, 9, 6)
    for x in range(2, 7):
        for z in range(2, 7):
            t.set(x, 0, z, m.found())
    for (x, z) in ring(1, 1, 7, 7):
        top = 7 if fortified else 4 + t.rng.randint(0, 4)
        for y in range(1, top + 1):
            t.set(x, y, z, m.wall())
    crenels(t, m, ring(1, 1, 7, 7), 8)
    for x in range(2, 7):                                      # a floor halfway up
        for z in range(2, 7):
            if (x, z) != (6, 3) and m.keep(0.5):
                t.set(x, 4, z, m.floor())
    for (x, y, z) in ((4, 3, 7), (1, 3, 4), (7, 3, 4), (4, 6, 7), (1, 6, 4), (7, 6, 4), (1, 6, 2), (7, 6, 6)):
        if t.get(x, y, z):
            t.set(x, y, z, "iron_bars" if fortified or t.rng.random() < 0.4 else "air")
    t.set(4, 1, 1, "air")
    t.set(4, 2, 1, "air")
    for y in range(1, 4):
        t.set(6, y, 3, "ladder", {"facing": "west", "waterlogged": "false"})
    t.set(6, 4, 3, "air")
    t.jigsaw(4, 1, 0, "north_up", "foglurker:castle_tower", "foglurker:castle_tower", "minecraft:empty", "minecraft:air")
    for y in range(1, 4):                                      # a few steps up to the halfway floor
        pass
    if fortified:
        for (x, z) in ((1, 1), (7, 1), (1, 7), (7, 7)):
            t.set(x, 8, z, m.wall())
            t.set(x, 9, z, "soul_lantern", {"hanging": "false", "waterlogged": "false"})
        for (x, y, z) in ((3, 1, 3), (5, 5, 5), (3, 5, 5)):
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
        for (x, y, z) in ((2, 3, 2), (6, 6, 6), (2, 2, 6), (5, 3, 2)):
            if t.get(x, y, z) == "minecraft:air":
                t.set(x, y, z, "cobweb")
    return t


def castle_gatehouse(kind, fortified):
    t = Template(11, 9, 9, seed=121 if fortified else 131)
    m = Mats(fortified, t)
    t.air(4, 1, 0, 6, 5, 8)
    for z in range(9):
        for x in (4, 5, 6):
            t.set(x, 0, z, m.found())
        for x in (3, 7):                                       # passage walls
            for y in range(1, 6):
                if not fortified and y == 5 and t.rng.random() < 0.3:
                    continue
                t.set(x, y, z, m.wall())
        for x in (4, 5, 6):                                    # walkway over the passage
            if m.keep(0.35):
                t.set(x, 6, z, m.floor())
    for (x1, x2) in ((0, 2), (8, 10)):                         # flanking towers
        for x in range(x1, x2 + 1):
            for z in range(5, 9):
                for y in range(1, 6):
                    if x in (x1, x2) or z in (5, 8):
                        if not fortified and y >= 4 and t.rng.random() < 0.3:
                            continue
                        t.set(x, y, z, m.wall())
                    else:
                        t.set(x, y, z, "air")
        crenels(t, m, [(x1, 5), (x2, 5), (x1, 8), (x2, 8), (x1 + 1, 8), (x1 + 1, 5)], 6)
    for x in (4, 5, 6):                                        # the gate: portcullis under an arch
        for y in (3, 4):
            if fortified or t.rng.random() < 0.6:
                t.set(x, y, 8, "iron_bars")
        t.set(x, 5, 8, m.wall())
    crenels(t, m, [(x, 8) for x in range(3, 8)], 7)
    t.jigsaw(5, 1, 0, "north_up", "foglurker:castle_gate", "foglurker:castle_gate", "minecraft:empty", "minecraft:air")
    if fortified:
        for (x, z) in ((0, 5), (2, 5), (8, 5), (10, 5), (3, 2), (7, 2)):
            y = 6 if x not in (3, 7) else 6
            t.set(x, y, z, m.wall())
            t.set(x, y + 1, z, "soul_lantern", {"hanging": "false", "waterlogged": "false"})
        for (x, y, z) in ((1, 1, 6), (9, 1, 6), (4, 1, 1), (6, 1, 1)):
            t.set(x, y, z, "lantern", {"hanging": "false", "waterlogged": "false"})
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
    return t
