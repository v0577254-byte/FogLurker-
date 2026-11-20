package com.foglurker.forms.dev;

import com.foglurker.forms.FormsMod;
import com.foglurker.forms.FogLurker;
import com.foglurker.forms.Form;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dev only (never in the jar; see build.gradle): started with -Pshowcase. Makes a flat world and, like a player
 * would, runs /foglurker spawn <type> at its feet for every shape in a row; then walks round them and checks none
 * moved or turned its body while every head looks at the player's eyes. Prints SHOWCASE lines, saves screenshots.
 */
public final class FormsShowcase {
    private static boolean creating;
    private static int t = -1;
    private static final Map<Integer, double[]> START = new HashMap<>();
    private static double worstMove;
    private static double worstBodyTurn;
    private static double worstAim;
    private static int aimSamples;
    private static double worstClientBodyTurn;
    private static double worstClientAim;
    private static double worstPuppetBody;
    private static final java.util.Set<String> NO_PUPPET = new java.util.TreeSet<>();
    private static final int COLS = 10;
    private static final int PER_SPOT = 50;

    private FormsShowcase() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> tick());
    }

    private static double x(int i) {
        return (i % COLS) * 5 + 0.5;
    }

    private static double z(int i) {
        return (i / COLS) * 14 + 0.5;
    }

    private static void say(String s) {
        System.out.println("SHOWCASE " + s);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), 1, msg -> {
        });
        say("screenshot " + name);
    }

    private static void cmd(Minecraft mc, String c) {
        mc.player.connection.sendCommand(c);
    }

    private static List<? extends FogLurker> lurkers(Minecraft mc) {
        return mc.getSingleplayerServer().overworld().getEntities(FormsMod.FOG_LURKER, c -> true);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!creating && mc.screen != null && mc.getOverlay() == null) {
                creating = true;
                LevelSettings settings = new LevelSettings("FormsTest", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                        new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("FormsTest" + System.currentTimeMillis(), settings,
                        new WorldOptions(1234L, false, false), WorldPresets::createFlatWorldDimensions, mc.screen);
            }
            return;
        }
        t++;
        ServerPlayer sp = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
        Form[] forms = Form.values();
        if (t == 40) {
            cmd(mc, "time set 6000");
            cmd(mc, "weather clear");
        }
        // A grid, 10 to a row (5 blocks apart, rows 14 apart: room for a ghast). Every 6 ticks: stand at the next
        // spot facing north and spawn the next shape at our feet with the command, as a player would.
        int i = (t - 60) / 6;
        if (t >= 60 && (t - 60) % 6 == 0 && i < forms.length) {
            cmd(mc, String.format(java.util.Locale.ROOT, "tp @s %.1f -60 %.1f 180 0", x(i), z(i)));
        } else if (t >= 60 && (t - 60) % 6 == 3 && i < forms.length) {
            cmd(mc, "foglurker spawn " + forms[i].id());
        }
        int done = 60 + forms.length * 6 + 10;
        // Camera spots, 5 figures per picture: from the front and a little to the side, so you can see each body
        // still faces south while the head (or, with no separate head, the whole thing) turns to the camera.
        // Then one at night.
        int rows = (forms.length + COLS - 1) / COLS;
        java.util.List<double[]> spotList = new java.util.ArrayList<>();
        java.util.List<String> nameList = new java.util.ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int h = 0; h < 2 && r * COLS + h * 5 < forms.length; h++) {
                double cx = x(r * COLS + h * 5) + 10, cz = z(r * COLS);
                double camX = cx + 7, camZ = cz + 10;
                float yaw = (float) (Mth.atan2(cz - camZ, cx - camX) * Mth.RAD_TO_DEG) - 90F;
                spotList.add(new double[]{camX, camZ, Mth.wrapDegrees(yaw), 14});
                StringBuilder n = new StringBuilder("forms_");
                for (int k = r * COLS + h * 5; k < Math.min(forms.length, r * COLS + h * 5 + 5); k++) {
                    n.append(k == r * COLS + h * 5 ? "" : "-").append(forms[k].id());
                }
                nameList.add(n.append(".png").toString());
            }
        }
        spotList.add(spotList.get(0));
        nameList.add("forms_night.png");
        double[][] spots = spotList.toArray(new double[0][]);
        String[] names = nameList.toArray(new String[0]);
        if (t == done) {
            for (FogLurker l : lurkers(mc)) {
                START.put(l.getId(), new double[]{l.getX(), l.getY(), l.getZ(), l.yBodyRot});
                say(String.format("%s at %.1f %.1f %.1f body %.0f mimic %s", l.getForm().id(), l.getX(), l.getY(), l.getZ(),
                        l.yBodyRot, l.getMimicName()));
            }
            say("spawned " + lurkers(mc).size() + " of " + forms.length);
            mc.gui.getChat().clearMessages(false);
            mc.options.hideGui = true;
        }
        for (int k = 0; k < spots.length; k++) {
            int at = done + k * PER_SPOT;
            if (t == at) {
                if (k == spots.length - 1) {
                    cmd(mc, "time set 18000");
                }
                cmd(mc, String.format(java.util.Locale.ROOT, "tp @s %.2f -60 %.2f %.0f %.0f", spots[k][0], spots[k][1], spots[k][2], spots[k][3]));
            }
            if (t == at + PER_SPOT - 5) {
                shot(mc, names[k]);
            }
        }
        if (t > done + 20 && t < done + spots.length * PER_SPOT) {
            for (FogLurker l : lurkers(mc)) {
                double[] s = START.get(l.getId());
                if (s == null) {
                    continue;
                }
                worstMove = Math.max(worstMove, Math.sqrt(l.distanceToSqr(s[0], s[1], s[2])));
                worstBodyTurn = Math.max(worstBodyTurn, Math.abs(Mth.wrapDegrees(l.yBodyRot - (float) s[3])));
                boolean settled = (t - done) % PER_SPOT > 25; // the head has had time to turn after each move
                if (settled && l.distanceTo(sp) < 20) { // the ones in the picture (it notices you within 64 blocks)
                    double dx = sp.getX() - l.getX(), dz = sp.getZ() - l.getZ();
                    float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
                    worstAim = Math.max(worstAim, Math.abs(Mth.wrapDegrees(l.getYHeadRot() - want)));
                    aimSamples++;
                }
            }
            // What is drawn: the client's copies must agree (no body turning there either).
            for (var e : mc.level.entitiesForRendering()) {
                if (e instanceof FogLurker l) {
                    worstClientBodyTurn = Math.max(worstClientBodyTurn, Math.abs(Mth.wrapDegrees(l.yBodyRot - 0F)));
                    // The puppet drawn for it: it must exist, and its body must face where the figure's does (or,
                    // with no separate head, where the head looks).
                    if (l.getForm() != Form.CRAWLER) {
                        var puppet = com.foglurker.forms.client.PuppetCache.get(l);
                        if (puppet == null) {
                            NO_PUPPET.add(l.getForm().id());
                        } else {
                            float want = l.getForm().turnsWhole() ? l.yHeadRot : l.yBodyRot;
                            worstPuppetBody = Math.max(worstPuppetBody, Math.abs(Mth.wrapDegrees(puppet.yBodyRot - want)));
                        }
                    }
                    if ((t - done) % PER_SPOT > 35 && l.distanceTo(mc.player) < 20) {
                        double dx = mc.player.getX() - l.getX(), dz = mc.player.getZ() - l.getZ();
                        float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
                        worstClientAim = Math.max(worstClientAim, Math.abs(Mth.wrapDegrees(l.getYHeadRot() - want)));
                    }
                }
            }
        }
        if (t == done + spots.length * PER_SPOT + 5) {
            say(String.format("RESULT forms=%d moved=%.4f bodyTurn=%.2f worstAim=%.1f deg (%d samples)", lurkers(mc).size(),
                    worstMove, worstBodyTurn, worstAim, aimSamples));
            // Saving and loading the world: each one must come back in the same shape (and the same player's skin).
            int kept = 0;
            var server = mc.getSingleplayerServer();
            for (FogLurker l : lurkers(mc)) {
                var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                        net.minecraft.util.ProblemReporter.DISCARDING, server.registryAccess());
                l.saveWithoutId(out);
                FogLurker back = FormsMod.FOG_LURKER.create(server.overworld(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
                back.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,
                        server.registryAccess(), out.buildResult()));
                if (back.getForm() == l.getForm() && java.util.Objects.equals(back.getMimicUuid(), l.getMimicUuid())
                        && back.getMimicName().equals(l.getMimicName()) && back.isPersistenceRequired()) {
                    kept++;
                }
            }
            say("SAVE kept " + kept + " of " + lurkers(mc).size());
            say(String.format("CLIENT bodyTurn=%.2f worstAim=%.1f deg puppetBody=%.2f noPuppet=%s", worstClientBodyTurn,
                    worstClientAim, worstPuppetBody, NO_PUPPET));
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {
                }
                mc.execute(mc::stop);
            }).start();
        }
    }
}
