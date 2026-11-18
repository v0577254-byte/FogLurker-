package com.foglurker.network;

import com.foglurker.client.ClientEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Tells one client how thick the fog should be while the creature is hunting them (0..1), and what it is doing to
 * them ({@link Mood}, for their Discord status).
 */
public record FogPacket(float intensity, byte mood) {

    public FogPacket(float intensity) {
        this(intensity, Mood.NONE);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(intensity);
        buf.writeByte(mood);
    }

    public static FogPacket decode(FriendlyByteBuf buf) {
        return new FogPacket(buf.readFloat(), buf.readByte());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientEffects.setFogTarget(intensity, mood));
        ctx.get().setPacketHandled(true);
    }
}
