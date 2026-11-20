package com.foglurker.entity;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.Locale;

/**
 * How much of its bite your armour stops. Its bites get past ordinary armour maths, so this is the only
 * protection that counts against it. A full set gives:
 * <pre>
 * leather, chainmail   no protection
 * copper               10%   (copper armour from mods or newer versions)
 * iron (and turtle)    25%
 * gold                 30%
 * diamond              45%
 * netherite            65%
 * </pre>
 * A mixed set gives the average of its four pieces. Protection IV adds 5% for a full set (1.25% a piece),
 * but only on diamond and netherite: full Protection IV diamond is 50%, netherite 70% (the most). Lower
 * Protection levels, and Protection on any other armour, do nothing against it.
 */
public final class LurkerArmor {
    public static final float MAX_REDUCTION = 0.70F;
    /** Protection IV on a diamond or netherite piece (a full set adds 5%). */
    private static final float PROTECTION_IV_BONUS = 0.05F;
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private LurkerArmor() {
    }

    /** What a full set of this piece's material stops (0 to 0.65). */
    public static float materialReduction(ItemStack stack) {
        if (!(stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor)) {
            return 0F; // nothing, or a pumpkin
        }
        // The armour material's name (minecraft:diamond, minecraft:netherite...).
        String name = armor.getMaterial().unwrapKey().map(k -> k.location().getPath()).orElse("").toLowerCase(Locale.ROOT);
        if (name.contains("netherite")) {
            return 0.65F;
        }
        if (name.contains("diamond")) {
            return 0.45F;
        }
        if (name.contains("gold")) {
            return 0.30F;
        }
        if (name.contains("iron") || name.contains("turtle")) {
            return 0.25F;
        }
        if (name.contains("copper")) {
            return 0.10F;
        }
        return 0F; // leather, chainmail and anything unknown
    }

    /** The share of a bite that this entity's armour stops, 0 to 0.70. */
    public static float reduction(LivingEntity e) {
        float total = 0F;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack piece = e.getItemBySlot(slot);
            float r = materialReduction(piece);
            if (r >= 0.45F && EnchantmentHelper.getItemEnchantmentLevel(e.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), piece) >= 4) {
                r += PROTECTION_IV_BONUS; // diamond (45%) and netherite (65%) only
            }
            total += r / 4F;
        }
        return Math.min(MAX_REDUCTION, total);
    }
}
