package com.foglurker.mixin;

import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.DyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A copied dog wears the real dog's collar colour (DisguiseCache). */
@Mixin(Wolf.class)
public interface WolfInvoker {
    @Invoker("setCollarColor")
    void foglurker$setCollarColor(DyeColor color);
}
