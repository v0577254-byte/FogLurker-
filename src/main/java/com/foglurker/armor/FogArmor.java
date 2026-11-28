package com.foglurker.armor;

import com.foglurker.FogLurkerMod;
import com.foglurker.registry.ModEffects;
import com.foglurker.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;

public final class FogArmor {
   private static final List<Supplier<? extends Item>> ITEMS = new ArrayList<>(8);
   private static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
   private static final ResourceLocation FULL_SET_ADVANCEMENT = FogLurkerMod.id("ascension_from_the_fog");

   private FogArmor() {
   }

   public static Supplier<? extends Item> supplier(int index) {
      return ITEMS.get(index);
   }

   public static void register() {
      ArmorMaterial material = new ArmorMaterial() {
         public int getDurabilityForType(Type type) {
            return FogArmor.durability(type);
         }

         public int getDefenseForType(Type type) {
            return ArmorMaterials.NETHERITE.getDefenseForType(type);
         }

         public int getEnchantmentValue() {
            return 0;
         }

         public SoundEvent getEquipSound() {
            return ArmorMaterials.NETHERITE.getEquipSound();
         }

         public Ingredient getRepairIngredient() {
            return Ingredient.of(new ItemLike[]{(ItemLike)ModItems.INGOT_OF_THE_FOG.get()});
         }

         public String getName() {
            return "foglurker:fog";
         }

         public float getToughness() {
            return 3.0F;
         }

         public float getKnockbackResistance() {
            return 0.1F;
         }
      };

      for (boolean diamond : List.of(false, true)) {
         for (Type type : List.of(Type.HELMET, Type.CHESTPLATE, Type.LEGGINGS, Type.BOOTS)) {
            String name = (diamond ? "diamond_" : "") + type.getName() + "_of_the_fog";
            ITEMS.add(ModItems.ITEMS.register(name, () -> new FogArmor.Piece(material, type, diamond)));
         }
      }
   }

   private static int durability(Type type) {
      return switch (type) {
         case HELMET -> 450;
         case CHESTPLATE -> 750;
         case LEGGINGS -> 615;
         case BOOTS -> 530;
         default -> throw new IncompatibleClassChangeError();
      };
   }

   public static boolean isFogArmor(ItemStack stack) {
      return stack.getItem() instanceof FogArmor.Piece;
   }

   private static boolean fits(ItemStack stack, EquipmentSlot slot) {
      return stack.getItem() instanceof FogArmor.Piece piece && piece.slot == slot;
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

   public static int protectedPieces(LivingEntity entity) {
      int count = 0;

      for (EquipmentSlot slot : SLOTS) {
         ItemStack stack = entity.getItemBySlot(slot);
         if (fits(stack, slot) && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.ALL_DAMAGE_PROTECTION, stack) >= 4) {
            count++;
         }
      }

      return count;
   }

   private static FogArmor.Defense defense(LivingEntity entity) {
      int points = 0;
      int count = 0;
      float reduction = 0.0F;

      for (EquipmentSlot slot : SLOTS) {
         if (entity.getItemBySlot(slot).getItem() instanceof FogArmor.Piece piece && piece.slot == slot) {
            points += piece.points;
            count++;
            reduction += piece.points * (piece.diamond ? 0.7F : 0.8F) / 20.0F;
         }
      }

      if (count > 0
         && (entity.getMainHandItem().is((Item)ModItems.PROTECTOR_OF_THE_FOG.get()) || entity.getOffhandItem().is((Item)ModItems.PROTECTOR_OF_THE_FOG.get()))) {
         reduction -= 0.1F * points / 20.0F;
      }

      return new FogArmor.Defense(count, points, reduction);
   }

   public static float damageReductionFraction(LivingEntity entity) {
      return defense(entity).reduction + protectedPieces(entity) * 0.0125F;
   }

   public static int damageReductionPercent(LivingEntity entity) {
      return Math.round(100.0F * damageReductionFraction(entity));
   }

   public static float absorption(LivingEntity entity, DamageSource source, float damage, float vanillaDamage) {
      if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
         return vanillaDamage;
      } else {
         FogArmor.Defense fog = defense(entity);
         if (fog.count == 0) {
            return vanillaDamage;
         } else {
            float reduction = fog.reduction;
            if (!source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS) && !source.is(DamageTypeTags.BYPASSES_EFFECTS)) {
               reduction += 0.0125F * protectedPieces(entity);
            }

            float otherArmor = Math.max(0, entity.getArmorValue() - fog.points);
            float otherToughness = Math.max(0.0F, (float)entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS) - 3 * fog.count);
            float remaining = otherArmor == 0.0F ? damage : CombatRules.getDamageAfterAbsorb(damage, otherArmor, otherToughness);
            return remaining * (1.0F - reduction);
         }
      }
   }

   public static void checkFullSetAdvancement(ServerPlayer player) {
      if (hasFullSet(player)) {
         Advancement advancement = player.server.getAdvancements().getAdvancement(FULL_SET_ADVANCEMENT);
         if (advancement != null && !player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            player.getAdvancements().award(advancement, "full_fog_armor");
         }
      }
   }

   public static void tick(LivingEntity entity) {
      if (entity.level().isClientSide()) {
         mist(entity);
      } else {
         if (entity.hasEffect((MobEffect)ModEffects.MAULED.get()) && hasFullSet(entity)) {
            entity.removeEffect((MobEffect)ModEffects.MAULED.get());
         }

         if (entity instanceof ServerPlayer player) {
            checkFullSetAdvancement(player);
         }
      }
   }

   public static void mist(LivingEntity entity) {
      if (entity.level().isClientSide() && (entity.tickCount + entity.getId()) % 8 == 0 && !entity.isInvisible()) {
         int clouds = (pieceCount(entity) + 1) / 2;

         for (int i = 0; i < clouds; i++) {
            double angle = entity.tickCount * 0.085 + entity.getId() + Math.PI * i;
            double height = (Math.sin(angle * 0.67) + 1.0) * 0.4 + 0.1;
            double sin = Math.sin(angle);
            double cos = Math.cos(angle);
            entity.level()
               .addParticle(
                  ParticleTypes.CLOUD,
                  entity.getX() + cos * 0.46,
                  entity.getY() + entity.getBbHeight() * height,
                  entity.getZ() + sin * 0.46,
                  -sin * 0.02,
                  0.012,
                  cos * 0.02
               );
         }
      }
   }

   private record Defense(int count, int points, float reduction) {
   }

   public static final class Piece extends ArmorItem {
      public final EquipmentSlot slot;
      public final boolean diamond;
      public final int points;

      private Piece(ArmorMaterial material, Type type, boolean diamond) {
         super(material, type, new Properties().fireResistant());
         this.slot = type.getSlot();
         this.diamond = diamond;
         this.points = material.getDefenseForType(type);
      }

      /** Worn, the pieces made from diamond have the light-blue outline their icons have. */
      @Override
      public String getArmorTexture(ItemStack stack, net.minecraft.world.entity.Entity entity, EquipmentSlot slot, String type) {
         return this.diamond && type == null
            ? FogLurkerMod.MOD_ID + ":textures/models/armor/fog_diamond_layer_" + (slot == EquipmentSlot.LEGS ? 2 : 1) + ".png"
            : null;
      }
   }
}
