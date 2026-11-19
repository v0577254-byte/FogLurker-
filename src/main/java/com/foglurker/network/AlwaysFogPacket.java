package com.foglurker.network;

import com.foglurker.client.ClientEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** How thick the fog that is always there is on this server (0..1; /foglurker fog). */
public record AlwaysFogPacket(float level) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(level);
    }

    public static AlwaysFogPacket decode(FriendlyByteBuf buf) {
        return new AlwaysFogPacket(buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientEffects.setAlwaysFog(level));
        ctx.get().setPacketHandled(true);
    }
}
