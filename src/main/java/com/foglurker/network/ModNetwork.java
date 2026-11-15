package com.foglurker.network;

import com.foglurker.FogLurkerMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            FogLurkerMod.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(FogPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FogPacket::encode)
                .decoder(FogPacket::decode)
                .consumerMainThread(FogPacket::handle)
                .add();
        CHANNEL.messageBuilder(ScarePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ScarePacket::encode)
                .decoder(ScarePacket::decode)
                .consumerMainThread(ScarePacket::handle)
                .add();
    }

    public static void send(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
}
