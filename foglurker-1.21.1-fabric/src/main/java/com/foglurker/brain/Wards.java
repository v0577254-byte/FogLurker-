package com.foglurker.brain;

import com.foglurker.config.FogLurkerConfig;
import com.foglurker.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import org.jetbrains.annotations.Nullable;

/**
 * Soul fire wards. A soul torch, soul lantern, lit soul campfire or soul fire protects everyone
 * within {@code wardRadius} blocks: the attacking form will not shed its skin near it, will not
 * come inside the circle, and cannot hit anyone standing in it.
 */
public final class Wards {
    private Wards() {
    }

    public static int radius() {
        return FogLurkerConfig.COMMON.wardRadius.get();
    }

    /** The nearest ward block protecting this position, or null. */
    @Nullable
    public static BlockPos wardNear(ServerLevel level, BlockPos center) {
        int r = radius();
        if (r <= 0) {
            return null;
        }
        BlockPos best = null;
        int bestD = Integer.MAX_VALUE;
        int rr = r * r;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        // Only the ball, not the whole cube around it (about half the lookups).
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int flat = dx * dx + dz * dz;
                if (flat > rr) {
                    continue;
                }
                p.set(center.getX() + dx, center.getY(), center.getZ() + dz);
                if (!level.isLoaded(p)) {
                    continue; // never load or generate chunks just to look for torches
                }
                for (int dy = -r; dy <= r; dy++) {
                    int d = flat + dy * dy;
                    if (d > rr || d >= bestD) {
                        continue;
                    }
                    p.setY(center.getY() + dy);
                    BlockState s = level.getBlockState(p);
                    if (!s.is(ModTags.WARDS)) {
                        continue;
                    }
                    if (s.hasProperty(BlockStateProperties.LIT) && !s.getValue(BlockStateProperties.LIT)) {
                        continue; // a soul campfire that has gone out protects nobody
                    }
                    bestD = d;
                    best = p.immutable();
                }
            }
        }
        return best;
    }

    public static boolean isWarded(Entity e) {
        return e.level() instanceof ServerLevel level && wardNear(level, e.blockPosition()) != null;
    }
}
