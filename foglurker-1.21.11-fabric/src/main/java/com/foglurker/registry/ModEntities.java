package com.foglurker.registry;

import com.foglurker.FogLurkerMod;
import com.foglurker.entity.FogLurkerEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    private static final ResourceKey<EntityType<?>> FOG_LURKER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, FogLurkerMod.id("fog_lurker"));

    /** MISC category: never spawned by vanilla natural spawning, only by the director or commands. */
    public static final Reg<EntityType<FogLurkerEntity>> FOG_LURKER = Reg.of(Registry.register(BuiltInRegistries.ENTITY_TYPE,
            FOG_LURKER_KEY, EntityType.Builder.<FogLurkerEntity>of(FogLurkerEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .fireImmune()
                    .build(FOG_LURKER_KEY)));

    private ModEntities() {
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(FOG_LURKER.get(), FogLurkerEntity.createAttributes());
    }
}
