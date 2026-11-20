package com.foglurker.mixin;

import com.foglurker.client.ClientEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The camera jolt when it reveals itself, added where Minecraft tilts the view when you get hurt. Only the camera:
 * Minecraft tilts the hand there too, and the jolt never shook the hand.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Unique
    private boolean foglurker$inHand;

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void foglurker$handStart(net.minecraft.client.Camera camera, float partialTick, Matrix4f projection, CallbackInfo ci) {
        foglurker$inHand = true;
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void foglurker$handEnd(net.minecraft.client.Camera camera, float partialTick, Matrix4f projection, CallbackInfo ci) {
        foglurker$inHand = false;
    }

    // RETURN, not TAIL: bobHurt returns early whenever you are not just hurt (nearly every frame).
    @Inject(method = "bobHurt", at = @At("RETURN"))
    private void foglurker$shake(PoseStack pose, float partialTick, CallbackInfo ci) {
        if (!foglurker$inHand) {
            ClientEffects.shakeCamera(pose, partialTick);
        }
    }
}
