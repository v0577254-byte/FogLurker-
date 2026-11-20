package com.foglurker.mixin;

import com.foglurker.effect.MauledEffect;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mauled: no getting into a boat (where Forge fired EntityMountEvent). */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z", at = @At("HEAD"), cancellable = true)
    private void foglurker$mount(Entity vehicle, boolean force, boolean sendEvent, CallbackInfoReturnable<Boolean> cir) {
        if (MauledEffect.refusesMount((Entity) (Object) this, vehicle)) {
            cir.setReturnValue(false);
        }
    }
}
