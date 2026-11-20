# Fog Lurker for Minecraft 1.21.11 (Fabric) — 0.2.0 fabric beta

The whole Fog Lurker mod (the same one as the Forge 1.20.1 version in the folder above, public line), ported to
**Fabric** and **Minecraft 1.21.11**: the creature and its disguises, the fog and mist, hauntings, sleep paralysis,
the night meeting, the Protector of the Fog, learning, the copied chat (with an optional Gemini key) and the
opt-in voice snippets. Every command and config option works the same; see the main README for what it all does.

## New in 0.2.0 (same as Forge 1.5.0)

- It **rolls to appear every 10 minutes** instead of 5, so there is time to take in what just happened (an old config
  file still at 5 is moved to 10 once).
- **Jumpscares** among the hauntings: the fog rushes in around you for no reason and lifts again, a scream right behind
  you with a short jolt (the screen goes dark red for a moment, never a bright flash), a roar far off, a whisper at your
  ear, a heartbeat speeding up and stopping, something running past behind you. Config `jumpscares`.
- **Villagers sense it**: within 32 blocks (48 while it attacks), in any form, they run into their houses and hide, as
  when a bell rings. Config `villagersHide`.
- **AFK** only ends when you look around properly (45 degrees or more) or walk at least a block: a nudge does not.

## Installing

1. Install **Fabric Loader 0.20.1 or newer** for Minecraft **1.21.11** (in Prism Launcher: a 1.21.11 instance with Fabric).
2. Put **Fabric API** (0.141 or newer for 1.21.11) and `FogLurker-0.2.0-fabric-beta.jar` in `mods/`, on the server and
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
- **No Discord Rich Presence** in this build.

## Building and testing

Gradle runs on **Java 25** (Fabric Loom needs it); the mod is built for Java 21.

```bash
./gradlew build               # -> build/libs/FogLurker-0.2.0-fabric-beta.jar
./gradlew runGametest         # all 53 in-game tests on a game-test server (build/gametest/junit.xml)
./gradlew runClient -Pshowcase   # dev check: makes a world, runs the commands, saves screenshots
```
