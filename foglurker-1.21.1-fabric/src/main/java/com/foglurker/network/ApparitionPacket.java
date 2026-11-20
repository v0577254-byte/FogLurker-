package com.foglurker.network;

import com.foglurker.FogLurkerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Something at the edge of your vision (client only). (Server to client; the client side is in ClientNetwork.) */
public record ApparitionPacket() implements CustomPacketPayload {
    public static final Type<ApparitionPacket> TYPE = new Type<>(FogLurkerMod.id("apparition"));
    public static final ApparitionPacket INSTANCE = new ApparitionPacket();
    public static final StreamCodec<RegistryFriendlyByteBuf, ApparitionPacket> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<ApparitionPacket> type() {
        return TYPE;
    }
}
