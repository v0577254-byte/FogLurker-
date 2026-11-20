package com.foglurker.mixin;

import com.foglurker.world.PlayerEvents;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A bell rung by an arrow or trident drives it away too. */
@Mixin(Projectile.class)
public abstract class ProjectileMixin {
    @Inject(method = "onHitBlock", at = @At("HEAD"))
    private void foglurker$hitBlock(BlockHitResult hit, CallbackInfo ci) {
        PlayerEvents.onProjectileHitBlock((Projectile) (Object) this, hit);
    }
}
