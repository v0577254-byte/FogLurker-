package com.foglurker.entity;

import com.foglurker.config.LurkerDifficulty;
import com.foglurker.registry.ModItems;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * What it drops when a player kills it. Rarer on the easy modes; no drop is ever more than 75% likely.
 *
 * <pre>
 *              Fog of the Day/Night   Teeth   Foggy Stick   Ingot of the Fog
 * Very Easy            25%             15%        4%            1%
 * Easy                 40%             25%        7%            2%
 * Medium               60%             40%       10%            3%
 * Hard                 75%             60%       14%            4%
 * Deathwish            75%             75%       18%            6%  (1.5x Hard)
 * </pre>
 * The fog is The Fog of the Day if it died in daylight, The Fog of the Night otherwise.
 * The Foggy Stick is never rarer than the Ingot.
 */
public final class LurkerLoot {
    private LurkerLoot() {
    }

    /** No drop is ever more likely than this. */
    public static final float MAX_CHANCE = 0.75F;

    /** {fog, teeth, foggy stick, ingot} chances for a mode. */
    public static float[] chances(LurkerDifficulty d) {
        return switch (d) {
            case VERY_EASY -> new float[]{0.25F, 0.15F, 0.04F, 0.01F};
            case EASY -> new float[]{0.40F, 0.25F, 0.07F, 0.02F};
            case MEDIUM -> new float[]{0.60F, 0.40F, 0.10F, 0.03F};
            case HARD -> new float[]{0.75F, 0.60F, 0.14F, 0.04F};
            case DEATHWISH -> new float[]{0.75F, 0.75F, 0.18F, 0.06F};
        };
    }

    public static List<ItemStack> roll(LurkerDifficulty d, boolean night, RandomSource r) {
        float[] c = chances(d);
        List<ItemStack> out = new ArrayList<>();
        if (r.nextFloat() < c[0]) {
            int count = d == LurkerDifficulty.DEATHWISH ? 2 + r.nextInt(2) : 1 + r.nextInt(2);
            out.add(new ItemStack(night ? ModItems.FOG_OF_THE_NIGHT.get() : ModItems.FOG_OF_THE_DAY.get(), count));
        }
        if (r.nextFloat() < c[1]) {
            out.add(new ItemStack(ModItems.TEETH_OF_THE_LURKER.get(), 1 + r.nextInt(2)));
        }
        if (r.nextFloat() < c[2]) {
            out.add(new ItemStack(ModItems.FOGGY_STICK.get()));
        }
        if (r.nextFloat() < c[3]) {
            out.add(new ItemStack(ModItems.INGOT_OF_THE_FOG.get()));
        }
        return out;
    }
}
