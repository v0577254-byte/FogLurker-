package com.foglurker.network;

import com.foglurker.client.ClientEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Tells one client how thick the fog should be while the creature is hunting them (0..1). */
public record FogPacket(float intensity) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(intensity);
    }

    public static FogPacket decode(FriendlyByteBuf buf) {
        return new FogPacket(buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientEffects.setFogTarget(intensity));
        ctx.get().setPacketHandled(true);
    }
}
