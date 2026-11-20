package com.foglurker.forms.client;

import com.foglurker.forms.FogLurker;
import com.foglurker.forms.Form;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Draws the figure in its shape: the pale crawler with its own renderer, anything else by handing its puppet
 * (see {@link PuppetCache}) to the real vanilla renderer for that mob or player.
 */
public class FogLurkerRenderer extends EntityRenderer<FogLurker, FogLurkerRenderer.State> {
    private final CrawlerRenderer crawler;

    /** One frame: the crawler's own state, or the puppet's. */
    public static class State extends EntityRenderState {
        @Nullable CrawlerRenderState crawler;
        @Nullable EntityRenderState puppet;
    }

    public FogLurkerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.crawler = new CrawlerRenderer(ctx);
        this.shadowRadius = 0.5F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FogLurker lurker, State state, float partialTick) {
        this.shadowRadius = Math.max(0.3F, lurker.getBbWidth() * 0.7F);
        super.extractRenderState(lurker, state, partialTick);
        state.puppet = null;
        state.crawler = null;
        if (lurker.getForm() != Form.CRAWLER) {
            LivingEntity puppet = PuppetCache.get(lurker);
            if (puppet != null) {
                state.puppet = this.entityRenderDispatcher.extractEntity(puppet, partialTick);
                return;
            }
        }
        state.crawler = crawler.createRenderState(lurker, partialTick);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.puppet != null) {
            EntityRenderer<?, EntityRenderState> renderer =
                    (EntityRenderer<?, EntityRenderState>) this.entityRenderDispatcher.getRenderer(state.puppet);
            renderer.submit(state.puppet, pose, collector, camera);
        } else if (state.crawler != null) {
            crawler.submit(state.crawler, pose, collector, camera);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Vec3 getRenderOffset(State state) {
        if (state.puppet instanceof AvatarRenderState) {
            EntityRenderer<?, EntityRenderState> renderer =
                    (EntityRenderer<?, EntityRenderState>) this.entityRenderDispatcher.getRenderer(state.puppet);
            return renderer.getRenderOffset(state.puppet);
        }
        return Vec3.ZERO;
    }

    @Override
    protected boolean shouldShowName(FogLurker entity, double distanceSq) {
        return false;
    }
}
