package com.foglurker.network;

import com.foglurker.client.SleepVision;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** You lie down after having had sleep paralysis once: a faint picture of the creature while you sleep. */
public record SleepVisionPacket() {

    public void encode(FriendlyByteBuf buf) {
    }

    public static SleepVisionPacket decode(FriendlyByteBuf buf) {
        return new SleepVisionPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> SleepVision::show);
        ctx.get().setPacketHandled(true);
    }
}
