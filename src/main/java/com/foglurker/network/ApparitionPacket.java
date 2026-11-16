package com.foglurker.network;

import com.foglurker.client.Apparitions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Tells one client to imagine the creature at the edge of its vision. Nothing exists on the server. */
public record ApparitionPacket() {

    public void encode(FriendlyByteBuf buf) {
    }

    public static ApparitionPacket decode(FriendlyByteBuf buf) {
        return new ApparitionPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> Apparitions::spawn);
        ctx.get().setPacketHandled(true);
    }
}
