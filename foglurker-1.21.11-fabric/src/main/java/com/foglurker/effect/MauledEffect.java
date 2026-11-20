package com.foglurker.effect;

import com.foglurker.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

/**
 * "Mauled": its teeth went into your leg. While it lasts you cannot jump and cannot get into a boat
 * (so water stops being an instant escape). Enforced on both sides so it never rubber-bands.
 * (The hooks are called by the mixins in com.foglurker.mixin.)
 */
public class MauledEffect extends MobEffect {
    public MauledEffect() {
        super(MobEffectCategory.HARMFUL, 0x8A0A12);
    }

    public static boolean isMauled(LivingEntity e) {
        return e.hasEffect(ModEffects.MAULED);
    }

    /** Right after a jump started (LivingEntityMixin): the leg gives way. */
    public static void onJump(LivingEntity e) {
        if (isMauled(e)) {
            // No height, and no free sprint-jump boost either (vanilla adds 0.2 forward when you jump while
            // sprinting, which would make holding space a speed exploit).
            Vec3 v = e.getDeltaMovement();
            if (e.isSprinting()) {
                float f = e.getYRot() * Mth.DEG_TO_RAD;
                v = v.add(Mth.sin(f) * 0.2F, 0.0, -Mth.cos(f) * 0.2F);
            }
            e.setDeltaMovement(v.x, 0.0, v.z);
            if (e instanceof ServerPlayer sp) {
                // Say why the jump failed (above the hotbar, so it never fills the chat).
                sp.displayClientMessage(Component.translatable("effect.foglurker.mauled.description")
                        .withStyle(ChatFormatting.DARK_RED), true);
            }
        }
    }

    /**
     * A riptide launch is a jump in all but name: the trident cannot be charged while Mauled, and a charge
     * started before the bite does not launch either (TridentItemMixin).
     */
    public static boolean blocksRiptide(LivingEntity user, ItemStack stack) {
        return isMauled(user) && stack.getItem() instanceof TridentItem
                && EnchantmentHelper.getTridentSpinAttackStrength(stack, user) > 0;
    }

    /**
     * Getting into a boat (EntityMixin). Server side only: the server decides who sits in a boat. Refusing on the
     * client as well could leave the two disagreeing for a moment when the effect runs out.
     *
     * @return true to refuse
     */
    public static boolean refusesMount(Entity rider, Entity vehicle) {
        if (rider instanceof ServerPlayer p && isMauled(p) && vehicle instanceof AbstractBoat) {
            p.displayClientMessage(Component.literal("Your leg will not hold you.").withStyle(ChatFormatting.DARK_RED), true);
            return true;
        }
        return false;
    }
}
