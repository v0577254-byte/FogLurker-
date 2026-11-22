package com.foglurker.world;

import com.foglurker.FogLurkerMod;
import com.foglurker.brain.LurkerDirector;
import com.foglurker.config.FogLurkerConfig;
import com.foglurker.network.ModNetwork;
import com.foglurker.network.UnderBedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Only while you sleep: about one sleep in three, a moment after you lie down, your own face (your skin) rises from
 * under the foot of your bed and looks at you, for one second. Then it is gone. It is only on your screen.
 * Like the other hauntings: after the grace period, with hauntings and natural spawning on, and never in a sleep that
 * turns into sleep paralysis.
 */
@Mod.EventBusSubscriber(modid = FogLurkerMod.MOD_ID)
public final class UnderTheBed {
    public static final double CHANCE = 0.35;
    /** One second. */
    public static final int SHOW_TICKS = 20;

    /** Decided when they lie down: the sleep tick it shows at, or -1 for none this sleep. Removed when they get up. */
    private static final Map<UUID, Integer> THIS_SLEEP = new HashMap<>();

    private UnderTheBed() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p instanceof FakePlayer) {
            return;
        }
        if (!p.isSleeping()) {
            THIS_SLEEP.remove(p.getUUID());
            return;
        }
        Integer at = THIS_SLEEP.get(p.getUUID());
        if (at == null) {
            lieDown(p, p.getRandom().nextDouble(), 12 + p.getRandom().nextInt(16));
        } else if (at >= 0 && p.getSleepTimer() >= at) {
            THIS_SLEEP.put(p.getUUID(), -1); // once per sleep
            show(p);
        }
    }

    /**
     * They just lay down: decide whether it comes this sleep. Public for the game tests.
     *
     * @return true if it will show
     */
    public static boolean lieDown(ServerPlayer p, double roll, int atTick) {
        boolean happens = roll < CHANCE && allowed(p);
        THIS_SLEEP.put(p.getUUID(), happens ? atTick : -1);
        return happens;
    }

    static boolean allowed(ServerPlayer p) {
        return whyNot(p).isEmpty();
    }

    /** Why it would not come for them right now ("" if it would). Public for the game tests. */
    public static String whyNot(ServerPlayer p) {
        FogLurkerSavedData data = FogLurkerSavedData.get(p.server);
        if (!FogLurkerConfig.COMMON.hauntings.get()) {
            return "hauntings are off";
        }
        if (!data.naturalSpawning()) {
            return "natural spawning is off";
        }
        if (data.inGrace()) {
            return "grace period";
        }
        if (data.restingAt(p.server.overworld().getGameTime())) {
            return "resting after a kill";
        }
        if (p.isCreative() || p.isSpectator()) {
            return "creative or spectator";
        }
        if (LurkerDirector.leftAloneTicks(p) != 0) {
            return "left alone for " + LurkerDirector.leftAloneTicks(p) + " more ticks";
        }
        // like every other haunting: not while it is hunting them, and not while they are AFK
        if (LurkerDirector.isHunted(p.getUUID())) {
            return "being hunted";
        }
        if (com.foglurker.world.AfkTracker.isAfk(p)) {
            return "AFK";
        }
        return "";
    }

    /** Sends it, if they are still asleep and this is not a sleep paralysis night. Public for the game tests. */
    public static boolean show(ServerPlayer p) {
        if (!p.isSleeping() || SleepParalysis.happensThisSleep(p.getUUID()) || !allowed(p)) {
            return false;
        }
        ModNetwork.send(p, new UnderBedPacket(SHOW_TICKS));
        return true;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        THIS_SLEEP.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        THIS_SLEEP.clear();
    }
}
