package com.foglurker.network;

import com.foglurker.client.SleepVision;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * You lie down: what you see while you sleep. {@code picture}: the faint picture of the creature (after sleep
 * paralysis, after three days, or once you have seen it as itself). {@code words}: the rare red, glitching words.
 */
public record SleepVisionPacket(boolean picture, boolean words) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(picture);
        buf.writeBoolean(words);
    }

    public static SleepVisionPacket decode(FriendlyByteBuf buf) {
        return new SleepVisionPacket(buf.readBoolean(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SleepVision.show(picture, words));
        ctx.get().setPacketHandled(true);
    }
}
