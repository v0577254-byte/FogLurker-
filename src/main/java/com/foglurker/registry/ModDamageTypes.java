package com.foglurker.registry;

import com.foglurker.FogLurkerMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/** Damage types are data driven (see data/foglurker/damage_type); these are just the keys. */
public final class ModDamageTypes {
    /** Normal hits: skip vanilla armour and Protection; only its own table (LurkerArmor) reduces them. */
    public static final ResourceKey<DamageType> LURKER =
            ResourceKey.create(Registries.DAMAGE_TYPE, FogLurkerMod.id("lurker"));
    /** Deathwish hits: skip vanilla armour, enchantments, effects, resistance and shields; LurkerArmor still applies. Totems work. */
    public static final ResourceKey<DamageType> LURKER_ONESHOT =
            ResourceKey.create(Registries.DAMAGE_TYPE, FogLurkerMod.id("lurker_oneshot"));

    /** The Protector of the Fog against the Fog Lurker: straight through its armour. */
    public static final ResourceKey<DamageType> PROTECTOR =
            ResourceKey.create(Registries.DAMAGE_TYPE, FogLurkerMod.id("protector"));

    private ModDamageTypes() {
    }

    public static DamageSource source(Entity attacker, ResourceKey<DamageType> key) {
        return new DamageSource(attacker.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key), attacker, attacker);
    }
}
