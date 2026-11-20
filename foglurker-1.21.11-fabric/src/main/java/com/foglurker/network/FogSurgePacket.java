package com.foglurker.network;

import com.foglurker.FogLurkerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A jumpscare: the fog rushes in around one player for no reason, holds for this many ticks, then lifts. (Server to client.) */
public record FogSurgePacket(int holdTicks) implements CustomPacketPayload {
    public static final Type<FogSurgePacket> TYPE = new Type<>(FogLurkerMod.id("fog_surge"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FogSurgePacket> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, FogSurgePacket::holdTicks, FogSurgePacket::new);

    @Override
    public Type<FogSurgePacket> type() {
        return TYPE;
    }
}
