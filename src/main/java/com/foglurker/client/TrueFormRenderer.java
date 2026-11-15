package com.foglurker.client;

import com.foglurker.FogLurkerMod;
import com.foglurker.entity.FogLurkerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** Draws what is underneath: tall, thin, black, with eyes that glow in the dark. */
public class TrueFormRenderer extends LivingEntityRenderer<FogLurkerEntity, LurkerModel> {
    public static final ResourceLocation TEXTURE = FogLurkerMod.id("textures/entity/fog_lurker.png");
    private static final RenderType EYES = RenderType.eyes(FogLurkerMod.id("textures/entity/fog_lurker_eyes.png"));

    public TrueFormRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new LurkerModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5F);
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
        pose.scale(0.92F, 1.28F, 0.92F);
    }

    @Override
    protected boolean isShaking(FogLurkerEntity entity) {
        return entity.isTransforming() || entity.isAggressive() && entity.tickCount % 40 < 3;
    }

    @Override
    protected boolean shouldShowName(FogLurkerEntity entity) {
        return false;
    }
}
