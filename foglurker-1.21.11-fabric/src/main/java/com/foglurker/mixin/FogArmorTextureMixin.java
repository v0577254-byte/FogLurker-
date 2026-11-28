package com.foglurker.mixin;

import com.foglurker.client.FogArmorFrames;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Armour of the Fog as worn: its mist drifts (one of 32 frames, chosen as it is drawn). */
@Mixin(RenderTypes.class)
public abstract class FogArmorTextureMixin {
    @ModifyVariable(method = "armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Identifier foglurker$animatedArmor(Identifier texture) {
        return FogArmorFrames.texture(texture);
    }
}
