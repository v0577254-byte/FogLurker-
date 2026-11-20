# Fog Lurker: Forms (Minecraft 1.21.11)

The Fog Lurker as a still figure for videos, in any of its shapes (or as almost any mob), for Minecraft **1.21.11** (Forge 61.2.x). It
stands completely still: it never moves, never turns its body and makes no sounds. Only its head turns (up to
all the way round), to look you in the eyes. It takes no damage.

| Command (op / cheats on) | What appears at your feet |
| --- | --- |
| `/foglurker spawn` or `/foglurker spawn crawler` | the pale crawler (the Fog Lurker itself) |
| `/foglurker spawn player` | a copy of you, wearing your skin |
| `/foglurker spawn player <name>` | a copy of another online player, wearing their skin |
| `/foglurker spawn <type>` | any of the types below (press Tab to see them all) |

**Types** (90 in all; bosses (the Wither, the Ender Dragon, the Elder Guardian, the Warden) and creatures that
only live in water are left out):

- **Villagers:** `villager` (no job), `armorer`, `butcher`, `cartographer`, `cleric`, `farmer`, `fisherman`,
  `fletcher`, `leatherworker`, `librarian`, `mason`, `nitwit`, `shepherd`, `toolsmith`, `weaponsmith`,
  `wandering_trader` (also `merchant`)
- **Animals:** `cow`, `mooshroom`, `sheep`, `pig`, `chicken`, `rabbit`, `goat`, `wolf`, `dog` (a tamed wolf, red
  collar), `cat`, `ocelot`, `fox`, `panda`, `polar_bear`, `bee`, `frog`, `turtle`, `axolotl`, `armadillo`,
  `sniffer`, `parrot`, `bat`, `strider`, `allay`, `happy_ghast`
- **Horses and mounts:** `horse`, `donkey`, `mule`, `skeleton_horse`, `zombie_horse`, `llama`, `trader_llama`,
  `camel`, `camel_husk`
- **Golems:** `iron_golem`, `snow_golem`, `copper_golem`
- **Hostile:** `zombie`, `husk`, `drowned`, `zombie_villager`, `skeleton`, `stray`, `bogged`, `parched`,
  `wither_skeleton`, `creeper`, `spider`, `cave_spider`, `enderman`, `endermite`, `silverfish`, `witch`, `slime`,
  `magma_cube`, `blaze`, `ghast`, `phantom`, `breeze`, `guardian`, `shulker`, `creaking`, `pillager`,
  `vindicator`, `evoker`, `vex`, `ravager`, `piglin`, `piglin_brute`, `zombified_piglin`, `hoglin`, `zoglin`

Each one spawns facing you; step back to see it. Mobs that usually carry a weapon hold it (skeletons a bow, the
pillager a crossbow, piglins a golden sword...). Slimes and magma cubes are the big size. Idle animations (arm
sway, flapping wings) hold still too: nothing moves but the head. A few mobs have no separate head (their face is
their body): the slime, magma cube, ghast, happy ghast, phantom, bee, frog, breeze, endermite and silverfish turn as
a whole to look at you. They stay after saving and reloading the world.
`/kill @e[type=foglurker:fog_lurker]` removes them all.

This is its own jar, separate from the crawler-only showcase (`../foglurker-1.21.11-crawler`) and the Fabric
port. It includes the crawler, so use it instead of the showcase jar, not alongside it (both are mod id
`foglurker`). The same mod for Fabric 1.21.11 is in `../foglurker-1.21.11-forms-fabric`.

## Building

Needs Java 21 (the game) and Java 25 (ForgeGradle 7's setup tool).

```bash
./gradlew build                  # -> build/libs/FogLurker-Forms-1.21.11-<version>.jar
./gradlew runClient -Pshowcase   # dev check: spawns every type with the command, walks round them, checks that
                                 # nothing moved or turned its body and every head faces you, saves screenshots
```
