package com.foglurker.test.mixin;

import net.minecraft.gametest.framework.GameTestServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tests only: the 1.21 game-test server runs its ticks as fast as it can. Fog Lurker's tests (like a real world)
 * mix ticks with real time (Gemini requests, voice files written in the background), so hold it to the normal
 * 20 ticks a second, as the game-test server did in 1.20.
 */
@Mixin(GameTestServer.class)
public abstract class GameTestServerMixin {
    @Unique
    private long foglurker$nextTickNanos;

    @Inject(method = "waitUntilNextTick", at = @At("HEAD"))
    private void foglurker$normalSpeed(CallbackInfo ci) {
        long now = System.nanoTime();
        if (foglurker$nextTickNanos == 0L || now - foglurker$nextTickNanos > 1_000_000_000L) {
            foglurker$nextTickNanos = now; // first tick, or far behind: start counting again from now
        }
        foglurker$nextTickNanos += 50_000_000L;
        long wait = foglurker$nextTickNanos - now;
        if (wait > 0) {
            try {
                Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
