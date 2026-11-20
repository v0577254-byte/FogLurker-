package com.foglurker.client;

import com.foglurker.FogLurkerMod;
import com.foglurker.config.FogLurkerConfig;
import com.foglurker.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;

/** Client-only atmosphere: the fog that rolls in while it hunts you, and the jolt when it reveals itself. */
public final class ClientEffects {
    private static float fogTarget;
    /** The light fog that is always there (Overworld), easing in and out with dimension changes. */
    private static float ambient;
    private static float ambientPrev;
    /** What the server says the fog that is always there is (/foglurker fog), 0..1. */
    private static float alwaysFog = com.foglurker.world.FogLurkerSavedData.DEFAULT_ALWAYS_FOG / 100F;

    public static void setAlwaysFog(float level) {
        alwaysFog = Float.isNaN(level) ? 0F : Mth.clamp(level, 0F, 1F);
    }
    private static float fog;
    private static float fogPrev;
    private static int sinceFogPacket;
    private static int scareTicks;
    private static int scareTotal = 1;
    private static int heartbeatIn;
    private static final Identifier VIGNETTE = Identifier.tryParse("minecraft:textures/misc/vignette.png");

    /** True while a world was loaded last tick, so leaving one always clears what it left behind. */
    private static boolean inWorld;

    private ClientEffects() {
    }

    /** Current fog thickness (0..1) and what it is heading for; for the debug checks. */
    public static float currentFog() {
        return fog;
    }

    public static float fogTargetNow() {
        return fogTarget;
    }

    public static void setFogTarget(float intensity) {
        if (Float.isNaN(intensity)) {
            intensity = 0F; // never let a bad value freeze the fog for the rest of the session
        }
        fogTarget = Mth.clamp(intensity, 0F, 1F);
        sinceFogPacket = 0;
    }

    public static void scare(int ticks) {
        scareTicks = ticks;
        scareTotal = Math.max(1, ticks);
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.HEARTBEAT.get(), 1.0F, 1.0F));
    }

    private static void reset() {
        FogWisps.clear();
        fog = fogPrev = fogTarget = 0;
        ambient = ambientPrev = 0;
        scareTicks = 0;
        DisguiseCache.clear();
        Apparitions.clear();
    }

    /** End of every client tick (ClientSetup). */
    public static void onClientTick(Minecraft mc) {
        if (mc.level == null) {
            if (inWorld || fog != 0 || scareTicks != 0) {
                reset(); // also drops the disguise copies and apparitions of the world just left
                inWorld = false;
            }
            return;
        }
        inWorld = true;
        FogWisps.preload(mc);
        if (mc.isPaused()) {
            return;
        }
        if (mc.level.getGameTime() % 20 == 0) {
            fogEventsLastSecond = fogEvents;
            fogAppliedLastSecond = fogApplied;
            fogEvents = 0;
            fogApplied = 0;
            DepthFog.tickSecond();
        }
        if (++sinceFogPacket > 80) {
            fogTarget = 0; // the server stopped telling us it is near
        }
        ambientPrev = ambient;
        float ambientWanted = FogLurkerConfig.CLIENT.fogEffects.get() && mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD
                ? alwaysFog : 0F;
        ambient += (ambientWanted - ambient) * 0.02F;
        FogWisps.tick(mc, Math.max(fog, ambient));
        fogPrev = fog;
        // Rolls in faster than it lifts: thick within ~2 s when it attacks, a slow clear when it leaves.
        fog += (fogTarget - fog) * (fogTarget > fog ? 0.06F : 0.03F);
        if (Math.abs(fog - fogTarget) < 0.001F) {
            fog = fogTarget;
        }
        if (scareTicks > 0) {
            scareTicks--;
            if (scareTicks > 0 && scareTicks % 16 == 0) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.HEARTBEAT.get(), 1.1F, 0.9F));
            }
        }
        if (mc.level.getGameTime() % 100 == 0) {
            DisguiseCache.cleanup(mc.level);
        }
        Apparitions.tick(mc);
        // A heartbeat that gets faster and louder the closer it gets (the fog level is how close it is).
        if (fog > 0.3F && scareTicks <= 0 && FogLurkerConfig.CLIENT.screenEffects.get()) {
            if (--heartbeatIn <= 0) {
                float closeness = Mth.clamp((fog - 0.3F) / 0.6F, 0F, 1F);
                heartbeatIn = (int) Mth.lerp(closeness, 44F, 13F);
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.HEARTBEAT.get(), 0.9F + closeness * 0.2F,
                        0.25F + closeness * 0.6F));
            }
        } else {
            heartbeatIn = 0;
        }
    }

    /** Leaving a world or server (ClientSetup). */
    public static void onLoggingOut() {
        reset();
    }

    // World-fog bookkeeping, so /foglurker debugeverything can tell whether anything blocks it.
    private static int fogEvents;
    private static int fogEventsLastSecond;
    private static int fogApplied;
    private static int fogAppliedLastSecond;
    private static int fogColor = 0xC0C6CE;

    /** Minecraft asked for the world fog this many times in the last second (0 = something skips Forge's fog). */
    public static int fogEventsLastSecond() {
        return fogEventsLastSecond;
    }

    /** ...and this many of those used the creature's fog. */
    public static int fogAppliedLastSecond() {
        return fogAppliedLastSecond;
    }

    /** Set when Minecraft's fog step applied the creature's fog this frame; read (and reset) by {@link DepthFog}. */
    private static boolean worldFogApplied;

    static boolean takeWorldFogApplied() {
        boolean was = worldFogApplied;
        worldFogApplied = false;
        return was;
    }

    /** The fog to draw in the world: the creature's, or the light fog that is always there, whichever is thicker. */
    static float fogAt(double partialTick) {
        return Math.max(fogNow(partialTick), Mth.lerp((float) partialTick, ambientPrev, ambient));
    }

    static int fogColorRgb() {
        return fogColor;
    }

    private static float fogNow(double partialTick) {
        return Mth.lerp((float) partialTick, fogPrev, fog);
    }

    /**
     * Minecraft's fog for this frame was just worked out (FogRendererMixin): pull it in to the creature's fog, and
     * tint it. {@code data} holds the distances (blocks) and {@code color} the colour; both are changed in place.
     */
    public static void applyFog(net.minecraft.client.renderer.fog.FogData data, org.joml.Vector4f color,
                                net.minecraft.client.Camera camera, float partialTick) {
        fogEvents++;
        applyFogColor(color, partialTick);
        if (!FogLurkerConfig.CLIENT.fogEffects.get() || camera.getFluidInCamera() != FogType.NONE) {
            return; // under water, in lava or powder snow Minecraft's own fog is the right one
        }
        float f = fogAt(partialTick);
        if (f < 0.01F) {
            return;
        }
        fogApplied++;
        worldFogApplied = true; // this frame Minecraft's own fog carries it: DepthFog stays out of the way
        // Works the same from 2 to 32 chunks of render distance: see FogCurve. (The render distance fog is what
        // the old near / far planes were; the environmental fog is where the creature's fog goes now.)
        float far = com.foglurker.util.FogCurve.far(f, data.renderDistanceEnd);
        float near = com.foglurker.util.FogCurve.near(f, far, data.renderDistanceStart);
        lastFar = far;
        data.environmentalStart = Math.min(data.environmentalStart, near);
        data.environmentalEnd = Math.min(data.environmentalEnd, far);
        data.skyEnd = Math.min(data.skyEnd, far);
        data.cloudEnd = Math.min(data.cloudEnd, far);
    }

    private static float lastFar = -1F;

    /** The fog distance drawn most recently (debug). */
    public static float lastFogFar() {
        return lastFar;
    }

    private static void applyFogColor(org.joml.Vector4f c, float partialTick) {
        float f = FogLurkerConfig.CLIENT.fogEffects.get() ? fogAt(partialTick) * 0.8F : 0F;
        if (f >= 0.01F) {
            float avg = (c.x + c.y + c.z) / 3F;
            c.x = Mth.lerp(f, c.x, avg * 0.95F);
            c.y = Mth.lerp(f, c.y, avg * 0.98F);
            c.z = Mth.lerp(f, c.z, avg * 1.05F);
            // At night the fog is a dark grey you can see, not plain black.
            float floor = 0.11F * f;
            c.x = Math.max(c.x, floor * 0.95F);
            c.y = Math.max(c.y, floor * 0.98F);
            c.z = Math.max(c.z, floor * 1.05F);
        }
        // Always remember the colour (also before the fog starts) so the screen veil matches the sky now.
        fogColor = ((int) (Mth.clamp(c.x, 0F, 1F) * 255) << 16) | ((int) (Mth.clamp(c.y, 0F, 1F) * 255) << 8)
                | (int) (Mth.clamp(c.z, 0F, 1F) * 255);
    }

    /** The camera jolt when it reveals itself (GameRendererMixin, where Minecraft tilts the view when you are hurt). */
    public static void shakeCamera(com.mojang.blaze3d.vertex.PoseStack pose, float partialTick) {
        if (scareTicks <= 0 || !FogLurkerConfig.CLIENT.screenEffects.get()) {
            return;
        }
        float t = scareTicks - partialTick;
        // Scaled by Minecraft's own Distortion Effects slider (0 = no shake at all).
        float amp = 4.0F * scareTicks / scareTotal * Minecraft.getInstance().options.screenEffectScale().get().floatValue();
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(Mth.sin(t * 1.7F) * amp));
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(Mth.cos(t * 2.3F) * amp * 0.5F));
    }

    /** Dark red pulse when it reveals itself; a faint darkening while the fog is thick. */
    /** Drawn under the hotbar, chat and messages (so "RUN!" stays readable): the fog veil and the dark edges. */
    public static void renderFogOverlay(GuiGraphics graphics, net.minecraft.client.DeltaTracker delta) {
        float partialTick = delta.getGameTimeDeltaPartialTick(false);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        // The fog veil: a see-through layer of fog over the whole view, thicker toward the edges. It works
        // even when shaders or a rendering mod replace Minecraft's world fog.
        float veil = FogLurkerConfig.CLIENT.fogEffects.get() && FogLurkerConfig.CLIENT.fogOverlay.get() ? fogNow(partialTick) : 0F;
        if (veil > 0.02F) {
            // When real fog is on screen (Minecraft's, or Fog Lurker's own), only a light haze at the edges is added:
            // a flat gray layer over everything is only for when nothing else can show it (a shader pack).
            boolean realFog = DepthFog.worldFogFramesLastSecond() > 0 || DepthFog.framesLastSecond() > 0;
            int base = (int) (Mth.clamp(veil * (realFog ? 0.0F : 0.42F), 0F, 1F) * 255);
            int edge = (int) (Mth.clamp(veil * (realFog ? 0.3F : 0.72F), 0F, 1F) * 255);
            graphics.fill(0, 0, width, height, (base << 24) | fogColor);
            int band = height / 3;
            graphics.fillGradient(0, 0, width, band, ((edge - base) << 24) | fogColor, fogColor & 0xFFFFFF);
            graphics.fillGradient(0, height - band, width, height, fogColor & 0xFFFFFF, ((edge - base) << 24) | fogColor);
        }
        if (!FogLurkerConfig.CLIENT.screenEffects.get()) {
            return;
        }
        // The edges of the screen close in as it gets nearer (same vignette vanilla uses, driven by the fog).
        float dread = Mth.clamp(fogNow(partialTick) * 1.15F, 0F, 1F);
        if (dread > 0.02F) {
            graphics.blit(net.minecraft.client.renderer.RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, width, height,
                    width, height, net.minecraft.util.ARGB.colorFromFloat(1.0F, dread, dread, dread));
        }
    }

    /** Drawn over everything: the dark red pulse when it reveals itself. */
    public static void renderOverlay(GuiGraphics graphics, net.minecraft.client.DeltaTracker delta) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (!FogLurkerConfig.CLIENT.screenEffects.get()) {
            return;
        }
        if (scareTicks > 0) {
            float alpha = 0.55F * scareTicks / scareTotal;
            int a = (int) (Mth.clamp(alpha, 0, 1) * 255) << 24;
            graphics.fill(0, 0, width, height, a | 0x2A0000);
        }
    }
}
