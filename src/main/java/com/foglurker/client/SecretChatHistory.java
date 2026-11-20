package com.foglurker.client;

import com.foglurker.FogLurkerMod;
import com.foglurker.security.SecretCommands;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps the Gemini API key out of the chat box's history: {@code /foglurker setgeminiapikey <key>} is dropped from
 * the lines the up arrow brings back, so nobody using this computer afterwards can bring the key back on screen.
 * (This version of the game keeps that history only in memory; it never writes it to a file.)
 */
@Mod.EventBusSubscriber(modid = FogLurkerMod.MOD_ID, value = Dist.CLIENT)
public final class SecretChatHistory {
    private SecretChatHistory() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) {
            return;
        }
        try {
            mc.gui.getChat().getRecentChat().removeIf(SecretCommands::isSecret);
        } catch (UnsupportedOperationException ignored) {
            // another mod made the list read-only: nothing to clean
        }
    }
}
