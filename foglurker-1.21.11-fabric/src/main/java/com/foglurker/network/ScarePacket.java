package com.foglurker.network;

import com.foglurker.FogLurkerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A jump scare on one client (shake, red flash, heartbeat) for this many ticks. (Server to client; the client side is in ClientNetwork.) */
public record ScarePacket(int durationTicks) implements CustomPacketPayload {
    public static final Type<ScarePacket> TYPE = new Type<>(FogLurkerMod.id("scare"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScarePacket> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, ScarePacket::durationTicks, ScarePacket::new);

    @Override
    public Type<ScarePacket> type() {
        return TYPE;
    }
}
