package com.foglurker.mixin;

import com.foglurker.effect.MauledEffect;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mauled: a riptide launch is a jump in all but name. */
@Mixin(TridentItem.class)
public abstract class TridentItemMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void foglurker$noRiptideCharge(Level level, Player player, InteractionHand hand,
                                           CallbackInfoReturnable<net.minecraft.world.InteractionResultHolder<ItemStack>> cir) {
        if (MauledEffect.blocksRiptide(player, player.getItemInHand(hand))) {
            cir.setReturnValue(net.minecraft.world.InteractionResultHolder.fail(player.getItemInHand(hand)));
        }
    }

    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void foglurker$noRiptideLaunch(ItemStack stack, Level level, LivingEntity user, int timeLeft,
                                           org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (MauledEffect.blocksRiptide(user, stack)) {
            ci.cancel();
        }
    }
}
