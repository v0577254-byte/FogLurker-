# Fog Lurker: Forms (Minecraft 1.21.11)

The Fog Lurker as a still figure for videos, in any of its shapes, for Minecraft **1.21.11** (Forge 61.2.x). It
stands completely still: it never moves, never turns its body and makes no sounds. Only its head turns (up to
all the way round), to look you in the eyes. It takes no damage.

| Command (op / cheats on) | What appears at your feet |
| --- | --- |
| `/foglurker spawn` | the pale crawler (the Fog Lurker itself) |
| `/foglurker spawn crawler` | the pale crawler |
| `/foglurker spawn player` | a copy of you, wearing your skin |
| `/foglurker spawn player <name>` | a copy of another online player, wearing their skin |
| `/foglurker spawn cow` / `sheep` / `pig` / `chicken` | that animal |
| `/foglurker spawn zombie` | a zombie |
| `/foglurker spawn wolf` | a wild wolf |
| `/foglurker spawn dog` | a tamed wolf (red collar) |

Each one spawns facing you; step back to see it. They stay after saving and reloading the world.
`/kill @e[type=foglurker:fog_lurker]` removes them all.

This is its own jar, separate from the crawler-only showcase (`../foglurker-1.21.11-crawler`) and the Fabric
port. It includes the crawler, so use it instead of the showcase jar, not alongside it (both are mod id
`foglurker`).

## Building

Needs Java 21 (the game) and Java 25 (ForgeGradle 7's setup tool).

```bash
./gradlew build                  # -> build/libs/FogLurker-Forms-1.21.11-<version>.jar
./gradlew runClient -Pshowcase   # dev check: spawns every type with the command, walks round them, checks that
                                 # nothing moved or turned its body and every head faces you, saves screenshots
```
