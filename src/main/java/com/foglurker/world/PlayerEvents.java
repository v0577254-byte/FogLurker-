package com.foglurker.world;

import com.foglurker.FogLurkerMod;
import com.foglurker.command.FogLurkerCommands;
import com.foglurker.config.FogLurkerConfig;
import com.foglurker.voice.VoiceBridge;
import com.foglurker.voice.VoiceConsent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FogLurkerMod.MOD_ID)
public final class PlayerEvents {
    private PlayerEvents() {
    }

    /** Voice snippet retention: wipe anything that outlived its 30 days before the world even loads. */
    @SubscribeEvent
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        com.foglurker.voice.VoiceRetention.onStart(System.currentTimeMillis());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        com.foglurker.voice.VoiceConsent.touch(event.getEntity().getUUID(), System.currentTimeMillis());
    }

    /** Ringing a bell drives the Fog Lurker away. */
    @SubscribeEvent
    public static void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                && level.getBlockState(event.getPos()).getBlock() instanceof net.minecraft.world.level.block.BellBlock) {
            com.foglurker.brain.LurkerDirector.onBell(level, event.getPos(),
                    event.getEntity() instanceof ServerPlayer sp ? sp : null);
        }
    }

    /** Tell every player once that voice snippets exist and are strictly opt-in. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        VoiceConsent.touch(player.getUUID(), System.currentTimeMillis());
        if (!VoiceBridge.isActive() || !FogLurkerConfig.COMMON.showVoiceNoticeOnJoin.get()
                || VoiceConsent.isOptedIn(player.getUUID())) {
            return;
        }
        if (FogLurkerSavedData.get(player.server).markVoiceNoticeShown(player.getUUID())) {
            player.sendSystemMessage(Component.literal("[Fog Lurker] ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("This world can save short snippets of your voice for its horror creature, "
                            + "but only if you opt in. Read the terms: ").withStyle(ChatFormatting.GRAY))
                    .append(FogLurkerCommands.termsLink()));
        }
    }
}
