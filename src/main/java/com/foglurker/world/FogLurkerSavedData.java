package com.foglurker.world;

import com.foglurker.config.FogLurkerConfig;
import com.foglurker.config.LurkerDifficulty;
import com.foglurker.learning.EntityBehaviorStats;
import com.foglurker.learning.PlayerBehaviorProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-world state: chosen mode, grace period progress, next spawn countdown and everything learned.
 * Stored in the overworld's data folder (world/data/foglurker.dat).
 */
public final class FogLurkerSavedData extends SavedData {
    private static final String NAME = "foglurker";

    private LurkerDifficulty difficulty;
    /** Ticks played (with at least one player online) since the world was first started with the mod. */
    private long graceElapsedTicks;
    /** Ticks until the next appearance; -1 = not scheduled yet. */
    private int nextSpawnTicks = -1;
    /** Set when a roll failed: the next roll always spawns it. */
    private boolean guaranteedNext;
    /** Whether it appears on its own (spawn rolls). Off: it only comes when summoned with a command. */
    private boolean naturalSpawning = true;
    /** Game time until which it rests after being killed: nothing appears and nothing haunts. */
    private long restUntil;
    private int encounters;
    private final Map<UUID, PlayerBehaviorProfile> profiles = new HashMap<>();
    private EntityBehaviorStats entityStats = new EntityBehaviorStats();
    private final Set<UUID> voiceNoticeShown = new HashSet<>();

    public static FogLurkerSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FogLurkerSavedData::load, FogLurkerSavedData::new, NAME);
    }

    public LurkerDifficulty difficulty() {
        if (difficulty == null) {
            difficulty = FogLurkerConfig.COMMON.parsedDefaultDifficulty();
            setDirty();
        }
        return difficulty;
    }

    public void setDifficulty(LurkerDifficulty d) {
        this.difficulty = d;
        setDirty();
    }

    public long graceRemainingTicks() {
        return Math.max(0, difficulty().graceTicks() - graceElapsedTicks);
    }

    public boolean inGrace() {
        return graceRemainingTicks() > 0;
    }

    private static long longestGrace() {
        long longest = 0;
        for (LurkerDifficulty d : LurkerDifficulty.values()) {
            longest = Math.max(longest, d.graceTicks());
        }
        return longest;
    }

    /** Ends the grace period now, whatever the mode (even if it is later switched to a longer one). */
    public void endGrace() {
        graceElapsedTicks = Math.max(graceElapsedTicks, longestGrace());
        nextSpawnTicks = -1; // the 5 minute roll timer starts on the next tick
        setDirty();
    }

    public void tickGrace() {
        if (!inGrace()) {
            // It ran out: it is out there now. Switching to a mode with a longer grace must not call it back.
            if (graceElapsedTicks < longestGrace()) {
                graceElapsedTicks = longestGrace();
                setDirty();
            }
            return;
        }
        graceElapsedTicks++;
        if (graceElapsedTicks % 200 == 0) {
            setDirty();
        }
    }

    public int nextSpawnTicks() {
        return nextSpawnTicks;
    }

    public void setNextSpawnTicks(int ticks) {
        this.nextSpawnTicks = ticks;
        setDirty();
    }

    public void countDownSpawn() {
        if (nextSpawnTicks > 0) {
            nextSpawnTicks--;
            if (nextSpawnTicks % 200 == 0) {
                setDirty();
            }
        }
    }

    public boolean nextIsGuaranteed() {
        return guaranteedNext;
    }

    public void setGuaranteedNext(boolean guaranteed) {
        this.guaranteedNext = guaranteed;
        setDirty();
    }

    /**
     * One spawn roll: guaranteed if the previous roll failed, otherwise {@code chance}.
     *
     * @param roll a random number in [0, 1)
     * @return true if it should appear now
     */
    public boolean rollSpawn(double roll, double chance) {
        // A chance of 0 means never: a miss must not turn into a guaranteed appearance.
        boolean spawn = chance > 0 && (guaranteedNext || roll < chance);
        guaranteedNext = !spawn && chance > 0;
        setDirty();
        return spawn;
    }

    public long restUntil() {
        return restUntil;
    }

    public boolean restingAt(long gameTime) {
        return gameTime < restUntil;
    }

    public void setRestUntil(long gameTime) {
        restUntil = gameTime;
        setDirty();
    }

    public boolean naturalSpawning() {
        return naturalSpawning;
    }

    public void setNaturalSpawning(boolean on) {
        if (on && !naturalSpawning) {
            nextSpawnTicks = -1; // starts a fresh 5-minute countdown (after grace, if any is left)
            guaranteedNext = false;
        }
        naturalSpawning = on;
        setDirty();
    }

    public int encounters() {
        return encounters;
    }

    public void countEncounter() {
        encounters++;
        setDirty();
    }

    public PlayerBehaviorProfile profile(UUID id) {
        return profiles.computeIfAbsent(id, k -> {
            setDirty();
            return new PlayerBehaviorProfile();
        });
    }

    public PlayerBehaviorProfile profileOrNull(UUID id) {
        return profiles.get(id);
    }

    public Map<UUID, PlayerBehaviorProfile> profiles() {
        return java.util.Collections.unmodifiableMap(profiles);
    }

    public EntityBehaviorStats entityStats() {
        return entityStats;
    }

    /** @return true the first time it is called for this player. */
    public boolean markVoiceNoticeShown(UUID id) {
        boolean added = voiceNoticeShown.add(id);
        if (added) {
            setDirty();
        }
        return added;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        boolean keepChat;
        try {
            keepChat = FogLurkerConfig.COMMON.learnChat.get();
        } catch (IllegalStateException notLoaded) {
            keepChat = false; // when unsure, keep nothing anyone typed
        }
        // Someone not seen for a year is forgotten (their name, habits and chat), so the file cannot grow forever.
        // Never anyone who opted out of chat learning: forgetting them would forget that choice too.
        // (Profiles from older saves, with no date, get one now, so their year starts counting too. Anyone online
        // right now is never forgotten, whatever their date says.)
        long nowMillis = System.currentTimeMillis();
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        // A clock that jumped years ahead would make everyone look gone: never forget more than a quarter at once.
        long dueCount = profiles.entrySet().stream().filter(e -> e.getValue().lastSeenMillis != 0 && !e.getValue().chatLearningOff
                && nowMillis - e.getValue().lastSeenMillis > 365L * 24 * 60 * 60 * 1000).count();
        boolean pruneOk = dueCount <= Math.max(2, profiles.size() / 4);
        if (!pruneOk) {
            com.foglurker.FogLurkerMod.LOGGER.warn("Fog Lurker: {} of {} player profiles look unseen for a year; not forgetting "
                    + "them (has the system clock jumped?)", dueCount, profiles.size());
        }
        if (pruneOk) profiles.entrySet().removeIf(e -> {
            PlayerBehaviorProfile p = e.getValue();
            if (p.lastSeenMillis == 0) {
                p.lastSeenMillis = nowMillis;
            }
            boolean online = server != null && server.getPlayerList().getPlayer(e.getKey()) != null;
            return !online && !p.chatLearningOff && nowMillis - p.lastSeenMillis > 365L * 24 * 60 * 60 * 1000;
        });
        if (!keepChat) {
            // learnChat off means nothing typed is kept: lines stored before it was switched off go too.
            profiles.values().forEach(p -> p.chatSamples.clear());
        }
        if (difficulty != null) {
            tag.putString("difficulty", difficulty.id());
        }
        tag.putLong("graceElapsed", graceElapsedTicks);
        tag.putInt("nextSpawn", nextSpawnTicks);
        tag.putBoolean("guaranteedNext", guaranteedNext);
        tag.putBoolean("naturalSpawning", naturalSpawning);
        tag.putLong("restUntil", restUntil);
        tag.putInt("encounters", encounters);
        CompoundTag prof = new CompoundTag();
        profiles.forEach((id, p) -> prof.put(id.toString(), p.save()));
        tag.put("profiles", prof);
        tag.put("entityStats", entityStats.save());
        ListTag notice = new ListTag();
        for (UUID id : voiceNoticeShown) {
            notice.add(NbtUtils.createUUID(id));
        }
        tag.put("voiceNotice", notice);
        tag.put("timers", com.foglurker.brain.LurkerDirector.saveTimers());
        return tag;
    }

    public static FogLurkerSavedData load(CompoundTag tag) {
        FogLurkerSavedData d = new FogLurkerSavedData();
        if (tag.contains("difficulty")) {
            d.difficulty = LurkerDifficulty.byId(tag.getString("difficulty"));
        }
        d.graceElapsedTicks = tag.getLong("graceElapsed");
        d.nextSpawnTicks = tag.contains("nextSpawn") ? tag.getInt("nextSpawn") : -1;
        d.guaranteedNext = tag.getBoolean("guaranteedNext");
        d.naturalSpawning = !tag.contains("naturalSpawning") || tag.getBoolean("naturalSpawning");
        d.restUntil = tag.getLong("restUntil");
        d.encounters = tag.getInt("encounters");
        CompoundTag prof = tag.getCompound("profiles");
        for (String k : prof.getAllKeys()) {
            try {
                d.profiles.put(UUID.fromString(k), PlayerBehaviorProfile.load(prof.getCompound(k)));
            } catch (RuntimeException damaged) {
                // one damaged profile is skipped; it must never cost everyone else's (Minecraft would drop the whole file)
            }
        }
        d.entityStats = EntityBehaviorStats.load(tag.getCompound("entityStats"));
        ListTag notice = tag.getList("voiceNotice", Tag.TAG_INT_ARRAY);
        for (Tag t : notice) {
            try {
                d.voiceNoticeShown.add(NbtUtils.loadUUID(t));
            } catch (RuntimeException damaged) {
                // a damaged entry: at worst that player sees the notice once more
            }
        }
        com.foglurker.brain.LurkerDirector.loadTimers(tag.getCompound("timers"));
        return d;
    }
}
