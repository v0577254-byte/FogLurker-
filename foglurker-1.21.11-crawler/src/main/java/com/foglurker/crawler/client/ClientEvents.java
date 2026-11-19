package com.foglurker.crawler.client;

import com.foglurker.crawler.CrawlerMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CrawlerMod.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CrawlerModel.LAYER, CrawlerModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CrawlerMod.CRAWLER.get(), CrawlerRenderer::new);
        if (Boolean.getBoolean("foglurker.showcase")) {
            try { // dev only: the class is not in the jar
                Class.forName("com.foglurker.crawler.dev.CrawlerShowcase").getMethod("init").invoke(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("showcase needs the dev classes", e);
            }
        }
    }
}
