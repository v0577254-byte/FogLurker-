package com.foglurker.mixin;

import com.foglurker.armor.FogArmor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class FogArmorEnchantMixin {
   @Inject(method = "isEnchantable", at = @At("HEAD"), cancellable = true)
   private void foglurker$lockFogArmor(CallbackInfoReturnable<Boolean> cir) {
      if (FogArmor.isFogArmor((ItemStack) (Object) this)) {
         cir.setReturnValue(false);
      }
   }
}
