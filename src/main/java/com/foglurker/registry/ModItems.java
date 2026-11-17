package com.foglurker.registry;

import com.foglurker.FogLurkerMod;
import com.foglurker.item.FogDropItem;
import com.foglurker.item.ProtectorOfTheFogItem;
import com.foglurker.item.ProtectorRechargeRecipe;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

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

    public static final RegistryObject<RecipeSerializer<ProtectorRechargeRecipe>> PROTECTOR_RECHARGE =
            RECIPES.register("protector_recharge", () -> new SimpleCraftingRecipeSerializer<>(ProtectorRechargeRecipe::new));

    private ModItems() {
    }

    /** Mod bus: show the items in the creative inventory. */
    public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(FOG_OF_THE_DAY);
            event.accept(FOG_OF_THE_NIGHT);
            event.accept(TEETH_OF_THE_LURKER);
            event.accept(FOGGY_STICK);
            event.accept(INGOT_OF_THE_FOG);
        } else if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(PROTECTOR_OF_THE_FOG);
        }
    }
}
