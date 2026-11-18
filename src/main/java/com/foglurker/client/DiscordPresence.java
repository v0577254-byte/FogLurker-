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
 * Discord Rich Presence: "Playing Fog Lurker" on your Discord profile while the game runs, with a line about what
 * is happening to you ("Feeling watched...", "Who's that?", "RUNNING FOR MY LIFE", the occasional rare one) and
 * single- or multiplayer plus the day. Never a server address, a world name or anyone's name. Off with {@code discordRichPresence} in the client config.
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

    // ------------------------------------------------------------------ what it says

    /** Each state's lines; one is picked when the state starts and kept until it changes. */
    private static final java.util.Map<String, String[]> LINES = java.util.Map.ofEntries(
            java.util.Map.entry("menu", new String[]{"In the menus", "In the menus", "Building up courage"}),
            java.util.Map.entry("dead", new String[]{"Got got.", "Respawning...", "That did not go well"}),
            java.util.Map.entry("spectator", new String[]{"Haunting as a ghost", "Watching from the other side"}),
            java.util.Map.entry("creative", new String[]{"Just debugging stuff"}),
            java.util.Map.entry("killed", new String[]{"The Fog Lurker is dead!", "The fog lifts... for now"}),
            java.util.Map.entry("dancing", new String[]{"Watching it do the Macarena", "...is it dancing?"}),
            java.util.Map.entry("hunted", new String[]{"Being hunted...", "RUNNING FOR MY LIFE", "Run. Run. Run."}),
            java.util.Map.entry("hunted_water", new String[]{"Hiding in the water", "It can't swim. Right?"}),
            java.util.Map.entry("hunted_hurt", new String[]{"Barely alive", "One more bite and I'm done"}),
            java.util.Map.entry("transform", new String[]{"Something is very wrong", "It's changing shape..."}),
            java.util.Map.entry("meeting", new String[]{"Who's that?", "Someone is standing in the fog"}),
            java.util.Map.entry("bushes", new String[]{"I thought I saw something moving in the bushes"}),
            java.util.Map.entry("following", new String[]{"Following a friend into the woods", "A friend wants to show me something"}),
            java.util.Map.entry("watched", new String[]{"Feeling watched...", "Something is out there", "That cow keeps staring at me"}),
            java.util.Map.entry("fleeing", new String[]{"It ran away?!", "Survived... this time"}),
            java.util.Map.entry("sleeping", new String[]{"Trying to sleep...", "Sleeping with one eye open"}),
            java.util.Map.entry("nether", new String[]{"Hiding in the Nether", "Somewhere it can't follow (probably)"}),
            java.util.Map.entry("end", new String[]{"In the End"}),
            java.util.Map.entry("day", new String[]{"Lost in the fog", "Exploring", "Gathering wood, nervously", "Building a base"}),
            java.util.Map.entry("night", new String[]{"Surviving the night", "Out in the dark", "Keeping the torches lit"}));
    /** Now and then (about 1 in 25) an ordinary moment gets one of these instead. */
    private static final String[] RARE = {"WHY ISNT IT WORKING???", "Definitely not scared", "Pretending everything is fine",
            "It's just a cow. It's just a cow.", "Did that cow just blink?", "Checking behind me every 5 seconds"};
    /** States where nothing is going on, so a rare line may show and the line changes every 10 minutes. */
    private static final java.util.Set<String> CALM = java.util.Set.of("day", "night", "nether", "end", "creative", "menu");

    private static String state;
    private static String line;
    private static long pickedAt;
    /** When a Fog Lurker died near us (we probably killed it), System.currentTimeMillis. */
    private static long killedAt = Long.MIN_VALUE / 2;

    /** The state we are in: what it is doing to us first, then where we are and what time it is. */
    private static String state(Minecraft mc) {
        var player = mc.player;
        if (mc.level == null || player == null) {
            return "menu";
        }
        if (player.isDeadOrDying()) {
            return "dead";
        }
        if (player.isSpectator()) {
            return "spectator";
        }
        if (player.isCreative()) {
            return "creative";
        }
        boolean dancing = false;
        for (com.foglurker.entity.FogLurkerEntity l : mc.level.getEntitiesOfClass(com.foglurker.entity.FogLurkerEntity.class,
                player.getBoundingBox().inflate(48))) {
            dancing |= l.isDancing() && !l.isDeadOrDying();
        }
        if (System.currentTimeMillis() - killedAt < 120_000L) {
            return "killed";
        }
        if (dancing) {
            return "dancing";
        }
        switch (ClientEffects.mood()) {
            case com.foglurker.network.Mood.HUNTED -> {
                return player.isInWater() ? "hunted_water" : player.getHealth() <= 6F ? "hunted_hurt" : "hunted";
            }
            case com.foglurker.network.Mood.TRANSFORM -> {
                return "transform";
            }
            case com.foglurker.network.Mood.MEETING -> {
                return "meeting";
            }
            case com.foglurker.network.Mood.BUSHES -> {
                return "bushes";
            }
            case com.foglurker.network.Mood.FOLLOWING -> {
                return "following";
            }
            case com.foglurker.network.Mood.WATCHED -> {
                return "watched";
            }
            case com.foglurker.network.Mood.FLEEING -> {
                return "fleeing";
            }
            default -> {
            }
        }
        if (player.isSleeping()) {
            return "sleeping";
        }
        if (mc.level.dimension() == net.minecraft.world.level.Level.NETHER) {
            return "nether";
        }
        if (mc.level.dimension() == net.minecraft.world.level.Level.END) {
            return "end";
        }
        return mc.level.isNight() ? "night" : "day";
    }

    private static String pick(String state) {
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        if (CALM.contains(state) && !state.equals("menu") && r.nextInt(25) == 0) {
            return RARE[r.nextInt(RARE.length)];
        }
        String[] lines = LINES.get(state);
        return lines[r.nextInt(lines.length)];
    }

    /** Its death throes last about a second, so this looks four times a second. */
    private static void watchForDeaths(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        for (com.foglurker.entity.FogLurkerEntity l : mc.level.getEntitiesOfClass(com.foglurker.entity.FogLurkerEntity.class,
                mc.player.getBoundingBox().inflate(48))) {
            if (l.isDeadOrDying()) {
                killedAt = System.currentTimeMillis();
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++ticks % 5 == 0) {
            watchForDeaths(Minecraft.getInstance());
        }
        if (ticks % 20 != 0) {
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
        long nowMs = System.currentTimeMillis();
        long now = nowMs / 1000L;
        if (world != inWorld) {
            inWorld = world;
            inWorldSince = now;
        }
        Activity wanted = null;
        if (enabled) {
            String s = state(mc);
            if (!s.equals(state) || CALM.contains(s) && nowMs - pickedAt > 600_000L) {
                state = s;
                line = pick(s);
                pickedAt = nowMs;
            }
            wanted = !world ? new Activity(line, null, GAME_START)
                    : new Activity(line, (mc.hasSingleplayerServer() ? "Singleplayer" : "Multiplayer")
                    + " \u00b7 Day " + (mc.level.getDayTime() / 24000L + 1), inWorldSince);
        }
        Activity send = wanted;
        if (BUSY.compareAndSet(false, true)) {
            IO.execute(() -> {
                try {
                    sync(send);
                } catch (Throwable t) {
                    disconnect(); // Discord closed, or anything else: try again in a while
                } finally {
                    BUSY.set(false);
                }
            });
        }
    }

    /** IO thread. Brings Discord up to date, at most one update every 4 s (Discord's own limit is 5 per 20 s). */
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
        if (java.util.Objects.equals(wanted, shown) || now - lastSentAt < 4_000L) {
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
