package com.foglurker.client;

import com.foglurker.FogLurkerMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** After sleep paralysis: whenever you sleep, a faint picture of the creature in the dark. */
public final class SleepVision {
    private static final Identifier PICTURE = FogLurkerMod.id("textures/gui/sleep_paralysis.png");
    private static boolean showing;

    private SleepVision() {
    }

    public static void show() {
        showing = true;
    }

    /** Drawn over the darkening sleep screen, so it is the last thing you see. */
    public static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!showing || mc.player == null || !mc.player.isSleeping()) {
            showing = showing && mc.player != null && mc.player.isSleeping();
            return;
        }
        float partialTick = delta.getGameTimeDeltaPartialTick(false);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        // Fades in as the screen darkens: barely there, but there.
        float t = Mth.clamp((mc.player.getSleepTimer() + partialTick) / 60F, 0F, 1F);
        float alpha = 0.22F * t;
        int size = (int) (Math.min(width, height) * 0.8F);
        int x = (width - size) / 2;
        int y = (height - size) / 2;
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, PICTURE, x, y, 0, 0, size, size, size, size,
                net.minecraft.util.ARGB.white(alpha));
    }
}
