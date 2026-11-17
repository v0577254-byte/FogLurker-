package com.foglurker.item;

import com.foglurker.registry.ModItems;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Recharging The Protector of the Fog in a crafting table: the sword in the middle slot, and The Fog of the
 * Day or The Fog of the Night in any of the slots around it. Each fog item gives back one scare burst, so
 * you can put them in one at a time. Nothing goes to waste: it will not take more than it is missing.
 */
public class ProtectorRechargeRecipe extends CustomRecipe {
    public ProtectorRechargeRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    /** @return how many fog items are around the sword, or 0 if this is not a recharge */
    private static int scan(CraftingContainer grid) {
        if (grid.getWidth() != 3 || grid.getHeight() != 3) {
            return 0; // the sword goes in the middle of a crafting table
        }
        ItemStack sword = grid.getItem(4);
        if (!sword.is(ModItems.PROTECTOR_OF_THE_FOG.get()) || sword.getDamageValue() <= 0) {
            return 0;
        }
        int fog = 0;
        for (int i = 0; i < 9; i++) {
            if (i == 4) {
                continue;
            }
            ItemStack s = grid.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            if (s.is(ModItems.FOG_OF_THE_DAY.get()) || s.is(ModItems.FOG_OF_THE_NIGHT.get())) {
                fog++;
            } else {
                return 0;
            }
        }
        return fog > sword.getDamageValue() ? 0 : fog;
    }

    @Override
    public boolean matches(CraftingContainer grid, Level level) {
        return scan(grid) > 0;
    }

    @Override
    public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
        int fog = scan(grid);
        if (fog <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack result = grid.getItem(4).copy();
        result.setCount(1);
        result.setDamageValue(Math.max(0, result.getDamageValue() - fog));
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModItems.PROTECTOR_RECHARGE.get();
    }
}
