package com.foglurker.forms.client;

import com.foglurker.forms.FormsMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Client side: the crawler's model and the renderer that draws every shape. */
public final class FormsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(CrawlerModel.LAYER, CrawlerModel::createBodyLayer);
        EntityRendererRegistry.register(FormsMod.FOG_LURKER, FogLurkerRenderer::new);
        if (Boolean.getBoolean("foglurker.showcase")) {
            try { // dev only: the class is not in the jar
                Class.forName("com.foglurker.forms.dev.FormsShowcase").getMethod("init").invoke(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("showcase needs the dev classes", e);
            }
        }
    }
}
