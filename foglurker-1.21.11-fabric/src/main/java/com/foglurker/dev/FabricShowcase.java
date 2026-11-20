package com.foglurker.dev;

import com.foglurker.brain.LurkerDirector;
import com.foglurker.client.ClientEffects;
import com.foglurker.client.DepthFog;
import com.foglurker.entity.FogLurkerEntity;
import com.foglurker.registry.ModEntities;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.List;

/**
 * Dev only (never in the jar; see build.gradle): started with -Pshowcase. Makes a world, then runs the mod's own
 * commands like a player would: the always-there fog, the creature as a cow, as a copy of you and as itself (by day
 * and at night, attacking), and the debug check. Saves screenshots and prints SHOWCASE lines, then quits.
 */
public final class FabricShowcase {
    private static boolean creating;
    private static int t = -1;

    private FabricShowcase() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> tick());
    }

    private static void say(String s) {
        System.out.println("SHOWCASE " + s);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), 1, msg -> {
        });
        say("screenshot " + name + " (fog " + String.format("%.2f", ClientEffects.currentFog()) + ", world fog frames "
                + DepthFog.worldFogFramesLastSecond() + ", wisps " + DepthFog.wispCount() + ")");
    }

    private static void cmd(Minecraft mc, String c) {
        mc.player.connection.sendCommand(c);
    }

    private static List<? extends FogLurkerEntity> lurkers(Minecraft mc) {
        return mc.getSingleplayerServer().overworld().getEntities(ModEntities.FOG_LURKER.get(), e -> true);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!creating && mc.screen != null && mc.getOverlay() == null) {
                creating = true;
                LevelSettings settings = new LevelSettings("FogTest", GameType.CREATIVE, false, Difficulty.NORMAL, true,
                        new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("FogTest" + System.currentTimeMillis(), settings,
                        new WorldOptions(42L, true, false), WorldPresets::createNormalWorldDimensions, mc.screen);
            }
            return;
        }
        t++;
        ServerPlayer sp = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
        switch (t) {
            case 60 -> {
                LurkerDirector.naturalSpawnsPaused = true;
                cmd(mc, "time set 6000");
                cmd(mc, "weather clear");
                cmd(mc, "foglurker endgrace");
                cmd(mc, "foglurker fog 60");
            }
            case 200 -> shot(mc, "fabric_fog_day.png");
            case 205 -> {
                cmd(mc, "gamemode survival"); // (it leaves creative players alone)
                cmd(mc, "effect give @s resistance 600 4 true");
            }
            case 210 -> cmd(mc, "foglurker spawn @s cow watch");
            case 300 -> {
                for (FogLurkerEntity e : lurkers(mc)) {
                    say("lurker as " + e.getDisguise() + " at " + e.blockPosition() + " dist " + String.format("%.1f", e.distanceTo(sp)));
                    sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, e.position().add(0, 0.6, 0));
                }
            }
            case 320 -> shot(mc, "fabric_cow.png");
            case 330 -> {
                cmd(mc, "foglurker despawn");
                cmd(mc, "foglurker spawn @s player watch");
            }
            case 420 -> {
                for (FogLurkerEntity e : lurkers(mc)) {
                    say("lurker as " + e.getDisguise() + " mimic " + e.getMimicName() + " dist " + String.format("%.1f", e.distanceTo(sp)));
                    sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, e.position().add(0, 1.4, 0));
                }
            }
            case 440 -> shot(mc, "fabric_player_copy.png");
            case 450 -> {
                cmd(mc, "foglurker despawn");
                cmd(mc, "time set 18000");
                cmd(mc, "foglurker spawn @s crawler attack");
            }
            case 470, 530, 590, 650, 710, 770, 830, 890 -> {
                for (FogLurkerEntity e : lurkers(mc)) {
                    say("t=" + t + " lurker " + e.getDisguise() + " dist " + String.format("%.1f", e.distanceTo(sp))
                            + " hp " + sp.getHealth() + " fog " + String.format("%.2f", ClientEffects.currentFog()));
                    sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, e.position().add(0, 0.6, 0));
                }
            }
            case 900 -> shot(mc, "fabric_crawler_night.png");
            case 920 -> cmd(mc, "foglurker debugeverything");
            case 1020 -> shot(mc, "fabric_debug.png");
            case 1040 -> {
                say("RESULT done");
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {
                    }
                    mc.execute(mc::stop);
                }).start();
            }
            default -> {
            }
        }
    }
}
