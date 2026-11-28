package com.foglurker.mixin;

import com.foglurker.armor.FogArmor;
import com.foglurker.registry.ModItems;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public abstract class FogArmorAnvilMixin {
   @Shadow
   @Final
   private DataSlot cost;

   @ModifyArg(method = "createResult", at = @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/world/item/ItemStack;setDamageValue(I)V"), index = 0)
   private int foglurker$finishFourthIngot(int remainingDamage) {
      AnvilMenu menu = (AnvilMenu) (Object) this;
      ItemStack left = menu.getSlot(0).getItem();
      return FogArmor.isFogArmor(left)
            && menu.getSlot(1).getItem().is((Item)ModItems.INGOT_OF_THE_FOG.get())
            && left.getDamageValue() - remainingDamage == 4 * (left.getMaxDamage() / 4)
         ? 0
         : remainingDamage;
   }

   @Inject(method = "createResult", at = @At("RETURN"))
   private void foglurker$lockEnchantments(CallbackInfo ci) {
      AnvilMenu menu = (AnvilMenu) (Object) this;
      ItemStack left = menu.getSlot(0).getItem();
      if (FogArmor.isFogArmor(left)) {
         ItemStack right = menu.getSlot(1).getItem();
         if (!right.isEmpty() && !right.is((Item)ModItems.INGOT_OF_THE_FOG.get())) {
            Slot result = menu.getSlot(menu.getResultSlot());
            if (result.hasItem()
               && (right.is(Items.ENCHANTED_BOOK) || !EnchantmentHelper.getEnchantments(right).isEmpty())
               && !EnchantmentHelper.getEnchantments(left).equals(EnchantmentHelper.getEnchantments(result.getItem()))) {
               result.set(ItemStack.EMPTY);
               this.cost.set(0);
               menu.broadcastChanges();
            }
         }
      }
   }
}
