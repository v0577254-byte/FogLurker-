package com.foglurker.network;

import com.foglurker.client.ClientDebug;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** TEMPORARY (remove with /foglurker debugeverything before release): asks the client to check itself. */
public record DebugPacket() {

    public void encode(FriendlyByteBuf buf) {
    }

    public static DebugPacket decode(FriendlyByteBuf buf) {
        return new DebugPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientDebug::run);
        ctx.get().setPacketHandled(true);
    }
}
