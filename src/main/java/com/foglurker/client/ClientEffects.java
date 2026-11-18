package com.foglurker.client;

import com.foglurker.FogLurkerMod;
import com.foglurker.config.FogLurkerConfig;
import com.foglurker.registry.ModSounds;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only atmosphere: the fog that rolls in while it hunts you, and the jolt when it reveals itself. */
@Mod.EventBusSubscriber(modid = FogLurkerMod.MOD_ID, value = Dist.CLIENT)
public final class ClientEffects {
    private static float fogTarget;
    private static float fog;
    private static float fogPrev;
    private static int sinceFogPacket;
    private static int scareTicks;
    private static int scareTotal = 1;
    private static int heartbeatIn;
    private static final ResourceLocation VIGNETTE = ResourceLocation.tryParse("minecraft:textures/misc/vignette.png");

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

    /** What the creature is doing to us right now ({@link com.foglurker.network.Mood}), for the Discord status. */
    private static byte mood;

    public static byte mood() {
        return mood;
    }

    public static void setFogTarget(float intensity, byte newMood) {
        setFogTarget(intensity);
        mood = intensity > 0F ? newMood : com.foglurker.network.Mood.NONE;
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
        fog = fogPrev = fogTarget = 0;
        mood = com.foglurker.network.Mood.NONE;
        scareTicks = 0;
        DisguiseCache.clear();
        Apparitions.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (inWorld || fog != 0 || scareTicks != 0) {
                reset(); // also drops the disguise copies and apparitions of the world just left
                inWorld = false;
            }
            return;
        }
        inWorld = true;
        if (mc.isPaused()) {
            return;
        }
        if (mc.level.getGameTime() % 20 == 0) {
            fogEventsLastSecond = fogEvents;
            fogAppliedLastSecond = fogApplied;
            fogEvents = 0;
            fogApplied = 0;
        }
        if (++sinceFogPacket > 80) {
            fogTarget = 0; // the server stopped telling us it is near
            mood = com.foglurker.network.Mood.NONE;
        }
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

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
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

    private static float fogNow(double partialTick) {
        return Mth.lerp((float) partialTick, fogPrev, fog);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getMode() == net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN) {
            fogEvents++; // once per frame (the sky pass fires this event too)
        }
        if (!FogLurkerConfig.CLIENT.fogEffects.get() || event.getType() != FogType.NONE) {
            return;
        }
        float f = fogNow(event.getPartialTick());
        if (f < 0.01F) {
            return;
        }
        fogApplied++;
        // Works the same from 2 to 32 chunks of render distance: see FogCurve.
        float far = com.foglurker.util.FogCurve.far(f, event.getFarPlaneDistance());
        float near = com.foglurker.util.FogCurve.near(f, far, event.getNearPlaneDistance());
        lastFar = far;
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(near);
        event.setCanceled(true); // required for the new distances to apply
    }

    private static float lastFar = -1F;

    /** The fog distance drawn most recently (debug). */
    public static float lastFogFar() {
        return lastFar;
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float f = FogLurkerConfig.CLIENT.fogEffects.get() ? fogNow(event.getPartialTick()) * 0.8F : 0F;
        if (f >= 0.01F) {
            float avg = (event.getRed() + event.getGreen() + event.getBlue()) / 3F;
            event.setRed(Mth.lerp(f, event.getRed(), avg * 0.95F));
            event.setGreen(Mth.lerp(f, event.getGreen(), avg * 0.98F));
            event.setBlue(Mth.lerp(f, event.getBlue(), avg * 1.05F));
        }
        // Always remember the colour (also before the fog starts) so the screen veil matches the sky now.
        fogColor = ((int) (Mth.clamp(event.getRed(), 0F, 1F) * 255) << 16) | ((int) (Mth.clamp(event.getGreen(), 0F, 1F) * 255) << 8)
                | (int) (Mth.clamp(event.getBlue(), 0F, 1F) * 255);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (scareTicks <= 0 || !FogLurkerConfig.CLIENT.screenEffects.get()) {
            return;
        }
        float t = scareTicks - (float) event.getPartialTick();
        // Scaled by Minecraft's own Distortion Effects slider (0 = no shake at all).
        float amp = 4.0F * scareTicks / scareTotal * Minecraft.getInstance().options.screenEffectScale().get().floatValue();
        event.setRoll(event.getRoll() + Mth.sin(t * 1.7F) * amp);
        event.setYaw(event.getYaw() + Mth.cos(t * 2.3F) * amp * 0.5F);
    }

    /** Dark red pulse when it reveals itself; a faint darkening while the fog is thick. */
    /** Drawn under the hotbar, chat and messages (so "RUN!" stays readable): the fog veil and the dark edges. */
    public static void renderFogOverlay(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        // The fog veil: a see-through layer of fog over the whole view, thicker toward the edges. It works
        // even when shaders or a rendering mod replace Minecraft's world fog.
        float veil = FogLurkerConfig.CLIENT.fogEffects.get() && FogLurkerConfig.CLIENT.fogOverlay.get() ? fogNow(partialTick) : 0F;
        if (veil > 0.02F) {
            int base = (int) (Mth.clamp(veil * 0.42F, 0F, 1F) * 255);
            int edge = (int) (Mth.clamp(veil * 0.72F, 0F, 1F) * 255);
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
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            graphics.setColor(dread, dread, dread, 1.0F);
            graphics.blit(VIGNETTE, 0, 0, -90, 0.0F, 0.0F, width, height, width, height);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
        }
    }

    /** Drawn over everything: the dark red pulse when it reveals itself. */
    public static void renderOverlay(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
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
