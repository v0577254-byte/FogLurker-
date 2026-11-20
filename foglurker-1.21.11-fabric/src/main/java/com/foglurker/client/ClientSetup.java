package com.foglurker.client;

import com.foglurker.FogLurkerMod;
import com.foglurker.config.FogLurkerConfig;
import com.foglurker.entity.FogLurkerEntity;
import com.foglurker.network.AlwaysFogPacket;
import com.foglurker.network.ApparitionPacket;
import com.foglurker.network.DebugPacket;
import com.foglurker.network.FogPacket;
import com.foglurker.network.ScarePacket;
import com.foglurker.network.SleepVisionPacket;
import com.foglurker.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** The client side: renderers, the HUD layers, the per-tick effects and what the server sends. */
public final class ClientSetup implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FogLurkerConfig.CLIENT_SPEC.load(FabricLoader.getInstance().getConfigDir().resolve("foglurker-client.toml"));
        FogLurkerEntity.clientTicker = DisguiseCache::tick;

        EntityModelLayerRegistry.registerModelLayer(CrawlerModel.LAYER, CrawlerModel::createBodyLayer);
        EntityRendererRegistry.register(ModEntities.FOG_LURKER.get(), FogLurkerRenderer::new);

        // Under everything (so "RUN!" and the chat stay readable): the fog veil and the dark edges.
        HudElementRegistry.addFirst(FogLurkerMod.id("fog"), ClientEffects::renderFogOverlay);
        // Over the darkening sleep screen: the faint picture after sleep paralysis.
        HudElementRegistry.attachElementAfter(VanillaHudElements.SLEEP, FogLurkerMod.id("sleep_paralysis"), SleepVision::render);
        // Over everything: the dark red pulse when it reveals itself.
        HudElementRegistry.addLast(FogLurkerMod.id("scare"), ClientEffects::renderOverlay);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            ClientEffects.onClientTick(mc);
            ClientDebug.onClientTick();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> ClientEffects.onLoggingOut());

        WorldRenderEvents.END_MAIN.register(ctx -> {
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            DepthFog.onEndMain(camera, ctx.consumers(), ctx.matrices(), camera.getPartialTickTime());
        });

        // What the server sends (handled on the client thread).
        ClientPlayNetworking.registerGlobalReceiver(FogPacket.TYPE, (p, ctx) -> ctx.client().execute(() -> ClientEffects.setFogTarget(p.intensity())));
        ClientPlayNetworking.registerGlobalReceiver(ScarePacket.TYPE, (p, ctx) -> ctx.client().execute(() -> ClientEffects.scare(p.durationTicks())));
        ClientPlayNetworking.registerGlobalReceiver(ApparitionPacket.TYPE, (p, ctx) -> ctx.client().execute(Apparitions::spawn));
        ClientPlayNetworking.registerGlobalReceiver(AlwaysFogPacket.TYPE, (p, ctx) -> ctx.client().execute(() -> ClientEffects.setAlwaysFog(p.level())));
        ClientPlayNetworking.registerGlobalReceiver(SleepVisionPacket.TYPE, (p, ctx) -> ctx.client().execute(SleepVision::show));
        // TEMPORARY: /foglurker debugeverything
        ClientPlayNetworking.registerGlobalReceiver(DebugPacket.TYPE, (p, ctx) -> ctx.client().execute(ClientDebug::run));

        if (Boolean.getBoolean("foglurker.showcase")) {
            try { // dev only: the class is not in the jar
                Class.forName("com.foglurker.dev.FabricShowcase").getMethod("init").invoke(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("showcase needs the dev classes", e);
            }
        }
    }
}
