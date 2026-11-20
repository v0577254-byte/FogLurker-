package com.foglurker.mixin;

import com.foglurker.item.ProtectorOfTheFogItem;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Protector of the Fog cannot be merged or given books on an anvil (renaming still works). */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
    @Shadow
    @Final
    private DataSlot cost;

    @Inject(method = "createResult", at = @At("TAIL"))
    private void foglurker$noProtectorMerging(CallbackInfo ci) {
        ItemCombinerMenuAccessor self = (ItemCombinerMenuAccessor) this;
        if (ProtectorOfTheFogItem.blocksAnvil(self.foglurker$inputSlots().getItem(0), self.foglurker$inputSlots().getItem(1))) {
            self.foglurker$resultSlots().setItem(0, ItemStack.EMPTY);
            this.cost.set(0);
        }
    }
}
