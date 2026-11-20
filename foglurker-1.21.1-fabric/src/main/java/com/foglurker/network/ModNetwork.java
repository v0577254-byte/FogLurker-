package com.foglurker.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Everything the server tells a client. Each payload has its own id, so a client without the mod (or an older one)
 * simply never gets it; ServerPlayNetworking.canSend is checked before sending.
 */
public final class ModNetwork {
    private ModNetwork() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(FogPacket.TYPE, FogPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(ScarePacket.TYPE, ScarePacket.CODEC);
        PayloadTypeRegistry.playS2C().register(ApparitionPacket.TYPE, ApparitionPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(AlwaysFogPacket.TYPE, AlwaysFogPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SleepVisionPacket.TYPE, SleepVisionPacket.CODEC);
        // TEMPORARY: /foglurker debugeverything (remove before release)
        PayloadTypeRegistry.playS2C().register(DebugPacket.TYPE, DebugPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(FogSurgePacket.TYPE, FogSurgePacket.CODEC);
    }

    public static void send(ServerPlayer player, CustomPacketPayload msg) {
        if (ServerPlayNetworking.canSend(player, msg.type())) {
            ServerPlayNetworking.send(player, msg);
        }
    }
}
