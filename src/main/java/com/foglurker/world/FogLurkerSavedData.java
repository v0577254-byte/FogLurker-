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

    /** Ends the grace period now, whatever the mode (even if it is later switched to a longer one). */
    public void endGrace() {
        long longest = 0;
        for (LurkerDifficulty d : LurkerDifficulty.values()) {
            longest = Math.max(longest, d.graceTicks());
        }
        graceElapsedTicks = Math.max(graceElapsedTicks, longest);
        nextSpawnTicks = -1; // the 5 minute roll timer starts on the next tick
        setDirty();
    }

    public void tickGrace() {
        if (inGrace()) {
            graceElapsedTicks++;
            if (graceElapsedTicks % 200 == 0) {
                setDirty();
            }
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
        boolean spawn = guaranteedNext || roll < chance;
        guaranteedNext = !spawn;
        setDirty();
        return spawn;
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
        if (difficulty != null) {
            tag.putString("difficulty", difficulty.id());
        }
        tag.putLong("graceElapsed", graceElapsedTicks);
        tag.putInt("nextSpawn", nextSpawnTicks);
        tag.putBoolean("guaranteedNext", guaranteedNext);
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
        d.encounters = tag.getInt("encounters");
        CompoundTag prof = tag.getCompound("profiles");
        for (String k : prof.getAllKeys()) {
            try {
                d.profiles.put(UUID.fromString(k), PlayerBehaviorProfile.load(prof.getCompound(k)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        d.entityStats = EntityBehaviorStats.load(tag.getCompound("entityStats"));
        ListTag notice = tag.getList("voiceNotice", Tag.TAG_INT_ARRAY);
        for (Tag t : notice) {
            d.voiceNoticeShown.add(NbtUtils.loadUUID(t));
        }
        return d;
    }
}
