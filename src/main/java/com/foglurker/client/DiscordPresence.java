package com.foglurker.client;

import com.foglurker.FogLurkerMod;
import com.foglurker.config.FogLurkerConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Discord Rich Presence: "Playing Fog Lurker" on your Discord profile while the game runs, with a line saying
 * whether you are in the menus, lost in the fog, or being hunted. It only ever says single- or multiplayer: never
 * a server address, a world name or anyone's name. Off with {@code discordRichPresence} in the client config.
 * Your online status (Do Not Disturb and so on) is yours to set in Discord; apps cannot change it.
 */
@Mod.EventBusSubscriber(modid = FogLurkerMod.MOD_ID, value = Dist.CLIENT)
public final class DiscordPresence {
    /** The "Fog Lurker" application on Discord's developer portal (a public id, not a secret). */
    static final String APPLICATION_ID = "1572665283158610010";

    /** All talking to Discord happens on this one thread, so the game never waits for it. */
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Fog Lurker Discord presence");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final long GAME_START = System.currentTimeMillis() / 1000L;

    private record Activity(String details, String state, long start) {
    }

    // client thread
    private static int ticks;
    private static boolean inWorld;
    private static long inWorldSince = GAME_START;

    // IO thread
    private static DiscordIpc ipc;
    private static Activity shown;
    private static long lastSentAt;
    private static long nextConnectAt;
    private static boolean announced;

    private DiscordPresence() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % 20 != 0) {
            return; // once a second is plenty
        }
        boolean enabled;
        try {
            enabled = FogLurkerConfig.CLIENT.discordRichPresence.get();
        } catch (IllegalStateException notLoadedYet) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean world = mc.level != null && mc.player != null;
        long now = System.currentTimeMillis() / 1000L;
        if (world != inWorld) {
            inWorld = world;
            inWorldSince = now;
        }
        Activity wanted = !enabled ? null
                : !world ? new Activity("In the menus", null, GAME_START)
                : new Activity(ClientEffects.fogTargetNow() > 0.2F ? "Being hunted..." : "Lost in the fog",
                mc.hasSingleplayerServer() ? "Singleplayer" : "Multiplayer", inWorldSince);
        if (BUSY.compareAndSet(false, true)) {
            IO.execute(() -> {
                try {
                    sync(wanted);
                } catch (Throwable t) {
                    disconnect(); // Discord closed, or anything else: try again in a while
                } finally {
                    BUSY.set(false);
                }
            });
        }
    }

    /** IO thread. Brings Discord up to date, at most one update every 5 s (Discord's own limit is 5 per 20 s). */
    private static void sync(Activity wanted) throws java.io.IOException {
        long now = System.currentTimeMillis();
        if (ipc == null) {
            if (wanted == null || now < nextConnectAt) {
                return; // nothing to show, or tried a moment ago
            }
            nextConnectAt = now + 20_000L; // Discord not running: look again in 20 s, quietly
            ipc = DiscordIpc.connect(APPLICATION_ID);
            if (ipc == null) {
                return;
            }
            shown = null;
            if (!announced) {
                announced = true;
                FogLurkerMod.LOGGER.info("Fog Lurker: connected to Discord, showing Rich Presence "
                        + "(discordRichPresence in foglurker-client.toml turns it off)");
            }
        }
        if (java.util.Objects.equals(wanted, shown) || now - lastSentAt < 5_000L) {
            return;
        }
        if (wanted == null) {
            ipc.setActivity(null, null, 0); // switched off: clear it and let go of Discord
            disconnect();
            return;
        }
        ipc.setActivity(wanted.details(), wanted.state(), wanted.start());
        shown = wanted;
        lastSentAt = now;
    }

    private static void disconnect() {
        if (ipc != null) {
            ipc.close();
            ipc = null;
        }
        shown = null;
    }
}
