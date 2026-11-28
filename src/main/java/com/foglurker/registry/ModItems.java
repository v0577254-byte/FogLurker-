package com.foglurker.registry;

import com.foglurker.FogLurkerMod;
import com.foglurker.item.FogDropItem;
import com.foglurker.item.ProtectorOfTheFogItem;
import com.foglurker.item.ProtectorRechargeRecipe;
import com.foglurker.armor.FogArmor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/** What it leaves behind when it dies, and the sword made from it. */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FogLurkerMod.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, FogLurkerMod.MOD_ID);

    public static final RegistryObject<Item> FOG_OF_THE_DAY = ITEMS.register("fog_of_the_day",
            () -> new FogDropItem(new Item.Properties().rarity(Rarity.UNCOMMON), "Pale mist from a day it died. Still cold."));
    public static final RegistryObject<Item> FOG_OF_THE_NIGHT = ITEMS.register("fog_of_the_night",
            () -> new FogDropItem(new Item.Properties().rarity(Rarity.UNCOMMON), "Dark mist from a night it died. It hums."));
    public static final RegistryObject<Item> TEETH_OF_THE_LURKER = ITEMS.register("teeth_of_the_lurker",
            () -> new FogDropItem(new Item.Properties(), "Too many of them, and too long."));
    public static final RegistryObject<Item> FOGGY_STICK = ITEMS.register("foggy_stick",
            () -> new FogDropItem(new Item.Properties().rarity(Rarity.UNCOMMON), "The fog clings to it."));
    public static final RegistryObject<Item> INGOT_OF_THE_FOG = ITEMS.register("ingot_of_the_fog",
            () -> new FogDropItem(new Item.Properties().rarity(Rarity.EPIC), "Fog, pressed solid. Very rare."));
    public static final RegistryObject<Item> PROTECTOR_OF_THE_FOG = ITEMS.register("protector_of_the_fog",
            () -> new ProtectorOfTheFogItem(new Item.Properties()));
    /** Upgrades a piece of Armour of the Fog to its diamond version at a smithing table. */
    public static final RegistryObject<Item> ASCENSION_FROM_THE_FOG_SMITHING_TEMPLATE = ITEMS.register(
            "ascension_from_the_fog_smithing_template", ModItems::ascensionTemplate);

    static {
        FogArmor.register(); // the eight pieces of Armour of the Fog
    }

    public static final RegistryObject<RecipeSerializer<ProtectorRechargeRecipe>> PROTECTOR_RECHARGE =
            RECIPES.register("protector_recharge", () -> new SimpleCraftingRecipeSerializer<>(ProtectorRechargeRecipe::new));

    private ModItems() {
    }

    private static SmithingTemplateItem ascensionTemplate() {
        String prefix = "item.foglurker.smithing_template.ascension_from_the_fog.";
        return new SmithingTemplateItem(
                Component.translatable(prefix + "applies_to").withStyle(ChatFormatting.BLUE),
                Component.translatable(prefix + "ingredients").withStyle(ChatFormatting.BLUE),
                Component.translatable("upgrade.foglurker.ascension_from_the_fog").withStyle(ChatFormatting.GRAY),
                Component.translatable(prefix + "base_slot_description"),
                Component.translatable(prefix + "additions_slot_description"),
                List.of(new ResourceLocation("item/empty_armor_slot_helmet"), new ResourceLocation("item/empty_armor_slot_chestplate"),
                        new ResourceLocation("item/empty_armor_slot_leggings"), new ResourceLocation("item/empty_armor_slot_boots")),
                List.of(new ResourceLocation("item/empty_slot_ingot")));
    }

    /** Mod bus: show the items in the creative inventory. */
    public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(FOG_OF_THE_DAY);
            event.accept(FOG_OF_THE_NIGHT);
            event.accept(TEETH_OF_THE_LURKER);
            event.accept(FOGGY_STICK);
            event.accept(INGOT_OF_THE_FOG);
            event.accept(ASCENSION_FROM_THE_FOG_SMITHING_TEMPLATE);
        } else if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(PROTECTOR_OF_THE_FOG);
            for (int i = 0; i < 8; i++) {
                event.accept(FogArmor.supplier(i).get());
            }
        }
    }
}
