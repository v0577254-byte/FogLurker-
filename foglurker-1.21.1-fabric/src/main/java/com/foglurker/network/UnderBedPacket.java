package com.foglurker.network;

import com.foglurker.FogLurkerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** While you sleep: for a moment, your own face rises from under the foot of your bed. (Server to client.) */
public record UnderBedPacket(int ticks) implements CustomPacketPayload {
    public static final Type<UnderBedPacket> TYPE = new Type<>(FogLurkerMod.id("under_bed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UnderBedPacket> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, UnderBedPacket::ticks, UnderBedPacket::new);

    @Override
    public Type<UnderBedPacket> type() {
        return TYPE;
    }
}
