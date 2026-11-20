package com.foglurker.mixin;

import com.foglurker.client.ClientEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The creature's fog goes into Minecraft's own fog, once Minecraft (and every other mod: a high priority runs this last)
 * has worked out its distances and colour for this frame. Sodium and Distant Horizons read these same values.
 */
@Mixin(value = FogRenderer.class, priority = 1500)
public abstract class FogRendererMixin {
    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void foglurker$color(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darken,
                                        CallbackInfo ci) {
        Vector4f c = new Vector4f(fogRed, fogGreen, fogBlue, 1F);
        ClientEffects.applyFogColor(c, partialTick);
        fogRed = c.x;
        fogGreen = c.y;
        fogBlue = c.z;
        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
    }

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void foglurker$fog(Camera camera, FogRenderer.FogMode mode, float farPlane, boolean thickFog, float partialTick,
                                      CallbackInfo ci) {
        ClientEffects.applyFog(camera, mode == FogRenderer.FogMode.FOG_TERRAIN, farPlane, partialTick);
    }
}
