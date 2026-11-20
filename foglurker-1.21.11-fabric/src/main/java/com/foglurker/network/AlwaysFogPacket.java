package com.foglurker.network;

import com.foglurker.FogLurkerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The world's always-there fog (0..1), from /foglurker fog or the mode. (Server to client; the client side is in ClientNetwork.) */
public record AlwaysFogPacket(float level) implements CustomPacketPayload {
    public static final Type<AlwaysFogPacket> TYPE = new Type<>(FogLurkerMod.id("always_fog"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AlwaysFogPacket> CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, AlwaysFogPacket::level, AlwaysFogPacket::new);

    @Override
    public Type<AlwaysFogPacket> type() {
        return TYPE;
    }
}
