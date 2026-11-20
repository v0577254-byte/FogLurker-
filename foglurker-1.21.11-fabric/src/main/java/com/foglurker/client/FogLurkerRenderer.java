package com.foglurker.client;

import com.foglurker.entity.Disguise;
import com.foglurker.entity.FogLurkerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Draws the creature as whatever it is pretending to be by handing a synced puppet entity to the
 * real vanilla renderer, so a disguised cow is pixel-for-pixel a cow and a stolen friend has their
 * real skin and name tag. While it transforms, it flickers between the disguise and its true form.
 */
public class FogLurkerRenderer extends EntityRenderer<FogLurkerEntity, FogLurkerRenderer.State> {
    private final CrawlerRenderer crawler;

    /** Everything needed to draw one frame: its true form, and the puppet's own state when it wears a disguise. */
    public static class State extends EntityRenderState {
        @Nullable CrawlerRenderState crawler;
        @Nullable EntityRenderState disguise;
        boolean transforming;
        float jitterX;
        float jitterZ;
        float flop;
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
    public void extractRenderState(FogLurkerEntity lurker, State state, float partialTick) {
        this.shadowRadius = Math.max(0.3F, lurker.getBbWidth() * 0.7F);
        super.extractRenderState(lurker, state, partialTick);
        state.transforming = lurker.isTransforming();
        Disguise disguise = lurker.getDisguise();
        boolean showTrue = disguise == Disguise.CRAWLER;
        if (state.transforming) {
            int t = lurker.tickCount;
            showTrue = ((t * 7 + (t >> 2)) % 5) < 2;
            float j = 0.06F;
            state.jitterX = (lurker.getRandom().nextFloat() - 0.5F) * j;
            state.jitterZ = (lurker.getRandom().nextFloat() - 0.5F) * j;
        } else {
            state.jitterX = 0F;
            state.jitterZ = 0F;
        }
        state.flop = lurker.flopProgress(partialTick);
        state.disguise = null;
        if (!showTrue) {
            LivingEntity dummy = DisguiseCache.get(lurker);
            if (dummy != null) {
                EntityRenderState puppet = this.entityRenderDispatcher.extractEntity(dummy, partialTick);
                // Named dogs show their name tag when you look at them, like the real one.
                if (disguise == Disguise.DOG && lurker.hasCustomName() && lurker.getCustomName() != null
                        && this.entityRenderDispatcher.crosshairPickEntity == lurker) {
                    puppet.nameTag = lurker.getCustomName();
                    puppet.nameTagAttachment = lurker.getAttachments().getNullable(
                            net.minecraft.world.entity.EntityAttachment.NAME_TAG, 0, lurker.getYRot(partialTick));
                } // (a copied player keeps their real name tag, drawn by the player renderer itself)
                state.disguise = puppet;
            } else {
                showTrue = true;
            }
        }
        // While it transforms it flickers between the stolen shape and the pale thing underneath.
        state.crawler = showTrue ? crawler.createRenderState(lurker, partialTick) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        if (state.transforming) {
            pose.translate(state.jitterX, 0, state.jitterZ);
        }
        if (state.flop > 0F) {
            // Flops flat like a pancake: squashed onto the ground and spread out.
            float k = state.flop * state.flop * (3 - 2 * state.flop);
            pose.scale(1F + 0.35F * k, 1F - 0.88F * k, 1F + 0.35F * k);
        }
        if (state.disguise != null) {
            EntityRenderer<?, EntityRenderState> renderer =
                    (EntityRenderer<?, EntityRenderState>) this.entityRenderDispatcher.getRenderer(state.disguise);
            renderer.submit(state.disguise, pose, collector, camera);
        }
        if (state.crawler != null) {
            crawler.submit(state.crawler, pose, collector, camera);
        }
        pose.popPose();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Vec3 getRenderOffset(State state) {
        if (state.disguise instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState && state.crawler == null) {
            // (a crouching copy of a player sits a little lower, like the real one)
            EntityRenderer<?, EntityRenderState> renderer =
                    (EntityRenderer<?, EntityRenderState>) this.entityRenderDispatcher.getRenderer(state.disguise);
            return renderer.getRenderOffset(state.disguise);
        }
        return Vec3.ZERO;
    }

    @Override
    protected boolean shouldShowName(FogLurkerEntity entity, double distanceSq) {
        return false;
    }
}
