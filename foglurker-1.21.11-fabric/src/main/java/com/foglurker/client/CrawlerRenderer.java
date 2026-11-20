package com.foglurker.client;

import com.foglurker.FogLurkerMod;
import com.foglurker.entity.FogLurkerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Draws the pale attacking form. Its pure white eyes glow in the dark. */
public class CrawlerRenderer extends LivingEntityRenderer<FogLurkerEntity, CrawlerRenderState, CrawlerModel> {
    public static final Identifier TEXTURE = FogLurkerMod.id("textures/entity/crawler.png");
    public static final Identifier EYES_TEXTURE = FogLurkerMod.id("textures/entity/crawler_eyes.png");
    private static final RenderType EYES = RenderTypes.eyes(EYES_TEXTURE);

    public CrawlerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CrawlerModel(ctx.bakeLayer(CrawlerModel.LAYER)), 0.75F);
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    @Override
    public CrawlerRenderState createRenderState() {
        return new CrawlerRenderState();
    }

    @Override
    public void extractRenderState(FogLurkerEntity lurker, CrawlerRenderState state, float partialTick) {
        super.extractRenderState(lurker, state, partialTick);
        state.attackTime = lurker.getAttackAnim(partialTick);
        state.aggressive = lurker.isAggressive();
        state.seed = lurker.getId();
        state.dancing = lurker.isDancing();
        state.danceTime = state.dancing ? lurker.danceTime(partialTick) : 0F;
        state.lying = lurker.isLying();
        state.transforming = lurker.isTransforming();
    }

    @Override
    public Identifier getTextureLocation(CrawlerRenderState state) {
        return TEXTURE;
    }

    @Override
    protected void scale(CrawlerRenderState state, PoseStack pose) {
        pose.scale(1.05F, 1.05F, 1.05F);
    }

    @Override
    protected boolean isShaking(CrawlerRenderState state) {
        return state.transforming;
    }

    @Override
    protected boolean shouldShowName(FogLurkerEntity entity, double distanceSq) {
        return false;
    }
}
