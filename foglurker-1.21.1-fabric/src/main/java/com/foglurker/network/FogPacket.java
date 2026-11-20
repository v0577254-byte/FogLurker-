package com.foglurker.network;

import com.foglurker.FogLurkerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Tells one client how thick the fog should be while the creature is hunting them (0..1). (Server to client; the client side is in ClientNetwork.) */
public record FogPacket(float intensity) implements CustomPacketPayload {
    public static final Type<FogPacket> TYPE = new Type<>(FogLurkerMod.id("fog"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FogPacket> CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, FogPacket::intensity, FogPacket::new);

    @Override
    public Type<FogPacket> type() {
        return TYPE;
    }
}
