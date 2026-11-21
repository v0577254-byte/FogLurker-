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

    /** "-nofog" scenes: act like a rendering mod with its fog switch off (Minecraft's fog step never reaches anyone). */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onRenderFog(net.minecraftforge.client.event.ViewportEvent.RenderFog event) {
        if (ENABLED && System.getProperty("foglurker.scene", "").endsWith("-nofog")) {
            event.setNearPlaneDistance(1.0E6F);
            event.setFarPlaneDistance(2.0E6F);
            event.setCanceled(true);
        }
    }

    /** "-dh" scenes: act like Distant Horizons, which lets the fog step run and then pushes the fog out of sight. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST, receiveCanceled = true)
    public static void onRenderFogLast(net.minecraftforge.client.event.ViewportEvent.RenderFog event) {
        if (ENABLED && System.getProperty("foglurker.scene", "").contains("-dh")) {
            event.setNearPlaneDistance(1.0E6F);
            event.setFarPlaneDistance(2.0E6F);
            event.setCanceled(true);
        }
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
        if ("snow".equals(System.getProperty("foglurker.scene"))) {
            snowScene(mc, server);
            return;
        }
        if ("scares".equals(System.getProperty("foglurker.scene"))) {
            scaresScene(mc, server);
            return;
        }
        if ("structures".equals(System.getProperty("foglurker.scene"))) {
            structuresScene(mc, server);
            return;
        }
        if ("newstuff".equals(System.getProperty("foglurker.scene"))) {
            newStuffScene(mc, server);
            return;
        }
        if ("keyhistory".equals(System.getProperty("foglurker.scene"))) {
            keyHistoryScene(mc);
            return;
        }
        if ("surge".equals(System.getProperty("foglurker.scene"))) {
            surgeScene(mc, server);
            return;
        }
        String scene = System.getProperty("foglurker.scene", "");
        if (scene.equals("sword")) {
            swordScene(mc, server);
            return;
        }
        if (scene.equals("afk")) {
            afkScene(mc, server);
            return;
        }
        if (scene.equals("sleep")) {
            sleepScene(mc, server);
            return;
        }
        if (scene.equals("portrait")) {
            portraitScene(mc, server);
            return;
        }
        if (scene.startsWith("fogattack")) {
            // "fogattack2" ... "fogattack32": the render distance to test at (default 32).
            String digits = scene.substring("fogattack".length()).replace("-nofog", "").replace("-dh", "").replace("-night", "");
            fogRenderDistance = digits.isEmpty() ? 32 : Integer.parseInt(digits);
            fogAttackScene(mc, server);
            return;
        }
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

    // ------------------------------------------------------------------ apparition, vignette, sinking

    private static FogLurkerEntity sinker;

    private static final class FogLurkerEntityCount {
        static boolean none(MinecraftServer server) {
            return com.foglurker.brain.LurkerDirector.active().isEmpty();
        }
    }

    /**
     * Exactly what a player does: survival, render distance 32, the creature stalks and then attacks, and the
     * fog has to come from the server. Logs how far you can see at each stage.
     */
    private static void fogAttackScene(Minecraft mc, MinecraftServer server) {
        switch (ticks) {
            case 40 -> {
                mc.options.renderDistance().set(fogRenderDistance);
                server.execute(() -> {
                    backToGround(server);
                    ServerPlayer p = sp(server);
                    p.serverLevel().setDayTime(System.getProperty("foglurker.scene", "").contains("-night") ? 18000 : 6000);
                    p.setGameMode(GameType.SURVIVAL);
                    p.setInvulnerable(true);
                });
            }
            case 70 -> server.execute(() -> server.getCommands().performPrefixedCommand(
                    sp(server).createCommandSourceStack().withPermission(2), "foglurker force stalk"));
            case 120, 160 -> logFog(mc, "stalking");
            case 165 -> shot(mc, "foglurker_fog_stalk_rd" + fogRenderDistance + System.getProperty("foglurker.scene", "").replaceAll("^fogattack\\d*", "") + ".png");
            case 170 -> server.execute(() -> {
                var ops = sp(server).createCommandSourceStack().withPermission(2);
                server.getCommands().performPrefixedCommand(ops, "foglurker despawn");
                server.getCommands().performPrefixedCommand(ops, "foglurker force attack");
            });
            case 180, 200, 220, 260 -> logFog(mc, "attacking");
            case 262 -> shot(mc, "foglurker_fog_attack_rd" + fogRenderDistance + System.getProperty("foglurker.scene", "").replaceAll("^fogattack\\d*", "") + ".png");
            case 300 -> mc.stop();
            default -> {
            }
        }
    }

    /** The drops and the sword in the hotbar, then a scare burst at a creature that is hunting you. */
    private static void swordScene(Minecraft mc, MinecraftServer server) {
        switch (ticks) {
            case 40 -> server.execute(() -> {
                ServerPlayer p = sp(server);
                p.serverLevel().setDayTime(18000);
                p.setGameMode(GameType.SURVIVAL);
                p.setInvulnerable(true);
                var inv = p.getInventory();
                inv.clearContent();
                inv.setItem(0, new net.minecraft.world.item.ItemStack(com.foglurker.registry.ModItems.PROTECTOR_OF_THE_FOG.get()));
                inv.setItem(1, new net.minecraft.world.item.ItemStack(com.foglurker.registry.ModItems.FOG_OF_THE_DAY.get(), 5));
                inv.setItem(2, new net.minecraft.world.item.ItemStack(com.foglurker.registry.ModItems.FOG_OF_THE_NIGHT.get(), 5));
                inv.setItem(3, new net.minecraft.world.item.ItemStack(com.foglurker.registry.ModItems.TEETH_OF_THE_LURKER.get(), 2));
                inv.setItem(4, new net.minecraft.world.item.ItemStack(com.foglurker.registry.ModItems.FOGGY_STICK.get()));
                inv.setItem(5, new net.minecraft.world.item.ItemStack(com.foglurker.registry.ModItems.INGOT_OF_THE_FOG.get()));
                inv.selected = 0;
            });
            case 60 -> server.execute(() -> server.getCommands().performPrefixedCommand(
                    sp(server).createCommandSourceStack().withPermission(2), "foglurker force attack"));
            case 110 -> server.execute(() -> {
                ServerPlayer p = sp(server);
                var l = com.foglurker.brain.LurkerDirector.active();
                if (!l.isEmpty()) {
                    var at = l.get(0).position();
                    float yaw = (float) Math.toDegrees(Math.atan2(at.z - p.getZ(), at.x - p.getX())) - 90F;
                    p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), yaw, 0F);
                }
            });
            case 116 -> server.execute(() -> {
                ServerPlayer p = sp(server);
                int reached = com.foglurker.item.ProtectorOfTheFogItem.burst(p.serverLevel(), p, p.getMainHandItem());
                FogLurkerMod.LOGGER.info("Showcase: scare burst reached {}, charges left {}", reached,
                        com.foglurker.item.ProtectorOfTheFogItem.charges(p.getMainHandItem()));
            });
            case 119 -> shot(mc, "foglurker_scare_burst.png");
            case 150 -> FogLurkerMod.LOGGER.info("Showcase: creatures out after the burst: {}", com.foglurker.brain.LurkerDirector.active().size());
            case 200 -> shot(mc, "foglurker_drops_hotbar.png");
            case 220 -> mc.stop();
            default -> {
            }
        }
    }

    /** Types /afk like a player: "AFK" above the hotbar and "<name> went afk" in chat. */
    private static void afkScene(Minecraft mc, MinecraftServer server) {
        switch (ticks) {
            case 60 -> server.execute(() -> sp(server).serverLevel().setDayTime(6000));
            case 80 -> mc.player.connection.sendCommand("afk");
            case 150 -> FogLurkerMod.LOGGER.info("Showcase: afk={} (tick 150, after 70 ticks)", com.foglurker.world.AfkTracker.isAfk(sp(server)));
            case 152 -> shot(mc, "foglurker_afk.png");
            case 160 -> mc.player.connection.sendCommand("afk");
            case 170 -> FogLurkerMod.LOGGER.info("Showcase: afk={} after /afk again", com.foglurker.world.AfkTracker.isAfk(sp(server)));
            case 175 -> shot(mc, "foglurker_afk_back.png");
            case 200 -> mc.stop();
            default -> {
            }
        }
    }

    private static int fogRenderDistance = 32;

    private static void logFog(Minecraft mc, String what) {
        FogLurkerMod.LOGGER.info("Showcase: {} t{}: fog {} -> {}, you can see {} blocks (render distance {} chunks), creatures out: {}",
                what, ticks, String.format("%.2f", ClientEffects.currentFog()), ClientEffects.fogTargetNow(),
                String.format("%.0f", ClientEffects.lastFogFar()), mc.options.getEffectiveRenderDistance(),
                mc.level.getEntitiesOfClass(FogLurkerEntity.class, mc.player.getBoundingBox().inflate(96)).size());
        FogLurkerMod.LOGGER.info("Showcase: world fog shown {} frames, Fog Lurker's own fog {} frames (last second), wisps {}",
                com.foglurker.client.DepthFog.worldFogFramesLastSecond(), com.foglurker.client.DepthFog.framesLastSecond(),
                com.foglurker.client.DepthFog.wispCount());
    }

    private static final String[] SHOW = {"lookout_tower", "abandoned_campfire", "hunters_bunker", "fog_well",
            "abandoned_castle", "fortified_castle"};
    private static BlockPos showAt;
    private static net.minecraft.world.level.levelgen.structure.BoundingBox showBox;

    /**
     * Each structure placed out in the world for real (as world generation would), and photographed. The bunker and the
     * well are mostly underground, so they are also shown as a cut-out up in the sky.
     */
    private static void structuresScene(Minecraft mc, MinecraftServer server) {
        int i = (ticks - 40) / 160;
        int step = (ticks - 40) % 160;
        if (ticks < 40) {
            return;
        }
        if (i >= SHOW.length) {
            // Last: the Ingot of the Fog in your hand.
            switch (step) {
                case 0 -> server.execute(() -> {
                    ServerPlayer p = sp(server);
                    p.setGameMode(GameType.SURVIVAL);
                    p.getInventory().selected = 0;
                    p.getInventory().setItem(0, new ItemStack(com.foglurker.registry.ModItems.INGOT_OF_THE_FOG.get()));
                    p.getInventory().setItem(1, new ItemStack(com.foglurker.registry.ModItems.FOGGY_STICK.get()));
                    p.teleportTo(p.serverLevel(), showAt.getX() + 0.5, 200, showAt.getZ() + 0.5, 0F, 0F);
                    p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 400, 0, false, false));
                });
                case 30 -> mc.options.hideGui = false;
                case 40 -> shot(mc, "foglurker_ingot_in_hand.png");
                case 50 -> mc.stop();
                default -> {
                }
            }
            return;
        }
        String name = SHOW[i];
        boolean inSky = name.equals("hunters_bunker") || name.equals("fog_well");
        var cmds = server.createCommandSourceStack().withPermission(4);
        switch (step) {
            case 0 -> server.execute(() -> {
                ServerPlayer p = sp(server);
                ServerLevel level = p.serverLevel();
                mc.options.hideGui = true;
                level.setDayTime(6000);
                p.setGameMode(GameType.SPECTATOR);
                showAt = new BlockPos(i * 160, 0, 300); // around the spawn, which is on land
                p.teleportTo(level, showAt.getX(), 160, showAt.getZ(), 0F, 60F);
            });
            case 60 -> server.execute(() -> {
                ServerLevel level = sp(server).serverLevel();
                BlockPos best = null;
                for (int r = 0; r <= 48 && best == null; r += 8) {
                    for (int dx = -r; dx <= r && best == null; dx += 8) {
                        for (int dz = -r; dz <= r && best == null; dz += 8) {
                            int x = showAt.getX() + dx;
                            int z = showAt.getZ() + dz;
                            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                            if (level.getFluidState(new BlockPos(x, y - 1, z)).isEmpty() && y > level.getSeaLevel()) {
                                best = new BlockPos(x, y, z);
                            }
                        }
                    }
                }
                showAt = best != null ? best : showAt;
                if (inSky) {
                    var t = level.getStructureManager().get(FogLurkerMod.id(name)).orElseThrow();
                    BlockPos at = new BlockPos(showAt.getX(), 175, showAt.getZ());
                    showBox = net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(at, at.offset(t.getSize()).offset(-1, -1, -1));
                    return;
                }
                // Where it will really be: /place builds it from the corner of the chunk, the same way as this. (Its box
                // includes 12 blocks round it for blending the ground in: not part of the build.)
                var structure = server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                        .get(FogLurkerMod.id(name));
                var gen = level.getChunkSource().getGenerator();
                var start = structure.generate(server.registryAccess(), gen, gen.getBiomeSource(), level.getChunkSource().randomState(),
                        level.getStructureManager(), level.getSeed(), new net.minecraft.world.level.ChunkPos(showAt), 0, level, b -> true);
                var b = start.getBoundingBox();
                showBox = new net.minecraft.world.level.levelgen.structure.BoundingBox(b.minX() + 12, b.minY() + 12, b.minZ() + 12,
                        b.maxX() - 12, b.maxY() - 12, b.maxZ() - 12);
            });
            case 70 -> server.execute(() -> {
                // (For the photo only: the trees round it out of the way.)
                ServerLevel level = sp(server).serverLevel();
                var c = showBox.getCenter();
                int rad = Math.max(showBox.getXSpan(), showBox.getZSpan()) / 2 + 14;
                for (BlockPos b : BlockPos.betweenClosed(new BlockPos(c.getX() - rad, showAt.getY() - 6, c.getZ() - rad),
                        new BlockPos(c.getX() + rad, showAt.getY() + 40, c.getZ() + rad))) {
                    var st = level.getBlockState(b);
                    if (st.is(net.minecraft.tags.BlockTags.LEAVES) || st.is(net.minecraft.tags.BlockTags.LOGS)
                            || st.is(Blocks.TALL_GRASS) || st.is(Blocks.LARGE_FERN)) {
                        level.setBlock(b, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            });
            case 80 -> server.execute(() -> {
                String command = inSky
                        ? "place template foglurker:" + name + " " + showBox.minX() + " " + showBox.minY() + " " + showBox.minZ()
                        : "place structure foglurker:" + name + " " + showAt.getX() + " " + showAt.getY() + " " + showAt.getZ();
                int r = server.getCommands().performPrefixedCommand(cmds, command);
                FogLurkerMod.LOGGER.info("Showcase: placed {} -> {} at {}", name, r, com.foglurker.world.FogStructures.box(showBox));
            });
            case 100 -> server.execute(() -> {
                ServerPlayer p = sp(server);
                ServerLevel level = p.serverLevel();
                var c = showBox.getCenter();
                int span = Math.max(showBox.getXSpan(), showBox.getZSpan());
                int height = showBox.getYSpan();
                double back = inSky ? span * 0.8 + 3 + height * 0.25 : span > 30 ? span * 0.45 + 4 : span * 0.9 + 6;
                double baseY = inSky ? showBox.minY() : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
                double up = inSky ? height * 0.55 : span > 30 ? span * 0.35 + 6 : span * 0.5 + 5;
                p.teleportTo(level, c.getX() - back, baseY + up, c.getZ() - back, -45F, 30F);
                double lookY = inSky ? showBox.minY() + height * 0.45 : baseY + Math.min(8, height / 3.0);
                p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                        new net.minecraft.world.phys.Vec3(c.getX() + 0.5, lookY, c.getZ() + 0.5));
            });
            case 150 -> shot(mc, "foglurker_structure_" + name + ".png");
            default -> {
            }
        }
    }

    private static BlockPos showcaseBed;

    /**
     * The new shapes standing in a row in daylight (villagers in their jobs, a horse, hostile mobs with their
     * weapons), then a night in bed: for one second your own face rises from under the foot of the bed.
     */
    private static void newStuffScene(Minecraft mc, MinecraftServer server) {
        switch (ticks) {
            case 40 -> {
                mc.options.hideGui = true;
                server.execute(() -> {
                    ServerPlayer p = sp(server);
                    ServerLevel level = p.serverLevel();
                    com.foglurker.brain.LurkerDirector.naturalSpawnsPaused = true;
                    level.setDayTime(6000);
                    p.setGameMode(GameType.CREATIVE);
                    int y = 150;
                    BlockPos c = new BlockPos(p.getBlockX(), y, p.getBlockZ());
                    for (BlockPos b : BlockPos.betweenClosed(c.offset(-12, -1, -6), c.offset(12, -1, 12))) {
                        level.setBlockAndUpdate(b, Blocks.GRASS_BLOCK.defaultBlockState());
                    }
                    for (BlockPos b : BlockPos.betweenClosed(c.offset(-12, 0, -6), c.offset(12, 6, 12))) {
                        level.setBlockAndUpdate(b, Blocks.AIR.defaultBlockState());
                    }
                    p.teleportTo(level, c.getX() + 0.5, y, c.getZ() + 0.5 - 4, 0F, 10F); // facing +Z, the row ahead
                    Disguise[] row = {Disguise.VILLAGER, Disguise.VILLAGER, Disguise.WANDERING_TRADER, Disguise.HORSE,
                            Disguise.LLAMA, Disguise.SKELETON, Disguise.CREEPER, Disguise.SPIDER, Disguise.ENDERMAN,
                            Disguise.WITCH, Disguise.PILLAGER};
                    int[] variants = {4, 9, 0, 3, 1, 0, 0, 0, 0, 0, 0};
                    for (int k = 0; k < row.length; k++) {
                        FogLurkerEntity e = ModEntities.FOG_LURKER.get().create(level);
                        e.setNoAi(true);
                        e.setDisguise(row[k]);
                        e.setVariant(variants[k]);
                        double x = c.getX() + 0.5 + (k - row.length / 2) * 2.0;
                        e.moveTo(x, y, c.getZ() + 6.5, 180F, 0F);
                        e.setYHeadRot(180F);
                        e.setYBodyRot(180F);
                        level.addFreshEntity(e);
                    }
                    // A bed behind them, head to the south.
                    showcaseBed = c.offset(0, 0, -9);
                    var bed = Blocks.RED_BED.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.NORTH);
                    level.setBlockAndUpdate(showcaseBed, bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
                            net.minecraft.world.level.block.state.properties.BedPart.HEAD));
                    level.setBlockAndUpdate(showcaseBed.south(), bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
                            net.minecraft.world.level.block.state.properties.BedPart.FOOT));
                    FogLurkerMod.LOGGER.info("Showcase: new shapes placed");
                });
            }
            case 140 -> shot(mc, "foglurker_new_shapes.png");
            case 150 -> server.execute(() -> {
                ServerPlayer p = sp(server);
                p.serverLevel().setDayTime(18000);
                p.setGameMode(GameType.SURVIVAL);
                var data = com.foglurker.world.FogLurkerSavedData.get(server);
                if (data.inGrace()) {
                    data.endGrace();
                }
                p.teleportTo(p.serverLevel(), showcaseBed.getX() + 0.5, showcaseBed.getY(), showcaseBed.getZ() + 0.5, 0F, 0F);
                p.startSleeping(showcaseBed);
            });
            case 175 -> server.execute(() -> FogLurkerMod.LOGGER.info("Showcase: under the bed sent={}",
                    com.foglurker.world.UnderTheBed.show(sp(server))));
            case 178 -> {
                FogLurkerMod.LOGGER.info("Showcase: under the bed showing={} sleeping={}",
                        com.foglurker.client.UnderBed.showing(), mc.player.isSleeping());
                shot(mc, "foglurker_under_bed.png");
            }
            case 200 -> FogLurkerMod.LOGGER.info("Showcase: under the bed after a second showing={}",
                    com.foglurker.client.UnderBed.showing());
            case 210 -> server.execute(() -> sp(server).stopSleeping());
            case 230 -> mc.stop();
            default -> {
            }
        }
    }

    /**
     * The Gemini key never stays in the chat box's history: a line typed as if setting it (a fake key, never run) is
     * gone from the up-arrow list a tick later, while an ordinary command stays.
     */
    private static void keyHistoryScene(Minecraft mc) {
        switch (ticks) {
            case 40 -> {
                mc.gui.getChat().addRecentChat("/foglurker setgeminiapikey AIzaFAKEshowcaseKEY0000000000000000000");
                mc.gui.getChat().addRecentChat("/time query daytime");
            }
            case 45 -> {
                boolean inChat = mc.gui.getChat().getRecentChat().stream().anyMatch(l -> l.contains("setgeminiapikey"));
                boolean kept = mc.gui.getChat().getRecentChat().contains("/time query daytime");
                FogLurkerMod.LOGGER.info("Showcase: key history {} (in chat {}, ordinary command kept {})",
                        !inChat && kept ? "OK" : "FAILED", inChat, kept);
            }
            case 50 -> mc.stop();
            default -> {
            }
        }
    }

    /** The fog-surge jumpscare: clear day, then the fog rushes in for no reason, then lifts. */
    private static void surgeScene(Minecraft mc, MinecraftServer server) {
        switch (ticks) {
            case 40 -> {
                mc.options.hideGui = true;
                server.execute(() -> {
                    ServerPlayer p = sp(server);
                    p.serverLevel().setDayTime(6000);
                    p.setGameMode(GameType.SURVIVAL);
                });
            }
            case 100 -> shot(mc, "foglurker_surge_before.png");
            case 110 -> server.execute(() -> FogLurkerMod.LOGGER.info("Showcase: surge sent={}",
                    com.foglurker.brain.Hauntings.fogSurge(sp(server), net.minecraft.util.RandomSource.create(7))));
            case 120, 135, 150, 200, 260, 330, 398 -> FogLurkerMod.LOGGER.info("Showcase: surge tick {} fog={} surgeLeft={}", ticks,
                    ClientEffects.currentFog(), ClientEffects.surgeTicksLeft());
            case 140 -> shot(mc, "foglurker_surge_in.png");
            case 400 -> shot(mc, "foglurker_surge_after.png");
            case 420 -> mc.stop();
            default -> {
            }
        }
    }

    private static void scaresScene(Minecraft mc, MinecraftServer server) {
        switch (ticks) {
            case 40 -> {
                mc.options.hideGui = true;
                server.execute(() -> {
                    ServerPlayer p = sp(server);
                    ServerLevel level = p.serverLevel();
                    level.setDayTime(6000);
                    p.setGameMode(GameType.CREATIVE);
                    stageX = p.getBlockX();
                    stageZ = p.getBlockZ();
                    stageY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, stageX, stageZ);
                    for (int x = stageX - 24; x <= stageX + 24; x++) {
                        for (int z = stageZ - 24; z <= stageZ + 24; z++) {
                            level.setBlock(new BlockPos(x, stageY - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                            for (int dy = 0; dy < 10; dy++) {
                                level.setBlock(new BlockPos(x, stageY + dy, z), Blocks.AIR.defaultBlockState(), 2);
                            }
                        }
                    }
                    p.teleportTo(level, stageX + 0.5, stageY, stageZ + 0.5, 0F, 5F);
                });
            }
            case 140 -> FogLurkerMod.LOGGER.info("Showcase: apparition spawned={} count={}", com.foglurker.client.Apparitions.spawn(),
                    com.foglurker.client.Apparitions.count());
            case 146 -> shot(mc, "foglurker_apparition_edge.png");
            case 150 -> {
                net.minecraft.world.phys.Vec3 at = com.foglurker.client.Apparitions.firstPos();
                FogLurkerMod.LOGGER.info("Showcase: apparition at {} still there before looking: {}", at, com.foglurker.client.Apparitions.count());
                if (at != null) {
                    float yaw = (float) Math.toDegrees(Math.atan2(at.z - mc.player.getZ(), at.x - mc.player.getX())) - 90F;
                    server.execute(() -> sp(server).teleportTo(sp(server).serverLevel(), stageX + 0.5, stageY, stageZ + 0.5, yaw, 5F));
                }
            }
            case 170 -> {
                FogLurkerMod.LOGGER.info("Showcase: apparitions left after looking straight at it: {}", com.foglurker.client.Apparitions.count());
                shot(mc, "foglurker_apparition_gone.png");
            }
            case 180 -> server.execute(() -> {
                ServerPlayer p = sp(server);
                ServerLevel level = p.serverLevel();
                p.teleportTo(level, stageX + 0.5, stageY, stageZ + 0.5, 0F, 8F);
                sinker = ModEntities.FOG_LURKER.get().create(level);
                sinker.moveTo(stageX + 0.5, stageY, stageZ + 6.5, 180F, 0F);
                sinker.setYHeadRot(180F);
                sinker.setYBodyRot(180F);
                sinker.setNoAi(true);
                level.addFreshEntity(sinker);
                // Lying in a patch of tall grass and ferns, like a dog.
                for (int x = -2; x <= 2; x++) {
                    for (int z = 4; z <= 8; z++) {
                        if (x == 0 && z == 6) {
                            continue;
                        }
                        var plant = (x + z) % 3 == 0 ? Blocks.LARGE_FERN.defaultBlockState() : Blocks.TALL_GRASS.defaultBlockState();
                        net.minecraft.world.level.block.DoublePlantBlock.placeAt(level, plant, new BlockPos(stageX + x, stageY, stageZ + z), 2);
                    }
                }
                sinker.setLying(true);
            });
            case 260 -> {
                mc.options.hideGui = true;
                shot(mc, "foglurker_lying.png");
            }
            case 270 -> server.execute(() -> {
                ServerPlayer p = sp(server);
                p.teleportTo(p.serverLevel(), stageX + 3.5, stageY, stageZ + 3.0, 45F, 22F);
            });
            case 300 -> shot(mc, "foglurker_lying_close.png");
            case 310 -> server.execute(() -> {
                ServerPlayer p = sp(server);
                p.teleportTo(p.serverLevel(), stageX + 0.5, stageY, stageZ + 0.5, 0F, 8F);
            });
            case 395 -> server.execute(() -> {
                ServerPlayer p = sp(server);
                ServerLevel level = p.serverLevel();
                for (int x = -2; x <= 2; x++) {
                    for (int z = 4; z <= 8; z++) {
                        level.setBlock(new BlockPos(stageX + x, stageY, stageZ + z), Blocks.AIR.defaultBlockState(), 2);
                        level.setBlock(new BlockPos(stageX + x, stageY + 1, stageZ + z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                p.teleportTo(level, stageX + 2.5, stageY, stageZ + 3.5, 35F, 25F);
            });
            case 410 -> shot(mc, "foglurker_lying_open.png");
            case 412 -> server.execute(() -> {
                ServerPlayer p = sp(server);
                p.teleportTo(p.serverLevel(), stageX + 3.5, stageY, stageZ + 6.5, 90F, 15F);
            });
            case 422 -> shot(mc, "foglurker_lying_side.png");
            case 425 -> mc.options.hideGui = false;
            case 430 -> shot(mc, "foglurker_vignette.png");
            case 432 -> mc.options.hideGui = true;
            case 434 -> server.execute(() -> {
                ServerPlayer p = sp(server);
                ServerLevel level = p.serverLevel();
                sinker.setLying(false);
                for (int x = -2; x <= 2; x++) {
                    for (int z = 4; z <= 8; z++) {
                        level.setBlock(new BlockPos(stageX + x, stageY, stageZ + z), Blocks.AIR.defaultBlockState(), 2);
                        level.setBlock(new BlockPos(stageX + x, stageY + 1, stageZ + z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                p.teleportTo(level, stageX + 0.5, stageY, stageZ + 2.0, 0F, 22F);
            });
            case 444 -> shot(mc, "foglurker_standing.png");
            case 446 -> server.execute(() -> sinker.beginFlop());
            case 456 -> shot(mc, "foglurker_flop.png");
            case 480 -> mc.stop();
            default -> {
            }
        }
        if (ticks > 190 && ticks < 435) {
            ClientEffects.setFogTarget(0.6F);
        }
    }

    // ------------------------------------------------------------------ snowy mountain scene

    private static void snowScene(Minecraft mc, MinecraftServer server) {
        switch (ticks) {
            case 40 -> {
                mc.options.hideGui = true;
                server.execute(() -> goToSnowyMountain(server));
            }
            case 200 -> server.execute(() -> placeSnowScene(server));
            case 300 -> shot(mc, "foglurker_snow_close.png");
            case 310 -> server.execute(() -> {
                ServerPlayer p = sp(server);
                p.teleportTo(p.serverLevel(), stageX - dirX * 8 + 0.5, stageY + 5, stageZ - dirZ * 8 + 0.5, sceneYaw, 22F);
            });
            case 390 -> shot(mc, "foglurker_snow_wide.png");
            case 640 -> shot(mc, "foglurker_snow_fog.png");
            case 660 -> mc.stop();
            default -> {
            }
        }
        if (ticks > 400 && ticks < 650) {
            ClientEffects.setFogTarget(0.55F);
        }
    }

    private static void goToSnowyMountain(MinecraftServer server) {
        ServerPlayer p = sp(server);
        ServerLevel level = p.serverLevel();
        level.setDayTime(5000);
        level.setWeatherParameters(6000, 0, false, false);
        p.setGameMode(GameType.CREATIVE);
        var found = level.findClosestBiome3d(b -> b.is(net.minecraft.world.level.biome.Biomes.SNOWY_SLOPES)
                        || b.is(net.minecraft.world.level.biome.Biomes.FROZEN_PEAKS) || b.is(net.minecraft.world.level.biome.Biomes.JAGGED_PEAKS),
                p.blockPosition(), 6400, 32, 64);
        if (found == null) {
            FogLurkerMod.LOGGER.warn("Showcase: no snowy mountain found");
            return;
        }
        BlockPos at = found.getFirst();
        FogLurkerMod.LOGGER.info("Showcase: snowy mountain ({}) at {}", found.getSecond().unwrapKey().map(Object::toString).orElse("?"), at);
        level.getChunk(at.getX() >> 4, at.getZ() >> 4); // generate it now
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        p.teleportTo(level, at.getX() + 0.5, y + 1, at.getZ() + 0.5, 0F, 10F);
    }

    private static int dirX;
    private static int dirZ;
    private static float sceneYaw;

    private static void placeSnowScene(MinecraftServer server) {
        ServerPlayer p = sp(server);
        ServerLevel level = p.serverLevel();
        int px = p.getBlockX();
        int pz = p.getBlockZ();
        // Face uphill so the peak is the backdrop.
        int[][] dirs = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
        float[] yaws = {0F, 90F, 180F, -90F};
        int best = 0;
        int bestH = Integer.MIN_VALUE;
        for (int i = 0; i < 4; i++) {
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px + dirs[i][0] * 24, pz + dirs[i][1] * 24);
            if (h > bestH) {
                bestH = h;
                best = i;
            }
        }
        dirX = dirs[best][0];
        dirZ = dirs[best][1];
        sceneYaw = yaws[best];
        stageX = px;
        stageZ = pz;
        stageY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
        // Carve a flat snowy ledge 11 wide and 12 deep into the slope.
        var snow = net.minecraft.world.level.block.Blocks.SNOW_BLOCK.defaultBlockState();
        var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (int f = -10; f <= 11; f++) {
            for (int side = -5; side <= 5; side++) {
                int x = px + dirX * f - dirZ * side;
                int z = pz + dirZ * f + dirX * side;
                for (int dy = -3; dy < 0; dy++) {
                    level.setBlock(new BlockPos(x, stageY + dy, z), snow, 2);
                }
                for (int dy = 0; dy < 12; dy++) {
                    level.setBlock(new BlockPos(x, stageY + dy, z), air, 2);
                }
            }
        }
        p.teleportTo(level, px + 0.5, stageY, pz + 0.5, sceneYaw, 10F);
        // Hiding form (a wolf, at home on a snowy mountain) and its real form, side by side.
        spawnAt(level, px + dirX * 5 + dirZ * 2, pz + dirZ * 5 - dirX * 2, Disguise.WOLF, false, sceneYaw + 180F);
        spawnAt(level, px + dirX * 5 - dirZ * 2, pz + dirZ * 5 + dirX * 2, Disguise.CRAWLER, true, sceneYaw + 180F);
    }

    /** The portrait scene leaves the player high in the sky: clear what it built and stand on the ground again. */
    private static void backToGround(MinecraftServer server) {
        ServerPlayer p = sp(server);
        ServerLevel level = p.serverLevel();
        int x = p.getBlockX();
        int z = p.getBlockZ();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                for (int y = 280; y < 320; y++) {
                    level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + dx, y, z + dz),
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                }
            }
        }
        p.getAbilities().flying = false;
        p.onUpdateAbilities();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        p.teleportTo(level, x + 0.5, ground, z + 0.5, p.getYRot(), 0F);
    }

    /** Someone who has had sleep paralysis goes to bed at night: the faint picture, and the always-there fog. */
    private static void sleepScene(Minecraft mc, MinecraftServer server) {
        switch (ticks) {
            case 10 -> server.execute(() -> {
                backToGround(server);
                sp(server).serverLevel().setDayTime(6000);
            });
            case 50 -> shot(mc, "foglurker_ambient_fog.png");
            case 55 -> server.execute(() -> sp(server).serverLevel().setDayTime(18000));
            case 70 -> server.execute(() -> {
                ServerPlayer p = sp(server);
                ServerLevel level = p.serverLevel();
                p.setGameMode(GameType.SURVIVAL);
                var prof = com.foglurker.world.FogLurkerSavedData.get(server).profile(p.getUUID());
                prof.sleepParalysisSeen = true;
                prof.sleepParalysisForced = false;
                net.minecraft.core.BlockPos foot = p.blockPosition().north(2);
                net.minecraft.core.BlockPos head = foot.north();
                level.setBlockAndUpdate(foot, net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.NORTH));
                level.setBlockAndUpdate(head, net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.NORTH)
                        .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
                p.startSleepInBed(head).ifLeft(problem -> FogLurkerMod.LOGGER.info("Showcase: could not sleep: {}", problem));
            });
            case 150 -> shot(mc, "foglurker_sleep_vision.png");
            case 170 -> mc.stop();
            default -> {
            }
        }
    }

    /** The crawler's face against the open sky, for the faint picture shown in bed after sleep paralysis. */
    private static void portraitScene(Minecraft mc, MinecraftServer server) {
        switch (ticks) {
            case 40 -> {
                mc.options.hideGui = true;
                mc.options.fov().set(50);
                server.execute(() -> {
                    ServerPlayer p = sp(server);
                    ServerLevel level = p.serverLevel();
                    level.setDayTime(6000);
                    p.setGameMode(GameType.CREATIVE);
                    int x = p.getBlockX();
                    int z = p.getBlockZ();
                    int y = 300; // above everything: nothing but sky behind it
                    var barrier = net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState();
                    level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, y - 1, z), barrier);
                    level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, y, z - 3), barrier);
                    FogLurkerEntity l = ModEntities.FOG_LURKER.get().create(level);
                    l.moveTo(x + 0.5, y + 0.75, z - 3.2, 0F, 0F); // facing south, toward the camera
                    l.setYHeadRot(0F);
                    l.setYBodyRot(0F);
                    l.setNoAi(true);
                    l.setDisguise(Disguise.CRAWLER);
                    l.setAggressive(true);
                    level.addFreshEntity(l);
                    p.getAbilities().flying = true;
                    p.onUpdateAbilities();
                    p.teleportTo(level, x + 0.5, y, z + 0.9, 180F, 2F);
                });
            }
            case 140 -> shot(mc, "foglurker_portrait.png");
            case 160 -> mc.stop();
            default -> {
            }
        }
    }

    private static void spawnAt(ServerLevel level, int x, int z, Disguise d, boolean aggressive, float yaw) {
        int y = stageY;
        FogLurkerEntity l = ModEntities.FOG_LURKER.get().create(level);
        l.moveTo(x + 0.5, y, z + 0.5, yaw, 0F);
        l.setYHeadRot(yaw);
        l.setYBodyRot(yaw);
        l.setNoAi(true);
        l.setDisguise(d);
        l.setAggressive(aggressive);
        level.addFreshEntity(l);
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
                Disguise.CHICKEN, Disguise.ZOMBIE, Disguise.WOLF, Disguise.CRAWLER};
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

    private static final int CRAWLER_INDEX = 8;

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
