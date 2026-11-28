package com.foglurker.registry;

import com.foglurker.FogLurkerMod;
import com.foglurker.item.FogDropItem;
import com.foglurker.item.ProtectorOfTheFogItem;
import com.foglurker.item.ProtectorRechargeRecipe;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.function.Function;

/** What it leaves behind when it dies, and the sword made from it. */
public final class ModItems {
    public static final Reg<Item> FOG_OF_THE_DAY = item("fog_of_the_day",
            p -> new FogDropItem(p.rarity(Rarity.UNCOMMON), "Pale mist from a day it died. Still cold."));
    public static final Reg<Item> FOG_OF_THE_NIGHT = item("fog_of_the_night",
            p -> new FogDropItem(p.rarity(Rarity.UNCOMMON), "Dark mist from a night it died. It hums."));
    public static final Reg<Item> TEETH_OF_THE_LURKER = item("teeth_of_the_lurker",
            p -> new FogDropItem(p, "Too many of them, and too long."));
    public static final Reg<Item> FOGGY_STICK = item("foggy_stick",
            p -> new FogDropItem(p.rarity(Rarity.UNCOMMON), "The fog clings to it."));
    public static final Reg<Item> INGOT_OF_THE_FOG = item("ingot_of_the_fog",
            p -> new FogDropItem(p.rarity(Rarity.EPIC), "Fog, pressed solid. Very rare."));
    public static final Reg<Item> PROTECTOR_OF_THE_FOG = item("protector_of_the_fog", ProtectorOfTheFogItem::new);
    /** Turns netherite (or diamond) armour into Armour of the Fog at a smithing table. */
    public static final Reg<Item> ASCENSION_FROM_THE_FOG_SMITHING_TEMPLATE = item("ascension_from_the_fog_smithing_template",
            p -> ascensionTemplate());

    static {
        com.foglurker.armor.FogArmor.register(); // the eight pieces of Armour of the Fog
    }

    public static final Reg<RecipeSerializer<ProtectorRechargeRecipe>> PROTECTOR_RECHARGE = Reg.of(Registry.register(
            BuiltInRegistries.RECIPE_SERIALIZER, FogLurkerMod.id("protector_recharge"),
            new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(ProtectorRechargeRecipe::new)));

    private ModItems() {
    }

    private static Reg<Item> item(String name, Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, FogLurkerMod.id(name));
        return Reg.of(Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties())));
    }

    private static net.minecraft.world.item.SmithingTemplateItem ascensionTemplate() {
        String prefix = "item.foglurker.smithing_template.ascension_from_the_fog.";
        return new net.minecraft.world.item.SmithingTemplateItem(
                net.minecraft.network.chat.Component.translatable(prefix + "applies_to").withStyle(net.minecraft.ChatFormatting.BLUE),
                net.minecraft.network.chat.Component.translatable(prefix + "ingredients").withStyle(net.minecraft.ChatFormatting.BLUE),
                net.minecraft.network.chat.Component.translatable("upgrade.foglurker.ascension_from_the_fog").withStyle(net.minecraft.ChatFormatting.GRAY),
                net.minecraft.network.chat.Component.translatable(prefix + "base_slot_description"),
                net.minecraft.network.chat.Component.translatable(prefix + "additions_slot_description"),
                java.util.List.of(net.minecraft.resources.ResourceLocation.withDefaultNamespace("item/empty_armor_slot_helmet"),
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("item/empty_armor_slot_chestplate"),
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("item/empty_armor_slot_leggings"),
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("item/empty_armor_slot_boots")),
                java.util.List.of(net.minecraft.resources.ResourceLocation.withDefaultNamespace("item/empty_slot_ingot")));
    }

    /** Shows the items in the creative inventory. */
    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(e -> {
            e.accept(FOG_OF_THE_DAY.get());
            e.accept(FOG_OF_THE_NIGHT.get());
            e.accept(TEETH_OF_THE_LURKER.get());
            e.accept(FOGGY_STICK.get());
            e.accept(INGOT_OF_THE_FOG.get());
            e.accept(ASCENSION_FROM_THE_FOG_SMITHING_TEMPLATE.get());
        });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(e -> {
            e.accept(PROTECTOR_OF_THE_FOG.get());
            for (int i = 0; i < 8; i++) {
                e.accept(com.foglurker.armor.FogArmor.supplier(i).get());
            }
        });
    }
}
