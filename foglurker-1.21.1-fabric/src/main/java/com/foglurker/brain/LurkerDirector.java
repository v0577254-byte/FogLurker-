package com.foglurker.brain;

import com.foglurker.FogLurkerMod;
import com.foglurker.config.FogLurkerConfig;
import com.foglurker.config.LurkerDifficulty;
import com.foglurker.entity.Disguise;
import com.foglurker.entity.FogLurkerEntity;
import com.foglurker.learning.PlayerBehaviorProfile;
import com.foglurker.learning.RecentChat;
import com.foglurker.registry.ModEntities;
import com.foglurker.voice.VoiceBridge;
import com.foglurker.world.FogLurkerSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.entity.FakePlayer;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides when, where, as whom and against whom the Fog Lurker appears. Also runs the grace period.
 */
public final class LurkerDirector {
    private static final Set<FogLurkerEntity> ACTIVE = new HashSet<>();
    /** Game tests switch natural appearances off so creatures from one test never wander into another. */
    public static volatile boolean naturalSpawnsPaused;
    /** Game time each player's last appearance ended, so nobody gets two in a row while others wait. */
    private static final java.util.Map<java.util.UUID, Long> LAST_VISIT = new java.util.HashMap<>();
    /** Game time until which a player is left alone (they got away from the night meeting). */
    private static final java.util.Map<java.util.UUID, Long> LEFT_ALONE_UNTIL = new java.util.HashMap<>();
    /** Chance a Deathwish appearance at night, out in the open, is the night meeting. */
    public static final float NIGHT_MEETING_CHANCE = 0.3F;
    /** Game time until which bells do not work against an appearance aimed at that player. */
    private static final java.util.Map<java.util.UUID, Long> BELL_IMMUNE_UNTIL = new java.util.HashMap<>();

    private LurkerDirector() {
    }

    // =================================================================== ticking

    /** Runs one piece of side work; an unexpected error is logged instead of crashing the server tick. */
    static void safely(String what, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException e) {
            FogLurkerMod.LOGGER.error("Fog Lurker: {} failed; it is tried again later", what, e);
        }
    }

    /** Every server tick, at the end (ModEvents). */
    public static void onServerTick(MinecraftServer server) {
        // Voice upkeep is wrapped: whatever goes wrong with a disk, it must never take the server tick down.
        safely("voice chat", VoiceBridge::tick);
        if (server.getPlayerList().getPlayerCount() == 0) {
            if (server.getTickCount() % 12000 == 0) {
                // Running, just empty: not "unused". The 30-day expiry still runs, so someone who returns after
                // 30 days away finds their clips gone, as promised, even if nobody else came by.
                safely("voice clean-up", () -> com.foglurker.voice.VoiceRetention.emptyHeartbeat(System.currentTimeMillis()));
            }
            return; // the spawn countdown, grace and hauntings only run while someone is playing (rest, fear, peace and
                    // the bell cooldown are world time and keep counting, like the kill message's "30 minutes")
        }
        FogLurkerSavedData data = FogLurkerSavedData.get(server);
        data.tickGrace();
        safely("the hauntings", () -> Hauntings.tick(server, data));
        if (server.getTickCount() % 12000 == 0) { // every 10 minutes
            List<java.util.UUID> online = new ArrayList<>();
            server.getPlayerList().getPlayers().forEach(p -> online.add(p.getUUID()));
            safely("voice clean-up", () -> com.foglurker.voice.VoiceRetention.heartbeat(System.currentTimeMillis(), online));
        }
        ACTIVE.removeIf(Entity::isRemoved);
        if (naturalSpawnsPaused || !data.naturalSpawning() || data.inGrace() || isResting(server) || !rollsRunning(server)) {
            return; // busy with everyone it could go after: the clock waits
        }
        if (data.nextSpawnTicks() < 0) {
            // The grace period just ended: first roll in 5 minutes.
            data.setNextSpawnTicks(rollInterval());
            return;
        }
        if (data.nextSpawnTicks() > rollInterval()) {
            data.setNextSpawnTicks(rollInterval()); // spawnRollMinutes was lowered: the shorter wait applies now
        }
        data.countDownSpawn();
        if (data.nextSpawnTicks() == 0) {
            // Every 5 minutes: 50/50. If it did not come, the next roll is guaranteed.
            boolean spawn = data.rollSpawn(server.overworld().getRandom().nextDouble(), FogLurkerConfig.COMMON.spawnChance.get());
            if (spawn && !trySpawnNaturally(server, data)) {
                // It wanted to come but nobody was eligible / there was no room: keep it owed, retry in 30 s.
                data.setGuaranteedNext(true);
                data.setNextSpawnTicks(600);
                return;
            }
            data.setNextSpawnTicks(rollInterval());
        }
    }

    /** Ticks between two spawn rolls (5 minutes by default). */
    public static int rollInterval() {
        return FogLurkerConfig.COMMON.spawnRollMinutes.get() * 60 * 20;
    }

    /**
     * Runs before the final save (players are still online). The countdown and grace only mark themselves dirty
     * every 10 s. A visit still going ends here, not when the creature is removed (that comes after the save,
     * too late to be kept): a hunt counts as getting away, as a logout does, and the next roll stays at least
     * 5 minutes away with the target remembered as just visited.
     */
    public static void onServerStopping(MinecraftServer server) {
        FogLurkerSavedData data = FogLurkerSavedData.get(server);
        long now = server.overworld().getGameTime();
        for (FogLurkerEntity lurker : active()) {
            LurkerBrain brain = lurker.brain();
            if (brain != null && !brain.isEnded()) {
                safely("closing a visit", () -> {
                    brain.onServerStopping();
                    LAST_VISIT.put(brain.targetId(), now);
                    if (data.nextSpawnTicks() < rollInterval()) {
                        data.setNextSpawnTicks(rollInterval());
                    }
                });
            }
        }
        data.setDirty();
    }

    public static void onServerStopped() {
        ACTIVE.clear();
        LAST_VISIT.clear();
        LEFT_ALONE_UNTIL.clear();
        FEAR_UNTIL.clear();
        BELL_IMMUNE_UNTIL.clear();
        naturalSpawnsPaused = false;
        RecentChat.clear();
        Hauntings.reset();
    }

    /** Counts as the end of a visit to this player (a creature switched away from them). */
    public static void noteLeft(java.util.UUID player) {
        MinecraftServer server = com.foglurker.FogLurkerMod.server();
        if (server != null) {
            LAST_VISIT.put(player, server.overworld().getGameTime());
            FogLurkerSavedData.get(server).setDirty();
        }
    }

    public static void onRemoved(FogLurkerEntity lurker) {
        boolean was = ACTIVE.remove(lurker);
        if (was && lurker.brain() != null && lurker.level().getServer() != null) {
            LAST_VISIT.put(lurker.brain().targetId(), lurker.level().getServer().overworld().getGameTime());
            FogLurkerSavedData.get(lurker.level().getServer()).setDirty();
        }
        if (was && lurker.level().getServer() != null) {
            // The next roll comes (at least) 5 minutes after any appearance ends, even with another still out,
            // so it never lands inside the "just visited" wait.
            FogLurkerSavedData data = FogLurkerSavedData.get(lurker.level().getServer());
            if (ACTIVE.isEmpty() || data.nextSpawnTicks() < rollInterval()) {
                data.setNextSpawnTicks(rollInterval());
            }
        }
    }

    public static List<FogLurkerEntity> active() {
        ACTIVE.removeIf(Entity::isRemoved);
        return new ArrayList<>(ACTIVE);
    }

    /**
     * A bell was rung: every creature within 32 blocks is driven off. It learns, though: once a bell has
     * driven it away from a player, bells do nothing against it for that player for a while
     * ({@code bellCooldownMinutes}).
     *
     * @param ringer who rang it (told when the bell does not work), or null
     * @return how many creatures were driven off
     */
    public static int onBell(ServerLevel level, BlockPos bell, @Nullable ServerPlayer ringer) {
        int driven = 0;
        boolean ignored = false;
        long now = level.getServer().overworld().getGameTime();
        long cooldown = FogLurkerConfig.COMMON.bellCooldownMinutes.get() * 60L * 20L;
        // Who was already immune before this ring: one ring drives off every creature after the same player.
        java.util.Set<java.util.UUID> immuneBefore = new java.util.HashSet<>();
        BELL_IMMUNE_UNTIL.forEach((id, until) -> {
            if (now < until) {
                immuneBefore.add(id);
            }
        });
        for (FogLurkerEntity lurker : active()) {
            LurkerBrain brain = lurker.brain();
            if (brain == null || brain.isEnded() || lurker.level() != level || !lurker.blockPosition().closerThan(bell, 32)
                    || brain.phase() == LurkerBrain.Phase.FLEE) {
                continue;
            }
            java.util.UUID target = brain.targetId();
            if (cooldown > 0 && immuneBefore.contains(target)) {
                ignored = true; // it has heard that bell before
                continue;
            }
            brain.onBell();
            if (cooldown > 0) {
                BELL_IMMUNE_UNTIL.put(target, now + cooldown);
                FogLurkerSavedData.get(level.getServer()).setDirty();
            }
            driven++;
        }
        if (ignored && driven == 0 && ringer != null) {
            ringer.displayClientMessage(net.minecraft.network.chat.Component.literal("The bell rings out... nothing runs this time.")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.ITALIC), true);
        }
        return driven;
    }

    public static int onBell(ServerLevel level, BlockPos bell) {
        return onBell(level, bell, null);
    }

    /** Forget bell immunity (game tests only). */
    public static void resetBells() {
        BELL_IMMUNE_UNTIL.clear();
    }

    /** Attack chance after escalation: +10% per escape, up to +30%. */
    public static double attackChance(LurkerDifficulty diff, PlayerBehaviorProfile profile) {
        return Math.min(1.0, diff.attackChance() + escalation(profile));
    }

    /** +10% per escape, up to +30% (0 with escapeEscalation off). */
    public static double escalation(PlayerBehaviorProfile profile) {
        return FogLurkerConfig.COMMON.escapeEscalation.get() ? Math.min(0.3, profile.escapes * 0.1) : 0.0;
    }

    /** True if another live creature is still sending its fog to this player. */
    public static boolean foggedByAnother(java.util.UUID player, LurkerBrain except) {
        for (FogLurkerEntity l : active()) {
            LurkerBrain b = l.brain();
            if (b != null && b != except && !b.isEnded() && player.equals(b.targetId())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ killed, and scared

    /** How long it rests after being killed. */
    public static final long REST_TICKS = 30L * 60 * 20;
    /** How long it remembers a scare burst. */
    public static final long FEAR_TICKS = 20L * 60 * 20;
    /** Game time until which it is afraid of a player (they used a scare burst near it, or were with someone who did). */
    private static final java.util.Map<java.util.UUID, Long> FEAR_UNTIL = new java.util.concurrent.ConcurrentHashMap<>();

    /** True for 30 minutes after it was killed: nothing appears and nothing haunts. */
    public static boolean isResting(MinecraftServer server) {
        return FogLurkerSavedData.get(server).restingAt(server.overworld().getGameTime());
    }

    public static long restTicksLeft(MinecraftServer server) {
        return Math.max(0, FogLurkerSavedData.get(server).restUntil() - server.overworld().getGameTime());
    }

    /** It died. Every other creature leaves too, and for 30 minutes it does nothing at all. */
    public static void onKilled(FogLurkerEntity dead, @Nullable ServerPlayer killer) {
        MinecraftServer server = dead.level().getServer();
        if (server == null) {
            return;
        }
        FogLurkerSavedData.get(server).setRestUntil(server.overworld().getGameTime() + REST_TICKS);
        Hauntings.cancelPending(); // no fake "friend" line still on its way (a fake "left" line still closes a fake "joined")
        for (FogLurkerEntity other : active()) {
            if (other != dead && other.brain() != null && !other.isDeadOrDying()) {
                other.brain().leaveBecauseKilled();
            }
        }
        Component line = Component.literal((killer != null ? killer.getGameProfile().getName() + " killed the Fog Lurker. "
                : "The Fog Lurker is dead. ") + "The fog lifts... for 30 minutes.").withStyle(ChatFormatting.DARK_AQUA);
        server.getPlayerList().broadcastSystemMessage(line, false);
    }

    /**
     * A scare burst: it is afraid of this player (and of anyone standing with them) for 20 minutes. While
     * it is, it is half as likely to attack them, and almost never when they are with friends.
     */
    public static void scare(ServerPlayer user) {
        long until = user.level().getServer().overworld().getGameTime() + FEAR_TICKS;
        FogLurkerSavedData.get(user.level().getServer()).setDirty();
        FEAR_UNTIL.put(user.getUUID(), until);
        for (ServerPlayer friend : user.level().getServer().getPlayerList().getPlayers()) {
            if (friend != user && friend.level() == user.level() && friend.distanceTo(user) <= 16) {
                FEAR_UNTIL.put(friend.getUUID(), until);
            }
        }
    }

    /** The 15-minute peace, the 20-minute fear and the bell cooldown, saved with the world (overworld game time). */
    public static net.minecraft.nbt.CompoundTag saveTimers() {
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        MinecraftServer server = com.foglurker.FogLurkerMod.server();
        long now = server == null ? Long.MIN_VALUE : server.overworld().getGameTime();
        t.put("leftAlone", writeTimes(LEFT_ALONE_UNTIL, now));
        t.put("fear", writeTimes(FEAR_UNTIL, now));
        t.put("bell", writeTimes(BELL_IMMUNE_UNTIL, now));
        // A visit only matters for half a roll; keep a day of them.
        t.put("lastVisit", writeTimes(LAST_VISIT, server == null ? Long.MIN_VALUE : now - Math.max(24000L, rollInterval() / 2L)));
        return t;
    }

    public static void loadTimers(net.minecraft.nbt.CompoundTag t) {
        readTimes(com.foglurker.util.Nbt.getListOrEmpty(t, "leftAlone"), LEFT_ALONE_UNTIL);
        readTimes(com.foglurker.util.Nbt.getListOrEmpty(t, "fear"), FEAR_UNTIL);
        readTimes(com.foglurker.util.Nbt.getListOrEmpty(t, "bell"), BELL_IMMUNE_UNTIL);
        readTimes(com.foglurker.util.Nbt.getListOrEmpty(t, "lastVisit"), LAST_VISIT);
    }

    private static net.minecraft.nbt.ListTag writeTimes(java.util.Map<java.util.UUID, Long> m, long keepAfter) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        m.forEach((id, until) -> {
            if (until < keepAfter) {
                return; // long over: not worth saving
            }
            net.minecraft.nbt.CompoundTag e = new net.minecraft.nbt.CompoundTag();
            e.putUUID("id", id);
            e.putLong("until", until);
            list.add(e);
        });
        return list;
    }

    private static void readTimes(net.minecraft.nbt.ListTag list, java.util.Map<java.util.UUID, Long> into) {
        for (int i = 0; i < list.size(); i++) {
            net.minecraft.nbt.CompoundTag e = com.foglurker.util.Nbt.getCompoundOrEmpty(list, i);
            if (e.hasUUID("id")) {
                into.merge(e.getUUID("id"), com.foglurker.util.Nbt.getLongOr(e, "until", 0L), Math::max);
            }
        }
    }

    public static boolean isFeared(ServerPlayer p) {
        return p.level().getServer().overworld().getGameTime() < FEAR_UNTIL.getOrDefault(p.getUUID(), Long.MIN_VALUE);
    }

    /** Attack chance for this player right now: escalation, then halved if it fears them, a tenth if they are with friends. */
    public static double attackChance(LurkerDifficulty diff, PlayerBehaviorProfile profile, ServerPlayer target) {
        return attackChance(diff, profile) * fearFactor(target);
    }

    /** 1 normally; 0.5 while it fears this player; 0.1 if they also have a friend within 16 blocks. */
    public static double fearFactor(ServerPlayer target) {
        if (!isFeared(target)) {
            return 1.0;
        }
        for (ServerPlayer o : target.level().getServer().getPlayerList().getPlayers()) {
            if (o != target && o.isAlive() && !o.isSpectator() && !com.foglurker.world.AfkTracker.isAfk(o)
                    && o.level() == target.level() && o.distanceTo(target) <= 16) {
                return 0.1;
            }
        }
        return 0.5;
    }

    public static void onPlayerChat(ServerPlayer player, String text) {
        for (FogLurkerEntity lurker : active()) {
            LurkerBrain brain = lurker.brain();
            if (brain != null && brain.targetId().equals(player.getUUID())) {
                brain.onTargetChat(text);
            }
        }
    }

    private static boolean eligible(ServerPlayer p) {
        return !(p instanceof FakePlayer) && p.isAlive() && !p.isSpectator() && !p.isCreative();
    }

    /** Whether the spawn-roll clock is counting down (it waits while it is busy with everyone it could visit). */
    public static boolean rollsRunning(MinecraftServer server) {
        ACTIVE.removeIf(Entity::isRemoved);
        // With nobody to visit (everyone AFK or hunted) the clock waits, so misses do not pile up. "Just visited"
        // does not stop the clock: that wait is shorter than a roll, so the next roll still comes 5 minutes after.
        return ACTIVE.size() < FogLurkerConfig.COMMON.maxConcurrent.get() && !freeCandidates(server, false).isEmpty();
    }

    /** Players it could appear to right now: not already being hunted, not just visited. */
    public static List<ServerPlayer> freeCandidates(MinecraftServer server) {
        return freeCandidates(server, true);
    }

    private static List<ServerPlayer> freeCandidates(MinecraftServer server, boolean skipJustVisited) {
        long now = server.overworld().getGameTime();
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (eligible(p) && (!FogLurkerConfig.COMMON.overworldOnly.get() || p.level().dimension() == Level.OVERWORLD)
                    && !isHunted(p.getUUID())
                    && (!skipJustVisited || now - LAST_VISIT.getOrDefault(p.getUUID(), Long.MIN_VALUE / 2) >= rollInterval() / 2)
                    && now >= LEFT_ALONE_UNTIL.getOrDefault(p.getUUID(), Long.MIN_VALUE)
                    && !com.foglurker.world.AfkTracker.isAfk(p)) { // nothing comes for someone who is not there
                candidates.add(p);
            }
        }
        return candidates;
    }

    /** Nothing comes for this player on its own for {@code ticks} (commands still work). */
    public static void leaveAlone(ServerPlayer p, long ticks) {
        LEFT_ALONE_UNTIL.put(p.getUUID(), p.level().getServer().overworld().getGameTime() + ticks);
        FogLurkerSavedData.get(p.level().getServer()).setDirty();
    }

    public static long leftAloneTicks(ServerPlayer p) {
        return Math.max(0, LEFT_ALONE_UNTIL.getOrDefault(p.getUUID(), 0L) - p.level().getServer().overworld().getGameTime());
    }

    /** Like {@link #isHunted}, not counting {@code self}. */
    public static boolean isHuntedByAnother(java.util.UUID player, LurkerBrain self) {
        for (FogLurkerEntity l : ACTIVE) {
            LurkerBrain b = l.brain();
            if (b != self && !l.isRemoved() && b != null && !b.isEnded() && b.targetId().equals(player)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isHunted(java.util.UUID player) {
        for (FogLurkerEntity l : ACTIVE) {
            LurkerBrain b = l.brain();
            if (!l.isRemoved() && b != null && !b.isEnded() && b.targetId().equals(player)) {
                return true;
            }
        }
        return false;
    }

    private static boolean trySpawnNaturally(MinecraftServer server, FogLurkerSavedData data) {
        List<ServerPlayer> candidates = freeCandidates(server);
        if (candidates.isEmpty()) {
            return false;
        }
        RandomSource r = server.overworld().getRandom();
        // Prefer whoever is furthest from everyone else: the one who wandered off alone.
        candidates.sort((a, b) -> Double.compare(loneliness(b), loneliness(a)));
        ServerPlayer target = r.nextFloat() < 0.7F ? candidates.get(0) : candidates.get(r.nextInt(candidates.size()));
        // If they are in a group, it picks its victim inside the group.
        List<ServerPlayer> group = new ArrayList<>();
        for (ServerPlayer p : candidates) {
            if (p.level() == target.level() && p.distanceTo(target) <= GROUP_RADIUS) {
                group.add(p);
            }
        }
        if (group.size() > 1) {
            target = pickInGroup(group, r);
        }
        if (spawn(target, null, null) != null) {
            return true;
        }
        // Nowhere to appear near them (up a 30-block pillar, say): anyone else free gets the visit instead,
        // rather than the same player being tried again every 30 seconds while everyone else waits.
        for (ServerPlayer other : candidates) {
            if (other != target && spawn(other, null, null) != null) {
                return true;
            }
        }
        return false;
    }

    /** Players this close to each other count as a group. */
    public static final double GROUP_RADIUS = 24.0;

    /**
     * Who it goes for in a group: usually the one with the least health (45%), otherwise usually the
     * one on the edge of the group, furthest from the others
     * (40%), and now and then anyone (15%).
     */
    public static ServerPlayer pickInGroup(List<ServerPlayer> group, RandomSource r) {
        float roll = r.nextFloat();
        if (roll < 0.45F) {
            ServerPlayer weakest = group.get(0);
            for (ServerPlayer p : group) {
                if (p.getHealth() + p.getAbsorptionAmount() < weakest.getHealth() + weakest.getAbsorptionAmount()) {
                    weakest = p;
                }
            }
            return weakest;
        }
        if (roll < 0.85F) {
            double cx = 0;
            double cz = 0;
            for (ServerPlayer p : group) {
                cx += p.getX();
                cz += p.getZ();
            }
            cx /= group.size();
            cz /= group.size();
            ServerPlayer edge = group.get(0);
            double far = -1;
            for (ServerPlayer p : group) {
                double d = net.minecraft.util.Mth.square(p.getX() - cx) + net.minecraft.util.Mth.square(p.getZ() - cz);
                if (d > far) {
                    far = d;
                    edge = p;
                }
            }
            return edge;
        }
        return group.get(r.nextInt(group.size()));
    }

    private static double loneliness(ServerPlayer p) {
        double nearest = 512;
        for (ServerPlayer o : p.level().getServer().getPlayerList().getPlayers()) {
            if (o != p && !o.isSpectator() && o.level() == p.level() && !(o instanceof net.fabricmc.fabric.api.entity.FakePlayer)
                    && !com.foglurker.world.AfkTracker.isAfk(o)) { // an AFK alt standing next to you is no company
                nearest = Math.min(nearest, o.distanceTo(p));
            }
        }
        return nearest;
    }

    // =================================================================== planning

    /**
     * Plans and spawns an appearance against {@code target}.
     *
     * @param forced       force a particular disguise (from the command), or null to choose
     * @param forcedAttack force attacking / not attacking, or null to roll by difficulty
     * @return the spawned creature, or null if no spot was found
     */
    @Nullable
    public static FogLurkerEntity spawn(ServerPlayer target, @Nullable Disguise forced, @Nullable Boolean forcedAttack) {
        ServerLevel level = target.serverLevel();
        FogLurkerSavedData data = FogLurkerSavedData.get(target.level().getServer());
        RandomSource r = level.getRandom();
        Encounter enc = plan(target, data, forced, forcedAttack, r);
        Vec3 pos = pickSpot(level, target, enc, r);
        if (pos == null) {
            // Low view distance or unloaded terrain: settle for somewhere closer, seen or not.
            pos = SpotFinder.findSpawn(level, target, 12, 24, null, true, false, r);
        }
        if (pos == null || !SpotFinder.isDry(level, BlockPos.containing(pos))) {
            return null; // never on, in or next to water
        }
        FogLurkerEntity lurker = ModEntities.FOG_LURKER.get().create(level);
        if (lurker == null) {
            return null;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(target.getZ() - pos.z, target.getX() - pos.x)) - 90F;
        lurker.moveTo(pos.x, pos.y, pos.z, yaw, 0F);
        lurker.setYHeadRot(yaw);
        lurker.setYBodyRot(yaw);
        lurker.begin(enc);
        level.addFreshEntity(lurker);
        ACTIVE.add(lurker);
        data.countEncounter();
        FogLurkerMod.LOGGER.debug("Fog Lurker appeared near {} as {} ({}, attack={})",
                target.getGameProfile().getName(), enc.disguise().id(), enc.mode(), enc.willAttack());
        return lurker;
    }

    /** A plan and a spot for {@code target} without spawning anything (used by the debug command). */
    public record DryRun(Encounter encounter, @Nullable Vec3 spot) {
    }

    public static DryRun dryRun(ServerPlayer target, Disguise disguise, boolean attack) {
        ServerLevel level = target.serverLevel();
        RandomSource r = level.getRandom();
        Encounter enc = plan(target, FogLurkerSavedData.get(target.level().getServer()), disguise, attack, r);
        Vec3 spot = pickSpot(level, target, enc, r);
        if (spot == null) {
            spot = SpotFinder.findSpawn(level, target, 12, 24, null, true, false, r);
        }
        return new DryRun(enc, spot);
    }

    /** What /foglurker force makes it do. */
    public enum Forced {
        /** Lie in tall grass or a bush and watch. Never attacks. */
        HIDE,
        /** Creep closer from cover to cover; drops flat when looked at. Vanishes when it gets close. */
        STALK,
        /** Hunt right now. */
        ATTACK
    }

    /** Spawns the pale crawler (no disguise) near {@code target} doing exactly what was asked. */
    @Nullable
    public static FogLurkerEntity spawnPale(ServerPlayer target, Forced what) {
        ServerLevel level = target.serverLevel();
        FogLurkerSavedData data = FogLurkerSavedData.get(target.level().getServer());
        RandomSource r = level.getRandom();
        LurkerBrain.Mode mode = what == Forced.STALK ? LurkerBrain.Mode.STALKER : LurkerBrain.Mode.WATCHER;
        Encounter enc = new Encounter(target.getUUID(), mode, Disguise.CRAWLER, null, null, what == Forced.ATTACK,
                data.difficulty(), 0, null);
        Vec3 pos = switch (what) {
            case HIDE -> pickSpot(level, target, enc, r);
            case STALK -> SpotFinder.findSpawn(level, target, 18, 28, null, true, true, r);
            case ATTACK -> SpotFinder.findSpawn(level, target, 10, 18, null, true, false, r);
        };
        if (pos == null) {
            pos = SpotFinder.findSpawn(level, target, 8, 20, null, false, false, r);
        }
        if (pos == null || !SpotFinder.isDry(level, BlockPos.containing(pos))) {
            return null;
        }
        FogLurkerEntity lurker = place(level, target, pos, enc);
        if (lurker != null && what == Forced.ATTACK && lurker.brain() != null) {
            lurker.brain().huntNow();
        }
        return lurker;
    }

    @Nullable
    private static FogLurkerEntity place(ServerLevel level, ServerPlayer target, Vec3 pos, Encounter enc) {
        FogLurkerEntity lurker = ModEntities.FOG_LURKER.get().create(level);
        if (lurker == null) {
            return null;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(target.getZ() - pos.z, target.getX() - pos.x)) - 90F;
        lurker.moveTo(pos.x, pos.y, pos.z, yaw, 0F);
        lurker.setYHeadRot(yaw);
        lurker.setYBodyRot(yaw);
        lurker.begin(enc);
        level.addFreshEntity(lurker);
        ACTIVE.add(lurker);
        FogLurkerSavedData.get(target.level().getServer()).countEncounter();
        return lurker;
    }

    /** Gives a hand-summoned creature (/summon) a plan against the nearest player. */
    public static boolean adopt(FogLurkerEntity lurker) {
        if (!(lurker.level() instanceof ServerLevel level)) {
            return false;
        }
        ServerPlayer nearest = null;
        double best = 96 * 96;
        for (ServerPlayer p : level.players()) {
            double d = p.distanceToSqr(lurker);
            if (!p.isSpectator() && p.isAlive() && !p.isCreative() && !(p instanceof net.fabricmc.fabric.api.entity.FakePlayer)
                    && !com.foglurker.world.AfkTracker.isAfk(p) && d < best) {
                best = d;
                nearest = p;
            }
        }
        if (nearest == null) {
            return false;
        }
        // /summon gives the pale crawler itself (README), not a random disguise.
        Encounter enc = plan(nearest, FogLurkerSavedData.get(level.getServer()), Disguise.CRAWLER, null, level.getRandom());
        lurker.begin(enc);
        ACTIVE.add(lurker);
        return true;
    }

    private record Option(LurkerBrain.Mode mode, Disguise disguise, int weight) {
    }

    private static Encounter plan(ServerPlayer target, FogLurkerSavedData data, @Nullable Disguise forced,
                                  @Nullable Boolean forcedAttack, RandomSource r) {
        LurkerDifficulty diff = data.difficulty();
        ServerLevel level = target.serverLevel();
        boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
        PlayerBehaviorProfile targetProfile = data.profile(target.getUUID());
        // Deathwish, at night, out in the open: sometimes you simply walk into it.
        if (forced == null && forcedAttack == null && diff == LurkerDifficulty.DEATHWISH && level.isNight()
                && SpotFinder.placeOf(level, target, null) == SpotFinder.Place.SURFACE && r.nextFloat() < NIGHT_MEETING_CHANCE) {
            // A 50/50 whether it means it, plus the grudge from earlier escapes and less after a scare burst's
            // fear, like any other attack.
            return nightMeeting(target, diff, !peaceful
                    && r.nextDouble() < Math.min(1.0, 0.5 + escalation(targetProfile)) * fearFactor(target));
        }
        boolean willAttack = forcedAttack != null ? forcedAttack : !peaceful && r.nextDouble() < attackChance(diff, targetProfile, target);

        ServerPlayer friend = pickFriend(target, r);
        boolean dogKnown = targetProfile.dogUuid != null;
        Wolf realDog = dogKnown && level.getEntity(targetProfile.dogUuid) instanceof Wolf w ? w : null;
        // Only when the real dog is not with them (a dog sitting right beside you must not get a twin).
        boolean dogAvailable = dogKnown && (realDog == null || realDog.distanceTo(target) > 24);

        List<Option> options = new ArrayList<>();
        if (forced != null) {
            options.add(new Option(modeFor(forced, friend != null), forced, 1));
        } else {
            if (!willAttack && r.nextDouble() < diff.pureWatchChance()) {
                options.add(new Option(LurkerBrain.Mode.WATCHER, Disguise.CRAWLER, 1));
            } else {
                // Copying a player is its favourite trick: a friend if one is online, otherwise you.
                if (friend != null) {
                    options.add(new Option(LurkerBrain.Mode.MIMIC, Disguise.PLAYER, 80));
                } else if (FogLurkerConfig.COMMON.allowDoppelganger.get()) {
                    options.add(new Option(LurkerBrain.Mode.DOPPELGANGER, Disguise.PLAYER, 35));
                }
                if (dogAvailable) {
                    options.add(new Option(LurkerBrain.Mode.DOG, Disguise.DOG, 22));
                }
                Disguise herd = nearbyHerdType(level, target);
                if (herd != null) {
                    options.add(new Option(LurkerBrain.Mode.HERD, herd, 26));
                } else {
                    Disguise[] farm = {Disguise.COW, Disguise.SHEEP, Disguise.PIG, Disguise.CHICKEN};
                    options.add(new Option(LurkerBrain.Mode.HERD, farm[r.nextInt(farm.length)], 8));
                }
                var biome = level.getBiome(target.blockPosition());
                if (biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_FOREST)) {
                    options.add(new Option(LurkerBrain.Mode.HERD, Disguise.WOLF, 8));
                }
                if (biome.is(BiomeTags.IS_SAVANNA) || biome.is(net.minecraft.world.level.biome.Biomes.PLAINS)
                        || biome.is(net.minecraft.world.level.biome.Biomes.SUNFLOWER_PLAINS)) {
                    options.add(new Option(LurkerBrain.Mode.HERD, r.nextInt(4) == 0 ? Disguise.DONKEY : Disguise.HORSE, 6));
                }
                if (biome.is(BiomeTags.IS_MOUNTAIN)) {
                    options.add(new Option(LurkerBrain.Mode.HERD, Disguise.GOAT, 6));
                }
                boolean dark = level.isNight() || level.getBrightness(LightLayer.SKY, target.blockPosition()) < 6;
                if (dark) {
                    options.add(new Option(LurkerBrain.Mode.ZOMBIE, darkShape(level, target.blockPosition(), r), 12));
                }
                options.add(new Option(LurkerBrain.Mode.WATCHER, Disguise.CRAWLER, willAttack ? 0 : 6));
            }
        }
        Option pick = weighted(options, r);

        java.util.UUID copiedId = null;
        String copiedName = null;
        int variant = 0;
        String dogName = null;
        switch (pick.mode()) {
            case MIMIC -> {
                if (friend != null) {
                    copiedId = friend.getUUID();
                    copiedName = friend.getGameProfile().getName();
                } else {
                    copiedId = target.getUUID();
                    copiedName = target.getGameProfile().getName();
                }
            }
            case DOPPELGANGER -> {
                copiedId = target.getUUID();
                copiedName = target.getGameProfile().getName();
            }
            case DOG -> {
                copiedId = target.getUUID();
                if (targetProfile.dogUuid != null) {
                    variant = Math.max(0, targetProfile.dogCollar);
                    dogName = targetProfile.dogName;
                } else {
                    variant = DyeColor.RED.getId();
                }
            }
            default -> variant = variantFor(pick.disguise(), r);
        }
        return new Encounter(target.getUUID(), pick.mode(), pick.disguise(), copiedId, copiedName, willAttack, diff,
                variant, dogName);
    }

    private static LurkerBrain.Mode modeFor(Disguise d, boolean hasFriend) {
        return switch (d) {
            case PLAYER -> hasFriend ? LurkerBrain.Mode.MIMIC : LurkerBrain.Mode.DOPPELGANGER;
            case DOG -> LurkerBrain.Mode.DOG;
            case ZOMBIE -> LurkerBrain.Mode.ZOMBIE;
            case CRAWLER -> LurkerBrain.Mode.WATCHER;
            default -> d.isHostile() ? LurkerBrain.Mode.ZOMBIE : LurkerBrain.Mode.HERD;
        };
    }

    /** For the game tests. */
    public static Disguise darkShapeForTests(ServerLevel level, BlockPos at, RandomSource r) {
        return darkShape(level, at, r);
    }

    /** Which hostile mob it is in the dark: a zombie most often, the local kind in a desert or where it snows. */
    static Disguise darkShape(ServerLevel level, BlockPos at, RandomSource r) {
        var biome = level.getBiome(at);
        boolean desert = biome.is(net.minecraft.world.level.biome.Biomes.DESERT);
        boolean snowy = biome.value().coldEnoughToSnow(at);
        int roll = r.nextInt(20);
        if (roll < 6) {
            return desert ? Disguise.HUSK : Disguise.ZOMBIE;
        } else if (roll < 10) {
            return snowy ? Disguise.STRAY : Disguise.SKELETON;
        } else if (roll < 13) {
            return Disguise.CREEPER;
        } else if (roll < 16) {
            return Disguise.SPIDER;
        } else if (roll < 18) {
            return Disguise.ENDERMAN;
        } else if (roll < 19) {
            return Disguise.WITCH;
        }
        return Disguise.PILLAGER;
    }

    /** The look of a stolen shape: a sheep's wool, a villager's job, a horse's coat... (0 for one look only) */
    public static int variantFor(Disguise d, RandomSource r) {
        return switch (d) {
            case SHEEP -> net.minecraft.world.entity.animal.Sheep.getRandomSheepColor(r).getId();
            case VILLAGER -> r.nextInt(Disguise.VILLAGER_JOBS.length);
            case HORSE -> r.nextInt(7);
            case LLAMA -> r.nextInt(4);
            case RABBIT -> r.nextInt(6);
            case MOOSHROOM -> r.nextInt(10) == 0 ? 1 : 0; // brown ones are rare
            default -> 0;
        };
    }

    private static Option weighted(List<Option> options, RandomSource r) {
        int total = options.stream().mapToInt(Option::weight).sum();
        if (total <= 0) {
            return options.get(0);
        }
        int roll = r.nextInt(total);
        for (Option o : options) {
            roll -= o.weight();
            if (roll < 0) {
                return o;
            }
        }
        return options.get(options.size() - 1);
    }

    /** Another online player who is far away from the target (so the target cannot see the real one). */
    @Nullable
    private static ServerPlayer pickFriend(ServerPlayer target, RandomSource r) {
        int minSep = FogLurkerConfig.COMMON.minPlayerSeparation.get();
        List<ServerPlayer> far = new ArrayList<>();
        for (ServerPlayer p : target.level().getServer().getPlayerList().getPlayers()) {
            if (p == target || p instanceof FakePlayer || p.isSpectator() || !p.isAlive()) {
                continue;
            }
            if (p.level() != target.level() || p.distanceTo(target) >= minSep) {
                far.add(p);
            }
        }
        if (far.isEmpty()) {
            return null;
        }
        // Prefer someone in the same dimension: "they went to get wood".
        far.sort((a, b) -> Boolean.compare(b.level() == target.level(), a.level() == target.level()));
        return r.nextFloat() < 0.8F ? far.get(0) : far.get(r.nextInt(far.size()));
    }

    @Nullable
    private static Disguise nearbyHerdType(ServerLevel level, ServerPlayer target) {
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(48),
                m -> m.isAlive() && m.distanceTo(target) > 10);
        int[] counts = new int[Disguise.values().length];
        for (Mob m : mobs) {
            Disguise d = Disguise.of(m);
            if (d != null && d != Disguise.DOG && d.isPassive()) {
                counts[d.ordinal()]++;
            }
        }
        Disguise best = null;
        int max = 0;
        for (Disguise d : Disguise.values()) {
            if (counts[d.ordinal()] > max) {
                max = counts[d.ordinal()];
                best = d;
            }
        }
        return best;
    }

    // =================================================================== placement

    @Nullable
    /** The night meeting: 50/50 whether it means "i will not hurt you". */
    public static Encounter nightMeeting(ServerPlayer target, LurkerDifficulty diff, boolean willAttack) {
        return new Encounter(target.getUUID(), LurkerBrain.Mode.NIGHT_MEETING, Disguise.CRAWLER, null, null, willAttack, diff, 0, null);
    }

    /** Starts a night meeting with {@code target} right now (tests and /foglurker force meet). */
    @Nullable
    public static FogLurkerEntity spawnNightMeeting(ServerPlayer target, boolean willAttack) {
        ServerLevel level = target.serverLevel();
        Encounter enc = nightMeeting(target, LurkerDifficulty.DEATHWISH, willAttack);
        Vec3 pos = pickSpot(level, target, enc, level.getRandom());
        return pos == null ? null : place(level, target, pos, enc);
    }

    private static Vec3 pickSpot(ServerLevel level, ServerPlayer target, Encounter enc, RandomSource r) {
        switch (enc.mode()) {
            case NIGHT_MEETING -> {
                // Right in front of you, a few steps away: you walk straight into it.
                Vec3 ahead = target.getViewVector(1F).multiply(1, 0, 1);
                Vec3 spot = SpotFinder.findSpawn(level, target, 6, 10, ahead.lengthSqr() < 1e-4 ? null : ahead, false, false, r);
                return spot != null ? spot : SpotFinder.findSpawn(level, target, 6, 14, null, false, false, r);
            }
            case MIMIC -> {
                ServerPlayer friend = enc.copiedId() == null ? null : target.level().getServer().getPlayerList().getPlayer(enc.copiedId());
                Vec3 dir = friend != null && friend.level() == target.level()
                        ? friend.position().subtract(target.position()) : null;
                // Walk in from the direction the real friend went: it is "them coming back".
                return SpotFinder.findSpawn(level, target, 26, 38, dir, true, false, r);
            }
            case DOG -> {
                return SpotFinder.findSpawn(level, target, 18, 28, null, true, false, r);
            }
            case HERD -> {
                Vec3 herd = herdSpot(level, target, enc.disguise());
                if (herd != null) {
                    Vec3 p = nearHidden(level, target, herd, r);
                    if (p != null) {
                        return p;
                    }
                }
                return SpotFinder.findSpawn(level, target, 14, 26, null, true, false, r);
            }
            case ZOMBIE -> {
                return SpotFinder.findSpawn(level, target, 18, 30, null, true, false, r);
            }
            case WATCHER -> {
                PlayerBehaviorProfile prof = FogLurkerSavedData.get(target.level().getServer()).profileOrNull(target.getUUID());
                BlockPos home = prof != null && prof.isNearHome(level.dimension().location().toString(), target.blockPosition(), 48)
                        ? prof.home() : null;
                SpotFinder.Place place = SpotFinder.placeOf(level, target, home);
                if (place == SpotFinder.Place.INDOORS) {
                    // You are inside: it stands outside, where you can see it through the window.
                    Vec3 outside = SpotFinder.findOutside(level, target, r);
                    if (outside != null) {
                        return outside;
                    }
                }
                if (place == SpotFinder.Place.UNDERGROUND) {
                    // In a cave there is no "far away": it waits in the dark at the end of the tunnel.
                    Vec3 dark = SpotFinder.findSpawn(level, target, 12, 28, null, false, true, r);
                    if (dark != null) {
                        return dark;
                    }
                }
                return SpotFinder.findSpawn(level, target, 36, 56, null, false, true, r);
            }
            case DOPPELGANGER -> {
                return SpotFinder.findSpawn(level, target, 22, 34, null, false, true, r);
            }
        }
        return null;
    }

    @Nullable
    private static Vec3 herdSpot(ServerLevel level, ServerPlayer target, Disguise type) {
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(48),
                m -> m.isAlive() && Disguise.of(m) == type && m.distanceTo(target) > 10);
        return mobs.isEmpty() ? null : mobs.get(level.getRandom().nextInt(mobs.size())).position();
    }

    @Nullable
    private static Vec3 nearHidden(ServerLevel level, ServerPlayer target, Vec3 around, RandomSource r) {
        Vec3 fallback = null;
        for (int i = 0; i < 16; i++) {
            int x = (int) Math.floor(around.x + (r.nextDouble() - 0.5) * 8);
            int z = (int) Math.floor(around.z + (r.nextDouble() - 0.5) * 8);
            var g = SpotFinder.ground(level, x, z, (int) Math.floor(around.y), true);
            if (g == null) {
                continue;
            }
            Vec3 spot = Vec3.atBottomCenterOf(g);
            if (!SpotFinder.canSee(target, spot.add(0, 1, 0))) {
                return spot;
            }
            fallback = spot;
        }
        return fallback;
    }
}
