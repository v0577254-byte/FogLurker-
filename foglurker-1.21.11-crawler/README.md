# Fog Lurker: Crawler (Minecraft 1.21.11)

The Fog Lurker's pale crawler for Minecraft **1.21.11** (Forge 61.2.x), made for filming: the same texture and
crouched posture as the full mod, standing completely still. Its body never moves or turns; only its head follows
you, looking you in the eyes (all the way round if you walk behind it). It makes no sounds and takes no damage.

- `/foglurker spawn` (op / cheats on): puts one at your feet, facing you. Step back to see it.
- `/kill @e[type=foglurker:crawler]` removes them.

The full mod (disguises, fog, hauntings...) is the 1.20.1 one in the folder above.

## Building

Needs Java 21 (the game) and Java 25 (ForgeGradle 7's setup tool).

```bash
./gradlew build                  # -> build/libs/FogLurker-Crawler-1.21.11-<version>.jar
./gradlew runClient -Pshowcase   # dev check: spawns one, walks round it, saves screenshots to run/screenshots
```
