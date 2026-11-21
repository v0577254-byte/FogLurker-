package com.foglurker.world;

import com.foglurker.FogLurkerMod;
import com.foglurker.brain.LurkerDirector;
import com.foglurker.learning.PlayerBehaviorProfile;
import com.foglurker.network.ModNetwork;
import com.foglurker.network.SleepVisionPacket;
import com.foglurker.registry.ModDamageTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.entity.FakePlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A rare encounter in every mode: when you lie down to sleep there is a 5% chance of sleep paralysis. A moment
 * into the sleep you are thrown out of bed, take half a heart of damage, and the Fog Lurker says in chat
 * "FogLurker: Hmm Did I Do That?". It never appears afterwards, and the next sleep is a normal one. From then
 * on, whenever you go to bed, a faint picture of it shows while you sleep.
 * {@code /foglurker force sleep-paralysis [player]} makes the next sleep a sure one.
 */
public final class SleepParalysis {
    public static final double CHANCE = 0.05;
    /** In a bed inside one of its hotspots (a lookout tower, an abandoned castle, a hunters' bunker). */
    public static final double HOTSPOT_CHANCE = 0.25;

    /** 5%, or 25% in a bed inside one of its hotspots. */
    public static double chance(ServerPlayer p) {
        return FogStructures.inHotspot(p.level(), p.getSleepingPos().orElse(p.blockPosition())) ? HOTSPOT_CHANCE : CHANCE;
    }

    /** How far into the sleep it strikes (2 s: you are lying down, the screen is darkening). */
    public static final int STRIKE_AT_TICKS = 40;
    /** After it happens, sleeping again in this time is always a normal sleep. */
    private static final long CALM_TICKS = 20L * 60 * 10;
    /** And it does not come for them on its own for a while after it (it is not there: only the dream was). */
    private static final long NO_VISIT_TICKS = 20L * 60 * 3;

    /** Decided when they lie down: true = this sleep is sleep paralysis. Removed when they get up. */
    private static final Map<UUID, Boolean> THIS_SLEEP = new HashMap<>();
    private static final Map<UUID, Long> CALM_UNTIL = new HashMap<>();

    private SleepParalysis() {
    }

    /** Every server player tick, at the end (ModEvents). */
    public static void onPlayerTick(ServerPlayer p) {
        if (p instanceof FakePlayer) {
            return;
        }
        if (!p.isSleeping()) {
            THIS_SLEEP.remove(p.getUUID());
            return;
        }
        if (!THIS_SLEEP.containsKey(p.getUUID())) {
            lieDown(p, p.getRandom().nextDouble());
        } else if (p.getSleepTimer() >= STRIKE_AT_TICKS && Boolean.TRUE.equals(THIS_SLEEP.get(p.getUUID()))) {
            strike(p);
        }
    }

    /**
     * They just lay down: decide whether this sleep is the one. Public for the game tests.
     *
     * @param roll a random number 0..1
     * @return true if it will be sleep paralysis
     */
    public static boolean lieDown(ServerPlayer p, double roll) {
        FogLurkerSavedData data = FogLurkerSavedData.get(p.level().getServer());
        PlayerBehaviorProfile prof = data.profile(p.getUUID());
        long now = p.level().getServer().overworld().getGameTime();
        boolean calm = CALM_UNTIL.getOrDefault(p.getUUID(), Long.MIN_VALUE) > now;
        boolean happens = prof.sleepParalysisForced
                || !calm && data.naturalSpawning() && !p.isCreative() && !p.isSpectator() && roll < chance(p);
        THIS_SLEEP.put(p.getUUID(), happens);
        if (!happens && prof.sleepParalysisSeen) {
            ModNetwork.send(p, SleepVisionPacket.INSTANCE); // it is still there when they close their eyes
        }
        return happens;
    }

    /** Sleep paralysis: out of bed, half a heart, and a question in chat. Public for the game tests. */
    public static void strike(ServerPlayer p) {
        FogLurkerSavedData data = FogLurkerSavedData.get(p.level().getServer());
        PlayerBehaviorProfile prof = data.profile(p.getUUID());
        prof.sleepParalysisForced = false;
        prof.sleepParalysisSeen = true;
        data.setDirty();
        THIS_SLEEP.put(p.getUUID(), false); // once per sleep
        long now = p.level().getServer().overworld().getGameTime();
        CALM_UNTIL.put(p.getUUID(), now + CALM_TICKS); // going back to bed now is a normal sleep
        if (p.isSleeping()) {
            p.stopSleepInBed(true, true); // thrown out of bed
        }
        p.hurt(ModDamageTypes.source(p.level(), ModDamageTypes.SLEEP_PARALYSIS), 1.0F); // half a heart
        p.sendSystemMessage(Component.literal("FogLurker: Hmm Did I Do That?"));
        LurkerDirector.leaveAlone(p, NO_VISIT_TICKS); // and it does not show up after it
    }

    /** True while this sleep is going to be (or was) sleep paralysis: nothing else happens in it. */
    public static boolean happensThisSleep(UUID id) {
        return Boolean.TRUE.equals(THIS_SLEEP.get(id));
    }

    /** /foglurker force sleep-paralysis: the next time they sleep, it happens. */
    public static void forceNext(ServerPlayer p) {
        FogLurkerSavedData data = FogLurkerSavedData.get(p.level().getServer());
        data.profile(p.getUUID()).sleepParalysisForced = true;
        data.setDirty();
        CALM_UNTIL.remove(p.getUUID());
    }

    public static void onLogout(ServerPlayer p) {
        THIS_SLEEP.remove(p.getUUID());
    }

    public static void onServerStopped() {
        THIS_SLEEP.clear();
        CALM_UNTIL.clear();
    }
}
