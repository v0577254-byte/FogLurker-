package com.foglurker.network;

import com.foglurker.client.ClientEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Camera shake + dark vignette + heartbeat, for when it reveals itself. */
public record ScarePacket(int durationTicks) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(durationTicks);
    }

    public static ScarePacket decode(FriendlyByteBuf buf) {
        return new ScarePacket(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientEffects.scare(durationTicks));
        ctx.get().setPacketHandled(true);
    }
}
