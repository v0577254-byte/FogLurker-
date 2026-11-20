package com.foglurker.mixin;

import com.foglurker.ModEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The end of a player's tick on the server: where Forge fired PlayerTickEvent END. */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void foglurker$afterTick(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer sp) {
            ModEvents.onServerPlayerTick(sp);
        }
    }
}
