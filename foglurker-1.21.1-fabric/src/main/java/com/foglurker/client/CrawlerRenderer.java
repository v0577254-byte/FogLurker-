package com.foglurker.client;

import com.foglurker.FogLurkerMod;
import com.foglurker.entity.FogLurkerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** Draws the pale attacking form. Its pure white eyes glow in the dark. */
public class CrawlerRenderer extends LivingEntityRenderer<FogLurkerEntity, CrawlerModel> {
    public static final ResourceLocation TEXTURE = FogLurkerMod.id("textures/entity/crawler.png");
    public static final ResourceLocation EYES_TEXTURE = FogLurkerMod.id("textures/entity/crawler_eyes.png");
    private static final RenderType EYES = RenderType.eyes(EYES_TEXTURE);

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
    public ResourceLocation getTextureLocation(FogLurkerEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(FogLurkerEntity entity, PoseStack pose, float partialTick) {
        pose.scale(1.05F, 1.05F, 1.05F);
    }

    @Override
    protected boolean isShaking(FogLurkerEntity entity) {
        return entity.isTransforming();
    }

    @Override
    protected boolean shouldShowName(FogLurkerEntity entity) {
        return false;
    }
}
