package com.foglurker.item;

import net.minecraft.world.item.crafting.CraftingInput;

import com.foglurker.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Recharging The Protector of the Fog in a crafting table: the sword, and The Fog of the
 * Day or The Fog of the Night anywhere with it. Each fog item gives back one scare burst, so
 * you can put them in one at a time. Nothing goes to waste: it will not take more than it is missing.
 */
public class ProtectorRechargeRecipe extends CustomRecipe {
    public ProtectorRechargeRecipe(CraftingBookCategory category) {
        super(category);
    }

    /**
     * @return how many fog items are with the sword, or 0 if this is not a recharge. (1.21 trims the crafting grid
     * to the filled slots, so where the sword sits can no longer be told: the sword and fog anywhere in the grid.)
     */
    private static int scan(CraftingInput grid) {
        ItemStack sword = ItemStack.EMPTY;
        int fog = 0;
        for (int i = 0; i < grid.size(); i++) {
            ItemStack s = grid.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            if (s.is(ModItems.PROTECTOR_OF_THE_FOG.get())) {
                if (!sword.isEmpty()) {
                    return 0; // two swords
                }
                sword = s;
            } else if (s.is(ModItems.FOG_OF_THE_DAY.get()) || s.is(ModItems.FOG_OF_THE_NIGHT.get())) {
                fog++;
            } else {
                return 0;
            }
        }
        if (sword.isEmpty() || sword.getDamageValue() <= 0) {
            return 0;
        }
        return fog > sword.getDamageValue() ? 0 : fog;
    }

    private static ItemStack sword(CraftingInput grid) {
        for (int i = 0; i < grid.size(); i++) {
            if (grid.getItem(i).is(ModItems.PROTECTOR_OF_THE_FOG.get())) {
                return grid.getItem(i);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean matches(CraftingInput grid, Level level) {
        return scan(grid) > 0;
    }

    @Override
    public ItemStack assemble(CraftingInput grid, HolderLookup.Provider access) {
        int fog = scan(grid);
        if (fog <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack result = sword(grid).copy();
        result.setCount(1);
        result.setDamageValue(Math.max(0, result.getDamageValue() - fog));
        return result;
    }

    @Override
    public RecipeSerializer<ProtectorRechargeRecipe> getSerializer() {
        return ModItems.PROTECTOR_RECHARGE.get();
    }
}
