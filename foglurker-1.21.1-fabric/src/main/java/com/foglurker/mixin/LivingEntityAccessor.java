package com.foglurker.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The private swim animation values, copied so a stolen player visibly sprint-swims (DisguiseCache). */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("swimAmount")
    float foglurker$swimAmount();

    @Accessor("swimAmount")
    void foglurker$setSwimAmount(float value);

    @Accessor("swimAmountO")
    float foglurker$swimAmountO();

    @Accessor("swimAmountO")
    void foglurker$setSwimAmountO(float value);
}
