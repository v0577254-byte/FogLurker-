package com.foglurker.registry;

import com.foglurker.FogLurkerMod;
import com.foglurker.effect.MauledEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;

public final class ModEffects {
    /** Its teeth went into your leg: no jumping, no getting into a boat. (Hard and Deathwish bites.) */
    public static final Holder<MobEffect> MAULED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            FogLurkerMod.id("mauled"), new MauledEffect());

    private ModEffects() {
    }

    /** Loads the class (and so registers the effect). */
    public static void register() {
    }
}
