package com.foglurker.registry;

import com.foglurker.FogLurkerMod;
import com.foglurker.effect.MauledEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, FogLurkerMod.MOD_ID);

    /** Its teeth went into your leg: no jumping, no getting into a boat. (Hard and Deathwish bites.) */
    public static final RegistryObject<MobEffect> MAULED = EFFECTS.register("mauled", MauledEffect::new);

    private ModEffects() {
    }
}
