package com.foglurker.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SinglePoolElement.class)
public interface FogWellTemplateAccessor {
   @Accessor("template")
   Either<?, ?> foglurker$getTemplate();
}
