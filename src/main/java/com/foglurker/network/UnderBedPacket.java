package com.foglurker.network;

import com.foglurker.client.UnderBed;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** While you sleep: for a moment, your own face rises from under the foot of your bed. Nothing exists on the server. */
public record UnderBedPacket(int ticks) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(ticks);
    }

    public static UnderBedPacket decode(FriendlyByteBuf buf) {
        return new UnderBedPacket(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> UnderBed.show(ticks));
        ctx.get().setPacketHandled(true);
    }
}
