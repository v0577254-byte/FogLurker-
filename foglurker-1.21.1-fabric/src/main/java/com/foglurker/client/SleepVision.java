package com.foglurker.client;

import com.foglurker.FogLurkerMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** After sleep paralysis: whenever you sleep, a faint picture of the creature in the dark. */
public final class SleepVision {
    private static final ResourceLocation PICTURE = FogLurkerMod.id("textures/gui/sleep_paralysis.png");
    private static boolean showing;
    /** Until when (ms) it waits for the bed to be reached (the packet can arrive a moment before you lie down). */
    private static long waitUntil;

    private SleepVision() {
    }

    public static void show() {
        showing = true;
        waitUntil = net.minecraft.Util.getMillis() + 2000L;
    }

    /** Drawn over the darkening sleep screen, so it is the last thing you see. */
    public static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!showing || mc.player == null) {
            showing = false;
            return;
        }
        if (!mc.player.isSleeping()) {
            // Not in bed (yet): wait a moment for it, but once the sleep is over the picture goes.
            showing = net.minecraft.Util.getMillis() < waitUntil;
            return;
        }
        waitUntil = 0L; // asleep now: from here on, waking up ends it
        float partialTick = delta.getGameTimeDeltaPartialTick(false);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        // Fades in as the screen darkens: barely there, but there.
        float t = Mth.clamp((mc.player.getSleepTimer() + partialTick) / 60F, 0F, 1F);
        float alpha = 0.22F * t;
        int size = (int) (Math.min(width, height) * 0.8F);
        int x = (width - size) / 2;
        int y = (height - size) / 2;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        graphics.setColor(1F, 1F, 1F, alpha);
        graphics.blit(PICTURE, x, y, 0, 0, size, size, size, size);
        graphics.setColor(1F, 1F, 1F, 1F);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }
}
