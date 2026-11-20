package com.foglurker.network;

import com.foglurker.FogLurkerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** TEMPORARY: /foglurker debugeverything runs its client checks. (Server to client; the client side is in ClientNetwork.) */
public record DebugPacket() implements CustomPacketPayload {
    public static final Type<DebugPacket> TYPE = new Type<>(FogLurkerMod.id("debug"));
    public static final DebugPacket INSTANCE = new DebugPacket();
    public static final StreamCodec<RegistryFriendlyByteBuf, DebugPacket> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<DebugPacket> type() {
        return TYPE;
    }
}
