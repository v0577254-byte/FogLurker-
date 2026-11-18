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
                && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                && level.getBlockState(event.getPos()).getBlock() instanceof net.minecraft.world.level.block.BellBlock) {
            // Only a bell that really rang counts (sneak-clicking with a block in hand, or the wrong face, does not
            // ring it). The click is handled first; the bell's state is checked right after.
            net.minecraft.core.BlockPos pos = event.getPos().immutable();
            ServerPlayer ringer = event.getEntity() instanceof ServerPlayer sp ? sp : null;
            checkRungAfter(level, pos, ringer);
        }
    }

    /**
     * The click (or hit) event comes before the bell rings, so the bell is checked once this has been handled:
     * it counts only if it really rang just now (it started shaking, or its swing restarted).
     * {@code tell} always queues; {@code execute} would run at once on the server thread, before the ring.
     */
    private static void checkRungAfter(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos,
                                       @javax.annotation.Nullable ServerPlayer ringer) {
        int before = level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.BellBlockEntity b && b.shaking
                ? b.ticks : -1;
        var server = level.getServer();
        int clickedAt = server.getTickCount();
        server.tell(new net.minecraft.server.TickTask(clickedAt, () -> {
            // A swing that was already going has only counted on since the click (before + ticks passed); a new
            // ring restarted it from 0, so it is lower than that, whenever this check gets to run.
            int passed = server.getTickCount() - clickedAt;
            if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.BellBlockEntity bell
                    && bell.shaking && bell.ticks <= 5 && (before < 0 || bell.ticks < before + passed)) {
                com.foglurker.brain.LurkerDirector.onBell(level, pos, ringer);
            }
        }));
    }

    /** A bell rung by an arrow or trident counts too (its shooter is the ringer). */
    @SubscribeEvent
    public static void onProjectileHit(net.minecraftforge.event.entity.ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof net.minecraft.world.phys.BlockHitResult hit
                && event.getProjectile().level() instanceof net.minecraft.server.level.ServerLevel level
                && level.getBlockState(hit.getBlockPos()).getBlock() instanceof net.minecraft.world.level.block.BellBlock) {
            net.minecraft.core.BlockPos pos = hit.getBlockPos().immutable();
            ServerPlayer ringer = event.getProjectile().getOwner() instanceof ServerPlayer sp ? sp : null;
            checkRungAfter(level, pos, ringer);
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
                || !VoiceConsent.isReadable() // cannot tell whether they opted in: try at the next join
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
