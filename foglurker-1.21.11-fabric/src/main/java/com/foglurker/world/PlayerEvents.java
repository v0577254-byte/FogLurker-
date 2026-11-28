package com.foglurker.world;

import com.foglurker.FogLurkerMod;
import com.foglurker.command.FogLurkerCommands;
import com.foglurker.config.FogLurkerConfig;
import com.foglurker.voice.VoiceBridge;
import com.foglurker.voice.VoiceConsent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerEvents {
    private PlayerEvents() {
    }

    /** Voice snippet retention: wipe anything that outlived its 30 days before the world even loads. */
    public static void onServerStarted() {
        com.foglurker.voice.VoiceRetention.onStart(System.currentTimeMillis());
    }

    public static void onLogout(ServerPlayer player) {
        com.foglurker.voice.VoiceConsent.touch(player.getUUID(), System.currentTimeMillis());
    }

    /** Ringing a bell drives the Fog Lurker away. Opening the lookout tower's chest puts its Ingot of the Fog in the middle. */
    public static void onUseBlock(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level world,
                                  net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (world instanceof net.minecraft.server.level.ServerLevel level
                && hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
            var state = level.getBlockState(hit.getBlockPos());
            if (state.getBlock() instanceof net.minecraft.world.level.block.BellBlock) {
                // Only a bell that really rang counts (sneak-clicking with a block in hand, or the wrong face, does not
                // ring it). The click is handled first; the bell's state is checked right after.
                net.minecraft.core.BlockPos pos = hit.getBlockPos().immutable();
                ServerPlayer ringer = player instanceof ServerPlayer sp ? sp : null;
                checkRungAfter(level, pos, ringer);
            } else if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {
                ServerPlayer sp = player instanceof ServerPlayer p ? p : null;
                pinLookoutIngot(level, hit.getBlockPos(), sp);
                if (state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
                    pinLookoutIngot(level, hit.getBlockPos().relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state)), sp);
                }
            }
        }
    }

    /** The lookout tower's chest, filled on first opening, with its Ingot of the Fog moved to the middle slot. */
    private static void pinLookoutIngot(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos,
                                        @org.jetbrains.annotations.Nullable ServerPlayer player) {
        if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest
                && chest.getLootTable() != null
                && "foglurker:chests/lookout_tower".equals(chest.getLootTable().identifier().toString())) {
            chest.unpackLootTable(player);
            for (int i = 0; i < chest.getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack st = chest.getItem(i);
                if (st.is(com.foglurker.registry.ModItems.INGOT_OF_THE_FOG.get())) {
                    if (i != 4) {
                        net.minecraft.world.item.ItemStack prev = chest.getItem(4);
                        chest.setItem(4, st);
                        chest.setItem(i, prev);
                        chest.setChanged();
                    }
                    break;
                }
            }
        }
    }

    /**
     * The click (or hit) event comes before the bell rings, so the bell is checked once this has been handled:
     * it counts only if it really rang just now (it started shaking, or its swing restarted).
     * {@code tell} always queues; {@code execute} would run at once on the server thread, before the ring.
     */
    private static void checkRungAfter(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos,
                                       @org.jetbrains.annotations.Nullable ServerPlayer ringer) {
        int before = level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.BellBlockEntity b && b.shaking
                ? b.ticks : -1;
        var server = level.getServer();
        int clickedAt = server.getTickCount();
        server.schedule(new net.minecraft.server.TickTask(clickedAt, () -> {
            // A swing that was already going has only counted on since the click (before + ticks passed); a new
            // ring restarted it from 0, so it is lower than that, whenever this check gets to run.
            int passed = server.getTickCount() - clickedAt;
            if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.BellBlockEntity bell
                    && bell.shaking && bell.ticks <= 5 && (before < 0 || bell.ticks < before + passed)) {
                com.foglurker.brain.LurkerDirector.onBell(level, pos, ringer);
            }
        }));
    }

    /** A bell rung by an arrow or trident counts too (its shooter is the ringer). Called by ProjectileMixin. */
    public static void onProjectileHitBlock(net.minecraft.world.entity.projectile.Projectile projectile,
                                            net.minecraft.world.phys.BlockHitResult hit) {
        if (projectile.level() instanceof net.minecraft.server.level.ServerLevel level
                && level.getBlockState(hit.getBlockPos()).getBlock() instanceof net.minecraft.world.level.block.BellBlock) {
            net.minecraft.core.BlockPos pos = hit.getBlockPos().immutable();
            ServerPlayer ringer = projectile.getOwner() instanceof ServerPlayer sp ? sp : null;
            checkRungAfter(level, pos, ringer);
        }
    }

    /** How thick this world's always-there fog is (the mode's own, or /foglurker fog). */
    public static void sendAlwaysFog(ServerPlayer player) {
        com.foglurker.network.ModNetwork.send(player, new com.foglurker.network.AlwaysFogPacket(
                FogLurkerSavedData.get(player.level().getServer()).alwaysFog() / 100F));
    }

    /** ...to everyone, after it changed. */
    public static void sendAlwaysFogToAll(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            sendAlwaysFog(p);
        }
    }

    /** Tell every player once that voice snippets exist and are strictly opt-in. */
    public static void onLogin(ServerPlayer player) {
        sendAlwaysFog(player);
        VoiceConsent.touch(player.getUUID(), System.currentTimeMillis());
        if (!VoiceBridge.isActive() || !FogLurkerConfig.COMMON.showVoiceNoticeOnJoin.get()
                || !VoiceConsent.isReadable() // cannot tell whether they opted in: try at the next join
                || VoiceConsent.isOptedIn(player.getUUID())) {
            return;
        }
        if (FogLurkerSavedData.get(player.level().getServer()).markVoiceNoticeShown(player.getUUID())) {
            player.sendSystemMessage(Component.literal("[Fog Lurker] ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("This world can save short snippets of your voice for its horror creature, "
                            + "but only if you opt in. Read the terms: ").withStyle(ChatFormatting.GRAY))
                    .append(FogLurkerCommands.termsLink()));
        }
    }
}
