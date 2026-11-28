package com.foglurker.mixin;

import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Where the ground meets a jigsaw piece (blocks above the bottom of its box). The terrain is shaped to it round the
 * structure; vanilla always says 1, which only fits a piece whose bottom layer is its floor.
 */
@Mixin(PoolElementStructurePiece.class)
public interface PoolPieceGroundAccessor {
    @Mutable
    @Accessor("groundLevelDelta")
    void foglurker$setGroundLevelDelta(int delta);
}
