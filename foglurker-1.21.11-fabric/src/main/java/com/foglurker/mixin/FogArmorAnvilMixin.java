package com.foglurker.mixin;

import com.foglurker.armor.FogArmor;
import com.foglurker.registry.ModItems;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Armour of the Fog is mended with Ingots of the Fog, a quarter each: the fourth one finishes the job (vanilla's
 * quarter rounds down and would leave a sliver). Books cannot go on it: no enchantment lists it as supported.
 */
@Mixin(AnvilMenu.class)
public abstract class FogArmorAnvilMixin {
    @ModifyArg(method = "createResult", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/item/ItemStack;setDamageValue(I)V"), index = 0)
    private int foglurker$finishFourthIngot(int remainingDamage) {
        ItemCombinerMenuAccessor self = (ItemCombinerMenuAccessor) this;
        ItemStack left = self.foglurker$inputSlots().getItem(0);
        return FogArmor.isFogArmor(left)
                && self.foglurker$inputSlots().getItem(1).is(ModItems.INGOT_OF_THE_FOG.get())
                && left.getDamageValue() - remainingDamage == 4 * (left.getMaxDamage() / 4)
                ? 0
                : remainingDamage;
    }
}
