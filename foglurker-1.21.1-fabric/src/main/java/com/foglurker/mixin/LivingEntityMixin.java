package com.foglurker.mixin;

import com.foglurker.effect.MauledEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mauled: no jumping (where Forge fired LivingJumpEvent). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "jumpFromGround", at = @At("TAIL"))
    private void foglurker$afterJump(CallbackInfo ci) {
        MauledEffect.onJump((LivingEntity) (Object) this);
    }
}
