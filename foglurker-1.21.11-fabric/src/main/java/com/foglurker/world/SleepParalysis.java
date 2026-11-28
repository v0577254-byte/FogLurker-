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
 * on, whenever you go to bed, a faint picture of it shows while you sleep. The same picture shows every time you
 * sleep once three days have passed for you, or once you have seen the creature itself (its own pale shape, not a
 * disguise). And rarely, as you fall asleep, red glitching words ask whether you can see it.
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
            lieDown(p, p.getRandom().nextDouble(), p.getRandom().nextDouble());
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
        return lieDown(p, roll, 1.0);
    }

    /** @param wordsRoll a random number 0..1 for the rare words ({@link #WORDS_CHANCE}) */
    public static boolean lieDown(ServerPlayer p, double roll, double wordsRoll) {
        FogLurkerSavedData data = FogLurkerSavedData.get(p.level().getServer());
        PlayerBehaviorProfile prof = data.profile(p.getUUID());
        // A full set of Armour of the Fog keeps sleep paralysis away (unless an admin forced it: then it comes now,
        // not on some later night after the armour is off). It does not keep the pictures away.
        boolean armoured = !prof.sleepParalysisForced && com.foglurker.armor.FogArmor.hasFullSet(p);
        long now = p.level().getServer().overworld().getGameTime();
        boolean calm = CALM_UNTIL.getOrDefault(p.getUUID(), Long.MIN_VALUE) > now;
        // (Like the other hauntings: not with them switched off, while it rests after a kill, while they are AFK, at
        // peace or being hunted.)
        boolean happens = !armoured && prof.sleepParalysisForced
                || !armoured && !calm && data.naturalSpawning() && com.foglurker.config.FogLurkerConfig.COMMON.hauntings.get()
                && !data.restingAt(now) && !p.isCreative() && !p.isSpectator() && !AfkTracker.isAfk(p)
                && LurkerDirector.leftAloneTicks(p) == 0 && !LurkerDirector.isHunted(p.getUUID()) && roll < chance(p);
        THIS_SLEEP.put(p.getUUID(), happens);
        if (!happens) {
            boolean picture = seesPicture(p, prof); // it is still there when they close their eyes
            boolean words = FORCED_WORDS.remove(p.getUUID()) || wordsRoll < WORDS_CHANCE && !p.isCreative() && !p.isSpectator()
                    && com.foglurker.config.FogLurkerConfig.COMMON.hauntings.get();
            if (picture || words) {
                ModNetwork.send(p, new SleepVisionPacket(picture, words));
            }
            if (words) {
                ModAdvancements.award(p, ModAdvancements.WORDS);
            }
        }
        return happens;
    }

    /** How often falling asleep shows the red words ("€AN Y0UU $EE M33?"). */
    public static final double WORDS_CHANCE = 0.04;
    /** After this many days (for them: played, or passed while they slept) the picture shows every time they sleep. */
    public static final int PICTURE_AFTER_DAYS = 3;
    /** /foglurker force sleep-words: the next time they sleep, the words show. */
    private static final java.util.Set<UUID> FORCED_WORDS = new java.util.HashSet<>();

    /**
     * The faint picture shows whenever they sleep once they have had sleep paralysis, once {@link #PICTURE_AFTER_DAYS}
     * days have passed for them, or once they have seen the creature itself (its pale shape, not a disguise).
     */
    public static boolean seesPicture(ServerPlayer p, PlayerBehaviorProfile prof) {
        return prof.sleepParalysisSeen || prof.sawRealShape || daysPassed(p, prof) >= PICTURE_AFTER_DAYS;
    }

    /**
     * Whole days that have passed for them in this world: the time they have played, or the world's days since they
     * were first counted (so nights slept through count too), whichever is more.
     */
    public static long daysPassed(ServerPlayer p, PlayerBehaviorProfile prof) {
        long played = p.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME));
        long dayTime = p.level().getServer().overworld().getDayTime();
        if (prof.firstDayTime == Long.MIN_VALUE) {
            prof.firstDayTime = Math.max(0, dayTime - played); // (counted from when they started playing here)
            FogLurkerSavedData.get(p.level().getServer()).setDirty();
        }
        return Math.max(played, dayTime - prof.firstDayTime) / 24000L;
    }

    /** They saw the creature in its own shape: from now on the picture shows whenever they sleep. */
    public static void sawRealShape(ServerPlayer p) {
        FogLurkerSavedData data = FogLurkerSavedData.get(p.level().getServer());
        PlayerBehaviorProfile prof = data.profile(p.getUUID());
        if (!prof.sawRealShape) {
            prof.sawRealShape = true;
            data.setDirty();
        }
        ModAdvancements.award(p, ModAdvancements.FIRST_SIGHTING);
    }

    /** /foglurker force sleep-words: the next time they sleep, the red words show. */
    public static void forceWords(ServerPlayer p) {
        FORCED_WORDS.add(p.getUUID());
    }

    /** Sleep paralysis: out of bed, half a heart, and a question in chat. Public for the game tests. */
    public static void strike(ServerPlayer p) {
        FogLurkerSavedData data = FogLurkerSavedData.get(p.level().getServer());
        PlayerBehaviorProfile prof = data.profile(p.getUUID());
        if (!prof.sleepParalysisForced && com.foglurker.armor.FogArmor.hasFullSet(p)) {
            THIS_SLEEP.put(p.getUUID(), false);
            return;
        }
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
        ModAdvancements.award(p, ModAdvancements.SLEEP_PARALYSIS);
        if (LurkerDirector.leftAloneTicks(p) < NO_VISIT_TICKS) {
            LurkerDirector.leaveAlone(p, NO_VISIT_TICKS); // and it does not show up after it (never shortens a longer peace)
        }
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
        FORCED_WORDS.clear();
        CALM_UNTIL.clear();
    }
}
