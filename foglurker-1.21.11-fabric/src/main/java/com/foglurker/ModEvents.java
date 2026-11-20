package com.foglurker;

import com.foglurker.brain.LurkerDirector;
import com.foglurker.command.FogLurkerCommands;
import com.foglurker.learning.BehaviorTracker;
import com.foglurker.world.AfkTracker;
import com.foglurker.world.PlayerEvents;
import com.foglurker.world.SleepParalysis;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Player;

/**
 * Wires Fabric's callbacks (and the mixins' hooks) to the mod's handlers: what Forge's event bus did. Every
 * callback here only watches; none of them cancels anything (the mixins do the few refusals: Mauled, the anvil).
 */
public final class ModEvents {
    private ModEvents() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> PlayerEvents.onServerStarted());
        ServerLifecycleEvents.SERVER_STOPPING.register(LurkerDirector::onServerStopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            LurkerDirector.onServerStopped();
            AfkTracker.onServerStopped();
            SleepParalysis.onServerStopped();
            BehaviorTracker.onServerStopped();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            LurkerDirector.onServerTick(server);
            AfkTracker.onServerTick(server);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer p = handler.getPlayer();
            AfkTracker.onLogin(p);
            PlayerEvents.onLogin(p);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer p = handler.getPlayer();
            PlayerEvents.onLogout(p);
            AfkTracker.onLogout(p);
            SleepParalysis.onLogout(p);
            BehaviorTracker.onLogout(p);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> AfkTracker.onMovedByGame(newPlayer));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, from, to) -> AfkTracker.onMovedByGame(player));

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String text = message.signedContent();
            AfkTracker.onChat(sender);
            BehaviorTracker.onChat(sender, text);
        });
        EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) -> {
            BehaviorTracker.onSleep(player, pos);
            return null; // only watching: vanilla decides
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClientSide()) {
                AfkTracker.onActivity(player);
                PlayerEvents.onUseBlock(player, world, hand, hit);
                BehaviorTracker.onUseBlock(player, hit.getBlockPos());
            }
            return InteractionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClientSide()) {
                AfkTracker.onActivity(player);
            }
            return InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClientSide()) {
                AfkTracker.onActivity(player);
            }
            return InteractionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClientSide()) {
                AfkTracker.onActivity(player);
            }
            return InteractionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!world.isClientSide()) {
                AfkTracker.onActivity(player);
            }
            return InteractionResult.PASS;
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> AfkTracker.onActivity(player));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> FogLurkerCommands.register(dispatcher));
    }

    // ------------------------------------------------------------------ called by the mixins

    /** The end of every player's tick on the server (PlayerMixin), like Forge's PlayerTickEvent END. */
    public static void onServerPlayerTick(ServerPlayer p) {
        BehaviorTracker.onPlayerTick(p);
        SleepParalysis.onPlayerTick(p);
    }

    /** A block was placed by a player (BlockItemMixin): counts as activity. */
    public static void onPlaced(Player player) {
        AfkTracker.onActivity(player);
    }

    /** A menu opened for a player (ServerPlayerMixin). */
    public static void onMenuOpened(ServerPlayer p, AbstractContainerMenu menu) {
        BehaviorTracker.onContainer(p, menu);
    }
}
