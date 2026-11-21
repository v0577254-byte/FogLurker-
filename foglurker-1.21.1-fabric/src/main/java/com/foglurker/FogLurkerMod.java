package com.foglurker;

import com.foglurker.config.FogLurkerConfig;
import com.foglurker.network.ModNetwork;
import com.foglurker.registry.ModEffects;
import com.foglurker.registry.ModEntities;
import com.foglurker.registry.ModItems;
import com.foglurker.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Fog Lurker: a horror creature that studies how players move, type and sound,
 * then wears their faces (or their pets) to lure their friends into the woods.
 */
public final class FogLurkerMod implements ModInitializer {
    public static final String MOD_ID = "foglurker";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** The running server (integrated or dedicated), or null between worlds. */
    private static volatile @Nullable MinecraftServer server;

    @Override
    public void onInitialize() {
        FogLurkerConfig.COMMON_SPEC.load(FabricLoader.getInstance().getConfigDir().resolve("foglurker-common.toml"));
        FogLurkerConfig.update(); // an old file still at an old default gets the new one, once
        ModSounds.register();
        ModEffects.register();
        ModEntities.register();
        com.foglurker.world.DryLandStructure.register();
        ModItems.register();
        ModNetwork.register();
        ServerLifecycleEvents.SERVER_STARTING.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
        ModEvents.register();
    }

    public static @Nullable MinecraftServer server() {
        return server;
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
