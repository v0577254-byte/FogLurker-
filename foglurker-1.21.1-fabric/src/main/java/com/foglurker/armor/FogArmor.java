package com.foglurker.armor;

import com.foglurker.FogLurkerMod;
import com.foglurker.registry.ModEffects;
import com.foglurker.registry.ModItems;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Armour of the Fog: four pieces made at a smithing table from netherite (or diamond) armour, an Ingot of the Fog and
 * the Ascension From The Fog template. Netherite's armour points, toughness 3, a little knockback resistance, it does
 * not burn, it is mended with Ingots of the Fog and it cannot be enchanted. It takes its own share off the damage
 * (4% a point from netherite, 3.5% from diamond), and a full set keeps the Mauled bite, sleep paralysis and some of
 * the fog away.
 */
public final class FogArmor {
    private static final List<Supplier<? extends Item>> ITEMS = new ArrayList<>(8);
    private static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static final ResourceLocation FULL_SET_ADVANCEMENT = FogLurkerMod.id("ascension_from_the_fog");
    private static Holder<ArmorMaterial> material;

    private FogArmor() {
    }

    /** 0-3 the pieces made from netherite (helmet, chestplate, leggings, boots), 4-7 the ones made from diamond. */
    public static Supplier<? extends Item> supplier(int index) {
        return ITEMS.get(index);
    }

    public static void register() {
        if (material != null) {
            return;
        }
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            defense.put(type, ArmorMaterials.NETHERITE.value().getDefense(type));
        }
        material = Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL, FogLurkerMod.id("fog"),
                new ArmorMaterial(defense, 0, ArmorMaterials.NETHERITE.value().equipSound(),
                        () -> Ingredient.of(ModItems.INGOT_OF_THE_FOG.get()),
                        List.of(new ArmorMaterial.Layer(FogLurkerMod.id("fog"))), 3.0F, 0.1F));
        // The same, worn with the light-blue outline of the pieces made from diamond.
        Holder<ArmorMaterial> diamondMaterial = Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL, FogLurkerMod.id("fog_diamond"),
                new ArmorMaterial(defense, 0, ArmorMaterials.NETHERITE.value().equipSound(),
                        () -> Ingredient.of(ModItems.INGOT_OF_THE_FOG.get()),
                        List.of(new ArmorMaterial.Layer(FogLurkerMod.id("fog_diamond"))), 3.0F, 0.1F));
        for (boolean diamond : List.of(false, true)) {
            for (ArmorItem.Type type : List.of(ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS)) {
                String name = (diamond ? "diamond_" : "") + type.getName() + "_of_the_fog";
                Piece piece = Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, FogLurkerMod.id(name)),
                        new Piece(diamond ? diamondMaterial : material, type, diamond));
                ITEMS.add(() -> piece);
            }
        }
    }

    private static int durability(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 450;
            case CHESTPLATE -> 750;
            case LEGGINGS -> 615;
            case BOOTS -> 530;
            default -> 500;
        };
    }

    public static boolean isFogArmor(ItemStack stack) {
        return stack.getItem() instanceof Piece;
    }

    private static boolean fits(ItemStack stack, EquipmentSlot slot) {
        return stack.getItem() instanceof Piece piece && piece.slot == slot;
    }

    public static int pieceCount(LivingEntity entity) {
        int count = 0;
        for (EquipmentSlot slot : SLOTS) {
            if (fits(entity.getItemBySlot(slot), slot)) {
                count++;
            }
        }
        return count;
    }

    public static boolean hasFullSet(LivingEntity entity) {
        for (EquipmentSlot slot : SLOTS) {
            if (!fits(entity.getItemBySlot(slot), slot)) {
                return false;
            }
        }
        return true;
    }

    /** Pieces worn with Protection IV on them (each takes a little more off). */
    public static int protectedPieces(LivingEntity entity) {
        var protection = entity.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(Enchantments.PROTECTION);
        if (protection.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (fits(stack, slot) && EnchantmentHelper.getItemEnchantmentLevel(protection.get(), stack) >= 4) {
                count++;
            }
        }
        return count;
    }

    private static Defense defense(LivingEntity entity) {
        int points = 0;
        int count = 0;
        float reduction = 0.0F;
        for (EquipmentSlot slot : SLOTS) {
            if (entity.getItemBySlot(slot).getItem() instanceof Piece piece && piece.slot == slot) {
                points += piece.points;
                count++;
                reduction += piece.points * (piece.diamond ? 0.7F : 0.8F) / 20.0F;
            }
        }
        if (count > 0 && (entity.getMainHandItem().is(ModItems.PROTECTOR_OF_THE_FOG.get())
                || entity.getOffhandItem().is(ModItems.PROTECTOR_OF_THE_FOG.get()))) {
            reduction -= 0.1F * points / 20.0F;
        }
        return new Defense(count, points, reduction);
    }

    public static float damageReductionFraction(LivingEntity entity) {
        return defense(entity).reduction + protectedPieces(entity) * 0.0125F;
    }

    public static int damageReductionPercent(LivingEntity entity) {
        return Math.round(100.0F * damageReductionFraction(entity));
    }

    /** What is left of the damage after armour, when Armour of the Fog is worn (else vanilla's own result). */
    public static float absorption(LivingEntity entity, DamageSource source, float damage, float vanillaDamage) {
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return vanillaDamage;
        }
        Defense fog = defense(entity);
        if (fog.count == 0) {
            return vanillaDamage;
        }
        float reduction = fog.reduction;
        if (!source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS) && !source.is(DamageTypeTags.BYPASSES_EFFECTS)) {
            reduction += 0.0125F * protectedPieces(entity);
        }
        float otherArmor = Math.max(0, entity.getArmorValue() - fog.points);
        float otherToughness = Math.max(0.0F, (float) entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS) - 3 * fog.count);
        float remaining = otherArmor == 0.0F ? damage : CombatRules.getDamageAfterAbsorb(entity, damage, source, otherArmor, otherToughness);
        return remaining * (1.0F - reduction);
    }

    public static void checkFullSetAdvancement(ServerPlayer player) {
        if (hasFullSet(player)) {
            AdvancementHolder advancement = player.server.getAdvancements().get(FULL_SET_ADVANCEMENT);
            if (advancement != null && !player.getAdvancements().getOrStartProgress(advancement).isDone()) {
                player.getAdvancements().award(advancement, "full_fog_armor");
            }
        }
    }

    /** Every tick of everyone (LivingEntity mixin): the mist on the client; Mauled shaken off and the advancement on the server. */
    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            mist(entity);
        } else {
            if (entity.hasEffect(ModEffects.MAULED) && hasFullSet(entity)) {
                entity.removeEffect(ModEffects.MAULED);
            }
            if (entity instanceof ServerPlayer player && (player.tickCount & 15) == 0) {
                checkFullSetAdvancement(player);
            }
        }
    }

    /** A faint mist drifting round whoever wears it (more with more pieces). */
    public static void mist(LivingEntity entity) {
        if (entity.level().isClientSide() && (entity.tickCount + entity.getId()) % 8 == 0 && !entity.isInvisible()) {
            int clouds = (pieceCount(entity) + 1) / 2;
            for (int i = 0; i < clouds; i++) {
                double angle = entity.tickCount * 0.085 + entity.getId() + Math.PI * i;
                double height = (Math.sin(angle * 0.67) + 1.0) * 0.4 + 0.1;
                double sin = Math.sin(angle);
                double cos = Math.cos(angle);
                entity.level().addParticle(ParticleTypes.CLOUD, entity.getX() + cos * 0.46, entity.getY() + entity.getBbHeight() * height,
                        entity.getZ() + sin * 0.46, -sin * 0.02, 0.012, cos * 0.02);
            }
        }
    }

    private record Defense(int count, int points, float reduction) {
    }

    public static final class Piece extends ArmorItem {
        public final EquipmentSlot slot;
        public final boolean diamond;
        public final int points;

        private Piece(Holder<ArmorMaterial> material, ArmorItem.Type type, boolean diamond) {
            super(material, type, new Item.Properties().fireResistant().durability(durability(type)));
            this.slot = type.getSlot();
            this.diamond = diamond;
            this.points = material.value().getDefense(type);
        }

        /** It cannot be enchanted (the enchantments of the piece it was made from carry over). */
        @Override
        public boolean isEnchantable(ItemStack stack) {
            return false;
        }
    }
}
