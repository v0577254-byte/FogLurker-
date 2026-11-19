package com.foglurker.client;

import com.foglurker.FogLurkerMod;
import com.foglurker.entity.FogLurkerEntity;
import com.foglurker.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = FogLurkerMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    /** The creature's own distance fog, for when another mod switches Minecraft's fog off (see DepthFog). */
    @SubscribeEvent
    public static void registerShaders(net.minecraftforge.client.event.RegisterShadersEvent event) {
        try {
            event.registerShader(new net.minecraft.client.renderer.ShaderInstance(event.getResourceProvider(),
                    FogLurkerMod.id("depth_fog"), com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION), DepthFog::setShader);
        } catch (Exception e) {
            // A graphics driver that will not compile it: the fog veil over the screen still shows the fog.
            FogLurkerMod.LOGGER.warn("Fog Lurker: its own fog shader could not be loaded ({}); using the fog veil only", e.toString());
        }
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        FogLurkerEntity.clientTicker = DisguiseCache::tick;
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FOG_LURKER.get(), FogLurkerRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CrawlerModel.LAYER, CrawlerModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerBelowAll("fog", ClientEffects::renderFogOverlay);
        event.registerAboveAll("scare", ClientEffects::renderOverlay);
    }
}
