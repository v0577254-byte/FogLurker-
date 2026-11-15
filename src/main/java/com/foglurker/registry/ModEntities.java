package com.foglurker.registry;

import com.foglurker.FogLurkerMod;
import com.foglurker.entity.FogLurkerEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, FogLurkerMod.MOD_ID);

    /** MISC category: never spawned by vanilla natural spawning, only by the director or commands. */
    public static final RegistryObject<EntityType<FogLurkerEntity>> FOG_LURKER = ENTITIES.register("fog_lurker",
            () -> EntityType.Builder.<FogLurkerEntity>of(FogLurkerEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .fireImmune()
                    .build("fog_lurker"));

    private ModEntities() {
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(FOG_LURKER.get(), FogLurkerEntity.createAttributes().build());
    }
}
