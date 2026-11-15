package com.foglurker.test;

import com.foglurker.FogLurkerMod;
import com.foglurker.client.ClientEffects;
import com.foglurker.entity.Disguise;
import com.foglurker.entity.FogLurkerEntity;
import com.foglurker.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dev-only visual check (never shipped): run with -Dfoglurker.showcase=true in a world named
 * "FogShowcase". Lines up every disguise, then takes screenshots with and without the fog.
 */
@Mod.EventBusSubscriber(modid = FogLurkerMod.MOD_ID, value = Dist.CLIENT)
public final class ClientShowcase {
    private static final boolean ENABLED = Boolean.getBoolean("foglurker.showcase");
    private static int ticks;
    private static int stageX;
    private static int stageY;
    private static int stageZ;

    private ClientShowcase() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null) {
            return;
        }
        ticks++;
        switch (ticks) {
            case 60 -> {
                mc.options.hideGui = true;
                server.execute(() -> setUp(server));
            }
            case 220 -> shot(mc, "foglurker_disguises.png");
            case 230 -> server.execute(() -> camera(server, CRAWLER_INDEX, 2.0, -3.0, 34F, 16F));
            case 290 -> shot(mc, "foglurker_crawler_front.png");
            case 300 -> server.execute(() -> camera(server, CRAWLER_INDEX, 4.2, 0.3, 90F, 10F));
            case 360 -> shot(mc, "foglurker_crawler_side.png");
            case 370 -> server.execute(() -> camera(server, 0, -2.0, -2.5, -35F, 15F));
            case 430 -> shot(mc, "foglurker_mimic_closeup.png");
            case 440 -> server.execute(() -> swimScene(server));
            case 520 -> shot(mc, "foglurker_swim.png");
            case 530 -> server.execute(() -> sp(server).teleportTo(sp(server).serverLevel(), stageX + 0.5, stageY, stageZ - 3.5, 0F, 14F));
            case 700 -> shot(mc, "foglurker_fog.png");
            case 710 -> server.execute(() -> {
                ServerPlayer p = sp(server);
                p.teleportTo(p.serverLevel(), stageX + 0.5, stageY, stageZ - 6.5, 0F, 8F);
            });
            case 720 -> server.execute(() -> {
                ClientEffects.setFogTarget(0F);
                server.getCommands().performPrefixedCommand(sp(server).createCommandSourceStack().withPermission(2), "foglurker-macarena 2");
            });
            case 790 -> shot(mc, "foglurker_macarena_shoulders.png");
            case 815 -> shot(mc, "foglurker_macarena_head.png");
            case 855 -> shot(mc, "foglurker_macarena_back.png");
            case 878 -> shot(mc, "foglurker_macarena_wiggle.png");
            case 890 -> {
                mc.options.hideGui = false;
                mc.player.connection.sendCommand("foglurker debugeverything");
            }
            case 950 -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen(""));
            case 970 -> shot(mc, "foglurker_debug_chat.png");
            case 990 -> mc.stop();
            default -> {
            }
        }
        if (ticks > 540 && ticks < 705) {
            ClientEffects.setFogTarget(0.7F);
        }
    }

    private static ServerPlayer sp(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(),
                msg -> FogLurkerMod.LOGGER.info("Showcase: {}", msg.getString()));
    }

    private static void setUp(MinecraftServer server) {
        ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = sp.serverLevel();
        level.setDayTime(6000);
        sp.setGameMode(GameType.CREATIVE);
        int x0 = sp.getBlockX();
        int z0 = sp.getBlockZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0);
        // A clean grass stage so nothing blocks the view.
        for (int x = x0 - 16; x <= x0 + 16; x++) {
            for (int z = z0 - 8; z <= z0 + 12; z++) {
                level.setBlockAndUpdate(new BlockPos(x, y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState());
                for (int dy = 0; dy < 8; dy++) {
                    level.setBlockAndUpdate(new BlockPos(x, y + dy, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        stageX = x0;
        stageY = y;
        stageZ = z0;
        sp.teleportTo(level, x0 + 0.5, y, z0 - 3.5, 0F, 14F);
        Disguise[] row = {Disguise.PLAYER, Disguise.DOG, Disguise.COW, Disguise.SHEEP, Disguise.PIG,
                Disguise.CHICKEN, Disguise.ZOMBIE, Disguise.WOLF, Disguise.NONE, Disguise.CRAWLER};
        for (int i = 0; i < row.length; i++) {
            Disguise d = row[i];
            int x = x0 - 9 + i * 2;
            int z = z0 + 6;
            int gy = y;
            FogLurkerEntity l = ModEntities.FOG_LURKER.get().create(level);
            l.moveTo(x + 0.5, gy, z + 0.5, 180F, 0F);
            l.setYHeadRot(180F);
            l.setYBodyRot(180F);
            l.setNoAi(true);
            l.setDisguise(d);
            switch (d) {
                case PLAYER -> {
                    l.setMimic(sp.getUUID(), sp.getGameProfile().getName());
                    l.copyLookOf(sp);
                    l.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                    l.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                }
                case DOG -> {
                    l.setVariant(DyeColor.LIGHT_BLUE.getId());
                    l.setDisguiseName("Biscuit");
                    l.setFlag(FogLurkerEntity.FLAG_SITTING, true);
                }
                case SHEEP -> l.setVariant(DyeColor.PINK.getId());
                case CRAWLER -> l.setAggressive(true);
                default -> {
                }
            }
            level.addFreshEntity(l);
        }
    }

    private static final int CRAWLER_INDEX = 9;

    /** Puts the camera at an offset from the n-th creature in the row. */
    private static void camera(MinecraftServer server, int index, double dx, double dz, float yaw, float pitch) {
        ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
        double x = stageX - 9 + index * 2 + 0.5;
        double z = stageZ + 6 + 0.5;
        sp.teleportTo(sp.serverLevel(), x + dx, stageY, z + dz, yaw, pitch);
    }

    /** A stolen player sprint-swimming across a pool. */
    private static void swimScene(MinecraftServer server) {
        ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = sp.serverLevel();
        int px = stageX - 4;
        int pz = stageZ - 6;
        for (int x = px - 3; x <= px + 3; x++) {
            for (int z = pz - 2; z <= pz + 2; z++) {
                level.setBlockAndUpdate(new BlockPos(x, stageY - 2, z), Blocks.SAND.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, stageY - 1, z), Blocks.WATER.defaultBlockState());
            }
        }
        FogLurkerEntity l = ModEntities.FOG_LURKER.get().create(level);
        l.moveTo(px + 0.5, stageY - 1.0, pz + 0.5, 90F, 0F);
        l.setYHeadRot(90F);
        l.setYBodyRot(90F);
        l.setNoAi(true);
        l.setNoGravity(true);
        l.setDisguise(Disguise.PLAYER);
        l.setMimic(sp.getUUID(), sp.getGameProfile().getName());
        l.copyLookOf(sp);
        l.setSwimming(true);
        l.setPose(Pose.SWIMMING);
        level.addFreshEntity(l);
        sp.teleportTo(level, px + 0.5, stageY, pz + 4.5, 180F, 30F);
    }
}
