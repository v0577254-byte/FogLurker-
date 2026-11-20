package com.foglurker.client;

import com.foglurker.config.FogLurkerConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Keeps track of whether the creature's fog really reached the screen, and draws the wisps of mist.
 *
 * <p>On Fabric 1.21.11 the creature's fog goes into Minecraft's own fog step itself (FogRendererMixin), at the last
 * moment before the fog is sent to the graphics card and after other mods have had their say, so Sodium and
 * Distant Horizons keep it. If something still switches the fog off (a shader pack), the fog veil over the screen
 * shows it instead (ClientEffects). The full-screen fog redraw of the Forge 1.20.1 version is not in this beta.</p>
 */
public final class DepthFog {
    /** Shader packs replace the whole picture: there, the fog veil over the screen does the job. */
    static final boolean SHADER_PACK_MOD = FabricLoader.getInstance().isModLoaded("iris")
            || FabricLoader.getInstance().isModLoaded("oculus");

    private static int frames;
    private static int framesLastSecond;
    private static int worldFrames;
    private static int worldFramesLastSecond;

    private DepthFog() {
    }

    /** How many wisps of mist are out (debug and tests). */
    public static int wispCount() {
        return FogWisps.count();
    }

    /** Frames in the last second where this drew the fog itself (always 0 in this beta: see the class comment). */
    public static int framesLastSecond() {
        return framesLastSecond;
    }

    static void tickSecond() {
        framesLastSecond = frames;
        frames = 0;
        worldFramesLastSecond = worldFrames;
        worldFrames = 0;
    }

    /** Frames in the last second where Minecraft's own fog really showed the creature's fog. */
    public static int worldFogFramesLastSecond() {
        return SHADER_PACK_MOD ? 0 : worldFramesLastSecond;
    }

    /** At the end of the world's main pass (ClientSetup). */
    static void onEndMain(Camera camera, MultiBufferSource buffers, PoseStack pose, float partialTick) {
        if (ClientEffects.takeWorldFogApplied()) {
            worldFrames++;
        }
        if (!FogLurkerConfig.CLIENT.fogEffects.get()) {
            return;
        }
        float f = ClientEffects.fogAt(partialTick);
        if (camera.getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE) {
            return; // under water or lava Minecraft's own fog is the right one
        }
        // The wisps of mist, on top of the finished world.
        FogWisps.render(camera, buffers, pose, partialTick, f);
    }
}
