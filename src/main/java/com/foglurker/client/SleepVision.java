package com.foglurker.client;

import com.foglurker.FogLurkerMod;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** After sleep paralysis: whenever you sleep, a faint picture of the creature in the dark. */
public final class SleepVision {
    private static final ResourceLocation PICTURE = FogLurkerMod.id("textures/gui/sleep_paralysis.png");
    private static boolean showing;

    private SleepVision() {
    }

    public static void show() {
        showing = true;
    }

    /** Drawn over the darkening sleep screen, so it is the last thing you see. */
    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!showing || mc.player == null || !mc.player.isSleeping()) {
            showing = showing && mc.player != null && mc.player.isSleeping();
            return;
        }
        // Fades in as the screen darkens: barely there, but there.
        float t = Mth.clamp((mc.player.getSleepTimer() + partialTick) / 60F, 0F, 1F);
        float alpha = 0.22F * t;
        int size = (int) (Math.min(width, height) * 0.8F);
        int x = (width - size) / 2;
        int y = (height - size) / 2;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1F, 1F, 1F, alpha);
        graphics.blit(PICTURE, x, y, 0, 0, size, size, size, size);
        graphics.setColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
    }
}
