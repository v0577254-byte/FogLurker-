# Fog Lurker for Minecraft 1.21.11 (Fabric) — 0.1.0 fabric beta

The whole Fog Lurker mod (the same one as the Forge 1.20.1 version in the folder above, public line), ported to
**Fabric** and **Minecraft 1.21.11**: the creature and its disguises, the fog and mist, hauntings, sleep paralysis,
the night meeting, the Protector of the Fog, learning, the copied chat (with an optional Gemini key) and the
opt-in voice snippets. Every command and config option works the same; see the main README for what it all does.

## Installing

1. Install **Fabric Loader 0.20.1 or newer** for Minecraft **1.21.11** (in Prism Launcher: a 1.21.11 instance with Fabric).
2. Put **Fabric API** (0.141 or newer for 1.21.11) and `FogLurker-0.1.0-fabric-beta.jar` in `mods/`, on the server and
   every client.
3. Optional, for voices: **Simple Voice Chat** (Fabric, 1.21.11) on the server and every client.

The config files are the same as on Forge: `config/foglurker-common.toml` and `config/foglurker-client.toml`.

## What is different from the Forge 1.20.1 version

- **Recharging the Protector:** Minecraft 1.21 trims the crafting grid to the filled slots, so the sword no longer
  has to sit in the middle: the sword and The Fog of the Day or of the Night anywhere in the grid recharge it.
- **The fog** goes into Minecraft's own fog step, at the last moment before it reaches the graphics card (after other
  mods have had their say), so Sodium and Distant Horizons keep it. The Forge version's own full-screen fog redraw (for
  mods that switch Minecraft's fog off) is not in this beta; if something still switches the fog off (a shader
  pack), the fog veil over the screen shows it, as before.
- **Claim mods:** Fabric has no event for a mob breaking a block, so when the attacking form claws through a wall it
  checks the `mob_griefing` gamerule, `breakBlocks` and its own protected blocks, but not claim mods. (Hauntings that
  put out a torch still ask claim mods first, as a fake player breaking the block.)
- **No Discord Rich Presence** in this build.

## Building and testing

Gradle runs on **Java 25** (Fabric Loom needs it); the mod is built for Java 21.

```bash
./gradlew build               # -> build/libs/FogLurker-0.1.0-fabric-beta.jar
./gradlew runGametest         # all 52 in-game tests on a game-test server (build/gametest/junit.xml)
./gradlew runClient -Pshowcase   # dev check: makes a world, runs the commands, saves screenshots
```
