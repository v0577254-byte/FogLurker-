package com.foglurker.mixin;

import com.foglurker.client.ClientEffects;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The creature's fog goes into Minecraft's own fog, right before the distances and colour are sent to the graphics
 * card (after every fog environment and other mod has had its say: a high priority runs it last).
 */
@Mixin(value = FogRenderer.class, priority = 1500)
public abstract class FogRendererMixin {
    @Inject(method = "setupFog", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;getDevice()Lcom/mojang/blaze3d/systems/GpuDevice;"))
    private void foglurker$fog(Camera camera, int renderDistance, DeltaTracker delta, float darken, ClientLevel level,
                               CallbackInfoReturnable<Vector4f> cir, @Local FogData data, @Local Vector4f color) {
        ClientEffects.applyFog(data, color, camera, delta.getGameTimeDeltaPartialTick(false));
    }
}
