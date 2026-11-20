package com.foglurker.network;

import com.foglurker.FogLurkerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A faint picture of the creature while falling asleep. (Server to client; the client side is in ClientNetwork.) */
public record SleepVisionPacket() implements CustomPacketPayload {
    public static final Type<SleepVisionPacket> TYPE = new Type<>(FogLurkerMod.id("sleep_vision"));
    public static final SleepVisionPacket INSTANCE = new SleepVisionPacket();
    public static final StreamCodec<RegistryFriendlyByteBuf, SleepVisionPacket> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<SleepVisionPacket> type() {
        return TYPE;
    }
}
