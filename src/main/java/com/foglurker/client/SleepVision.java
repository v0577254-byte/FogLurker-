package com.foglurker.client;

import com.foglurker.FogLurkerMod;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * What you see while you sleep: a faint picture of the creature in the dark (after sleep paralysis, after three
 * days, or once you have seen it as itself), and, rarely, big red glitching words.
 */
public final class SleepVision {
    private static final ResourceLocation PICTURE = FogLurkerMod.id("textures/gui/sleep_paralysis.png");
    /** The words, exactly as they are written. */
    public static final String WORDS = "€AN Y0UU $EE M33?";
    private static final String GLITCH = "#%$@!?/\\|<>01€";
    private static boolean picture;
    private static boolean words;
    private static long seed;

    private SleepVision() {
    }

    public static void show(boolean showPicture, boolean showWords) {
        picture = showPicture;
        words = showWords;
        seed = System.nanoTime();
    }

    /** Drawn over the darkening sleep screen, so it is the last thing you see. */
    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!picture && !words) {
            return;
        }
        if (mc.player == null || !mc.player.isSleeping()) {
            picture = false;
            words = false;
            return;
        }
        float sleep = mc.player.getSleepTimer() + partialTick;
        if (picture) {
            // Fades in as the screen darkens: barely there, but there.
            float alpha = 0.22F * Mth.clamp(sleep / 60F, 0F, 1F);
            int size = (int) (Math.min(width, height) * 0.8F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.setColor(1F, 1F, 1F, alpha);
            graphics.blit(PICTURE, (width - size) / 2, (height - size) / 2, 0, 0, size, size, size, size);
            graphics.setColor(1F, 1F, 1F, 1F);
            RenderSystem.disableBlend();
        }
        if (words && sleep > 15F) {
            drawWords(graphics, mc.font, width, height, Mth.clamp((sleep - 15F) / 25F, 0F, 1F));
        }
    }

    /** Big, red, and glitching: it jitters, tears sideways in bands, splits, flickers and loses letters. */
    private static void drawWords(GuiGraphics graphics, Font font, int width, int height, float fade) {
        long frame = System.currentTimeMillis() / 60L; // a new glitch about 16 times a second
        RandomSource r = RandomSource.create(seed ^ frame * 0x9E3779B97F4A7C15L);
        StringBuilder text = new StringBuilder(WORDS);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != ' ' && r.nextFloat() < 0.07F) {
                text.setCharAt(i, GLITCH.charAt(r.nextInt(GLITCH.length())));
            }
        }
        String shown = text.toString();
        float scale = Math.max(1F, width * 0.8F / font.width(WORDS));
        float textW = font.width(shown) * scale;
        float textH = 8 * scale;
        float flicker = r.nextFloat() < 0.12F ? 0.35F : 1F;
        int alpha = (int) (255 * fade * flicker);
        if (alpha < 8) {
            return;
        }
        float cx = width / 2F + (r.nextFloat() - 0.5F) * scale * 1.5F;
        float cy = height / 2F + (r.nextFloat() - 0.5F) * scale;
        // Torn into horizontal bands, each pushed a little sideways now and then.
        int bands = 5;
        float top = cy - textH / 2F;
        for (int b = 0; b < bands; b++) {
            int y0 = Mth.floor(top + textH * b / bands);
            int y1 = Mth.ceil(top + textH * (b + 1) / bands);
            float shift = r.nextFloat() < 0.2F ? (r.nextFloat() - 0.5F) * scale * 6F : 0F;
            graphics.enableScissor(0, y0, width, y1);
            if (r.nextFloat() < 0.5F) { // the colour split: a darker red copy and a pale one either side
                drawLine(graphics, font, shown, cx + shift - scale * 0.8F, cy, scale, (alpha / 2) << 24 | 0x5A0000);
                drawLine(graphics, font, shown, cx + shift + scale * 0.8F, cy, scale, (alpha / 3) << 24 | 0xFF6060);
            }
            drawLine(graphics, font, shown, cx + shift, cy, scale, alpha << 24 | 0xD00000);
            graphics.disableScissor();
        }
        if (r.nextFloat() < 0.1F) { // a thin bright tear right across
            int y = (int) (top + r.nextFloat() * textH);
            graphics.fill((int) (cx - textW / 2), y, (int) (cx + textW / 2), y + Math.max(1, (int) (scale / 3)),
                    (alpha / 2) << 24 | 0xFF2020);
        }
    }

    private static void drawLine(GuiGraphics graphics, Font font, String s, float cx, float cy, float scale, int argb) {
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0);
        graphics.pose().scale(scale, scale, 1F);
        graphics.drawString(font, s, -font.width(s) / 2, -4, argb, false);
        graphics.pose().popPose();
    }
}
