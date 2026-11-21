"""A tiny structure-template builder: blocks by position, palettes, block entities, entities, NBT out."""
import random
import nbtlib
from nbtlib import Compound, List, Int, String, Double, Byte, File

# Blocks that join to their neighbours (fences, panes, bars): their side properties are worked out at the end.
CONNECTS = ("_fence", "_pane", "iron_bars")
SOLID_HINT = ("planks", "log", "bricks", "stone", "cobblestone", "andesite", "tuff", "deepslate", "wool", "dirt",
              "podzol", "glass", "concrete")


class Template:
    def __init__(self, sx, sy, sz, seed=1):
        self.size = (sx, sy, sz)
        self.blocks = {}      # (x,y,z) -> (name, props dict, nbt dict or None)
        self.entities = []    # (x,y,z, nbt dict)
        self.rng = random.Random(seed)

    def inside(self, x, y, z):
        return 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]

    def set(self, x, y, z, name, props=None, nbt=None):
        if not self.inside(x, y, z):
            raise ValueError(f"{name} at {(x, y, z)} is outside {self.size}")
        if not name.startswith("minecraft:") and ":" not in name:
            name = "minecraft:" + name
        self.blocks[(x, y, z)] = (name, dict(props or {}), nbt)

    def get(self, x, y, z):
        b = self.blocks.get((x, y, z))
        return b[0] if b else None

    def fill(self, x1, y1, z1, x2, y2, z2, name, props=None):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, name, props)

    def air(self, x1, y1, z1, x2, y2, z2):
        self.fill(x1, y1, z1, x2, y2, z2, "air")

    def remove(self, x, y, z):
        self.blocks.pop((x, y, z), None)

    def chest(self, x, y, z, loot, facing="north", barrel=False):
        if barrel:
            self.set(x, y, z, "barrel", {"facing": "up", "open": "false"}, {"id": "minecraft:barrel", "LootTable": loot})
        else:
            self.set(x, y, z, "chest", {"facing": facing, "type": "single", "waterlogged": "false"},
                     {"id": "minecraft:chest", "LootTable": loot})

    def jigsaw(self, x, y, z, orientation, name, target, pool, final_state, joint="aligned"):
        self.set(x, y, z, "jigsaw", {"orientation": orientation},
                 {"id": "minecraft:jigsaw", "name": name, "target": target, "pool": pool,
                  "final_state": final_state, "joint": joint})

    def bed(self, x, y, z, facing, color="red"):
        """Foot at (x,y,z), head one block towards `facing`."""
        dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing]
        self.set(x, y, z, f"{color}_bed", {"facing": facing, "occupied": "false", "part": "foot"})
        self.set(x + dx, y, z + dz, f"{color}_bed", {"facing": facing, "occupied": "false", "part": "head"})

    def entity(self, x, y, z, nbt):
        self.entities.append((x, y, z, nbt))

    # ------------------------------------------------------------ fences, panes and bars join their neighbours
    def _joins(self, name, other):
        if other is None or other == "minecraft:air":
            return False
        if "fence" in name and "fence" in other:
            return True
        if ("pane" in name or "iron_bars" in name) and ("pane" in other or "iron_bars" in other):
            return True
        if other.endswith("_slab") or other.endswith("_stairs") or "ladder" in other or "lantern" in other \
                or "torch" in other or "bed" in other or "cobweb" in other or "chest" in other or "jigsaw" in other:
            return False
        return any(h in other for h in SOLID_HINT)

    def connect(self):
        for (x, y, z), (name, props, nbt) in list(self.blocks.items()):
            if not any(c in name for c in CONNECTS):
                continue
            for side, (dx, dz) in {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}.items():
                props[side] = "true" if self._joins(name, self.get(x + dx, y, z + dz)) else "false"
            props["waterlogged"] = "false"

    # ------------------------------------------------------------ counting and writing
    def counts(self):
        c = {}
        for name, _, _ in self.blocks.values():
            if name in ("minecraft:air", "minecraft:jigsaw", "minecraft:structure_void"):
                continue
            c[name] = c.get(name, 0) + 1
        return c

    def solid_total(self):
        return sum(self.counts().values())

    def save(self, path, data_version, entity_fix=None):
        self.connect()
        palette = []
        index = {}
        blocks = List[Compound]()
        for (x, y, z) in sorted(self.blocks, key=lambda p: (p[1], p[2], p[0])):
            name, props, nbt = self.blocks[(x, y, z)]
            key = (name, tuple(sorted(props.items())))
            if key not in index:
                index[key] = len(palette)
                entry = Compound({"Name": String(name)})
                if props:
                    entry["Properties"] = Compound({k: String(v) for k, v in sorted(props.items())})
                palette.append(entry)
            b = Compound({"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(index[key])})
            if nbt:
                b["nbt"] = Compound({k: String(v) for k, v in nbt.items()})
            blocks.append(b)
        ents = List[Compound]()
        for (x, y, z, nbt) in self.entities:
            n = entity_fix(nbt) if entity_fix else nbt
            ents.append(Compound({
                "pos": List[Double]([Double(x + 0.5), Double(y), Double(z + 0.5)]),
                "blockPos": List[Int]([Int(x), Int(y), Int(z)]),
                "nbt": to_nbt(n)}))
        root = Compound({
            "DataVersion": Int(data_version),
            "size": List[Int]([Int(s) for s in self.size]),
            "palette": List[Compound](palette),
            "blocks": blocks,
            "entities": ents,
        })
        File(root, gzipped=True).save(path)


def to_nbt(v):
    """Python values to NBT: dict -> Compound, list -> List, bool -> Byte, int -> Int, float -> Double, str -> String."""
    if isinstance(v, Byte):                                    # (an NBT number already: keep its type)
        return v
    if isinstance(v, dict):
        return Compound({k: to_nbt(x) for k, x in v.items()})
    if isinstance(v, list):
        items = [to_nbt(x) for x in v]
        if not items:
            return List[Compound]()
        return List[type(items[0])](items)
    if isinstance(v, bool):
        return Byte(1 if v else 0)
    if isinstance(v, int):
        return Int(v)
    if isinstance(v, float):
        return Double(v)
    return String(str(v))
