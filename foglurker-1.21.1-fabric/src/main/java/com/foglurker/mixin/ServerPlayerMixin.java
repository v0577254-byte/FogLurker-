package com.foglurker.mixin;

import com.foglurker.ModEvents;
import com.foglurker.world.AfkTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    /** A menu was opened (chest, barrel, shulker box...): where it is tells it where you live. */
    @Inject(method = "openMenu", at = @At("RETURN"))
    private void foglurker$menuOpened(MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        if (cir.getReturnValue().isPresent()) {
            ServerPlayer self = (ServerPlayer) (Object) this;
            ModEvents.onMenuOpened(self, self.containerMenu);
        }
    }

    /**
     * Trying to sleep in a bed, before vanilla's own checks (too far, blocked, monsters, daytime) and before it moves
     * the respawn point: where Forge fired PlayerSleepInBedEvent. (Fabric's ALLOW_SLEEPING fires only after those.)
     */
    @Inject(method = "startSleepInBed", at = @At("HEAD"))
    private void foglurker$trySleep(net.minecraft.core.BlockPos pos,
                                    CallbackInfoReturnable<com.mojang.datafixers.util.Either<net.minecraft.world.entity.player.Player.BedSleepingProblem, net.minecraft.util.Unit>> cir) {
        com.foglurker.learning.BehaviorTracker.onSleep((ServerPlayer) (Object) this, pos);
    }

    /** "[AFK]" after the name in the player list. */
    @Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
    private void foglurker$afkTag(CallbackInfoReturnable<Component> cir) {
        Component name = cir.getReturnValue();
        Component tagged = AfkTracker.tabListName((ServerPlayer) (Object) this, name);
        if (tagged != name) {
            cir.setReturnValue(tagged);
        }
    }
}
