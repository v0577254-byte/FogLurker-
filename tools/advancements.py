"""The Fog Lurker's own advancement tab, written for one Minecraft version into a resources folder.

    python3 tools/advancements.py <resources folder> <1.20.1|1.21.1|1.21.11>

Some are granted by the game's own triggers (killing it, being Mauled, finding its places, the items); the ones
marked CODE use the "impossible" trigger and are granted by the mod itself (com.foglurker...ModAdvancements).
The names and descriptions go into lang/en_us.json.
"""
import json
import os
import sys

res, version = sys.argv[1], sys.argv[2]
V20 = version == "1.20.1"
V2111 = version == "1.21.11"
ADV_DIR = os.path.join(res, "data", "foglurker", "advancements" if V20 else "advancement")
CODE = {"trigger": "minecraft:impossible"}


def icon(item):
    return {"item": item} if V20 else {"id": item}


def has(*items):
    if V20:
        return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": list(items)}]}}
    return {"trigger": "minecraft:inventory_changed",
            "conditions": {"items": [{"items": items[0] if len(items) == 1 else list(items)}]}}


def found(structure):
    key = "structure" if V20 else "structures"
    return {"trigger": "minecraft:location", "conditions": {"player": [
        {"condition": "minecraft:entity_properties", "entity": "this",
         "predicate": {"location": {key: "foglurker:" + structure}}}]}}


KILLED = {"trigger": "minecraft:player_killed_entity", "conditions": {"entity": [
    {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"type": "foglurker:fog_lurker"}}]}}
MAULED = {"trigger": "minecraft:effects_changed", "conditions": {"effects": {"foglurker:mauled": {}}}}

PLACES = [  # structure, advancement, icon, title, description
    ("lookout_tower", "lookout", "minecraft:spyglass", "Lookout", "Find a lookout tower. From up there you can see it too"),
    ("abandoned_campfire", "cold_ashes", "minecraft:campfire", "Cold Ashes", "Find an abandoned campfire"),
    ("abandoned_castle", "fallen_keep", "minecraft:cracked_stone_bricks", "Fallen Keep", "Find an abandoned castle"),
    ("fortified_castle", "last_light", "minecraft:soul_lantern", "Last Light", "Find a fortified castle, where they still keep it out"),
    ("hunters_bunker", "dug_in", "minecraft:iron_shovel", "Dug In", "Find a hunters' bunker"),
    ("fog_well", "something_in_the_well", "minecraft:cobweb", "Something in the Well", "Find a fog-choked well"),
]

# id: (parent, icon, title, description, criteria, frame, hidden, xp)
ADVANCEMENTS = {
    "root": (None, "foglurker:fog_of_the_night", "The Fog Lurker",
             "Something walks in the fog. Something that wears faces", {"joined": {"trigger": "minecraft:tick"}}, "task", False, 0),
    "first_sighting": ("root", "foglurker:teeth_of_the_lurker", "Did You See That?",
                       "See the Fog Lurker as it really is", {"done": CODE}, "task", False, 0),
    "still_breathing": ("first_sighting", "minecraft:feather", "Still Breathing",
                        "Get away from the Fog Lurker while it hunts you", {"done": CODE}, "goal", False, 0),
    "the_fog_lifts": ("still_breathing", "foglurker:fog_of_the_day", "The Fog Lifts",
                      "Kill the Fog Lurker", {"killed": KILLED}, "challenge", False, 100),
    "teeth_in_the_leg": ("first_sighting", "minecraft:bone", "Teeth in the Leg",
                         "Be Mauled by its bite", {"mauled": MAULED}, "task", False, 0),
    "for_whom_the_bell_tolls": ("first_sighting", "minecraft:bell", "For Whom the Bell Tolls",
                                "Drive it away by ringing a bell", {"done": CODE}, "task", False, 0),
    "hmm_did_i_do_that": ("root", "minecraft:red_bed", "Hmm Did I Do That?",
                          "Wake up from sleep paralysis", {"done": CODE}, "task", False, 0),
    "can_you_see_me": ("hmm_did_i_do_that", "minecraft:ender_eye", "€AN Y0UU $EE M33?",
                       "...yes.", {"done": CODE}, "challenge", True, 50),
    "pressed_fog": ("root", "foglurker:ingot_of_the_fog", "Pressed Fog",
                    "Get an Ingot of the Fog", {"ingot": has("foglurker:ingot_of_the_fog")}, "task", False, 0),
    "forged_in_fog": ("pressed_fog", "foglurker:protector_of_the_fog", "Forged in Fog",
                      "Craft the Protector of the Fog", {"sword": has("foglurker:protector_of_the_fog")}, "goal", False, 0),
    "begone": ("forged_in_fog", "minecraft:echo_shard", "Begone!",
               "Make it flee with a scare burst", {"done": CODE}, "goal", False, 0),
    "off_the_map": ("root", "minecraft:map", "Off the Map",
                    "Find one of the places it haunts", {s: found(s) for s, *_ in PLACES}, "task", False, 0),
}
for structure, adv, item, title, desc in PLACES:
    ADVANCEMENTS[adv] = ("off_the_map", item, title, desc, {"found": found(structure)}, "task", False, 0)
ADVANCEMENTS["mapped_the_fog"] = ("off_the_map", "minecraft:filled_map", "Mapped the Fog",
                                  "Find every kind of place it haunts", {s: found(s) for s, *_ in PLACES}, "challenge", False, 100)
ALL_REQUIRED = {"mapped_the_fog"}  # every criterion needed (the rest: any one)


def write(name, data):
    path = os.path.join(ADV_DIR, name + ".json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


lang = {}
for name, (parent, item, title, desc, criteria, frame, hidden, xp) in ADVANCEMENTS.items():
    display = {"icon": icon(item), "title": {"translate": f"advancements.foglurker.{name}.title"},
               "description": {"translate": f"advancements.foglurker.{name}.description"},
               "frame": frame, "show_toast": True, "announce_to_chat": name != "root", "hidden": hidden}
    if parent is None:
        display["background"] = "minecraft:block/gray_concrete_powder" if V2111 \
            else "minecraft:textures/block/gray_concrete_powder.png"
        display["show_toast"] = False
    adv = {"display": display, "criteria": criteria}
    if parent:
        adv["parent"] = "foglurker:" + parent
    if name in ALL_REQUIRED:
        adv["requirements"] = [[c] for c in criteria]
    else:
        adv["requirements"] = [list(criteria)]
    if xp:
        adv["rewards"] = {"experience": xp}
    adv["sends_telemetry_event"] = False
    write(name, adv)
    lang[f"advancements.foglurker.{name}.title"] = title
    lang[f"advancements.foglurker.{name}.description"] = desc

# Armour of the Fog's own (Codex's), moved into this tab under the ingot.
asc_path = os.path.join(ADV_DIR, "ascension_from_the_fog.json")
if os.path.exists(asc_path):
    asc = json.load(open(asc_path))
    asc["parent"] = "foglurker:pressed_fog"
    write("ascension_from_the_fog", asc)

lang_path = os.path.join(res, "assets", "foglurker", "lang", "en_us.json")
current = json.load(open(lang_path))
current.update(lang)
with open(lang_path, "w") as f:
    json.dump(current, f, indent=2, ensure_ascii=False)
    f.write("\n")
print(f"{version}: {len(ADVANCEMENTS)} advancements written to {ADV_DIR}")
