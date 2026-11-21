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

    /**
     * The Gemini key is never kept in the chat history or in command_history.txt: a line typed as if setting it
     * (a fake key, never run) is dropped, an ordinary command is still kept, and a key line saved in the file before
     * the game started (put there by the dev run) is gone from it.
     */
    private static String keyHistory(Minecraft mc) {
        String fake = "/foglurker setgeminiapikey AIzaFAKEshowcaseKEY0000000000000000000";
        mc.gui.getChat().addRecentChat(fake);
        mc.gui.getChat().addRecentChat("/time query daytime");
        boolean inChat = mc.gui.getChat().getRecentChat().stream().anyMatch(l -> l.contains("setgeminiapikey"));
        boolean normalKept = mc.gui.getChat().getRecentChat().contains("/time query daytime")
                && mc.commandHistory().history().contains("/time query daytime");
        boolean inHistory = mc.commandHistory().history().stream().anyMatch(l -> l.contains("setgeminiapikey"));
        String file;
        try {
            file = java.nio.file.Files.readString(mc.gameDirectory.toPath().resolve("command_history.txt"));
        } catch (java.io.IOException e) {
            file = "";
        }
        boolean inFile = file.contains("setgeminiapikey") || file.contains("AIza");
        boolean ok = !inChat && !inHistory && !inFile && normalKept;
        return (ok ? "OK" : "FAILED") + " (in chat " + inChat + ", in history " + inHistory + ", in file " + inFile
                + ", ordinary command kept " + normalKept + ", file keeps " + file.lines().count() + " lines)";
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
            case 40 -> say("key history " + keyHistory(mc));
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
                cmd(mc, "time set 6000");
                cmd(mc, "kill @e[type=foglurker:fog_lurker]");
            }
            // The fog-surge jumpscare, end to end: sent by the server, received here, the fog rushes in and lifts.
            case 1060 -> mc.getSingleplayerServer().execute(() -> say("surge sent " + com.foglurker.brain.Hauntings.fogSurge(
                    mc.getSingleplayerServer().getPlayerList().getPlayers().get(0), net.minecraft.util.RandomSource.create(7))));
            case 1075, 1090, 1150, 1230, 1300 -> say("surge fog " + com.foglurker.client.ClientEffects.currentFog() + " left "
                    + com.foglurker.client.ClientEffects.surgeTicksLeft());
            case 1085 -> shot(mc, "fabric_fog_surge.png");
            // Up in the open sky, in daylight: the new shapes standing still in a row.
            case 1320 -> {
                cmd(mc, "fill ~-10 149 ~-10 ~10 149 ~10 minecraft:stone");
                cmd(mc, "tp @s ~ 150 ~ 180 12");
            }
            case 1340 -> mc.getSingleplayerServer().execute(() -> {
                ServerPlayer me = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                com.foglurker.entity.Disguise[] shapes = {com.foglurker.entity.Disguise.VILLAGER, com.foglurker.entity.Disguise.WANDERING_TRADER,
                        com.foglurker.entity.Disguise.HORSE, com.foglurker.entity.Disguise.LLAMA, com.foglurker.entity.Disguise.SKELETON,
                        com.foglurker.entity.Disguise.CREEPER, com.foglurker.entity.Disguise.SPIDER, com.foglurker.entity.Disguise.PILLAGER};
                for (int k = 0; k < shapes.length; k++) {
                    FogLurkerEntity e = com.foglurker.registry.ModEntities.FOG_LURKER.get().create(me.level(),
                            net.minecraft.world.entity.EntitySpawnReason.EVENT);
                    e.setNoAi(true); // a statue: it never moves or hunts
                    e.setDisguise(shapes[k]);
                    e.setVariant(shapes[k] == com.foglurker.entity.Disguise.VILLAGER ? 4 : shapes[k] == com.foglurker.entity.Disguise.HORSE ? 3 : 0);
                    e.snapTo(me.getX() - 7.7 + k * 2.2, 150, me.getZ() - 6, 0F, 0F);
                    e.setYHeadRot(0F);
                    e.setYBodyRot(0F);
                    me.level().addFreshEntity(e);
                }
            });
            case 1345 -> mc.options.hideGui = true;
            case 1400 -> {
                say("statues " + lurkers(mc).size());
                shot(mc, "fabric_statues.png");
            }
            // A night in bed: for one second your own face rises from under the foot of the bed.
            case 1410 -> mc.getSingleplayerServer().execute(() -> {
                ServerPlayer me = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                me.level().setDayTime(18000);
                var data = com.foglurker.world.FogLurkerSavedData.get(mc.getSingleplayerServer());
                if (data.inGrace()) {
                    data.endGrace();
                }
                net.minecraft.core.BlockPos head = me.blockPosition().offset(0, 0, 3);
                var bed = net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.NORTH);
                me.level().setBlockAndUpdate(head, bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
                        net.minecraft.world.level.block.state.properties.BedPart.HEAD));
                me.level().setBlockAndUpdate(head.south(), bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
                        net.minecraft.world.level.block.state.properties.BedPart.FOOT));
                me.teleportTo(me.level(), head.getX() + 0.5, head.getY(), head.getZ() + 0.5, java.util.Set.of(), 0F, 0F, true);
                me.startSleeping(head);
            });
            case 1435 -> mc.gui.getChat().clearMessages(false); // (lying in bed shows the chat)
            case 1440 -> mc.getSingleplayerServer().execute(() -> say("under the bed sent " + com.foglurker.world.UnderTheBed.show(
                    mc.getSingleplayerServer().getPlayerList().getPlayers().get(0))));
            case 1443 -> {
                say("under the bed showing " + com.foglurker.client.UnderBed.showing() + " sleeping " + mc.player.isSleeping());
                var fig = com.foglurker.client.UnderBed.figure();
                if (fig != null) {
                    say("under the bed figure at " + fig.position() + " old " + new net.minecraft.world.phys.Vec3(fig.xOld, fig.yOld, fig.zOld)
                            + " disguise " + fig.getDisguise() + " dummy " + com.foglurker.client.DisguiseCache.get(fig)
                            + " in level " + (mc.level.getEntity(fig.getId()) == fig) + " camera " + mc.gameRenderer.getMainCamera().position()
                            + " yaw " + mc.gameRenderer.getMainCamera().yRot() + " pitch " + mc.gameRenderer.getMainCamera().xRot());
                }
                shot(mc, "fabric_under_bed.png");
            }
            case 1465 -> say("under the bed after a second showing " + com.foglurker.client.UnderBed.showing());
            case 1470 -> {
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
