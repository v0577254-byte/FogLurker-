package com.foglurker.brain;

import com.foglurker.config.FogLurkerConfig;
import com.foglurker.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import javax.annotation.Nullable;

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
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            if (!level.isLoaded(p)) {
                continue; // never load or generate chunks just to look for torches
            }
            BlockState s = level.getBlockState(p);
            if (!s.is(ModTags.WARDS)) {
                continue;
            }
            if (s.hasProperty(BlockStateProperties.LIT) && !s.getValue(BlockStateProperties.LIT)) {
                continue; // a soul campfire that has gone out protects nobody
            }
            double d = p.distSqr(center);
            if (d <= (double) r * r && d < bestD) {
                bestD = d;
                best = p.immutable();
            }
        }
        return best;
    }

    public static boolean isWarded(Entity e) {
        return e.level() instanceof ServerLevel level && wardNear(level, e.blockPosition()) != null;
    }
}
