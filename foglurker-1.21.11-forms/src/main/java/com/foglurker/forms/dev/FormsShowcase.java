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
import net.minecraftforge.event.TickEvent;

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

    private FormsShowcase() {
    }

    public static void init() {
        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> tick());
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
        return mc.getSingleplayerServer().overworld().getEntities(FormsMod.FOG_LURKER.get(), c -> true);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!creating && mc.screen != null && mc.getOverlay() == null) {
                creating = true;
                LevelSettings settings = new LevelSettings("FormsTest", GameType.CREATIVE, false, Difficulty.NORMAL, true,
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
        // Every 6 ticks: stand at the next spot (2.5 blocks apart, facing north) and spawn the next shape at our feet.
        int i = (t - 60) / 6;
        if (t >= 60 && (t - 60) % 6 == 0 && i < forms.length) {
            cmd(mc, "tp @s " + (i * 2.5 + 0.5) + " -60 0.5 180 0");
        } else if (t >= 60 && (t - 60) % 6 == 3 && i < forms.length) {
            cmd(mc, "foglurker spawn " + forms[i].id());
        }
        int done = 60 + forms.length * 6 + 10;
        // Camera spots: {x, z, yaw, pitch}. Front: back to the south, the whole row in view. Then close side views
        // (west of the player copy, east of the crawler): the bodies still face south, only the heads turn to us.
        double mid = (forms.length - 1) * 2.5 / 2 + 0.5;
        double[][] spots = {
                {mid, 14.5, 180, 12},
                {-3.0, 0.5, -90, 18},
                {(forms.length - 1) * 2.5 + 4.0, 0.5, 90, 18},
                {mid, 14.5, 180, 12},
        };
        String[] names = {"forms_front.png", "forms_side_player.png", "forms_side_crawler.png", "forms_night.png"};
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
            int at = done + k * 60;
            if (t == at) {
                if (k == 3) {
                    cmd(mc, "time set 18000");
                }
                cmd(mc, String.format(java.util.Locale.ROOT, "tp @s %.2f -60 %.2f %.0f %.0f", spots[k][0], spots[k][1], spots[k][2], spots[k][3]));
            }
            if (t == at + 55) {
                shot(mc, names[k]);
            }
        }
        if (t > done + 20 && t < done + spots.length * 60) {
            for (FogLurker l : lurkers(mc)) {
                double[] s = START.get(l.getId());
                if (s == null) {
                    continue;
                }
                worstMove = Math.max(worstMove, Math.sqrt(l.distanceToSqr(s[0], s[1], s[2])));
                worstBodyTurn = Math.max(worstBodyTurn, Math.abs(Mth.wrapDegrees(l.yBodyRot - (float) s[3])));
                boolean settled = (t - done) % 60 > 30; // the head has had time to turn after each move
                if (settled) {
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
                    if ((t - done) % 60 > 40) {
                        double dx = mc.player.getX() - l.getX(), dz = mc.player.getZ() - l.getZ();
                        float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
                        worstClientAim = Math.max(worstClientAim, Math.abs(Mth.wrapDegrees(l.getYHeadRot() - want)));
                    }
                }
            }
        }
        if (t == done + spots.length * 60 + 5) {
            say(String.format("RESULT forms=%d moved=%.4f bodyTurn=%.2f worstAim=%.1f deg (%d samples)", lurkers(mc).size(),
                    worstMove, worstBodyTurn, worstAim, aimSamples));
            // Saving and loading the world: each one must come back in the same shape (and the same player's skin).
            int kept = 0;
            var server = mc.getSingleplayerServer();
            for (FogLurker l : lurkers(mc)) {
                var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                        net.minecraft.util.ProblemReporter.DISCARDING, server.registryAccess());
                l.saveWithoutId(out);
                FogLurker back = FormsMod.FOG_LURKER.get().create(server.overworld(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
                back.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,
                        server.registryAccess(), out.buildResult()));
                if (back.getForm() == l.getForm() && java.util.Objects.equals(back.getMimicUuid(), l.getMimicUuid())
                        && back.getMimicName().equals(l.getMimicName()) && back.isPersistenceRequired()) {
                    kept++;
                }
            }
            say("SAVE kept " + kept + " of " + lurkers(mc).size());
            say(String.format("CLIENT bodyTurn=%.2f worstAim=%.1f deg", worstClientBodyTurn, worstClientAim));
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
