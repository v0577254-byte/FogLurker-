"""Writes the Fog Lurker structures (templates, worldgen, loot, tags) into a mod's resources.

usage: python gen.py <resources dir> <1.20.1|1.21.11|1.21.1> [--report]
"""
import json
import os
import sys

import structures as S

VERSIONS = {
    # data version, structure folder, loot folder
    "1.20.1": (3465, "structures", "loot_tables"),
    "1.21.1": (3955, "structure", "loot_table"),
    "1.21.11": (4671, "structure", "loot_table"),
}

# Budgets from the spec: (min, max) solid blocks; we allow 15% either way.
BUDGET = {
    "lookout_tower": (220, 280),
    "abandoned_campfire": (70, 95),
    "hunters_bunker": (260, 330),
    "fog_well": (130, 170),
    "abandoned_castle": (1400, 1800),
    "fortified_castle": (1500, 1900),
}


def build_all():
    out = {
        "lookout_tower": S.lookout_tower(),
        "abandoned_campfire": S.abandoned_campfire(),
        "hunters_bunker": S.hunters_bunker(),
        "fog_well": S.fog_well(),
        "fog_well_village": S.fog_well(village=True),
    }
    for kind, f in (("abandoned_castle", False), ("fortified_castle", True)):
        main = "courtyard_keep" if not f else "main_keep"
        out[f"{kind}/{main}"] = S.castle_keep(kind, f)
        out[f"{kind}/gatehouse"] = S.castle_gatehouse(kind, f)
        out[f"{kind}/wall_segment"] = S.castle_wing(kind, f)
        out[f"{kind}/corner_tower"] = S.castle_tower(kind, f)
    return out


def assembled(t, kind):
    main = "courtyard_keep" if kind == "abandoned_castle" else "main_keep"
    # keep + gatehouse + 2 wings + 2 towers (the keep has three doors)
    return (t[f"{kind}/{main}"].solid_total() + t[f"{kind}/gatehouse"].solid_total()
            + 2 * t[f"{kind}/wall_segment"].solid_total() + 2 * t[f"{kind}/corner_tower"].solid_total())


def report(t):
    ok = True
    for name in ("lookout_tower", "abandoned_campfire", "hunters_bunker", "fog_well"):
        total = t[name].solid_total()
        lo, hi = BUDGET[name]
        good = lo * 0.85 <= total <= hi * 1.15
        ok &= good
        print(f"{name:20s} {total:5d}  budget {lo}-{hi} {"OK" if good else "over the first budget (allowed)"}")
        print("    " + ", ".join(f"{k.split(':')[1]} {v}" for k, v in sorted(t[name].counts().items(), key=lambda kv: -kv[1])))
    for kind in ("abandoned_castle", "fortified_castle"):
        total = assembled(t, kind)
        lo, hi = BUDGET[kind]
        good = lo * 0.85 <= total <= hi * 1.15
        ok &= good
        merged = {}
        main = "courtyard_keep" if kind == "abandoned_castle" else "main_keep"
        for piece, n in ((main, 1), ("gatehouse", 1), ("wall_segment", 2), ("corner_tower", 2)):
            for k, v in t[f"{kind}/{piece}"].counts().items():
                merged[k] = merged.get(k, 0) + v * n
        print(f"{kind:20s} {total:5d}  budget {lo}-{hi} {"OK" if good else "over the first budget (allowed)"} (assembled: keep, gate, 2 walls, 2 towers)")
        print("    " + ", ".join(f"{k.split(':')[1]} {v}" for k, v in sorted(merged.items(), key=lambda kv: -kv[1])))
    return ok


def entity_fix(version):
    def fix(nbt):
        n = dict(nbt)
        armor = n.pop("_armor", None)
        if armor:
            if version == "1.20.1":
                n["ArmorItems"] = [{"id": "minecraft:" + armor[0], "Count": 1}, {"id": "minecraft:" + armor[1], "Count": 1},
                                   {"id": "minecraft:" + armor[2], "Count": 1}, {"id": "minecraft:" + armor[3], "Count": 1}]
            elif version == "1.21.1":
                n["ArmorItems"] = [{"id": "minecraft:" + a, "count": 1} for a in armor]
            else:
                n["equipment"] = {"feet": {"id": "minecraft:" + armor[0], "count": 1},
                                  "legs": {"id": "minecraft:" + armor[1], "count": 1},
                                  "chest": {"id": "minecraft:" + armor[2], "count": 1},
                                  "head": {"id": "minecraft:" + armor[3], "count": 1}}
        return n
    return fix


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


# ------------------------------------------------------------------ worldgen
STRUCTURES = {
    # name: (start pool, size, terrain adaptation, start height, biome tag values)
    "lookout_tower": ("foglurker:lookout_tower/start", 1, "beard_thin", 0,
                      ["#minecraft:is_forest", "#minecraft:is_taiga", "minecraft:windswept_hills",
                       "minecraft:windswept_forest", "minecraft:windswept_gravelly_hills"]),
    "abandoned_campfire": ("foglurker:abandoned_campfire/start", 1, "beard_thin", -1,
                           ["#minecraft:is_forest", "#minecraft:is_taiga"]),
    "abandoned_castle": ("foglurker:abandoned_castle/keep", 3, "beard_thin", -5,
                         ["minecraft:dark_forest", "minecraft:plains", "#minecraft:is_taiga"]),
    "fortified_castle": ("foglurker:fortified_castle/keep", 3, "beard_thin", -5,
                         ["minecraft:plains", "minecraft:meadow", "#minecraft:is_forest"]),
    "hunters_bunker": ("foglurker:hunters_bunker/start", 1, "bury", -6,
                       ["#minecraft:is_hill", "#minecraft:is_mountain", "#minecraft:is_taiga"]),
    "fog_well": ("foglurker:fog_well/start", 1, "none", -13,
                 ["minecraft:plains", "minecraft:sunflower_plains", "#minecraft:is_taiga", "#minecraft:is_savanna"]),
}

SETS = {
    # set name: (structures with weights, spacing, separation, salt, exclusion)
    "castles": ([("abandoned_castle", 3), ("fortified_castle", 1)], 72, 28, 1864207193, ("minecraft:villages", 6)),
    "wilderness": ([("abandoned_campfire", 5), ("lookout_tower", 3), ("hunters_bunker", 3)], 24, 9, 507399271,
                   ("foglurker:castles", 5)),
    "fog_wells": ([("fog_well", 1)], 44, 16, 942817603, ("foglurker:castles", 4)),
}

TAGS = {
    "lurker_hotspots": ["foglurker:lookout_tower", "foglurker:abandoned_castle", "foglurker:hunters_bunker"],
    "warded_strongholds": ["foglurker:fortified_castle"],
    "fog_wells": ["foglurker:fog_well"],
}


def pool(elements):
    return {"fallback": "minecraft:empty", "elements": [
        {"weight": w, "element": {"element_type": "minecraft:single_pool_element", "location": loc,
                                  "projection": "rigid", "processors": "minecraft:empty"}} for loc, w in elements]}


def worldgen(res, version):
    d = os.path.join(res, "data", "foglurker")
    for name, (start, size, adapt, height, biomes) in STRUCTURES.items():
        s = {"type": "minecraft:jigsaw", "biomes": f"#foglurker:has_structure/{name}", "step": "surface_structures",
             "spawn_overrides": {}, "terrain_adaptation": adapt, "start_pool": start, "size": size,
             "start_height": {"absolute": height}, "project_start_to_heightmap": "WORLD_SURFACE_WG",
             "max_distance_from_center": 80, "use_expansion_hack": False}
        if name == "hunters_bunker":
            s["step"] = "underground_structures"
        write_json(os.path.join(d, "worldgen", "structure", name + ".json"), s)
        write_json(os.path.join(d, "tags", "worldgen", "biome", "has_structure", name + ".json"),
                   {"replace": False, "values": biomes})
    for name in ("lookout_tower", "abandoned_campfire", "hunters_bunker", "fog_well"):
        write_json(os.path.join(d, "worldgen", "template_pool", name, "start.json"), pool([("foglurker:" + name, 1)]))
    for kind in ("abandoned_castle", "fortified_castle"):
        main = "courtyard_keep" if kind == "abandoned_castle" else "main_keep"
        for p, loc in (("keep", main), ("gatehouse", "gatehouse"), ("wings", "wall_segment"), ("towers", "corner_tower")):
            write_json(os.path.join(d, "worldgen", "template_pool", kind, p + ".json"), pool([(f"foglurker:{kind}/{loc}", 1)]))
    for name, (structs, spacing, sep, salt, excl) in SETS.items():
        placement = {"type": "minecraft:random_spread", "spacing": spacing, "separation": sep, "salt": salt}
        if excl:
            placement["exclusion_zone"] = {"other_set": excl[0], "chunk_count": excl[1]}
        write_json(os.path.join(d, "worldgen", "structure_set", name + ".json"),
                   {"structures": [{"structure": "foglurker:" + s, "weight": w} for s, w in structs], "placement": placement})
    for tag, values in TAGS.items():
        write_json(os.path.join(d, "tags", "worldgen", "structure", tag + ".json"), {"replace": False, "values": values})


# ------------------------------------------------------------------ loot
LOGBOOK = ["Ranger's log, day 3.\nThe fog came up the valley again at dusk. It does not move with the wind.",
           "Day 6.\nSomething walked the tree line all night. Same pace as me when I walk the deck.",
           "Day 9.\nThe soul lanterns help. Whatever it is will not step into their light.",
           "Day 11.\nIt wore Tom's face today. Tom left on day 2.",
           "Day 12.\nIf the fog rushes in for no reason, do not wait for it to lift. Get to the light."]
BUNKER_NOTE = ["The blade is spent.\n\nFog of the day, or fog of the night, laid beside it on the bench, and it bites again.",
               "Keep the teeth. It hates its own teeth."]


def item(name, weight=1, count=None, damage=None, enchant=False, extra=None):
    e = {"type": "minecraft:item", "name": name, "weight": weight}
    fns = []
    if count:
        fns.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": count[0], "max": count[1]}})
    if damage:
        fns.append({"function": "minecraft:set_damage", "damage": {"type": "minecraft:uniform", "min": damage[0], "max": damage[1]}})
    if enchant:
        fns.append({"function": "minecraft:enchant_randomly"})
    if extra:
        fns += extra
    if fns:
        e["functions"] = fns
    return e


def snbt_str(text):
    """A double-quoted SNBT string."""
    return '"' + text.replace("\\", "\\\\").replace('"', '\\"') + '"'


def book(version, title, author, pages):
    if version == "1.20.1":
        tag = "{title:%s,author:%s,resolved:1b,pages:[%s]}" % (
            snbt_str(title), snbt_str(author), ",".join(snbt_str(json.dumps({"text": p})) for p in pages))
        return [{"function": "minecraft:set_nbt", "tag": tag}]
    return [{"function": "minecraft:set_written_book_pages", "pages": pages, "mode": "replace_all"},
            {"function": "minecraft:set_book_cover", "title": title, "author": author}]


def loot(res, version, loot_dir):
    d = os.path.join(res, "data", "foglurker", loot_dir, "chests")
    F = "foglurker:"

    def table(*pools):
        return {"type": "minecraft:chest", "pools": list(pools)}

    def p(rolls, *entries):
        return {"rolls": {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]} if isinstance(rolls, tuple) else rolls,
                "entries": list(entries)}

    tables = {
        "lookout_tower": table(
            p(1, item("minecraft:written_book", extra=book(version, "Ranger's Logbook", "Ranger Hale", LOGBOOK))),
            p((2, 4), item(F + "foggy_stick", 3, (1, 2)), item("minecraft:spyglass", 2), item("minecraft:compass", 2),
              item("minecraft:paper", 4, (2, 6)), item("minecraft:book", 3, (1, 2)), item("minecraft:bread", 3, (1, 3)),
              item("minecraft:soul_torch", 2, (2, 5)))),
        "abandoned_campfire": table(
            p((2, 4), item("minecraft:cooked_beef", 3, (1, 3)), item("minecraft:cooked_porkchop", 3, (1, 3)),
              item("minecraft:cooked_salmon", 2, (1, 3)), item("minecraft:coal", 4, (2, 6)),
              item("minecraft:iron_nugget", 3, (3, 9)), item("minecraft:iron_axe", 1, damage=(0.2, 0.6)),
              item("minecraft:iron_shovel", 1, damage=(0.2, 0.6)), item(F + "foggy_stick", 2, (1, 2))),
            p(1, item(F + "fog_of_the_day", 1), item(F + "fog_of_the_night", 1), {"type": "minecraft:empty", "weight": 1})),
        "abandoned_castle": table(
            p((1, 2), item(F + "ingot_of_the_fog", 3, (1, 2)), item(F + "teeth_of_the_lurker", 3, (1, 3)),
              item(F + "protector_of_the_fog", 1, damage=(0.15, 0.4))),
            p((2, 5), item("minecraft:book", 2, enchant=True), item("minecraft:iron_sword", 3, damage=(0.3, 0.8)),
              item("minecraft:iron_chestplate", 2, damage=(0.3, 0.8)), item("minecraft:diamond_sword", 1, damage=(0.4, 0.8)),
              item("minecraft:iron_ingot", 4, (2, 5)), item("minecraft:gold_ingot", 3, (1, 4)), item("minecraft:bone", 4, (2, 6)))),
        "fortified_castle": table(
            p((1, 2), item(F + "protector_of_the_fog", 2), item(F + "ingot_of_the_fog", 3, (1, 3))),
            p((3, 6), item("minecraft:diamond_chestplate", 1), item("minecraft:diamond_sword", 1),
              item("minecraft:iron_helmet", 3), item("minecraft:iron_chestplate", 3), item("minecraft:iron_leggings", 3),
              item("minecraft:iron_boots", 3), item("minecraft:iron_sword", 3), item("minecraft:shield", 2),
              item("minecraft:golden_apple", 2, (1, 2)), item("minecraft:emerald", 4, (2, 7)),
              item("minecraft:soul_lantern", 2, (1, 3)))),
        "hunters_bunker": table(
            p(1, item("minecraft:written_book", extra=book(version, "Hunter's Note", "?", BUNKER_NOTE))),
            p((2, 4), item(F + "teeth_of_the_lurker", 3, (1, 3)), item(F + "protector_of_the_fog", 2, damage=(0.1, 0.3)),
              item("minecraft:iron_ingot", 4, (1, 4)), item("minecraft:arrow", 3, (4, 12)), item("minecraft:leather", 2, (1, 3)))),
        "fog_well": table(
            p((1, 2), item(F + "fog_of_the_night", 3), item(F + "foggy_stick", 3, (1, 2))),
            p((2, 4), item("minecraft:bone", 4, (1, 5)), item("minecraft:rotten_flesh", 4, (1, 6)),
              item("minecraft:string", 2, (1, 3)))),
    }
    # One extra roll in every chest: each of the mod's own items in it has a 2% chance there (so they turn up 1-2% more
    # often than from the pools above alone).
    bonus = {
        "lookout_tower": [item(F + "foggy_stick", 2)],
        "abandoned_campfire": [item(F + "foggy_stick", 2), item(F + "fog_of_the_day", 2), item(F + "fog_of_the_night", 2)],
        "abandoned_castle": [item(F + "ingot_of_the_fog", 2), item(F + "teeth_of_the_lurker", 2),
                             item(F + "protector_of_the_fog", 2, damage=(0.15, 0.4))],
        "fortified_castle": [item(F + "protector_of_the_fog", 2), item(F + "ingot_of_the_fog", 2)],
        "hunters_bunker": [item(F + "teeth_of_the_lurker", 2), item(F + "protector_of_the_fog", 2, damage=(0.1, 0.3))],
        "fog_well": [item(F + "fog_of_the_night", 2), item(F + "foggy_stick", 2)],
    }
    for name, entries in bonus.items():
        empty = 100 - sum(e["weight"] for e in entries)
        tables[name]["pools"].append(p(1, *entries, {"type": "minecraft:empty", "weight": empty}))
    for name, data in tables.items():
        write_json(os.path.join(d, name + ".json"), data)


def main():
    res, version = sys.argv[1], sys.argv[2]
    data_version, struct_dir, loot_dir = VERSIONS[version]
    t = build_all()
    ok = report(t)
    if "--report" in sys.argv:
        sys.exit(0 if ok else 1)
    base = os.path.join(res, "data", "foglurker", struct_dir)
    for name, tmpl in t.items():
        path = os.path.join(base, name + ".nbt")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        tmpl.save(path, data_version, entity_fix(version))
    worldgen(res, version)
    loot(res, version, loot_dir)
    print("wrote", len(t), "templates for", version, "to", base)


if __name__ == "__main__":
    main()
