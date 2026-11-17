package com.foglurker.brain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Finds places to appear, places to hide, and "the woods" to lure people into. Never loads chunks. */
public final class SpotFinder {
    private SpotFinder() {
    }

    public record Woods(Vec3 pos, int canopy, String direction) {
    }

    /** Where a player is right now. It hides differently in each. */
    public enum Place {
        /** Out in the open: it uses the woods. */
        SURFACE,
        /** Under a roof near the surface (your base): it stands outside, where a window lets you see it. */
        INDOORS,
        /** Down in a cave or mine: it waits in the darkest corners, just past your torches. */
        UNDERGROUND
    }

    public static Place placeOf(ServerLevel level, Entity e, @Nullable BlockPos home) {
        BlockPos p = e.blockPosition();
        if (!loaded(level, p.getX(), p.getZ())) {
            return Place.SURFACE;
        }
        if (level.dimensionType().hasCeiling()) {
            return Place.UNDERGROUND;
        }
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
        if (p.getY() >= surface) {
            return Place.SURFACE;
        }
        boolean nearHome = home != null && home.closerThan(p, 32);
        return p.getY() >= surface - (nearHome ? 14 : 6) ? Place.INDOORS : Place.UNDERGROUND;
    }

    /**
     * Base perimeter: a spot out in the open 5-16 blocks from a player who is indoors, preferring one
     * they can see from where they stand (through a window or an open door).
     */
    @Nullable
    public static Vec3 findOutside(ServerLevel level, ServerPlayer target, RandomSource r) {
        Vec3 origin = target.position();
        Vec3 best = null;
        double bestScore = -Double.MAX_VALUE;
        for (int i = 0; i < 48; i++) {
            double angle = r.nextDouble() * Math.PI * 2;
            double radius = 5 + r.nextDouble() * 11;
            int x = Mth.floor(origin.x + Math.cos(angle) * radius);
            int z = Mth.floor(origin.z + Math.sin(angle) * radius);
            if (!loaded(level, x, z)) {
                continue;
            }
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos g = new BlockPos(x, surface, z);
            if (Math.abs(surface - origin.y) > 5 || !isStandable(level, g) || !isDry(level, g)) {
                continue; // must be outside, on open ground at about your level
            }
            Vec3 spot = Vec3.atBottomCenterOf(g);
            boolean visible = canSee(target, spot.add(0, 1.0, 0));
            double score = r.nextDouble() + (visible ? 6 : 0) - radius * 0.05;
            if (score > bestScore) {
                bestScore = score;
                best = spot;
            }
        }
        return best;
    }

    public static boolean loaded(ServerLevel level, int x, int z) {
        return level.hasChunk(x >> 4, z >> 4);
    }

    public static boolean isStandable(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        if (!level.isInWorldBounds(feet)) {
            return false;
        }
        BlockState ground = level.getBlockState(below);
        if (!ground.isFaceSturdy(level, below, Direction.UP) || ground.is(BlockTags.LEAVES)) {
            return false;
        }
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(below).isEmpty()) {
            return false;
        }
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    /**
     * True when there is no water on or right next to the spot (3x3 around the feet and the block
     * below). It never appears in, on or at the edge of water.
     */
    public static boolean isDry(ServerLevel level, BlockPos feet) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.hasChunk((feet.getX() + dx) >> 4, (feet.getZ() + dz) >> 4)) {
                    return false; // at the edge of the loaded world: unknown ground, and never load a chunk to find out
                }
                for (int dy = -1; dy <= 1; dy++) {
                    if (level.getFluidState(feet.offset(dx, dy, dz)).is(FluidTags.WATER)) {
                        return false;
                    }
                }
            }
        }
        return !level.getBlockState(feet.below()).is(Blocks.LILY_PAD) && !level.getBlockState(feet).is(Blocks.LILY_PAD);
    }

    /** Ground under a column near a reference height (surface if the reference is on the surface). */
    @Nullable
    public static BlockPos ground(ServerLevel level, int x, int z, int refY) {
        return ground(level, x, z, refY, false);
    }

    /** @param requireDry reject anything in, on or touching water */
    @Nullable
    public static BlockPos ground(ServerLevel level, int x, int z, int refY, boolean requireDry) {
        if (!loaded(level, x, z)) {
            return null;
        }
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(surface - refY) <= 8) {
            BlockPos p = new BlockPos(x, surface, z);
            if (isStandable(level, p) && (!requireDry || isDry(level, p))) {
                return p;
            }
        }
        for (int dy = 6; dy >= -8; dy--) {
            BlockPos p = new BlockPos(x, refY + dy, z);
            if (isStandable(level, p) && (!requireDry || isDry(level, p))) {
                return p;
            }
        }
        return null;
    }

    /** How much a block hides something lying in it. Never sweet berry bushes (they hurt). */
    private static int coverValue(BlockState s) {
        if (s.is(Blocks.TALL_GRASS) || s.is(Blocks.LARGE_FERN)) {
            return 4;
        }
        if (s.is(BlockTags.LEAVES)) {
            return 3; // bushes, azaleas, low branches
        }
        if (s.is(Blocks.GRASS) || s.is(Blocks.FERN)) {
            return 1;
        }
        return 0;
    }

    /**
     * The best patch of tall grass, ferns or bushes within {@code radius} of {@code around} that is at
     * least {@code minDistFromPlayer} from the player, dry, and somewhere it can lie flat. Null if none
     * is good enough.
     */
    @Nullable
    public static Vec3 findCover(ServerLevel level, BlockPos around, Vec3 player, int radius, double minDistFromPlayer) {
        Vec3 best = null;
        double bestScore = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos p = around.offset(dx, dy, dz);
                    if (!loaded(level, p.getX(), p.getZ())) {
                        continue;
                    }
                    BlockState s = level.getBlockState(p);
                    BlockPos below = p.below();
                    int score = coverValue(s) * 2;
                    if (score == 0 || !s.getCollisionShape(level, p).isEmpty()
                            || !level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                            || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                            || !isDry(level, p)) {
                        continue;
                    }
                    for (int ox = -1; ox <= 1; ox++) {
                        for (int oz = -1; oz <= 1; oz++) {
                            if ((ox != 0 || oz != 0) && level.hasChunk((p.getX() + ox) >> 4, (p.getZ() + oz) >> 4)) {
                                score += coverValue(level.getBlockState(p.offset(ox, 0, oz)));
                                score += coverValue(level.getBlockState(p.offset(ox, 1, oz))) / 2;
                            }
                        }
                    }
                    Vec3 spot = Vec3.atBottomCenterOf(p);
                    double total = score - Math.sqrt(dx * dx + dz * dz) * 0.35;
                    if (spot.distanceTo(player) >= minDistFromPlayer && total > bestScore) {
                        bestScore = total;
                        best = spot;
                    }
                }
            }
        }
        return bestScore >= 6 ? best : null;
    }

    /** True if the player is swimming, in a boat, or standing in water: somewhere the attacking form cannot follow. */
    public static boolean isInOrOnWater(Entity e) {
        if (e.isInWater() || e.getVehicle() != null && e.getVehicle().isInWater()) {
            return true;
        }
        BlockPos p = e.blockPosition();
        return e.level().getFluidState(p).is(FluidTags.WATER)
                || !e.onGround() && e.level().getFluidState(p.below()).is(FluidTags.WATER);
    }

    /** The closest dry ground near {@code toward}, searched in rings: where it paces when you hide in water. */
    @Nullable
    public static Vec3 nearestDryGround(ServerLevel level, Vec3 toward, Vec3 from, int maxRadius) {
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        for (int r = 1; r <= maxRadius; r++) {
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8;
                int x = Mth.floor(toward.x + Math.cos(a) * r);
                int z = Mth.floor(toward.z + Math.sin(a) * r);
                BlockPos g = ground(level, x, z, Mth.floor(toward.y), true);
                if (g == null) {
                    continue;
                }
                Vec3 spot = Vec3.atBottomCenterOf(g);
                double score = spot.distanceToSqr(toward) + 0.25 * spot.distanceToSqr(from);
                if (score < bestScore) {
                    bestScore = score;
                    best = spot;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    public static boolean canSee(ServerPlayer viewer, Vec3 point) {
        Vec3 eye = viewer.getEyePosition();
        if (eye.distanceToSqr(point) > 128 * 128) {
            return false;
        }
        // The ray must not pass through chunks nobody has loaded (looking would load them): treat those as blocked.
        double len = eye.distanceTo(point);
        for (double d = 0; d <= len; d += 0.5) { // fine enough not to skip the corner of a chunk
            Vec3 at = len < 1e-6 ? point : eye.add(point.subtract(eye).scale(d / len));
            if (!viewer.level().hasChunkAt(net.minecraft.core.BlockPos.containing(at))) {
                return false;
            }
        }
        if (!viewer.level().hasChunkAt(net.minecraft.core.BlockPos.containing(point))) {
            return false;
        }
        return viewer.level().clip(new ClipContext(eye, point, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, viewer))
                .getType() == HitResult.Type.MISS;
    }

    /**
     * A spot between minR and maxR blocks from the player.
     *
     * @param preferDir  bias the direction (e.g. towards where the real friend is), may be null
     * @param wantHidden prefer spots the player cannot currently see
     * @param behind     prefer spots behind the player's back
     */
    @Nullable
    public static Vec3 findSpawn(ServerLevel level, ServerPlayer target, double minR, double maxR,
                                 @Nullable Vec3 preferDir, boolean wantHidden, boolean behind, RandomSource r) {
        Vec3 origin = target.position();
        Vec3 look = target.getViewVector(1F);
        Vec3 best = null;
        double bestScore = -Double.MAX_VALUE;
        boolean caverns = placeOf(level, target, null) == Place.UNDERGROUND;
        for (int i = 0; i < 40; i++) {
            double angle;
            if (preferDir != null && i < 24) {
                angle = Math.atan2(preferDir.z, preferDir.x) + (r.nextDouble() - 0.5) * Math.PI * 0.6;
            } else {
                angle = r.nextDouble() * Math.PI * 2;
            }
            double radius = minR + r.nextDouble() * (maxR - minR);
            int x = Mth.floor(origin.x + Math.cos(angle) * radius);
            int z = Mth.floor(origin.z + Math.sin(angle) * radius);
            BlockPos g = ground(level, x, z, Mth.floor(origin.y), true);
            if (g == null) {
                continue;
            }
            double dy = Math.abs(g.getY() - origin.y);
            if (dy > 6) {
                continue; // not up on a ledge or down in a pit it cannot climb out of
            }
            Vec3 spot = Vec3.atBottomCenterOf(g);
            boolean visible = canSee(target, spot.add(0, 1.5, 0));
            double score = r.nextDouble() - dy * 0.6;
            if (caverns) {
                // Caverns: the darker the better, just outside the reach of your torches.
                score += (7 - level.getMaxLocalRawBrightness(g)) * 0.7;
            }
            if (wantHidden) {
                score += visible ? -5 : 5;
            }
            if (behind) {
                Vec3 to = spot.subtract(origin).normalize();
                score += look.x * to.x + look.z * to.z < 0 ? 2 : -2;
            }
            if (score > bestScore) {
                bestScore = score;
                best = spot;
            }
        }
        return best;
    }

    /** How much tree canopy and how many trunks are around a point. */
    public static int canopyScore(ServerLevel level, int cx, int cz) {
        int score = 0;
        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dz = -4; dz <= 4; dz += 2) {
                int x = cx + dx;
                int z = cz + dz;
                if (!loaded(level, x, z)) {
                    return -1;
                }
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (top - ground >= 2) {
                    score++;
                }
                if (level.getBlockState(new BlockPos(x, ground - 1, z)).is(BlockTags.LOGS)) {
                    score++;
                }
            }
        }
        return score;
    }

    /** Picks the most "woods-like" place 28-64 blocks away, preferring directions away from {@code avoid}. */
    public static Woods findWoods(ServerLevel level, Vec3 origin, @Nullable Vec3 avoid, RandomSource r) {
        Vec3 bestPos = null;
        int bestCanopy = 0;
        double bestScore = -Double.MAX_VALUE;
        Vec3 away = avoid == null ? null : origin.subtract(avoid).multiply(1, 0, 1);
        if (away != null && away.lengthSqr() > 1e-4) {
            away = away.normalize();
        } else {
            away = null;
        }
        for (int i = 0; i < 28; i++) {
            double angle = r.nextDouble() * Math.PI * 2;
            double radius = 28 + r.nextInt(4) * 12;
            int x = Mth.floor(origin.x + Math.cos(angle) * radius);
            int z = Mth.floor(origin.z + Math.sin(angle) * radius);
            int canopy = canopyScore(level, x, z);
            if (canopy < 0) {
                continue;
            }
            BlockPos g = ground(level, x, z, Mth.floor(origin.y), true);
            if (g == null) {
                continue;
            }
            double score = canopy * 3 + r.nextDouble();
            if (away != null) {
                score += 4 * (away.x * Math.cos(angle) + away.z * Math.sin(angle));
            }
            if (score > bestScore) {
                bestScore = score;
                bestCanopy = canopy;
                bestPos = Vec3.atBottomCenterOf(g);
            }
        }
        if (bestPos == null) {
            // No dry loaded ground anywhere? Try the nearest dry ground "that way", else stay put.
            Vec3 dir = away != null ? away : new Vec3(r.nextDouble() - 0.5, 0, r.nextDouble() - 0.5).normalize();
            Vec3 dry = nearestDryGround(level, origin.add(dir.scale(32)), origin, 16);
            bestPos = dry != null ? dry : origin;
        }
        return new Woods(bestPos, bestCanopy, compass(origin, bestPos));
    }

    /** "north", "south-east"... as seen from {@code from}. Minecraft north is -Z. */
    public static String compass(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double deg = Math.toDegrees(Math.atan2(dx, -dz));
        if (deg < 0) {
            deg += 360;
        }
        String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return names[(int) Math.round(deg / 45.0) % 8];
    }
}
