package com.foglurker.mixin;

import com.foglurker.client.ClientEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The camera jolt when it reveals itself, added where Minecraft tilts the view when you get hurt. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "bobHurt", at = @At("TAIL"))
    private void foglurker$shake(PoseStack pose, float partialTick, CallbackInfo ci) {
        ClientEffects.shakeCamera(pose, partialTick);
    }
}
