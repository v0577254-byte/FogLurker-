package com.foglurker.forms.client;

import com.foglurker.forms.FogLurker;
import com.foglurker.forms.FormsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Draws the pale crawler. Its pure white eyes glow in the dark. */
public class CrawlerRenderer extends MobRenderer<FogLurker, CrawlerRenderState, CrawlerModel> {
    public static final Identifier TEXTURE = FormsMod.id("textures/entity/crawler.png");
    private static final RenderType EYES = RenderTypes.eyes(FormsMod.id("textures/entity/crawler_eyes.png"));

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
    public void extractRenderState(FogLurker crawler, CrawlerRenderState state, float partialTick) {
        super.extractRenderState(crawler, state, partialTick);
        state.attackTime = crawler.getAttackAnim(partialTick);
        state.aggressive = crawler.isAggressive();
        state.seed = crawler.getId();
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
    protected boolean shouldShowName(FogLurker crawler, double distanceSq) {
        return false;
    }
}
