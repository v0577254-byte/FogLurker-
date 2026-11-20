package com.foglurker.mixin;

import com.foglurker.security.SecretCommands;
import net.minecraft.client.CommandHistory;
import net.minecraft.util.ArrayListDeque;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The game writes the last commands you typed to {@code command_history.txt}, in plain text. A line that sets the
 * Gemini API key is never written there, and one already in the file (typed before this version) is wiped from it
 * when the game starts.
 */
@Mixin(CommandHistory.class)
public abstract class CommandHistoryMixin {
    @Shadow
    @Final
    private ArrayListDeque<String> lastCommands;

    @Shadow
    private void save() {
        throw new AssertionError(); // replaced by the game's own save()
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void foglurker$wipeSavedKey(CallbackInfo ci) {
        if (lastCommands.removeIf(SecretCommands::isSecret)) {
            save();
        }
    }

    @Inject(method = "addCommand", at = @At("HEAD"), cancellable = true)
    private void foglurker$keepKeyOut(String command, CallbackInfo ci) {
        if (SecretCommands.isSecret(command)) {
            ci.cancel();
        }
    }
}
