package com.foglurker.network;

import com.foglurker.FogLurkerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * You lie down: what you see while you sleep. {@code picture}: the faint picture of the creature (after sleep
 * paralysis, after three days, or once you have seen it as itself). {@code words}: the rare red, glitching words.
 * (Server to client; the client side is in ClientSetup.)
 */
public record SleepVisionPacket(boolean picture, boolean words) implements CustomPacketPayload {
    public static final Type<SleepVisionPacket> TYPE = new Type<>(FogLurkerMod.id("sleep_vision"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SleepVisionPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SleepVisionPacket::picture, ByteBufCodecs.BOOL, SleepVisionPacket::words, SleepVisionPacket::new);

    @Override
    public Type<SleepVisionPacket> type() {
        return TYPE;
    }
}
