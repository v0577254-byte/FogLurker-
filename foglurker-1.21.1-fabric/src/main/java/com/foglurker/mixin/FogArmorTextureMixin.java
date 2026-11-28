package com.foglurker.mixin;

import com.foglurker.client.FogArmorFrames;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(RenderType.class)
public abstract class FogArmorTextureMixin {
   @ModifyVariable(
      method = "armorCutoutNoCull(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private static ResourceLocation foglurker$animatedArmor(ResourceLocation texture) {
      return FogArmorFrames.texture(texture);
   }
}
