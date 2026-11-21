package com.foglurker.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A grazing sheep copy keeps its head down, like a real one eating grass (Sheep.eatAnimationTick is private). */
@Mixin(net.minecraft.world.entity.animal.sheep.Sheep.class)
public interface SheepAccessor {
    @Accessor("eatAnimationTick")
    int foglurker$eatAnimationTick();

    @Accessor("eatAnimationTick")
    void foglurker$setEatAnimationTick(int ticks);
}
