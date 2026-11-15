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
        if (ticks == 60) {
            mc.options.hideGui = true;
            server.execute(() -> setUp(server));
        }
        if (ticks == 220) {
            shot(mc, "foglurker_disguises.png");
        }
        if (ticks > 230 && ticks < 470) {
            ClientEffects.setFogTarget(0.7F);
        }
        if (ticks == 460) {
            shot(mc, "foglurker_fog.png");
        }
        if (ticks == 480) {
            server.execute(() -> closeUp(server));
        }
        if (ticks == 560) {
            shot(mc, "foglurker_mimic_closeup.png");
        }
        if (ticks == 580) {
            mc.stop();
        }
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
        for (int x = x0 - 12; x <= x0 + 12; x++) {
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
                Disguise.CHICKEN, Disguise.ZOMBIE, Disguise.WOLF, Disguise.NONE};
        for (int i = 0; i < row.length; i++) {
            Disguise d = row[i];
            int x = x0 - 8 + i * 2;
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
                default -> {
                }
            }
            level.addFreshEntity(l);
        }
    }

    private static void closeUp(MinecraftServer server) {
        ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = sp.serverLevel();
        // Right in front of the stolen-face copy and the dog.
        sp.teleportTo(level, stageX - 6.5, stageY, stageZ + 2.0, 20F, 10F);
    }
}
