# Fog Lurker: Forms (Minecraft 1.21.11, Fabric)

The Fog Lurker as a still figure for videos, in any of its shapes, for Minecraft **1.21.11** on **Fabric** (Loader 0.20+,
Fabric API needed). It stands completely still: it never moves, never turns its body and makes no sounds. Only its
head turns (up to all the way round), to look you in the eyes. It takes no damage.

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

This is its own jar: the Forge 1.21.11 Forms mod (`../foglurker-1.21.11-forms`) ported to Fabric. It is separate
from the full Fabric port (`../foglurker-1.21.11-fabric`, the 0.1.x fabric beta), which it does not change. Use one
or the other, not both together (both are mod id `foglurker`).

## Building

Gradle runs on Java 25 (Fabric Loom needs it); the mod is built for Java 21.

```bash
./gradlew build                  # -> build/libs/FogLurker-Forms-1.21.11-<version>.jar
./gradlew runClient -Pshowcase   # dev check: spawns every type with the command, walks round them, checks that
                                 # nothing moved or turned its body and every head faces you, saves screenshots
```
