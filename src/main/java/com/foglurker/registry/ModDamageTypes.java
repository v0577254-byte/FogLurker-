package com.foglurker.registry;

import com.foglurker.FogLurkerMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/** Damage types are data driven (see data/foglurker/damage_type); these are just the keys. */
public final class ModDamageTypes {
    /** Normal hits: bypass armour so "5 hearts" really means 5 hearts. */
    public static final ResourceKey<DamageType> LURKER =
            ResourceKey.create(Registries.DAMAGE_TYPE, FogLurkerMod.id("lurker"));
    /** Deathwish hits: bypass armour, enchantments, effects, resistance and shields. Totems still work. */
    public static final ResourceKey<DamageType> LURKER_ONESHOT =
            ResourceKey.create(Registries.DAMAGE_TYPE, FogLurkerMod.id("lurker_oneshot"));

    private ModDamageTypes() {
    }

    public static DamageSource source(Entity attacker, ResourceKey<DamageType> key) {
        return new DamageSource(attacker.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key), attacker, attacker);
    }
}
