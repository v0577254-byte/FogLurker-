package com.foglurker.crawler.dev;

import com.foglurker.crawler.Crawler;
import com.foglurker.crawler.CrawlerMod;
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

import java.util.List;

/**
 * Dev only (never in the jar; see build.gradle): started with -Pshowcase. Makes a flat world, runs
 * /foglurker spawn at the player's feet, walks the camera round it, and checks it never moves or turns its body
 * while its head keeps looking at the player's eyes. Prints SHOWCASE lines, saves screenshots, then quits.
 */
public final class CrawlerShowcase {
    private static boolean creating;
    private static int t = -1;
    private static double startX, startY, startZ;
    private static float startBody;
    private static double worstMove, worstBodyTurn, worstAim;
    private static int aimSamples;

    private CrawlerShowcase() {
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

    private static List<? extends Crawler> crawlers(Minecraft mc) {
        return mc.getSingleplayerServer().overworld().getEntities(CrawlerMod.CRAWLER.get(), c -> true);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!creating && mc.screen != null && mc.getOverlay() == null) {
                creating = true;
                LevelSettings settings = new LevelSettings("CrawlerTest", GameType.CREATIVE, false, Difficulty.NORMAL, true,
                        new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("CrawlerTest" + System.currentTimeMillis(), settings,
                        new WorldOptions(1234L, false, false), WorldPresets::createFlatWorldDimensions, mc.screen);
            }
            return;
        }
        t++;
        ServerPlayer sp = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
        switch (t) {
            case 40 -> {
                cmd(mc, "time set 6000");
                cmd(mc, "weather clear");
                cmd(mc, "tp @s 0.5 -60 0.5 0 0");
                cmd(mc, "gamemode survival"); // it must not care either way
            }
            case 60 -> cmd(mc, "foglurker spawn"); // at the feet, like the player would type it
            case 70 -> {
                List<? extends Crawler> all = crawlers(mc);
                Crawler c = all.get(0);
                startX = c.getX();
                startY = c.getY();
                startZ = c.getZ();
                startBody = c.yBodyRot;
                say(String.format("count=%d spawned at %.2f %.2f %.2f body=%.1f silent=%s (player at %.2f %.2f %.2f yaw %.1f)",
                        all.size(), c.getX(), c.getY(), c.getZ(), c.yBodyRot, c.isSilent(), sp.getX(), sp.getY(), sp.getZ(),
                        sp.getYRot()));
                cmd(mc, "tp @s 0.5 -60 -3.5 0 20"); // step back 4 blocks, face it
            }
            case 120 -> shot(mc, "crawler_front.png");
            case 130 -> cmd(mc, "tp @s 3.5 -60 2.5 135 15"); // round to its side: head turns, body must not
            case 180 -> shot(mc, "crawler_side.png");
            case 190 -> cmd(mc, "tp @s 0.5 -57 -2.5 0 45"); // above it: it looks up
            case 240 -> shot(mc, "crawler_above.png");
            case 250 -> {
                cmd(mc, "tp @s 0.5 -60 -3.5 0 20");
                cmd(mc, "time set 18000");
            }
            case 300 -> shot(mc, "crawler_night.png");
            case 310 -> {
                sp.hurtServer(mc.getSingleplayerServer().overworld(), sp.damageSources().playerAttack(sp), 0F);
                for (Crawler c : crawlers(mc)) { // a hit must not push or hurt it
                    c.hurtServer(mc.getSingleplayerServer().overworld(), sp.damageSources().playerAttack(sp), 10F);
                    c.knockback(2.0, 1.0, 0.0);
                }
            }
            case 340 -> cmd(mc, "kill @e[type=foglurker:crawler]");
            case 350 -> {
                say(String.format("RESULT moved=%.4f bodyTurn=%.2f worstAim=%.1f deg (%d samples) health=%.1f killedBy/kill=%s",
                        worstMove, worstBodyTurn, worstAim, aimSamples, 100F, crawlers(mc).isEmpty()));
                t = 10_000;
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
        if (t > 70 && t < 340) {
            for (Crawler c : crawlers(mc)) {
                worstMove = Math.max(worstMove, Math.sqrt(c.distanceToSqr(startX, startY, startZ)));
                worstBodyTurn = Math.max(worstBodyTurn, Math.abs(Mth.wrapDegrees(c.yBodyRot - startBody)));
                // Where the head points vs. where the player's eyes are (only once it had time to turn).
                int since = t < 130 ? t - 70 : t < 190 ? t - 130 : t < 250 ? t - 190 : t - 250;
                if (since > 30) {
                    double dx = sp.getX() - c.getX(), dz = sp.getZ() - c.getZ();
                    float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
                    worstAim = Math.max(worstAim, Math.abs(Mth.wrapDegrees(c.getYHeadRot() - want)));
                    aimSamples++;
                }
                if (t % 30 == 0) {
                    say(String.format("t=%d pos %.2f %.2f %.2f body=%.1f head=%.1f pitch=%.1f hp=%.0f", t, c.getX(), c.getY(),
                            c.getZ(), c.yBodyRot, c.getYHeadRot(), c.getXRot(), c.getHealth()));
                }
            }
        }
    }
}
