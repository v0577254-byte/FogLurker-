package com.foglurker.mixin;

import com.foglurker.armor.FogArmor;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class FogArmorDefenseMixin {
   @Inject(method = "getDamageAfterArmorAbsorb", at = @At("RETURN"), cancellable = true)
   private void foglurker$absorbDamage(DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
      float vanilla = cir.getReturnValueF();
      float adjusted = FogArmor.absorption((LivingEntity) (Object) this, source, damage, vanilla);
      if (adjusted != vanilla) {
         cir.setReturnValue(adjusted);
      }
   }

   @Redirect(
      method = "getDamageAfterMagicAbsorb",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterMagicAbsorb(FF)F")
   )
   private float foglurker$adjustProtection(float damage, float protection, DamageSource source, float incoming) {
      if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
         int pieces = FogArmor.protectedPieces((LivingEntity) (Object) this);
         protection = Math.max(0.0F, protection - 4 * pieces);
      }

      return CombatRules.getDamageAfterMagicAbsorb(damage, protection);
   }

   @Inject(method = "tick", at = @At("HEAD"))
   private void foglurker$tickFogArmor(CallbackInfo ci) {
      FogArmor.tick((LivingEntity) (Object) this);
   }
}
