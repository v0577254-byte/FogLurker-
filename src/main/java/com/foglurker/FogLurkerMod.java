package com.foglurker;

import com.foglurker.config.FogLurkerConfig;
import com.foglurker.network.ModNetwork;
import com.foglurker.registry.ModEntities;
import com.foglurker.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Fog Lurker: a horror creature that studies how players move, type and sound,
 * then wears their faces (or their pets) to lure their friends into the woods.
 */
@Mod(FogLurkerMod.MOD_ID)
@SuppressWarnings("removal") // the old accessors still work on every Forge 47.x build
public final class FogLurkerMod {
    public static final String MOD_ID = "foglurker";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FogLurkerMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITIES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        com.foglurker.registry.ModEffects.EFFECTS.register(modBus);
        modBus.addListener(ModEntities::registerAttributes);
        modBus.addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FogLurkerConfig.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, FogLurkerConfig.CLIENT_SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
