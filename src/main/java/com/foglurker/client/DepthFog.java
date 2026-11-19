package com.foglurker.client;

import com.foglurker.FogLurkerMod;
import com.foglurker.config.FogLurkerConfig;
import com.foglurker.util.FogCurve;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import javax.annotation.Nullable;

/**
 * The creature's fog when Minecraft's own fog does not run. Some mods (Embeddium/Sodium Extra's fog switch,
 * OptiFine's fog off, Distant Horizons) skip Minecraft's fog step entirely, and with it the creature's fog. Then
 * this draws the same fog itself, over the finished world, from a copy of the depth buffer: real distance fog at
 * every render distance, with the same distances as the normal path ({@link FogCurve}).
 */
@Mod.EventBusSubscriber(modid = FogLurkerMod.MOD_ID, value = Dist.CLIENT)
public final class DepthFog {
    /** Shader packs replace the whole picture: there, the fog veil over the screen does the job. */
    private static final boolean SHADER_PACK_MOD = ModList.get() != null
            && (ModList.get().isLoaded("oculus") || ModList.get().isLoaded("iris"));

    @Nullable
    private static ShaderInstance shader;
    @Nullable
    private static TextureTarget depthCopy;
    private static boolean broken;
    private static int frames;
    private static int framesLastSecond;

    private DepthFog() {
    }

    static void setShader(ShaderInstance s) {
        shader = s;
    }

    /** How many wisps of mist are out (debug and tests). */
    public static int wispCount() {
        return FogWisps.count();
    }

    /** Frames in the last second where this drew the fog (because Minecraft's fog did not run). */
    public static int framesLastSecond() {
        return framesLastSecond;
    }

    static void tickSecond() {
        framesLastSecond = frames;
        frames = 0;
        worldFramesLastSecond = worldFrames;
        worldFrames = 0;
    }

    private static int worldFrames;
    private static int worldFramesLastSecond;

    /** Frames in the last second where Minecraft's own fog really showed the creature's fog. */
    public static int worldFogFramesLastSecond() {
        return worldFramesLastSecond;
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        // Minecraft's fog step ran with the creature's fog... and nothing changed it afterwards. Distant Horizons, for
        // one, lets the step run and then pushes the fog out of sight: then the fog actually in use is far further.
        boolean worldFogDidIt = ClientEffects.takeWorldFogApplied()
                && RenderSystem.getShaderFogEnd() <= ClientEffects.lastFogFar() + 2F;
        if (worldFogDidIt) {
            worldFrames++;
        }
        if (SHADER_PACK_MOD || !FogLurkerConfig.CLIENT.fogEffects.get()) {
            return;
        }
        float f = ClientEffects.fogAt(event.getPartialTick());
        if (event.getCamera().getFluidInCamera() != FogType.NONE) {
            return; // under water or lava Minecraft's own fog is the right one
        }
        if (f >= 0.01F && !worldFogDidIt && !broken && shader != null) {
            try {
                draw(Minecraft.getInstance(), event.getProjectionMatrix(), event.getPoseStack().last().pose(), f);
                frames++;
            } catch (RuntimeException e) {
                broken = true; // never try again this session: the fog veil over the screen still shows it
                FogLurkerMod.LOGGER.warn("Fog Lurker: could not draw its own fog ({}); the fog veil still shows it", e.toString());
            }
        }
        // Then the wisps of mist on top of everything.
        FogWisps.render(event.getCamera(), event.getPoseStack().last().pose(), event.getPartialTick(), f);
    }

    private static void draw(Minecraft mc, Matrix4f proj, Matrix4f view, float f) {
        RenderTarget main = mc.getMainRenderTarget();
        if (depthCopy == null) {
            depthCopy = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        } else if (depthCopy.width != main.width || depthCopy.height != main.height) {
            depthCopy.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        if (main.isStencilEnabled() && !depthCopy.isStencilEnabled()) {
            depthCopy.enableStencil(); // the depth formats must match for the copy
        }
        depthCopy.copyDepthFrom(main);
        main.bindWrite(false);

        // The same distances the normal path uses: Minecraft's own terrain fog, pulled in by the creature's.
        float vanillaFar = Math.max(mc.gameRenderer.getRenderDistance(), 32F);
        float vanillaNear = vanillaFar - Mth.clamp(vanillaFar / 10F, 4F, 64F);
        float far = FogCurve.far(f, vanillaFar);
        float near = FogCurve.near(f, far, vanillaNear);
        float zNear = proj.m32() / (proj.m22() - 1F);
        float zFar = proj.m32() / (proj.m22() + 1F);
        int c = ClientEffects.fogColorRgb();

        ShaderInstance s = shader;
        s.setSampler("DepthSampler", depthCopy.getDepthTextureId());
        s.safeGetUniform("VeilColor").set((c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F);
        s.safeGetUniform("ProjScale").set(1F / proj.m00(), 1F / proj.m11());
        s.safeGetUniform("Planes").set(zNear, zFar);
        s.safeGetUniform("FogRange").set(near, far);
        s.safeGetUniform("Alphas").set(1F, 1F);
        Matrix4f invViewRot = new Matrix4f(view);
        invViewRot.setTranslation(0F, 0F, 0F);
        s.safeGetUniform("InvViewRot").set(invViewRot.invert());

        RenderSystem.setShader(() -> s);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bb.vertex(-1, -1, 0).endVertex();
        bb.vertex(1, -1, 0).endVertex();
        bb.vertex(1, 1, 0).endVertex();
        bb.vertex(-1, 1, 0).endVertex();
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
