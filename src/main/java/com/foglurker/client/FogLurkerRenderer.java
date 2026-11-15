package com.foglurker.client;

import com.foglurker.entity.Disguise;
import com.foglurker.entity.FogLurkerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the creature as whatever it is pretending to be by handing a synced puppet entity to the
 * real vanilla renderer, so a disguised cow is pixel-for-pixel a cow and a stolen friend has their
 * real skin and name tag. While it transforms, it flickers between the disguise and its true form.
 */
public class FogLurkerRenderer extends EntityRenderer<FogLurkerEntity> {
    private final TrueFormRenderer watcher;
    private final CrawlerRenderer crawler;

    public FogLurkerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.watcher = new TrueFormRenderer(ctx);
        this.crawler = new CrawlerRenderer(ctx);
        this.shadowRadius = 0.5F;
    }

    @Override
    public void render(FogLurkerEntity lurker, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        boolean transforming = lurker.isTransforming();
        Disguise disguise = lurker.getDisguise();
        boolean showTrue = disguise == Disguise.NONE || disguise == Disguise.CRAWLER;
        if (transforming) {
            int t = lurker.tickCount;
            showTrue = ((t * 7 + (t >> 2)) % 5) < 2;
            pose.pushPose();
            float j = 0.06F;
            pose.translate((lurker.getRandom().nextFloat() - 0.5F) * j, 0, (lurker.getRandom().nextFloat() - 0.5F) * j);
        }
        this.shadowRadius = Math.max(0.3F, lurker.getBbWidth() * 0.7F);
        if (!showTrue) {
            LivingEntity dummy = DisguiseCache.get(lurker);
            if (dummy != null) {
                EntityRenderer<? super LivingEntity> renderer = this.entityRenderDispatcher.getRenderer(dummy);
                renderer.render(dummy, yaw, partialTick, pose, buffers, light);
                // Named dogs show their name tag when you look at them, like the real one.
                if (lurker.getDisguise() == Disguise.DOG && lurker.hasCustomName()
                        && this.entityRenderDispatcher.crosshairPickEntity == lurker && lurker.getCustomName() != null) {
                    this.renderNameTag(lurker, lurker.getCustomName(), pose, buffers, light);
                }
            } else {
                showTrue = true;
            }
        }
        if (showTrue) {
            // While it transforms it flickers between the stolen shape and the crawler underneath.
            if (transforming || disguise == Disguise.CRAWLER) {
                crawler.render(lurker, yaw, partialTick, pose, buffers, light);
            } else {
                watcher.render(lurker, yaw, partialTick, pose, buffers, light);
            }
        }
        if (transforming) {
            pose.popPose();
        }
    }

    @Override
    public Vec3 getRenderOffset(FogLurkerEntity lurker, float partialTick) {
        if (lurker.getDisguise() == Disguise.PLAYER) {
            LivingEntity dummy = DisguiseCache.get(lurker);
            if (dummy != null) {
                return this.entityRenderDispatcher.getRenderer(dummy).getRenderOffset(dummy, partialTick);
            }
        }
        return Vec3.ZERO;
    }

    @Override
    protected boolean shouldShowName(FogLurkerEntity entity) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(FogLurkerEntity entity) {
        return TrueFormRenderer.TEXTURE;
    }
}
