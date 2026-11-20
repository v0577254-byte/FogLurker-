package com.foglurker.mixin;

import com.foglurker.security.SecretCommands;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code /foglurker setgeminiapikey <key>} is never kept: not in the lines the up arrow brings back, and not in
 * {@code command_history.txt}, where the game would otherwise write the key in plain text.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Inject(method = "addRecentChat", at = @At("HEAD"), cancellable = true)
    private void foglurker$keepKeyOut(String message, CallbackInfo ci) {
        if (SecretCommands.isSecret(message)) {
            ci.cancel();
        }
    }
}
