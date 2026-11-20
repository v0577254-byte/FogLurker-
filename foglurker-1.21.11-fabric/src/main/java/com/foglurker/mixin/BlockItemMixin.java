package com.foglurker.mixin;

import com.foglurker.ModEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Placing a block counts as activity (not AFK). */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place", at = @At("RETURN"))
    private void foglurker$placed(BlockPlaceContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().consumesAction() && ctx.getPlayer() != null && !ctx.getLevel().isClientSide()) {
            ModEvents.onPlaced(ctx.getPlayer());
        }
    }
}
