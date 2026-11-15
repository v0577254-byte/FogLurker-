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
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides when, where, as whom and against whom the Fog Lurker appears. Also runs the grace period.
 */
@Mod.EventBusSubscriber(modid = FogLurkerMod.MOD_ID)
public final class LurkerDirector {
    private static final Set<FogLurkerEntity> ACTIVE = new HashSet<>();

    private LurkerDirector() {
    }

    // =================================================================== ticking

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        VoiceBridge.tick();
        if (server.getPlayerList().getPlayerCount() == 0) {
            return; // timers only run while someone is playing
        }
        FogLurkerSavedData data = FogLurkerSavedData.get(server);
        data.tickGrace();
        ACTIVE.removeIf(Entity::isRemoved);
        if (data.inGrace() || !ACTIVE.isEmpty()) {
            return;
        }
        if (data.nextSpawnTicks() < 0) {
            data.setNextSpawnTicks(randomInterval(server.overworld().getRandom()));
            return;
        }
        data.countDownSpawn();
        if (data.nextSpawnTicks() == 0) {
            boolean spawned = trySpawnNaturally(server, data);
            // On failure (nobody eligible, no room), try again in 30 seconds.
            data.setNextSpawnTicks(spawned ? randomInterval(server.overworld().getRandom()) : 600);
        }
    }

    public static int randomInterval(RandomSource r) {
        int min = FogLurkerConfig.COMMON.spawnIntervalMinMinutes.get();
        int max = Math.max(min, FogLurkerConfig.COMMON.spawnIntervalMaxMinutes.get());
        return (min + r.nextInt(max - min + 1)) * 60 * 20 + r.nextInt(1200);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        RecentChat.clear();
    }

    public static void onRemoved(FogLurkerEntity lurker) {
        if (ACTIVE.remove(lurker) && ACTIVE.isEmpty() && lurker.getServer() != null) {
            FogLurkerSavedData data = FogLurkerSavedData.get(lurker.getServer());
            data.setNextSpawnTicks(randomInterval(lurker.getRandom()));
        }
    }

    public static List<FogLurkerEntity> active() {
        ACTIVE.removeIf(Entity::isRemoved);
        return new ArrayList<>(ACTIVE);
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

    private static boolean trySpawnNaturally(MinecraftServer server, FogLurkerSavedData data) {
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (eligible(p) && (!FogLurkerConfig.COMMON.overworldOnly.get() || p.level().dimension() == Level.OVERWORLD)) {
                candidates.add(p);
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }
        RandomSource r = server.overworld().getRandom();
        // Prefer whoever is furthest from everyone else: the one who wandered off alone.
        candidates.sort((a, b) -> Double.compare(loneliness(b), loneliness(a)));
        ServerPlayer target = r.nextFloat() < 0.7F ? candidates.get(0) : candidates.get(r.nextInt(candidates.size()));
        return spawn(target, null, null) != null;
    }

    private static double loneliness(ServerPlayer p) {
        double nearest = 512;
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
            if (o != p && !o.isSpectator() && o.level() == p.level()) {
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
        FogLurkerSavedData data = FogLurkerSavedData.get(target.server);
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
        Encounter enc = plan(target, FogLurkerSavedData.get(target.server), disguise, attack, r);
        Vec3 spot = pickSpot(level, target, enc, r);
        if (spot == null) {
            spot = SpotFinder.findSpawn(level, target, 12, 24, null, true, false, r);
        }
        return new DryRun(enc, spot);
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
            if (!p.isSpectator() && d < best) {
                best = d;
                nearest = p;
            }
        }
        if (nearest == null) {
            return false;
        }
        Encounter enc = plan(nearest, FogLurkerSavedData.get(level.getServer()), null, null, level.getRandom());
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
        boolean willAttack = forcedAttack != null ? forcedAttack : !peaceful && r.nextDouble() < diff.attackChance();
        PlayerBehaviorProfile targetProfile = data.profile(target.getUUID());

        ServerPlayer friend = pickFriend(target, r);
        boolean dogKnown = targetProfile.dogUuid != null;
        Wolf realDog = dogKnown && level.getEntity(targetProfile.dogUuid) instanceof Wolf w ? w : null;
        boolean dogAvailable = dogKnown && (realDog == null || realDog.distanceTo(target) > 24 || realDog.isInSittingPose());

        List<Option> options = new ArrayList<>();
        if (forced != null) {
            options.add(new Option(modeFor(forced, friend != null), forced, 1));
        } else {
            if (!willAttack && r.nextDouble() < diff.pureWatchChance()) {
                options.add(new Option(LurkerBrain.Mode.WATCHER, Disguise.NONE, 1));
            } else {
                if (friend != null) {
                    options.add(new Option(LurkerBrain.Mode.MIMIC, Disguise.PLAYER, 55));
                } else if (FogLurkerConfig.COMMON.allowDoppelganger.get()) {
                    options.add(new Option(LurkerBrain.Mode.DOPPELGANGER, Disguise.PLAYER, 14));
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
                if (level.getBiome(target.blockPosition()).is(BiomeTags.IS_TAIGA)
                        || level.getBiome(target.blockPosition()).is(BiomeTags.IS_FOREST)) {
                    options.add(new Option(LurkerBrain.Mode.HERD, Disguise.WOLF, 8));
                }
                boolean dark = level.isNight() || level.getBrightness(LightLayer.SKY, target.blockPosition()) < 6;
                if (dark) {
                    options.add(new Option(LurkerBrain.Mode.ZOMBIE, Disguise.ZOMBIE, 12));
                }
                options.add(new Option(LurkerBrain.Mode.WATCHER, Disguise.NONE, willAttack ? 0 : 6));
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
            default -> {
                if (pick.disguise() == Disguise.SHEEP) {
                    variant = Sheep.getRandomSheepColor(r).getId();
                }
            }
        }
        return new Encounter(target.getUUID(), pick.mode(), pick.disguise(), copiedId, copiedName, willAttack, diff,
                variant, dogName);
    }

    private static LurkerBrain.Mode modeFor(Disguise d, boolean hasFriend) {
        return switch (d) {
            case PLAYER -> hasFriend ? LurkerBrain.Mode.MIMIC : LurkerBrain.Mode.DOPPELGANGER;
            case DOG -> LurkerBrain.Mode.DOG;
            case ZOMBIE -> LurkerBrain.Mode.ZOMBIE;
            case NONE, CRAWLER -> LurkerBrain.Mode.WATCHER;
            default -> LurkerBrain.Mode.HERD;
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
        for (ServerPlayer p : target.server.getPlayerList().getPlayers()) {
            if (p == target || p instanceof FakePlayer || p.isSpectator()) {
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
            if (d != null && d != Disguise.DOG && d != Disguise.ZOMBIE) {
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
    private static Vec3 pickSpot(ServerLevel level, ServerPlayer target, Encounter enc, RandomSource r) {
        switch (enc.mode()) {
            case MIMIC -> {
                ServerPlayer friend = enc.copiedId() == null ? null : target.server.getPlayerList().getPlayer(enc.copiedId());
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
