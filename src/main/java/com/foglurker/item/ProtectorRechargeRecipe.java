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
 * Recharging The Protector of the Fog: the sword plus equal numbers of The Fog of the Day and The Fog of
 * the Night, anywhere in a crafting grid. Each fog item gives back one scare burst, so 5 of each refill it
 * from empty (in two crafts, because the grid holds 8 fog items at most).
 */
public class ProtectorRechargeRecipe extends CustomRecipe {
    public ProtectorRechargeRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    /** @return {sword slot, day count, night count}, or null if this is not a recharge */
    private static int[] scan(CraftingContainer grid) {
        int sword = -1;
        int day = 0;
        int night = 0;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack s = grid.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            if (s.is(ModItems.PROTECTOR_OF_THE_FOG.get()) && sword < 0) {
                sword = i;
            } else if (s.is(ModItems.FOG_OF_THE_DAY.get())) {
                day++;
            } else if (s.is(ModItems.FOG_OF_THE_NIGHT.get())) {
                night++;
            } else {
                return null;
            }
        }
        if (sword < 0 || day == 0 || day != night) {
            return null;
        }
        ItemStack s = grid.getItem(sword);
        if (s.getDamageValue() <= 0 || day + night > s.getDamageValue()) {
            return null; // already full, or more fog than it can take (nothing goes to waste)
        }
        return new int[]{sword, day, night};
    }

    @Override
    public boolean matches(CraftingContainer grid, Level level) {
        return scan(grid) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
        int[] found = scan(grid);
        if (found == null) {
            return ItemStack.EMPTY;
        }
        ItemStack result = grid.getItem(found[0]).copy();
        result.setCount(1);
        result.setDamageValue(Math.max(0, result.getDamageValue() - found[1] - found[2]));
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModItems.PROTECTOR_RECHARGE.get();
    }
}
