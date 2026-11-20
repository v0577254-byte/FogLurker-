package com.foglurker.network;

import com.foglurker.client.ClientEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A jumpscare: the fog rushes in around one player for no reason, holds for this many ticks, then lifts. */
public record FogSurgePacket(int holdTicks) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(holdTicks);
    }

    public static FogSurgePacket decode(FriendlyByteBuf buf) {
        return new FogSurgePacket(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientEffects.fogSurge(holdTicks));
        ctx.get().setPacketHandled(true);
    }
}
