package com.foglurker.mixin;

import com.foglurker.client.ClientEffects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The fog veil and the dark edges go under the whole HUD, so "RUN!" and the chat stay readable. */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void foglurker$fogUnderHud(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        ClientEffects.renderFogOverlay(graphics, delta);
    }
}
