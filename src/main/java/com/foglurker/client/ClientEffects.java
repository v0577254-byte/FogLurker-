package com.foglurker.client;

import com.foglurker.FogLurkerMod;
import com.foglurker.config.FogLurkerConfig;
import com.foglurker.registry.ModSounds;
import net.minecraft.client.Minecraft;
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

    private ClientEffects() {
    }

    public static void setFogTarget(float intensity) {
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
        scareTicks = 0;
        DisguiseCache.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (fog != 0 || scareTicks != 0) {
                reset();
            }
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        if (++sinceFogPacket > 80) {
            fogTarget = 0; // the server stopped telling us it is near
        }
        fogPrev = fog;
        fog += (fogTarget - fog) * 0.03F;
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
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static float fogNow(double partialTick) {
        return Mth.lerp((float) partialTick, fogPrev, fog);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!FogLurkerConfig.CLIENT.fogEffects.get() || event.getType() != FogType.NONE) {
            return;
        }
        float f = fogNow(event.getPartialTick());
        if (f < 0.01F) {
            return;
        }
        // Falls off fast: ~60 blocks of view while it stalks, ~14 while it lures you deep, ~9 when it attacks.
        float vanillaFar = event.getFarPlaneDistance();
        float far = Math.min(vanillaFar, 8.0F + (vanillaFar - 8.0F) * (float) Math.pow(1.0F - f, 2.2F));
        float near = Math.min(far * 0.5F, Mth.lerp(f, event.getNearPlaneDistance(), 0.0F));
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(near);
        event.setCanceled(true); // required for the new distances to apply
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!FogLurkerConfig.CLIENT.fogEffects.get()) {
            return;
        }
        float f = fogNow(event.getPartialTick()) * 0.8F;
        if (f < 0.01F) {
            return;
        }
        float avg = (event.getRed() + event.getGreen() + event.getBlue()) / 3F;
        event.setRed(Mth.lerp(f, event.getRed(), avg * 0.95F));
        event.setGreen(Mth.lerp(f, event.getGreen(), avg * 0.98F));
        event.setBlue(Mth.lerp(f, event.getBlue(), avg * 1.05F));
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (scareTicks <= 0 || !FogLurkerConfig.CLIENT.screenEffects.get()) {
            return;
        }
        float t = scareTicks - (float) event.getPartialTick();
        float amp = 4.0F * scareTicks / scareTotal;
        event.setRoll(event.getRoll() + Mth.sin(t * 1.7F) * amp);
        event.setYaw(event.getYaw() + Mth.cos(t * 2.3F) * amp * 0.5F);
    }

    /** Dark red pulse when it reveals itself; a faint darkening while the fog is thick. */
    public static void renderOverlay(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        if (!FogLurkerConfig.CLIENT.screenEffects.get()) {
            return;
        }
        float alpha = 0;
        int rgb = 0x000000;
        if (scareTicks > 0) {
            alpha = 0.55F * scareTicks / scareTotal;
            rgb = 0x2A0000;
        } else if (fog > 0.5F) {
            alpha = (fog - 0.5F) * 0.3F;
        }
        if (alpha <= 0.005F) {
            return;
        }
        int a = (int) (Mth.clamp(alpha, 0, 1) * 255) << 24;
        graphics.fill(0, 0, width, height, a | rgb);
    }
}
