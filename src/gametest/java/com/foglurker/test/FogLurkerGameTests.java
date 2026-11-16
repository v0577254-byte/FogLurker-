package com.foglurker.test;

import com.foglurker.FogLurkerMod;
import com.foglurker.brain.LurkerBrain;
import com.foglurker.brain.LurkerDirector;
import com.foglurker.chat.ChatStyle;
import com.foglurker.chat.MimicChat;
import com.foglurker.config.LurkerDifficulty;
import com.foglurker.entity.Disguise;
import com.foglurker.entity.FogLurkerEntity;
import com.foglurker.security.ApiKeyStore;
import com.foglurker.security.SecureStorage;
import com.foglurker.voice.SnippetStore;
import com.foglurker.voice.VoiceConsent;
import com.foglurker.world.FogLurkerSavedData;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Run with: ./gradlew runGameTestServer */
@GameTestHolder(FogLurkerMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FogLurkerGameTests {
    private FogLurkerGameTests() {
    }

    /** Logs the full stack trace of anything a test throws, then rethrows it so the test still fails. */
    private static void logged(GameTestHelper h, ThrowingRunnable body) {
        try {
            body.run();
        } catch (GameTestAssertException e) {
            throw e;
        } catch (Throwable t) {
            FogLurkerMod.LOGGER.error("Fog Lurker game test crashed", t);
            throw new GameTestAssertException(t.toString());
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    private static int playerCounter;

    /**
     * A logged-in survival player with a real (discarding) network channel. Forge 1.20.1's own
     * GameTestHelper.makeMockServerPlayerInLevel() crashes because its connection has no channel.
     */
    private static ServerPlayer player(GameTestHelper h, double x, double z) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "fogtest" + (++playerCounter)));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        channel.pipeline().addFirst("discard", new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                ReferenceCountUtil.release(msg);
                promise.setSuccess();
            }
        });
        channel.attr(Connection.ATTRIBUTE_PROTOCOL).set(ConnectionProtocol.PLAY);
        level.getServer().getPlayerList().placeNewPlayer(connection, p);
        p.setGameMode(GameType.SURVIVAL); // game-test worlds default to creative
        p.teleportTo(level, h.absoluteVec(new Vec3(x, 0, z)).x, h.absoluteVec(new Vec3(x, 0, z)).y,
                h.absoluteVec(new Vec3(x, 0, z)).z, 0F, 0F);
        return p;
    }

    /**
     * The test world has real terrain and pits dug for other tests. Behaviour tests flatten a field
     * around the player so they test the creature, not the scenery.
     */
    private static void flatten(GameTestHelper h, int radius) {
        ServerLevel level = h.getLevel();
        net.minecraft.core.BlockPos c = h.absolutePos(net.minecraft.core.BlockPos.ZERO);
        var grass = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
        var dirt = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
        var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                level.setBlock(c.offset(x, -2, z), dirt, 2);
                level.setBlock(c.offset(x, -1, z), grass, 2);
                for (int y = 0; y < 20; y++) {
                    level.setBlock(c.offset(x, y, z), air, 2);
                }
            }
        }
    }

    /**
     * The test world is persistent and its clock keeps running, so real zombies and creepers show
     * up at night and kill the test players. Make it noon, stop mob spawning and clear monsters.
     */
    private static void calm(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.setDayTime(6000);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, server);
        for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
            if (e instanceof net.minecraft.world.entity.monster.Enemy && !(e instanceof FogLurkerEntity)) {
                e.discard();
            }
        }
    }

    /** Leftover mock players from earlier tests would otherwise be picked as "friends" to copy. */
    private static void kickOtherPlayers(MinecraftServer server, ServerPlayer... keep) {
        calm(server);
        List<ServerPlayer> all = new ArrayList<>(server.getPlayerList().getPlayers());
        for (ServerPlayer p : all) {
            if (Arrays.stream(keep).noneMatch(k -> k == p)) {
                server.getPlayerList().remove(p);
            }
        }
    }

    @GameTest(template = "empty", batch = "commands")
    public static void commandsAndModes(GameTestHelper h) {
        logged(h, () -> commandsAndModesImpl(h));
    }

    private static void commandsAndModesImpl(GameTestHelper h) throws Exception {
        MinecraftServer server = h.getLevel().getServer();
        CommandDispatcher<CommandSourceStack> d = server.getCommands().getDispatcher();
        for (String root : List.of("foglurker", "flurk", "fglurk")) {
            check(d.getRoot().getChild(root) != null, "/" + root + " is not registered");
        }
        CommandSourceStack console = server.createCommandSourceStack().withSuppressedOutput();
        FogLurkerSavedData data = FogLurkerSavedData.get(server);
        LurkerDifficulty before = data.difficulty();

        d.execute("foglurker difficulty deathwish", console);
        check(data.difficulty() == LurkerDifficulty.DEATHWISH, "difficulty command did not set deathwish");
        d.execute("foglurker difficulty very easy", console);
        check(data.difficulty() == LurkerDifficulty.VERY_EASY, "'very easy' was not understood");
        d.execute("foglurker difficulty", console);
        d.execute("foglurker gracetimer", console);
        d.execute("foglurker spawntimer", console);

        check(LurkerDifficulty.VERY_EASY.graceTicks() == 45 * 1200, "very easy grace must be 45 min");
        check(LurkerDifficulty.EASY.graceTicks() == 30 * 1200, "easy grace must be 30 min");
        check(LurkerDifficulty.MEDIUM.graceTicks() == 24000, "medium grace must be 20 min / one Minecraft day");
        check(LurkerDifficulty.HARD.graceTicks() == 10 * 1200, "hard grace must be 10 min");
        check(LurkerDifficulty.VERY_EASY.damagePerHit() == 2F, "very easy must deal 1 heart");
        check(LurkerDifficulty.EASY.damagePerHit() == 6F, "easy must deal 3 hearts");
        check(LurkerDifficulty.MEDIUM.damagePerHit() == 10F, "medium must deal 5 hearts");
        check(LurkerDifficulty.HARD.damagePerHit() == 12F, "hard must deal 6 hearts");
        check(LurkerDifficulty.DEATHWISH.isOneShot(), "deathwish must one-shot");
        check(Math.abs(LurkerDifficulty.MEDIUM.attackChance() - 1 / 3D) < 1e-9, "medium attacks 1/3 spawns");
        check(LurkerDifficulty.DEATHWISH.attackChance() == 0.75, "deathwish attacks 3/4 spawns");
        check(LurkerDifficulty.DEATHWISH.speedMultiplier() == 1.5, "deathwish is 50% faster");
        int[] cooldowns = {60, 50, 40, 35, 30}; // 3 s, 2.5 s, 2 s, 1.75 s, 1.5 s
        for (LurkerDifficulty m : LurkerDifficulty.values()) {
            check(m.attackCooldownTicks() == cooldowns[m.ordinal()], m + " has the wrong time between hits");
        }
        check(LurkerDifficulty.byId("medium") == LurkerDifficulty.MEDIUM, "byId");
        data.setDifficulty(before);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "disguises", timeoutTicks = 600)
    public static void everyDisguiseSpawnsAndThinks(GameTestHelper h) {
        logged(h, () -> everyDisguiseSpawnsAndThinksImpl(h));
    }

    private static void everyDisguiseSpawnsAndThinksImpl(GameTestHelper h) throws Exception {
        ServerPlayer p = player(h, 1.5, 1.5);
        kickOtherPlayers(h.getLevel().getServer(), p);
        // Give the server a few seconds to load the chunks around the new player, as in real play.
        h.runAfterDelay(100, () -> logged(h, () -> spawnAll(h, p)));
    }

    private static void spawnAll(GameTestHelper h, ServerPlayer p) {
        List<FogLurkerEntity> spawned = new ArrayList<>();
        for (Disguise d : Disguise.values()) {
            FogLurkerEntity l = LurkerDirector.spawn(p, d, Boolean.FALSE);
            check(l != null && l.brain() != null, "could not spawn as " + d);
            check(l.getDisguise() == d, "expected disguise " + d + " but got " + l.getDisguise());
            check(Math.abs(l.getBbHeight() - d.dimensions().height) < 0.01F, d + " has the wrong hitbox height");
            spawned.add(l);
        }
        FogLurkerEntity mimic = spawned.get(Disguise.PLAYER.ordinal());
        check(mimic.brain().encounter().mode() == LurkerBrain.Mode.DOPPELGANGER, "alone, a player disguise copies the target");
        check(p.getUUID().equals(mimic.getMimicUuid()), "doppelganger must wear the target's own skin");
        // Let them all think for 10 seconds; any exception in their AI fails the run.
        h.runAfterDelay(200, () -> {
            for (FogLurkerEntity l : spawned) {
                if (!l.isRemoved()) {
                    l.brain().vanish(false);
                }
            }
            // Its real shape takes a second to sink into the ground; everything else is gone at once.
            h.runAfterDelay(40, () -> {
                check(LurkerDirector.active().isEmpty(), "vanished creatures are still tracked");
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", batch = "mimic", timeoutTicks = 1400)
    public static void mimicWalksUpGreetsAndLures(GameTestHelper h) {
        logged(h, () -> mimicWalksUpGreetsAndLuresImpl(h));
    }

    private static void mimicWalksUpGreetsAndLuresImpl(GameTestHelper h) throws Exception {
        flatten(h, 44);
        ServerPlayer target = player(h, 1.5, 1.5);
        ServerPlayer friend = player(h, 121.5, 1.5); // "went to get wood"
        kickOtherPlayers(h.getLevel().getServer(), target, friend);
        h.runAfterDelay(100, () -> logged(h, () -> startMimic(h, target, friend)));
    }

    private static void startMimic(GameTestHelper h, ServerPlayer target, ServerPlayer friend) {
        FogLurkerEntity l = LurkerDirector.spawn(target, Disguise.PLAYER, Boolean.FALSE);
        check(l != null && l.brain() != null, "mimic did not spawn");
        check(l.brain().encounter().mode() == LurkerBrain.Mode.MIMIC, "with a friend online it must mimic the friend");
        check(friend.getUUID().equals(l.getMimicUuid()), "it must wear the friend's skin");
        check(friend.getGameProfile().getName().equals(l.getMimicName()), "it must use the friend's name");
        h.succeedWhen(() -> {
            check(!l.isRemoved(), "the mimic vanished before luring (" + l.brain().describe() + ")");
            check(l.brain().phase().ordinal() >= LurkerBrain.Phase.LURE.ordinal(),
                    "still " + l.brain().describe() + ", " + l.distanceTo(target) + " blocks from the target at "
                            + target.blockPosition().toShortString());
            l.brain().vanish(false);
        });
    }

    @GameTest(template = "empty", batch = "damage", timeoutTicks = 200)
    public static void damageMatchesEachMode(GameTestHelper h) {
        logged(h, () -> damageMatchesEachModeImpl(h));
    }

    private static void damageMatchesEachModeImpl(GameTestHelper h) throws Exception {
        MinecraftServer server = h.getLevel().getServer();
        List<ServerPlayer> players = new ArrayList<>();
        for (int i = 0; i < LurkerDifficulty.values().length; i++) {
            ServerPlayer p = player(h, 1.5 + i, 1.5);
            // Armour must not matter: "5 hearts" means 5 hearts.
            p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            players.add(p);
        }
        kickOtherPlayers(server, players.toArray(new ServerPlayer[0]));
        // New players are protected for 3 seconds after joining.
        h.runAfterDelay(80, () -> logged(h, () -> {
            FogLurkerSavedData data = FogLurkerSavedData.get(server);
            LurkerDifficulty before = data.difficulty();
            int i = 0;
            for (LurkerDifficulty mode : LurkerDifficulty.values()) {
                data.setDifficulty(mode);
                ServerPlayer p = players.get(i++);
                p.setHealth(20F);
                FogLurkerEntity l = LurkerDirector.spawn(p, Disguise.COW, Boolean.TRUE);
                check(l != null, "no spawn for damage test");
                check(l.difficulty() == mode, "creature did not take the world's mode");
                l.doHurtTarget(p);
                if (mode.isOneShot()) {
                    check(p.isDeadOrDying(), "deathwish must one-shot (health " + p.getHealth() + ")");
                } else {
                    float expected = 20F - mode.damagePerHit();
                    check(Math.abs(p.getHealth() - expected) < 0.01F,
                            mode + ": expected " + expected + " health, got " + p.getHealth());
                }
                l.brain().vanish(false);
            }
            data.setDifficulty(before);
            h.succeed();
        }));
    }

    @GameTest(template = "empty", batch = "attack", timeoutTicks = 1200)
    public static void stalksTransformsAndAttacks(GameTestHelper h) {
        logged(h, () -> stalksTransformsAndAttacksImpl(h));
    }

    private static void stalksTransformsAndAttacksImpl(GameTestHelper h) throws Exception {
        flatten(h, 36);
        MinecraftServer server = h.getLevel().getServer();
        server.setDifficulty(Difficulty.NORMAL, true);
        ServerPlayer p = player(h, 1.5, 1.5);
        kickOtherPlayers(server, p);
        FogLurkerSavedData data = FogLurkerSavedData.get(server);
        LurkerDifficulty before = data.difficulty();
        h.runAfterDelay(100, () -> logged(h, () -> {
            data.setDifficulty(LurkerDifficulty.MEDIUM);
            p.setHealth(20F);
            FogLurkerEntity l = LurkerDirector.spawn(p, Disguise.ZOMBIE, Boolean.TRUE);
            data.setDifficulty(before);
            check(l != null, "zombie disguise did not spawn");
            check(l.brain().encounter().mode() == LurkerBrain.Mode.ZOMBIE, "wrong mode");
            h.succeedWhen(() -> {
                check(!l.isRemoved(), "it left without attacking (phase " + l.brain().phase() + ")");
                check(l.getDisguise() == Disguise.CRAWLER, "it has not become the crawler yet (phase " + l.brain().phase()
                        + ", " + l.distanceTo(p) + " blocks away)");
                check(p.getHealth() <= 10.01F, "no 5-heart hit landed yet (health " + p.getHealth() + ")");
                l.brain().vanish(false);
            });
        }));
    }

    @GameTest(template = "empty", batch = "cooldown", timeoutTicks = 200)
    public static void hitsAreSpacedOut(GameTestHelper h) {
        logged(h, () -> hitsAreSpacedOutImpl(h));
    }

    private static void hitsAreSpacedOutImpl(GameTestHelper h) throws Exception {
        MinecraftServer server = h.getLevel().getServer();
        FogLurkerSavedData data = FogLurkerSavedData.get(server);
        LurkerDifficulty before = data.difficulty();
        ServerPlayer p = player(h, 1.5, 1.5);
        kickOtherPlayers(server, p);
        h.runAfterDelay(80, () -> logged(h, () -> {
            data.setDifficulty(LurkerDifficulty.VERY_EASY); // 3 seconds between hits
            FogLurkerEntity l = LurkerDirector.spawn(p, Disguise.COW, Boolean.TRUE);
            data.setDifficulty(before);
            check(l != null, "no spawn");
            p.setHealth(20F);
            check(l.doHurtTarget(p), "first hit should land");
            p.invulnerableTime = 0;
            check(!l.doHurtTarget(p), "a second hit straight away must be blocked");
            check(Math.abs(p.getHealth() - 18F) < 0.01F, "only one heart should be gone, health " + p.getHealth());
            h.runAfterDelay(30, () -> {
                p.invulnerableTime = 0;
                check(!l.doHurtTarget(p), "1.5 s later it must still be waiting (very easy = 3 s)");
            });
            h.runAfterDelay(65, () -> {
                p.invulnerableTime = 0;
                check(l.doHurtTarget(p), "after 3 s it may hit again");
                l.brain().vanish(false);
                h.succeed();
            });
        }));
    }

    @GameTest(template = "empty", batch = "water", timeoutTicks = 300)
    public static void waterIsItsWeakness(GameTestHelper h) {
        logged(h, () -> waterIsItsWeaknessImpl(h));
    }

    private static void waterIsItsWeaknessImpl(GameTestHelper h) throws Exception {
        ServerLevel level = h.getLevel();
        ServerPlayer p = player(h, 1.5, 1.5);
        kickOtherPlayers(level.getServer(), p);
        // A pool 6 blocks away.
        net.minecraft.core.BlockPos pool = h.absolutePos(new net.minecraft.core.BlockPos(7, -1, 1));
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlockAndUpdate(pool.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(pool.offset(dx, 0, dz), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
                level.setBlockAndUpdate(pool.offset(dx, 1, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        }
        // It never chooses a spot on or next to water.
        check(com.foglurker.brain.SpotFinder.ground(level, pool.getX(), pool.getZ(), pool.getY(), true) == null,
                "a water column was accepted as a spawn spot");
        check(!com.foglurker.brain.SpotFinder.isDry(level, pool.above()), "water was reported as dry");

        h.runAfterDelay(80, () -> logged(h, () -> {
            FogLurkerEntity l = LurkerDirector.spawn(p, Disguise.CRAWLER, Boolean.FALSE);
            check(l != null, "no spawn");
            check(l.getPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.WATER) < 0,
                    "the crawler must refuse to path through water");
            // The debug command runs end to end next to real water (results go to the server log).
            level.getServer().getCommands().getDispatcher().execute("foglurker debugeverything",
                    p.createCommandSourceStack().withPermission(4));
            l.teleportTo(pool.getX() + 0.5, pool.getY(), pool.getZ() + 0.5);
            // Thrown in the water, the attacking form is driven away within a few seconds.
            h.succeedWhen(() -> check(l.isRemoved(), "the crawler is still in the water ("
                    + (l.isInWater() ? "in water" : "not in water") + ", health " + l.getHealth() + ")"));
        }));
    }

    @GameTest(template = "empty", batch = "commands")
    public static void spawnRollsEveryFiveMinutes(GameTestHelper h) {
        logged(h, () -> {
            check(com.foglurker.config.FogLurkerConfig.COMMON.spawnRollMinutes.get() == 5, "rolls must be 5 minutes apart");
            check(LurkerDirector.rollInterval() == 5 * 60 * 20, "roll interval in ticks");
            FogLurkerSavedData d = new FogLurkerSavedData();
            check(!d.rollSpawn(0.9, 0.5), "a 0.9 roll on a 50/50 must not spawn");
            check(d.nextIsGuaranteed(), "after a miss the next roll is guaranteed");
            check(d.rollSpawn(0.99, 0.5), "a guaranteed roll spawns whatever the dice say");
            check(!d.nextIsGuaranteed(), "after it spawned it goes back to 50/50");
            check(d.rollSpawn(0.1, 0.5), "a 0.1 roll on a 50/50 spawns");
            check(!d.rollSpawn(0.7, 0.5), "and the one after that is a normal 50/50 again");
            check(d.rollSpawn(0.7, 0.5), "...which, having missed, guarantees the next");
            h.succeed();
        });
    }

    private static FogLurkerEntity hunter(GameTestHelper h, ServerPlayer p, double rx, double rz) {
        FogLurkerEntity l = LurkerDirector.spawn(p, Disguise.CRAWLER, Boolean.TRUE);
        check(l != null, "no spawn");
        Vec3 at = h.absoluteVec(new Vec3(rx, 0, rz));
        l.teleportTo(at.x, at.y, at.z);
        l.brain().huntNow();
        return l;
    }

    private static void box(GameTestHelper h, net.minecraft.world.level.block.state.BlockState wall, int half, int height) {
        ServerLevel level = h.getLevel();
        net.minecraft.core.BlockPos c = h.absolutePos(new net.minecraft.core.BlockPos(1, 0, 1));
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                for (int y = 0; y <= height; y++) {
                    boolean shell = Math.abs(x) == half || Math.abs(z) == half || y == height;
                    if (shell) {
                        level.setBlockAndUpdate(c.offset(x, y, z), wall);
                    }
                }
            }
        }
    }

    @GameTest(template = "empty", batch = "climb", timeoutTicks = 900)
    public static void climbsYourTower(GameTestHelper h) {
        logged(h, () -> {
            flatten(h, 24);
            ServerLevel level = h.getLevel();
            // A 6 block deepslate tower (it cannot dig through it) with the player on top.
            for (int y = 0; y < 6; y++) {
                level.setBlockAndUpdate(h.absolutePos(new net.minecraft.core.BlockPos(1, y, 1)),
                        net.minecraft.world.level.block.Blocks.DEEPSLATE.defaultBlockState());
            }
            ServerPlayer p = player(h, 1.5, 1.5);
            kickOtherPlayers(level.getServer(), p);
            Vec3 top = h.absoluteVec(new Vec3(1.5, 6, 1.5));
            p.teleportTo(level, top.x, top.y, top.z, 0F, 0F);
            h.runAfterDelay(80, () -> logged(h, () -> {
                p.setHealth(20F);
                FogLurkerEntity l = hunter(h, p, 6.5, 1.5);
                h.succeedWhen(() -> {
                    check(!l.isRemoved(), "it gave up (" + l.brain().describe() + ")");
                    check(p.getHealth() < 20F, "it has not reached the top of the tower yet ("
                            + l.brain().describe() + ", y " + l.getY() + " vs " + p.getY() + ")");
                    l.brain().vanish(false);
                });
            }));
        });
    }

    @GameTest(template = "empty", batch = "dig", timeoutTicks = 1600)
    public static void breaksIntoYourShelter(GameTestHelper h) {
        logged(h, () -> {
            flatten(h, 24);
            h.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(true, h.getLevel().getServer());
            box(h, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2, 3);
            ServerPlayer p = player(h, 1.5, 1.5);
            kickOtherPlayers(h.getLevel().getServer(), p);
            h.runAfterDelay(80, () -> logged(h, () -> {
                p.setHealth(20F);
                FogLurkerEntity l = hunter(h, p, 8.5, 1.5);
                h.succeedWhen(() -> {
                    check(!l.isRemoved(), "it gave up (" + l.brain().describe() + ")");
                    check(p.getHealth() < 20F, "it has not got into the stone shelter yet (" + l.brain().describe() + ")");
                    l.brain().vanish(false);
                });
            }));
        });
    }

    @GameTest(template = "empty", batch = "deepslate", timeoutTicks = 700)
    public static void deepslateShelterHolds(GameTestHelper h) {
        logged(h, () -> {
            flatten(h, 24);
            h.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(true, h.getLevel().getServer());
            var deepslate = net.minecraft.world.level.block.Blocks.DEEPSLATE.defaultBlockState();
            box(h, deepslate, 2, 3);
            ServerPlayer p = player(h, 1.5, 1.5);
            kickOtherPlayers(h.getLevel().getServer(), p);
            h.runAfterDelay(80, () -> logged(h, () -> {
                p.setHealth(20F);
                FogLurkerEntity l = hunter(h, p, 8.5, 1.5);
                check(!l.brain().canBreak(h.absolutePos(new net.minecraft.core.BlockPos(3, 1, 1))), "deepslate must be protected");
                h.runAfterDelay(500, () -> logged(h, () -> {
                    net.minecraft.core.BlockPos c = h.absolutePos(new net.minecraft.core.BlockPos(1, 0, 1));
                    int missing = 0;
                    for (int x = -2; x <= 2; x++) {
                        for (int z = -2; z <= 2; z++) {
                            for (int y = 0; y <= 3; y++) {
                                boolean shell = Math.abs(x) == 2 || Math.abs(z) == 2 || y == 3;
                                if (shell && !h.getLevel().getBlockState(c.offset(x, y, z)).is(net.minecraft.world.level.block.Blocks.DEEPSLATE)) {
                                    missing++;
                                }
                            }
                        }
                    }
                    check(missing == 0, missing + " deepslate block(s) were broken");
                    check(p.getHealth() >= 20F, "it got through deepslate");
                    if (!l.isRemoved()) {
                        l.brain().vanish(false);
                    }
                    h.succeed();
                }));
            }));
        });
    }

    @GameTest(template = "empty", batch = "macarena", timeoutTicks = 400)
    public static void macarena(GameTestHelper h) {
        logged(h, () -> {
            MinecraftServer server = h.getLevel().getServer();
            ServerPlayer p = player(h, 1.5, 1.5);
            kickOtherPlayers(server, p);
            var root = server.getCommands().getDispatcher().getRoot();
            check(root.getChild("foglurker-macarena") != null, "/foglurker-macarena is not registered");
            check(!root.getChild("foglurker-macarena").canUse(p.createCommandSourceStack().withPermission(0)),
                    "/foglurker-macarena must be cheats only");
            int before = h.getLevel().getEntities(com.foglurker.registry.ModEntities.FOG_LURKER.get(), e -> true).size();
            server.getCommands().getDispatcher().execute("foglurker-macarena 1", p.createCommandSourceStack().withPermission(2));
            List<? extends FogLurkerEntity> dancers = h.getLevel().getEntities(com.foglurker.registry.ModEntities.FOG_LURKER.get(),
                    FogLurkerEntity::isDancing);
            check(dancers.size() == 1 && before == 0, "expected exactly one dancer, found " + dancers.size());
            FogLurkerEntity dancer = dancers.get(0);
            check(dancer.isInvulnerable() && dancer.getDisguise() == Disguise.CRAWLER, "dancer should be the harmless pale crawler");
            float yaw = dancer.getYRot();
            h.runAfterDelay(com.foglurker.entity.MacarenaDance.TICKS_PER_COUNT * 15 + 4, () ->
                    check(Math.abs(net.minecraft.util.Mth.wrapDegrees(dancer.getYRot() - yaw + 90)) < 1,
                            "it should turn a quarter on count 16 (yaw " + dancer.getYRot() + ", was " + yaw + ")"));
            h.runAfterDelay(com.foglurker.entity.MacarenaDance.CYCLE_TICKS + 5, () -> {
                check(dancer.isRemoved(), "after one loop it should take a bow and vanish");
                check(LurkerDirector.active().isEmpty(), "a dancer must never count as a hunting creature");
                h.succeed();
            });
        });
    }

    private static net.minecraft.core.BlockPos rel(GameTestHelper h, int x, int y, int z) {
        return h.absolutePos(new net.minecraft.core.BlockPos(x, y, z));
    }

    @GameTest(template = "empty", batch = "ward", timeoutTicks = 1200)
    public static void soulFireKeepsItBack(GameTestHelper h) {
        logged(h, () -> {
            flatten(h, 24);
            ServerLevel level = h.getLevel();
            var torch = rel(h, 2, 0, 1);
            level.setBlockAndUpdate(torch, net.minecraft.world.level.block.Blocks.SOUL_TORCH.defaultBlockState());
            ServerPlayer p = player(h, 1.5, 1.5);
            kickOtherPlayers(level.getServer(), p);
            check(com.foglurker.brain.Wards.isWarded(p), "a soul torch next to you should ward you");
            h.runAfterDelay(80, () -> logged(h, () -> {
                p.setHealth(20F);
                FogLurkerEntity l = hunter(h, p, 10.5, 1.5);
                h.runAfterDelay(240, () -> logged(h, () -> {
                    check(!l.isRemoved(), "it should still be circling (" + l.brain().describe() + ")");
                    check(p.getHealth() >= 20F, "it hit a player standing by soul fire");
                    check(l.distanceTo(p) > 4, "it came inside the soul fire's circle (" + l.distanceTo(p) + " blocks)");
                    // The torch goes out: now nothing protects you.
                    level.setBlockAndUpdate(torch, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    h.succeedWhen(() -> {
                        check(p.getHealth() < 20F, "with the ward gone it should attack (" + (l.isRemoved() ? "gone" : l.brain().describe()) + ")");
                        if (!l.isRemoved()) {
                            l.brain().vanish(true);
                        }
                    });
                }));
            }));
        });
    }

    @GameTest(template = "empty", batch = "bell", timeoutTicks = 400)
    public static void bellDrivesItOff(GameTestHelper h) {
        logged(h, () -> {
            flatten(h, 24);
            ServerPlayer p = player(h, 1.5, 1.5);
            kickOtherPlayers(h.getLevel().getServer(), p);
            h.runAfterDelay(80, () -> logged(h, () -> {
                p.setHealth(20F);
                FogLurkerEntity l = hunter(h, p, 12.5, 1.5);
                int driven = LurkerDirector.onBell(h.getLevel(), p.blockPosition());
                check(driven == 1, "the bell should reach the one creature nearby, reached " + driven);
                check(l.brain().phase() == LurkerBrain.Phase.FLEE, "after the bell it should flee, but it is " + l.brain().phase());
                h.succeedWhen(() -> {
                    check(l.isRemoved(), "it has not left yet (" + l.brain().describe() + ")");
                    check(p.getHealth() >= 20F, "it still hit the player after the bell");
                });
            }));
        });
    }

    @GameTest(template = "empty", batch = "haunt", timeoutTicks = 400)
    public static void hauntings(GameTestHelper h) {
        logged(h, () -> {
            flatten(h, 16);
            ServerLevel level = h.getLevel();
            MinecraftServer server = level.getServer();
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(true, server);
            ServerPlayer p = player(h, 1.5, 1.5);
            kickOtherPlayers(server, p);
            p.teleportTo(level, p.getX(), p.getY(), p.getZ(), 0F, 0F); // facing +Z
            FogLurkerSavedData data = FogLurkerSavedData.get(server);
            RandomSource r = RandomSource.create(42);
            var blocks = net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState();
            var behind = rel(h, 1, 0, -5);
            var soul = rel(h, 3, 0, -9);
            var inFront = rel(h, 1, 0, 7);
            level.setBlockAndUpdate(behind, blocks);
            level.setBlockAndUpdate(soul, net.minecraft.world.level.block.Blocks.SOUL_TORCH.defaultBlockState());
            level.setBlockAndUpdate(inFront, blocks);

            // Torches: only the ordinary one you cannot see goes out.
            check(com.foglurker.brain.Hauntings.snuffTorch(p, r), "the torch behind you should go out");
            check(level.getBlockState(behind).isAir(), "the torch behind the player is still there");
            check(level.getBlockState(soul).is(net.minecraft.world.level.block.Blocks.SOUL_TORCH), "a soul torch was put out");
            check(level.getBlockState(inFront).is(net.minecraft.world.level.block.Blocks.TORCH), "a torch in plain view was put out");
            check(!com.foglurker.brain.Hauntings.snuffTorch(p, r), "nothing left that it is allowed to put out");

            // Home: beds beat chests, and chests move home only when most of them are elsewhere.
            var prof = new com.foglurker.learning.PlayerBehaviorProfile();
            var a = new net.minecraft.core.BlockPos(0, 64, 0);
            var b = new net.minecraft.core.BlockPos(500, 64, 0);
            prof.usedContainerAt("o", a);
            prof.usedContainerAt("o", a);
            prof.usedContainerAt("o", b);
            check(prof.home().equals(a), "one chest far away must not move home");
            prof.usedContainerAt("o", b);
            prof.usedContainerAt("o", b);
            check(prof.home().equals(b), "home should follow where most chests are opened");
            prof.sleptAt("o", a);
            check(prof.home().equals(a) && prof.isNearHome("o", a.offset(5, 0, 5), 32), "a bed sets home");

            // A book appears in a chest at your base, in your own words. Then not again for 20 minutes.
            var chestPos = rel(h, 5, 0, 1);
            level.setBlockAndUpdate(chestPos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            var mine = data.profile(p.getUUID());
            mine.sleptAt(level.dimension().location().toString(), chestPos);
            mine.addChat("anyone seen my pickaxe");
            mine.addChat("brb getting wood");
            check(com.foglurker.brain.Hauntings.leaveBook(p, data, r), "it should leave a book in the chest");
            var chest = (net.minecraft.world.Container) level.getBlockEntity(chestPos);
            ItemStack book = chest.getItem(0);
            check(book.is(Items.WRITTEN_BOOK) && book.getTag().getList("pages", 8).size() >= 3, "no written book in the chest: " + book);
            check(book.getTag().getString("author").equals(p.getGameProfile().getName()), "the book should be signed with your name");
            check(!com.foglurker.brain.Hauntings.leaveBook(p, data, r), "only one book every 20 minutes");

            // The test area is a pocket deep under real terrain, so right here it is "underground".
            check(com.foglurker.brain.SpotFinder.placeOf(level, p, null) == com.foglurker.brain.SpotFinder.Place.UNDERGROUND,
                    "120 blocks below the surface should count as underground");

            // A friend who is not online "joins".
            UUID ghost = UUID.randomUUID();
            var friend = data.profile(ghost);
            friend.lastKnownName = "GhostFriend";
            friend.addChat("lol come here");
            check(com.foglurker.brain.Hauntings.ghostFriend(p, data, r), "an offline friend should be able to haunt");

            // Sounds, the apparition request and the random picker must all run cleanly.
            check(com.foglurker.brain.Hauntings.sound(p, data, r), "sound haunting failed");
            com.foglurker.brain.Hauntings.apparition(p);
            check(com.foglurker.brain.Hauntings.haunt(p, data, r) != null, "no haunting could run");
            // Let the queued footsteps, chat line and "left the game" play out.
            h.runAfterDelay(120, h::succeed);
        });
    }

    @GameTest(template = "empty", batch = "places", timeoutTicks = 200)
    public static void itWaitsOutsideYourWindow(GameTestHelper h) {
        logged(h, () -> {
            ServerLevel level = h.getLevel();
            ServerPlayer p = player(h, 1.5, 1.5);
            kickOtherPlayers(level.getServer(), p);
            // Build a field on the real surface far above the test pocket, and stand on it.
            int px = p.getBlockX();
            int pz = p.getBlockZ();
            int sy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz) + 2;
            var grass = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
            var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            for (int x = -18; x <= 18; x++) {
                for (int z = -18; z <= 18; z++) {
                    level.setBlock(new net.minecraft.core.BlockPos(px + x, sy - 1, pz + z), grass, 2);
                    for (int y = 0; y < 24; y++) {
                        level.setBlock(new net.minecraft.core.BlockPos(px + x, sy + y, pz + z), air, 2);
                    }
                }
            }
            p.teleportTo(level, px + 0.5, sy, pz + 0.5, 0F, 0F);
            var home = new net.minecraft.core.BlockPos(px, sy, pz);
            check(com.foglurker.brain.SpotFinder.placeOf(level, p, home) == com.foglurker.brain.SpotFinder.Place.SURFACE,
                    "standing in an open field should count as the surface");
            // Put a roof over the player: now they are indoors, at home.
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    level.setBlock(new net.minecraft.core.BlockPos(px + x, sy + 3, pz + z),
                            net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(), 2);
                }
            }
            check(com.foglurker.brain.SpotFinder.placeOf(level, p, home) == com.foglurker.brain.SpotFinder.Place.INDOORS,
                    "under a roof at home should count as indoors, got " + com.foglurker.brain.SpotFinder.placeOf(level, p, home));
            Vec3 outside = com.foglurker.brain.SpotFinder.findOutside(level, p, RandomSource.create(3));
            check(outside != null, "no spot outside the house was found");
            var feet = net.minecraft.core.BlockPos.containing(outside);
            check(level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ()) == feet.getY(),
                    "the spot must be out in the open, not under the roof: " + feet.toShortString());
            double d = outside.distanceTo(p.position());
            check(d >= 3 && d <= 17, "it should stand 5-16 blocks from you, was " + d);
            // And the watcher really appears there: close, outside, and it does not flee at once.
            FogLurkerSavedData data = FogLurkerSavedData.get(level.getServer());
            data.profile(p.getUUID()).sleptAt(level.dimension().location().toString(), home);
            FogLurkerEntity l = LurkerDirector.spawn(p, Disguise.CRAWLER, Boolean.FALSE);
            check(l != null && l.distanceTo(p) < 20, "the watcher should appear right outside, was "
                    + (l == null ? "null" : String.valueOf(l.distanceTo(p))));
            h.runAfterDelay(30, () -> {
                check(!l.isRemoved() && !l.isFlopped(), "it left the window straight away");
                l.brain().vanish(true);
                // Send the player back down so later tests find them where they expect.
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", batch = "sink", timeoutTicks = 300)
    public static void sinksIntoTheGroundWhenSpotted(GameTestHelper h) {
        logged(h, () -> {
            flatten(h, 30);
            ServerPlayer p = player(h, 1.5, 1.5);
            kickOtherPlayers(h.getLevel().getServer(), p);
            h.runAfterDelay(80, () -> logged(h, () -> {
                FogLurkerEntity l = LurkerDirector.spawn(p, Disguise.CRAWLER, Boolean.FALSE);
                check(l != null, "no spawn");
                h.runAfterDelay(10, () -> logged(h, () -> {
                    check(l.onGround(), "it should be standing on the ground");
                    l.brain().vanish(false);
                    check(l.isFlopped() && !l.isRemoved(), "spotted in its real shape it should flop flat, not pop");
                    h.succeedWhen(() -> check(l.isRemoved(), "it never finished flopping"));
                }));
            }));
        });
    }

    @GameTest(template = "empty", batch = "force", timeoutTicks = 900)
    public static void forceHideStalkAttackAndEndGrace(GameTestHelper h) {
        logged(h, () -> {
            flatten(h, 30);
            ServerLevel level = h.getLevel();
            MinecraftServer server = level.getServer();
            ServerPlayer p = player(h, 1.5, 1.5);
            kickOtherPlayers(server, p);
            var d = server.getCommands().getDispatcher();
            var op = p.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            check(!d.getRoot().getChild("foglurker").getChild("endgrace").canUse(p.createCommandSourceStack().withPermission(0)),
                    "endgrace must be cheats only");
            check(!d.getRoot().getChild("foglurker").getChild("force").canUse(p.createCommandSourceStack().withPermission(0)),
                    "force must be cheats only");

            FogLurkerSavedData data = FogLurkerSavedData.get(server);
            d.execute("foglurker endgrace", op);
            check(!data.inGrace(), "endgrace did not end the grace period");
            LurkerDifficulty before = data.difficulty();
            data.setDifficulty(LurkerDifficulty.VERY_EASY); // the longest grace: must still be over
            check(!data.inGrace(), "switching to a longer mode brought the grace period back");
            data.setDifficulty(before);

            // A patch of tall grass 26 blocks away for it to hide in.
            var grass = net.minecraft.world.level.block.Blocks.TALL_GRASS.defaultBlockState();
            for (int x = 24; x <= 28; x++) {
                for (int z = -2; z <= 2; z++) {
                    net.minecraft.world.level.block.DoublePlantBlock.placeAt(level, grass, rel(h, x, 0, z), 2);
                }
            }
            Vec3 cover = com.foglurker.brain.SpotFinder.findCover(level, rel(h, 20, 0, 1), p.position(), 14, 20);
            check(cover != null && cover.distanceTo(Vec3.atBottomCenterOf(rel(h, 26, 0, 0))) < 4,
                    "it should find the tall grass to hide in, found " + cover);

            h.runAfterDelay(80, () -> logged(h, () -> {
                d.execute("foglurker force hide", op);
                FogLurkerEntity hider = LurkerDirector.active().get(0);
                check(hider.getDisguise() == Disguise.CRAWLER && !hider.brain().encounter().willAttack(), "hide: should be the pale crawler, not attacking");
                h.runAfterDelay(140, () -> logged(h, () -> {
                    check(hider.isRemoved() || hider.isLying() || hider.isFlopped(),
                            "hide: it should be lying down by now (" + hider.brain().describe() + ")");
                    check(hider.isRemoved() || !hider.isLying() || hider.getBbHeight() < 0.7F, "lying down, its hitbox should be low too");
                    if (!hider.isRemoved()) {
                        hider.brain().vanish(true);
                    }

                    d.execute("foglurker force stalk", op);
                    FogLurkerEntity stalker = LurkerDirector.active().get(0);
                    String spawnedAt = stalker.blockPosition().toShortString() + " d=" + stalker.distanceTo(p);
                    check(stalker.brain().encounter().mode() == LurkerBrain.Mode.STALKER, "stalk: wrong mode");
                    // Look straight at it: it must drop flat and freeze.
                    double dx = stalker.getX() - p.getX();
                    double dz = stalker.getZ() - p.getZ();
                    float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;
                    p.teleportTo(level, p.getX(), p.getY(), p.getZ(), yaw, 0F);
                    p.setYHeadRot(yaw); // a real client keeps the head turned too; "looking" is judged by the head
                    h.runAfterDelay(10, () -> logged(h, () -> {
                        check(stalker.isRemoved() || stalker.isLying(), "stalk: looked at, it should drop flat ("
                                + stalker.brain().describe() + ", player at " + p.blockPosition().toShortString()
                                + " yaw " + p.getYRot() + ", dist " + stalker.distanceTo(p) + ", LOS " + p.hasLineOfSight(stalker)
                                + ", spawned " + spawnedAt + ", view " + p.getViewVector(1F) + " rot " + p.getYRot() + "/" + p.getXRot()
                                + ", dir " + stalker.position().subtract(p.getEyePosition()).normalize() + ")");
                        if (!stalker.isRemoved()) {
                            stalker.brain().vanish(true);
                        }
                        p.setHealth(20F);
                        d.execute("foglurker force attack", op);
                        FogLurkerEntity hunter = LurkerDirector.active().get(0);
                        check(hunter.brain().phase() == LurkerBrain.Phase.ATTACK, "attack: it should be hunting at once");
                        h.succeedWhen(() -> {
                            check(p.getHealth() < 20F, "attack: no hit yet (" + hunter.brain().describe() + ")");
                            hunter.brain().vanish(true);
                        });
                    }));
                }));
            }));
        });
    }

    @GameTest(template = "empty", batch = "retention", timeoutTicks = 200)
    public static void voiceSnippetsExpireAfterThirtyDays(GameTestHelper h) {
        logged(h, () -> {
            long day = 24L * 60 * 60 * 1000;
            long now = System.currentTimeMillis();
            short[] pcm = new short[48000];
            for (int i = 0; i < pcm.length; i++) {
                pcm[i] = (short) (Math.sin(i / 20.0) * 8000);
            }
            UUID gone = UUID.randomUUID();
            UUID active = UUID.randomUUID();
            VoiceConsent.optIn(gone);
            VoiceConsent.optIn(active);
            SnippetStore.saveAsync(gone, pcm);
            SnippetStore.saveAsync(active, pcm);
            h.runAfterDelay(30, () -> logged(h, () -> {
                check(SnippetStore.count(gone) == 1 && SnippetStore.count(active) == 1, "snippets were not saved");
                // "gone" removed the mod 31 days ago and never came back; "active" played yesterday.
                VoiceConsent.touch(gone, now - 31 * day);
                VoiceConsent.touch(active, now - day);
                int expired = VoiceConsent.expireStale(now, com.foglurker.voice.VoiceRetention.RETENTION_MILLIS);
                check(expired >= 1 && SnippetStore.count(gone) == 0 && !VoiceConsent.isOptedIn(gone),
                        "31 days unseen: snippets must be deleted");
                check(SnippetStore.count(active) == 1 && VoiceConsent.isOptedIn(active), "seen yesterday: snippets must stay");

                // The whole mod unused for 31 days: everything is wiped when it next starts.
                com.foglurker.voice.VoiceRetention.stamp(now - 31 * day);
                com.foglurker.voice.VoiceRetention.onStart(now);
                check(SnippetStore.count(active) == 0 && !VoiceConsent.isOptedIn(active),
                        "after 31 days without the mod running, every snippet must be wiped on start");
                // ...but a normal restart keeps everything.
                VoiceConsent.optIn(active);
                com.foglurker.voice.VoiceRetention.onStart(now + day);
                check(VoiceConsent.isOptedIn(active), "a normal restart must not wipe anything");
                VoiceConsent.optOut(active);
                String note = new String(java.nio.file.Files.readAllBytes(SecureStorage.root().resolve("READ_ME.txt")),
                        StandardCharsets.UTF_8);
                check(note.contains("safe") || note.contains("delete it"), "the folder note is missing");
                h.succeed();
            }));
        });
    }

    @GameTest(template = "empty", batch = "secure", timeoutTicks = 200)
    public static void secureFolderConsentAndSnippets(GameTestHelper h) {
        logged(h, () -> secureFolderConsentAndSnippetsImpl(h));
    }

    private static void secureFolderConsentAndSnippetsImpl(GameTestHelper h) throws Exception {
        byte[] plain = "hello from the fog".getBytes(StandardCharsets.UTF_8);
        byte[] enc = SecureStorage.encrypt(plain);
        check(!new String(enc, StandardCharsets.ISO_8859_1).contains("hello"), "ciphertext leaks plaintext");
        check(Arrays.equals(SecureStorage.decrypt(enc), plain), "decrypt(encrypt(x)) != x");
        enc[enc.length - 1] ^= 1;
        boolean tamperDetected = false;
        try {
            SecureStorage.decrypt(enc);
        } catch (IOException expected) {
            tamperDetected = true;
        }
        check(tamperDetected, "tampering was not detected");

        String key = "AIzaTESTKEY_0123456789abcdef";
        ApiKeyStore.set(key);
        check(key.equals(ApiKeyStore.get()), "API key did not round-trip");
        String onDisk = new String(Files.readAllBytes(SecureStorage.root().resolve("gemini_api_key.enc")),
                StandardCharsets.ISO_8859_1);
        check(!onDisk.contains("AIza"), "API key is stored in plain text");
        check(ApiKeyStore.masked(key).equals("AIza…cdef"), "masking is wrong: " + ApiKeyStore.masked(key));
        ApiKeyStore.set(null);
        check(!Files.exists(SecureStorage.root().resolve("gemini_api_key.enc")), "clearing did not delete the key file");

        UUID id = UUID.randomUUID();
        check(!VoiceConsent.isOptedIn(id), "players must start opted out");
        SnippetStore.saveAsync(id, new short[4800]);
        VoiceConsent.optIn(id);
        check(VoiceConsent.isOptedIn(id), "opt-in did not stick");
        short[] pcm = new short[48000];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) (Math.sin(i / 20.0) * 8000);
        }
        SnippetStore.saveAsync(id, pcm);
        h.runAfterDelay(40, () -> {
            check(SnippetStore.count(id) == 1, "expected exactly 1 snippet (the pre-opt-in one must be dropped), got "
                    + SnippetStore.count(id));
            short[] back = SnippetStore.loadRandom(id);
            check(back != null && Arrays.equals(back, pcm), "snippet did not round-trip");
            int deleted = VoiceConsent.optOut(id);
            check(deleted == 1 && SnippetStore.count(id) == 0, "opt-out must delete every snippet");
            check(!VoiceConsent.isOptedIn(id), "opt-out did not stick");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "chat", timeoutTicks = 100)
    public static void chatCopiesTypingStyle(GameTestHelper h) {
        logged(h, () -> chatCopiesTypingStyleImpl(h));
    }

    private static void chatCopiesTypingStyleImpl(GameTestHelper h) throws Exception {
        ChatStyle style = ChatStyle.analyze(List.of("lol come here bro", "bro wait for me", "where r u",
                "lol ok", "i found diamonds bro", "dont go there"));
        String out = style.apply("Come here!", RandomSource.create(7));
        check(out.equals(out.toLowerCase()), "should be lowercase like the samples: " + out);
        check(!out.endsWith("!") && !out.endsWith("."), "should drop punctuation like the samples: " + out);

        MimicChat.Context ctx = new MimicChat.Context("Alex", "Steve", null, "north-east", false, List.of());
        for (MimicChat.Intent intent : MimicChat.Intent.values()) {
            String said = MimicChat.compose(ctx, intent, intent == MimicChat.Intent.REPLY ? "where are you" : null,
                    RandomSource.create(intent.ordinal())).get(5, TimeUnit.SECONDS);
            check(said != null && !said.isBlank() && said.length() <= 120, intent + " produced: " + said);
        }
        check(MimicChat.sanitize("<Alex> \"come here\"\nsecond line", ctx).equals("come here"), "sanitize failed");
        check(MimicChat.sanitize("Alex: §cfollow me", ctx).equals("follow me"), "sanitize failed on prefix/colour");
        h.succeed();
    }
}
