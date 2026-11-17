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
        LurkerDirector.naturalSpawnsPaused = true; // tests spawn what they need themselves
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
        // A real player's moves keep the chunks around them running; a mock player never ticks, so tell the
        // chunk map ourselves. Otherwise a creature that appears on the far side of the field sits frozen
        // in a chunk outside the world-spawn area.
        level.getChunkSource().move(p);
        // ...and keep the chunks around them running, for tests that do not build their own field.
        int cx = p.getBlockX() >> 4;
        int cz = p.getBlockZ() >> 4;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                level.setChunkForced(cx + dx, cz + dz, true);
            }
        }
        return p;
    }

    /**
     * The test world has real terrain and pits dug for other tests. Behaviour tests flatten a field
     * around the player so they test the creature, not the scenery.
     */
    private static void flatten(GameTestHelper h, int radius) {
        // The test world is ordinary generated terrain and the tests run deep underground, so a fresh
        // world brings its own caves, aquifers, lava and gravel. Carve a sealed room (stone walls, ceiling
        // and sub-floor) so nothing can flow or fall into the field while a test runs.
        int r = Math.max(radius, 24); // room for spawn spots 15+ blocks away
        ServerLevel level = h.getLevel();
        net.minecraft.core.BlockPos c = h.absolutePos(net.minecraft.core.BlockPos.ZERO);
        var grass = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
        var dirt = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
        var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        for (int x = -r - 1; x <= r + 1; x++) {
            for (int z = -r - 1; z <= r + 1; z++) {
                boolean wall = Math.abs(x) == r + 1 || Math.abs(z) == r + 1;
                level.setBlock(c.offset(x, -3, z), stone, 2);
                level.setBlock(c.offset(x, -2, z), wall ? stone : dirt, 2);
                level.setBlock(c.offset(x, -1, z), wall ? stone : grass, 2);
                for (int y = 0; y < 20; y++) {
                    level.setBlock(c.offset(x, y, z), wall ? stone : air, 2);
                }
                level.setBlock(c.offset(x, 20, z), stone, 2);
            }
        }
        // Keep the whole field running like chunks next to a real player (mock players do not load chunks).
        for (int cx = (c.getX() - r - 1) >> 4; cx <= (c.getX() + r + 1) >> 4; cx++) {
            for (int cz = (c.getZ() - r - 1) >> 4; cz <= (c.getZ() + r + 1) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
        // Anything that fell or flowed in before the walls went up.
        for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,
                new net.minecraft.world.phys.AABB(c).inflate(r + 2, 24, r + 2))) {
            e.discard();
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
        List<net.minecraft.world.entity.Entity> everything = new ArrayList<>();
        level.getAllEntities().forEach(everything::add); // copy first: discarding while iterating corrupts the map
        for (net.minecraft.world.entity.Entity e : everything) {
            if (e instanceof net.minecraft.world.entity.monster.Enemy && !(e instanceof FogLurkerEntity)) {
                e.discard();
            }
        }
    }

    /** Leftover mock players from earlier tests would otherwise be picked as "friends" to copy. */
    private static void kickOtherPlayers(MinecraftServer server, ServerPlayer... keep) {
        calm(server);
        // Every batch runs in the same spot, so clear what earlier tests left behind: creatures still out
        // (a failed test never sends its own away), boats, dropped items and falling blocks.
        for (FogLurkerEntity l : LurkerDirector.active()) {
            l.discard();
        }
        List<net.minecraft.world.entity.Entity> leftovers = new ArrayList<>();
        server.overworld().getAllEntities().forEach(leftovers::add); // copy first: discarding while iterating corrupts the map
        for (net.minecraft.world.entity.Entity e : leftovers) {
            if (e instanceof FogLurkerEntity || e instanceof net.minecraft.world.entity.vehicle.Boat
                    || e instanceof net.minecraft.world.entity.item.ItemEntity
                    || e instanceof net.minecraft.world.entity.item.FallingBlockEntity) {
                e.discard();
            }
        }
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
                            + target.blockPosition().toShortString() + " [onGround " + l.onGround() + ", pos " + l.position()
                            + ", motion " + l.getDeltaMovement() + ", feet " + l.level().getBlockState(l.blockPosition())
                            + ", below " + l.level().getBlockState(l.blockPosition().below()) + ", noAi " + l.isNoAi()
                            + ", dims " + l.getBbWidth() + "x" + l.getBbHeight() + ", flags lying " + l.isLying()
                            + ", spawn-rel " + h.relativePos(l.blockPosition()).toShortString() + ", target alive " + target.isAlive()
                            + ", tickCount " + l.tickCount + ", entityTicking " + h.getLevel().isPositionEntityTicking(l.blockPosition())
                            + ", targetChunkTicking " + h.getLevel().isPositionEntityTicking(target.blockPosition())
                            + ", chunk " + new net.minecraft.world.level.ChunkPos(l.blockPosition()) + " vs " + new net.minecraft.world.level.ChunkPos(target.blockPosition())
                            + ", spawn " + h.getLevel().getSharedSpawnPos().toShortString() + "]");
            l.brain().vanish(false);
        });
    }

    @GameTest(template = "empty", batch = "damage", timeoutTicks = 200)
    public static void damageMatchesEachMode(GameTestHelper h) {
        logged(h, () -> damageMatchesEachModeImpl(h));
    }

    private static void damageMatchesEachModeImpl(GameTestHelper h) throws Exception {
        flatten(h, 24); // its own field: never depend on what an earlier test left here
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
        flatten(h, 24);
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
        flatten(h, 24);
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
            // Once the grace period has run out it stays over, even after switching to a mode with a longer one...
            FogLurkerSavedData g = new FogLurkerSavedData();
            g.setDifficulty(LurkerDifficulty.DEATHWISH);
            for (int i = 0; i <= LurkerDifficulty.DEATHWISH.graceTicks(); i++) {
                g.tickGrace();
            }
            check(!g.inGrace(), "5 minutes of Deathwish grace should be over");
            g.setDifficulty(LurkerDifficulty.VERY_EASY);
            check(!g.inGrace(), "once it has run out, a longer mode must not bring the grace period back");
            // ...but while it is still running, a longer mode does give you its longer grace.
            FogLurkerSavedData early = new FogLurkerSavedData();
            early.setDifficulty(LurkerDifficulty.DEATHWISH);
            early.tickGrace();
            early.setDifficulty(LurkerDifficulty.VERY_EASY);
            check(early.inGrace() && early.graceRemainingTicks() == LurkerDifficulty.VERY_EASY.graceTicks() - 1,
                    "a longer mode chosen during the grace period should extend it");
            // A chance of 0 really is never: misses must not turn into guaranteed appearances.
            FogLurkerSavedData never = new FogLurkerSavedData();
            for (int i = 0; i < 10; i++) {
                check(!never.rollSpawn(0.0, 0.0), "a 0% chance must never spawn (roll " + i + ")");
            }
            check(!never.nextIsGuaranteed(), "a 0% chance must not owe a guaranteed spawn");
            // Natural spawning can be switched off per world, survives a save, and starts fresh when switched back on.
            check(d.naturalSpawning(), "natural spawning is on by default");
            d.setNaturalSpawning(false);
            check(!FogLurkerSavedData.load(d.save(new net.minecraft.nbt.CompoundTag())).naturalSpawning(), "off must survive a save");
            check(FogLurkerSavedData.load(new net.minecraft.nbt.CompoundTag()).naturalSpawning(), "old worlds load with it on");
            d.setNextSpawnTicks(5);
            d.setGuaranteedNext(true);
            d.setNaturalSpawning(true);
            check(d.nextSpawnTicks() < 0 && !d.nextIsGuaranteed(), "switching back on should restart the countdown");
            // The command: anyone can view, only operators can change it.
            MinecraftServer server = h.getLevel().getServer();
            FogLurkerSavedData live = FogLurkerSavedData.get(server);
            boolean was = live.naturalSpawning();
            var cmds = server.getCommands().getDispatcher();
            try {
                cmds.execute("foglurker naturalspawning off", server.createCommandSourceStack().withPermission(4));
                check(!live.naturalSpawning(), "/foglurker naturalspawning off did not turn it off");
                check(cmds.execute("foglurker naturalspawning", server.createCommandSourceStack().withPermission(0)) == 0,
                        "viewing should report off");
                boolean refused = false;
                try {
                    cmds.execute("foglurker naturalspawning on", server.createCommandSourceStack().withPermission(0));
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                    refused = true;
                }
                check(refused && !live.naturalSpawning(), "a non-operator must not be able to turn it on");
                cmds.execute("foglurker naturalspawning on", server.createCommandSourceStack().withPermission(4));
                check(live.naturalSpawning(), "/foglurker naturalspawning on did not turn it on");
            } finally {
                live.setNaturalSpawning(was);
            }
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ killing it, its drops, the sword

    @GameTest(template = "empty", batch = "kill", timeoutTicks = 400)
    public static void healthPerModeSwordDamageAndDeath(GameTestHelper h) {
        logged(h, () -> {
            flatten(h, 24);
            MinecraftServer server = h.getLevel().getServer();
            server.setDifficulty(Difficulty.NORMAL, true);
            ServerPlayer p = player(h, 1.5, 1.5);
            kickOtherPlayers(server, p);
            h.runAfterDelay(80, () -> logged(h, () -> {
                FogLurkerSavedData data = FogLurkerSavedData.get(server);
                LurkerDifficulty before = data.difficulty();
                try {
                    // 50 / 75 / 100 / 125 / 175 full hearts.
                    int[] hearts = {50, 75, 100, 125, 175};
                    for (int i = 0; i < hearts.length; i++) {
                        data.setDifficulty(LurkerDifficulty.values()[i]);
                        FogLurkerEntity l = hunter(h, p, 12.5, 1.5);
                        check(l.getMaxHealth() == hearts[i] * 2F && l.getHealth() == hearts[i] * 2F,
                                LurkerDifficulty.values()[i] + ": expected " + hearts[i] + " hearts, has " + l.getMaxHealth() / 2F);
                        l.brain().vanish(true);
                    }
                    data.setDifficulty(LurkerDifficulty.MEDIUM);
                    FogLurkerEntity l = hunter(h, p, 12.5, 1.5);
                    // Bare hands go through its armour like any weapon...
                    float hp = l.getHealth();
                    l.hurt(p.damageSources().playerAttack(p), 4F);
                    check(l.getHealth() < hp && hp - l.getHealth() < 4F, "its armour should soak part of an ordinary hit");
                    // ...the Protector does 10, and 15 on a critical hit, straight through it.
                    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                            new ItemStack(com.foglurker.registry.ModItems.PROTECTOR_OF_THE_FOG.get()));
                    l.invulnerableTime = 0;
                    hp = l.getHealth();
                    l.hurt(p.damageSources().playerAttack(p), 4F);
                    check(Math.abs(hp - l.getHealth() - 10F) < 0.01F, "a full Protector hit should do 10, did " + (hp - l.getHealth()));
                    l.invulnerableTime = 0;
                    hp = l.getHealth();
                    l.hurt(p.damageSources().playerAttack(p), 6F);
                    check(Math.abs(hp - l.getHealth() - 15F) < 0.01F, "a critical Protector hit should do 15, did " + (hp - l.getHealth()));
                    check(!l.isDeadOrDying(), "it should still be standing");
                    // It can be killed now. Every other creature leaves, and it rests for 30 minutes.
                    FogLurkerEntity other = hunter(h, p, 1.5, 12.5);
                    for (int i = 0; i < 40 && !l.isDeadOrDying(); i++) {
                        l.invulnerableTime = 0;
                        l.hurt(p.damageSources().playerAttack(p), 6F);
                    }
                    check(l.isDeadOrDying(), "it should die (health " + l.getHealth() + ")");
                    check(LurkerDirector.isResting(server), "after it dies it should rest");
                    long rest = LurkerDirector.restTicksLeft(server);
                    check(rest > LurkerDirector.REST_TICKS - 40 && rest <= LurkerDirector.REST_TICKS, "it should rest 30 minutes, rests " + rest);
                    check(other.isRemoved() || other.isFlopped(), "the other creature should leave too");
                    check(FogLurkerSavedData.load(data.save(new net.minecraft.nbt.CompoundTag())).restUntil() == data.restUntil(),
                            "the rest must survive a restart");
                } finally {
                    data.setDifficulty(before);
                    data.setRestUntil(0);
                }
                h.succeed();
            }));
        });
    }

    @GameTest(template = "empty", batch = "kill", timeoutTicks = 100)
    public static void dropRatesFollowTheMode(GameTestHelper h) {
        logged(h, () -> {
            RandomSource r = RandomSource.create(1234);
            int n = 4000;
            float lastFog = 0F;
            for (LurkerDifficulty d : LurkerDifficulty.values()) {
                float[] c = com.foglurker.entity.LurkerLoot.chances(d);
                check(c[2] >= c[3], d + ": the foggy stick must never be rarer than the ingot");
                check(c[0] >= lastFog, d + ": drops should get more likely with harder modes");
                lastFog = c[0];
                int fog = 0, teeth = 0, stick = 0, ingot = 0;
                for (int i = 0; i < n; i++) {
                    for (ItemStack s : com.foglurker.entity.LurkerLoot.roll(d, i % 2 == 0, r)) {
                        if (s.is(com.foglurker.registry.ModItems.FOG_OF_THE_DAY.get())) {
                            fog++;
                            check(i % 2 != 0, "a night kill dropped The Fog of the Day");
                        } else if (s.is(com.foglurker.registry.ModItems.FOG_OF_THE_NIGHT.get())) {
                            fog++;
                            check(i % 2 == 0, "a day kill dropped The Fog of the Night");
                        } else if (s.is(com.foglurker.registry.ModItems.TEETH_OF_THE_LURKER.get())) {
                            teeth++;
                        } else if (s.is(com.foglurker.registry.ModItems.FOGGY_STICK.get())) {
                            stick++;
                        } else if (s.is(com.foglurker.registry.ModItems.INGOT_OF_THE_FOG.get())) {
                            ingot++;
                        }
                    }
                }
                check(Math.abs(fog / (float) n - c[0]) < 0.04F, d + ": fog dropped " + fog + "/" + n + ", expected " + c[0]);
                check(Math.abs(teeth / (float) n - c[1]) < 0.04F, d + ": teeth dropped " + teeth + "/" + n);
                check(Math.abs(stick / (float) n - c[2]) < 0.025F, d + ": foggy stick dropped " + stick + "/" + n);
                check(Math.abs(ingot / (float) n - c[3]) < 0.015F, d + ": ingot dropped " + ingot + "/" + n);
            }
            float[] hard = com.foglurker.entity.LurkerLoot.chances(LurkerDifficulty.HARD);
            float[] dw = com.foglurker.entity.LurkerLoot.chances(LurkerDifficulty.DEATHWISH);
            check(Math.abs(dw[3] - hard[3] * 1.5F) < 1e-6, "Deathwish ingot should be 1.5x Hard");
            check(dw[0] >= 0.95F && dw[1] >= 0.95F, "Deathwish drops should be almost guaranteed");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "sword", timeoutTicks = 300)
    public static void protectorScareBurstAndRecharge(GameTestHelper h) {
        logged(h, () -> {
            flatten(h, 24);
            MinecraftServer server = h.getLevel().getServer();
            server.setDifficulty(Difficulty.NORMAL, true);
            ServerPlayer p = player(h, 1.5, 1.5);
            ServerPlayer friend = player(h, 4.5, 1.5);
            ServerPlayer far = player(h, 1.5, 22.5);
            kickOtherPlayers(server, p, friend, far);
            h.runAfterDelay(80, () -> logged(h, () -> {
                var sword = (com.foglurker.item.ProtectorOfTheFogItem) com.foglurker.registry.ModItems.PROTECTOR_OF_THE_FOG.get();
                ItemStack stack = new ItemStack(sword);
                // A wooden sword against everything else, and it never wears from hitting.
                var mods = stack.getAttributeModifiers(EquipmentSlot.MAINHAND)
                        .get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                double bonus = mods.stream().mapToDouble(m -> m.getAmount()).sum();
                var wood = new ItemStack(Items.WOODEN_SWORD).getAttributeModifiers(EquipmentSlot.MAINHAND)
                        .get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                check(bonus == wood.stream().mapToDouble(m -> m.getAmount()).sum(), "it should hit like a wooden sword, bonus " + bonus);
                check(stack.getMaxDamage() == 10 && com.foglurker.item.ProtectorOfTheFogItem.charges(stack) == 10, "it should hold 10 scare bursts");
                sword.hurtEnemy(stack, friend, p);
                check(stack.getDamageValue() == 0, "hitting things must not use up scare bursts");
                // A scare burst reaches the creature hunting you wherever you aim: it flees or is weakened.
                FogLurkerSavedData data = FogLurkerSavedData.get(server);
                LurkerDifficulty before = data.difficulty();
                data.setDifficulty(LurkerDifficulty.MEDIUM);
                FogLurkerEntity l;
                try {
                    l = hunter(h, p, 14.5, 1.5);
                } finally {
                    data.setDifficulty(before);
                }
                p.setYRot(180F); // looking the other way
                int reached = com.foglurker.item.ProtectorOfTheFogItem.burst(h.getLevel(), p, stack);
                check(reached == 1, "the burst should reach the creature hunting you, reached " + reached);
                check(com.foglurker.item.ProtectorOfTheFogItem.charges(stack) == 9, "one burst should use one charge");
                boolean fled = l.isRemoved() || l.brain().phase() == LurkerBrain.Phase.FLEE;
                boolean weakened = l.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)
                        && l.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS);
                check(fled ^ weakened, "it should either flee or be weakened (fled " + fled + ", weakened " + weakened + ")");
                // And it fears you (and the friend standing with you) for 20 minutes.
                var profile = data.profile(p.getUUID());
                profile.escapes = 0;
                check(LurkerDirector.isFeared(p) && LurkerDirector.isFeared(friend) && !LurkerDirector.isFeared(far),
                        "you and the friend next to you should be feared, the far player not");
                double base = LurkerDirector.attackChance(LurkerDifficulty.MEDIUM, profile);
                check(Math.abs(LurkerDirector.attackChance(LurkerDifficulty.MEDIUM, profile, p) - base * 0.1) < 1e-9,
                        "with a friend next to you it should almost never attack");
                friend.teleportTo(friend.getX() + 40, friend.getY(), friend.getZ());
                check(Math.abs(LurkerDirector.attackChance(LurkerDifficulty.MEDIUM, profile, p) - base * 0.5) < 1e-9,
                        "alone, a scared creature should be half as likely to attack");
                // Empty: it does nothing.
                stack.setDamageValue(10);
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
                var result = sword.use(h.getLevel(), p, net.minecraft.world.InteractionHand.MAIN_HAND);
                check(result.getResult() == net.minecraft.world.InteractionResult.FAIL, "an empty sword must not burst");
                // Recharge: one charge per fog item, equal day and night, never past full.
                var recipe = new com.foglurker.item.ProtectorRechargeRecipe(new net.minecraft.resources.ResourceLocation("foglurker", "t"),
                        net.minecraft.world.item.crafting.CraftingBookCategory.EQUIPMENT);
                var grid = new net.minecraft.world.inventory.TransientCraftingContainer(
                        net.minecraft.world.inventory.ChestMenu.threeRows(0, p.getInventory()), 3, 3);
                grid.setItem(0, stack.copy());
                for (int i = 1; i <= 4; i++) {
                    grid.setItem(i, new ItemStack(com.foglurker.registry.ModItems.FOG_OF_THE_DAY.get()));
                    grid.setItem(i + 4, new ItemStack(com.foglurker.registry.ModItems.FOG_OF_THE_NIGHT.get()));
                }
                check(recipe.matches(grid, h.getLevel()), "4 day + 4 night should recharge an empty sword");
                ItemStack out = recipe.assemble(grid, server.registryAccess());
                check(com.foglurker.item.ProtectorOfTheFogItem.charges(out) == 8, "8 fog items should give 8 charges, got "
                        + com.foglurker.item.ProtectorOfTheFogItem.charges(out));
                grid.setItem(8, ItemStack.EMPTY);
                check(!recipe.matches(grid, h.getLevel()), "unequal day and night must not recharge");
                grid.setItem(0, out);
                grid.setItem(8, new ItemStack(com.foglurker.registry.ModItems.FOG_OF_THE_NIGHT.get()));
                check(!recipe.matches(grid, h.getLevel()), "more fog than it is missing must not be wasted");
                var recipes = server.getRecipeManager();
                check(recipes.byKey(new net.minecraft.resources.ResourceLocation("foglurker", "protector_of_the_fog")).isPresent()
                        && recipes.byKey(new net.minecraft.resources.ResourceLocation("foglurker", "protector_recharge")).isPresent(),
                        "the sword and recharge recipes should be loaded");
                h.succeed();
            }));
        });
    }

    // ------------------------------------------------------------------ AFK

    @GameTest(template = "empty", batch = "afk", timeoutTicks = 400)
    public static void afkAutomaticallyAndWithTheCommand(GameTestHelper h) {
        logged(h, () -> {
            flatten(h, 24);
            MinecraftServer server = h.getLevel().getServer();
            ServerPlayer p = player(h, 1.5, 1.5);
            ServerPlayer other = player(h, 6.5, 1.5);
            kickOtherPlayers(server, p, other);
            h.runAfterDelay(30, () -> logged(h, () -> {
                long now = server.getTickCount();
                long wait = com.foglurker.world.AfkTracker.afkTicks();
                check(wait == 5L * 60 * 20, "AFK should take 5 minutes by default, takes " + wait + " ticks");
                com.foglurker.world.AfkTracker.check(p, now);
                check(!com.foglurker.world.AfkTracker.isAfk(p), "a player who just joined is not AFK");
                com.foglurker.world.AfkTracker.check(p, now + wait - 40);
                check(!com.foglurker.world.AfkTracker.isAfk(p), "4 min 58 s of nothing is not yet AFK");
                com.foglurker.world.AfkTracker.check(p, now + wait + 20);
                check(com.foglurker.world.AfkTracker.isAfk(p), "5 minutes of nothing should make them AFK");
                check(!com.foglurker.world.AfkTracker.isManual(p), "that was automatic, not /afk");
                check(!LurkerDirector.freeCandidates(server).contains(p), "nothing should come for an AFK player");
                check(LurkerDirector.freeCandidates(server).contains(other), "the other player is still fair game");
                // Being pushed is not "being back": knocked 3 blocks while hurt.
                p.hurtTime = 10;
                p.teleportTo(p.getX() + 3, p.getY(), p.getZ());
                com.foglurker.world.AfkTracker.check(p, now + wait + 40);
                check(com.foglurker.world.AfkTracker.isAfk(p), "being knocked back must not end AFK");
                p.hurtTime = 0;
                // Respawning or being teleported far away is not walking either.
                p.teleportTo(p.getX() + 40, p.getY(), p.getZ());
                com.foglurker.world.AfkTracker.check(p, now + wait + 50);
                check(com.foglurker.world.AfkTracker.isAfk(p), "a 40 block jump (respawn, /tp) must not end AFK");
                // Looking around is.
                p.setYRot(p.getYRot() + 40F);
                com.foglurker.world.AfkTracker.check(p, now + wait + 60);
                check(!com.foglurker.world.AfkTracker.isAfk(p), "looking around should end AFK");

                // /afk: AFK at once, and the jiggle of closing the chat does not undo it.
                var cmds = server.getCommands().getDispatcher();
                check(cmds.execute("afk", p.createCommandSourceStack()) == 1, "/afk should work");
                check(com.foglurker.world.AfkTracker.isAfk(p) && com.foglurker.world.AfkTracker.isManual(p), "/afk should make them AFK now");
                long t0 = server.getTickCount();
                p.setYRot(p.getYRot() + 5F);
                com.foglurker.world.AfkTracker.check(p, t0 + 5);
                check(com.foglurker.world.AfkTracker.isAfk(p), "a twitch right after /afk must not undo it");
                p.setYRot(p.getYRot() + 30F);
                com.foglurker.world.AfkTracker.check(p, t0 + 60);
                check(!com.foglurker.world.AfkTracker.isAfk(p), "moving after /afk should bring them back");
                // /AFK works too, and typing it again comes back.
                check(cmds.execute("AFK", p.createCommandSourceStack()) == 1 && com.foglurker.world.AfkTracker.isAfk(p), "/AFK should work");
                check(cmds.execute("afk", p.createCommandSourceStack()) == 1 && !com.foglurker.world.AfkTracker.isAfk(p),
                        "/afk again should bring them back");

                // No hiding behind /afk once it is already after you.
                FogLurkerEntity l = hunter(h, p, 14.5, 1.5);
                check(cmds.execute("afk", p.createCommandSourceStack().withSuppressedOutput()) == 0, "/afk must be refused mid-hunt");
                check(!com.foglurker.world.AfkTracker.isAfk(p), "/afk mid-hunt must not make them AFK");
                l.brain().vanish(true);
                h.succeed();
            }));
        });
    }

    @GameTest(template = "empty", batch = "afk2", timeoutTicks = 400)
    public static void itLeavesSomeoneWhoGoesAfk(GameTestHelper h) {
        logged(h, () -> {
            flatten(h, 24);
            MinecraftServer server = h.getLevel().getServer();
            server.setDifficulty(Difficulty.NORMAL, true);
            ServerPlayer p = player(h, 1.5, 1.5);
            kickOtherPlayers(server, p);
            h.runAfterDelay(80, () -> logged(h, () -> {
                p.setHealth(20F);
                FogLurkerSavedData data = FogLurkerSavedData.get(server);
                LurkerDifficulty before = data.difficulty();
                data.setDifficulty(LurkerDifficulty.MEDIUM);
                FogLurkerEntity l;
                try {
                    l = hunter(h, p, 12.5, 1.5);
                } finally {
                    data.setDifficulty(before);
                }
                // They stood still for 5 minutes while it closed in.
                com.foglurker.world.AfkTracker.check(p, server.getTickCount() + com.foglurker.world.AfkTracker.afkTicks() + 20);
                check(com.foglurker.world.AfkTracker.isAfk(p), "test setup: the player should be AFK");
                // ...and operators cannot send it after them either.
                int before2 = LurkerDirector.active().size();
                check(server.getCommands().getDispatcher().execute("foglurker force attack " + p.getGameProfile().getName(),
                        server.createCommandSourceStack().withPermission(2).withSuppressedOutput()) == 0
                        && LurkerDirector.active().size() == before2, "force attack must refuse an AFK player");
                h.succeedWhen(() -> {
                    check(l.isRemoved() || l.isFlopped(), "it should leave an AFK player alone (" + l.brain().describe() + ")");
                    check(p.getHealth() >= 20F, "it hurt an AFK player");
                    check(LurkerDirector.leftAloneTicks(p) == 0, "going AFK is not 'getting away'");
                    com.foglurker.world.AfkTracker.comeBack(p);
                });
            }));
        });
    }

    // ------------------------------------------------------------------ night

    @GameTest(template = "empty", batch = "night", timeoutTicks = 100)
    public static void fogIsThickerAtNight(GameTestHelper h) {
        logged(h, () -> {
            float[] want = {1.10F, 1.15F, 1.25F, 1.35F, 1.50F};
            LurkerDifficulty[] modes = LurkerDifficulty.values();
            for (int i = 0; i < modes.length; i++) {
                check(Math.abs(modes[i].nightFogMultiplier() - want[i]) < 1e-6, modes[i] + " night fog should be x" + want[i]);
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "night", timeoutTicks = 100)
    public static void summonedShapeFlatFlopAndUnloadedGround(GameTestHelper h) {
        logged(h, () -> {
            ServerLevel level = h.getLevel();
            // /summon gives no shape: it must stay the pale crawler, not turn into a blank player.
            FogLurkerEntity summoned = com.foglurker.registry.ModEntities.FOG_LURKER.get().create(level);
            summoned.readAdditionalSaveData(new net.minecraft.nbt.CompoundTag());
            check(summoned.getDisguise() == Disguise.CRAWLER, "a summoned creature became " + summoned.getDisguise());
            net.minecraft.nbt.CompoundTag cow = new net.minecraft.nbt.CompoundTag();
            cow.putInt("Disguise", Disguise.COW.ordinal());
            summoned.readAdditionalSaveData(cow);
            check(summoned.getDisguise() == Disguise.COW, "a saved shape must still be read");
            // Flopped flat, its hitbox is flat too.
            summoned.setDisguise(Disguise.CRAWLER);
            float standing = summoned.getBbHeight();
            summoned.beginFlop();
            check(summoned.getBbHeight() < 0.3F && standing > 1F, "flopped, it should have a flat hitbox, got " + summoned.getBbHeight());
            summoned.discard();
            // Looking for dry ground never loads a chunk: far, unloaded ground is simply "not a spot".
            net.minecraft.core.BlockPos far = new net.minecraft.core.BlockPos(1_000_000, 64, 1_000_000);
            check(!level.hasChunk(far.getX() >> 4, far.getZ() >> 4), "test setup: that chunk should not be loaded");
            check(!com.foglurker.brain.SpotFinder.isDry(level, far), "unloaded ground must not count as a dry spot");
            check(!level.hasChunk(far.getX() >> 4, far.getZ() >> 4), "checking for water loaded a chunk");
            h.succeed();
        });
    }

    /** The fog must do its job at every render distance a player can pick: 2 to 32 chunks. */
    @GameTest(template = "empty", batch = "night", timeoutTicks = 100)
    public static void fogWorksFromTwoToThirtyTwoChunks(GameTestHelper h) {
        logged(h, () -> {
            float[] stages = {0.15F, 0.3F, 0.6F, 0.85F, 1.0F}; // watching, stalking, luring, attacking, thickest
            for (int chunks = 2; chunks <= 32; chunks++) {
                float view = chunks * 16F;
                String rd = chunks + " chunks: ";
                check(com.foglurker.util.FogCurve.far(0F, view) == view, rd + "no fog must leave the view alone");
                check(com.foglurker.util.FogCurve.far(0.01F, view) > view * 0.9F, rd + "the fog must ease in, not pop");
                float startNear = com.foglurker.util.FogCurve.near(0.01F, com.foglurker.util.FogCurve.far(0.01F, view), view - 4F);
                check(startNear > (view - 4F) * 0.9F, rd + "where the fog starts must ease in too, not jump (" + startNear + ")");
                float last = view;
                for (float f : stages) {
                    float far = com.foglurker.util.FogCurve.far(f, view);
                    float near = com.foglurker.util.FogCurve.near(f, far, view - 4F);
                    check(far < view * 0.85F, rd + "fog " + f + " should be clearly visible, but you still see " + far + " of " + view);
                    check(far <= last + 1e-4F, rd + "thicker fog must never let you see further (" + far + " after " + last + ")");
                    check(far >= Math.min(view, com.foglurker.util.FogCurve.MIN_VIEW) - 1e-4F, rd + "fog " + f + " leaves only " + far + " blocks");
                    check(near >= 0F && near < far, rd + "the fog must start in front of where it ends (" + near + " / " + far + ")");
                    last = far;
                }
                float stalk = com.foglurker.util.FogCurve.far(0.3F, view);
                float attack = com.foglurker.util.FogCurve.far(0.85F, view);
                check(stalk <= 85F, rd + "stalking fog must not be pushed out of sight by a long render distance (" + stalk + ")");
                check(attack <= 12F, rd + "attack fog should close in to about 12 blocks or less, got " + attack);
            }
            // Odd inputs never produce nonsense.
            check(com.foglurker.util.FogCurve.far(Float.NaN, 192F) == 192F, "NaN fog must be ignored");
            check(com.foglurker.util.FogCurve.far(0.5F, 0F) == 0F, "a zero view must stay zero");
            check(com.foglurker.util.FogCurve.far(5F, 192F) == com.foglurker.util.FogCurve.far(1F, 192F), "fog above 1 is treated as 1");
            check(com.foglurker.util.FogCurve.far(1F, 1024F) <= 12F, "64 chunks works too");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "meet", timeoutTicks = 1200)
    public static void nightMeetingRunThenItKills(GameTestHelper h) {
        logged(h, () -> {
            flatten(h, 24);
            MinecraftServer server = h.getLevel().getServer();
            server.setDifficulty(Difficulty.NORMAL, true);
            ServerPlayer p = player(h, 1.5, 1.5);
            kickOtherPlayers(server, p);
            h.runAfterDelay(80, () -> logged(h, () -> {
                p.setHealth(20F);
                FogLurkerEntity l = LurkerDirector.spawnNightMeeting(p, true);
                check(l != null, "no spot for the night meeting");
                check(l.brain().encounter().mode() == LurkerBrain.Mode.NIGHT_MEETING && l.getDisguise() == Disguise.CRAWLER,
                        "it should be the pale crawler, meeting them");
                check(l.distanceTo(p) <= 14, "it should be right in front of them, it is " + l.distanceTo(p) + " away");
                h.runAfterDelay(LurkerBrain.MEETING_RUN + 2, () -> logged(h, () -> {
                    check(l.brain().phase() == LurkerBrain.Phase.GREET && p.getHealth() >= 20F,
                            "it must not touch them before the head start is over (" + l.brain().describe() + ")");
                }));
                h.runAfterDelay(LurkerBrain.MEETING_CHASE - 10, () -> logged(h, () -> {
                    check(l.brain().phase() == LurkerBrain.Phase.GREET && p.getHealth() >= 20F,
                            "5 seconds after RUN! it should only just be setting off");
                }));
                h.runAfterDelay(LurkerBrain.MEETING_CHASE + 5, () -> h.succeedWhen(() -> {
                    check(p.isDeadOrDying(), "once it catches them they die instantly (" + l.brain().describe() + ")");
                    l.brain().vanish(true);
                }));
            }));
        });
    }

    @GameTest(template = "empty", batch = "meet2", timeoutTicks = 800)
    public static void nightMeetingSometimesMeansIt(GameTestHelper h) {
        logged(h, () -> {
            flatten(h, 24);
            MinecraftServer server = h.getLevel().getServer();
            server.setDifficulty(Difficulty.NORMAL, true);
            ServerPlayer p = player(h, 1.5, 1.5);
            kickOtherPlayers(server, p);
            h.runAfterDelay(80, () -> logged(h, () -> {
                p.setHealth(20F);
                FogLurkerEntity l = LurkerDirector.spawnNightMeeting(p, false);
                check(l != null, "no spot for the night meeting");
                h.runAfterDelay(LurkerBrain.MEETING_LEAVE + 40, () -> logged(h, () -> {
                    check(l.isRemoved() || l.isFlopped(), "when it means it, it leaves after talking (" + l.brain().describe() + ")");
                    check(p.getHealth() >= 20F, "it promised not to hurt them");
                    check(LurkerDirector.leftAloneTicks(p) == 0, "no chase, so no 15 minute pass");
                    h.succeed();
                }));
            }));
        });
    }

    @GameTest(template = "empty", batch = "meet3", timeoutTicks = 800)
    public static void outrunTheNightMeetingAndItLeavesYouAlone(GameTestHelper h) {
        logged(h, () -> {
            flatten(h, 24);
            MinecraftServer server = h.getLevel().getServer();
            server.setDifficulty(Difficulty.NORMAL, true);
            ServerPlayer p = player(h, 1.5, 1.5);
            kickOtherPlayers(server, p);
            h.runAfterDelay(80, () -> logged(h, () -> {
                p.setHealth(20F);
                FogLurkerEntity l = LurkerDirector.spawnNightMeeting(p, true);
                check(l != null, "no spot for the night meeting");
                h.runAfterDelay(LurkerBrain.MEETING_CHASE + 3, () -> logged(h, () -> {
                    check(l.brain().phase() == LurkerBrain.Phase.ATTACK, "the chase should have started");
                    l.brain().vanish(false); // as if it lost them
                    long left = LurkerDirector.leftAloneTicks(p);
                    check(left > LurkerBrain.MEETING_IMMUNITY_TICKS - 40 && left <= LurkerBrain.MEETING_IMMUNITY_TICKS,
                            "getting away should mean 15 minutes of peace, got " + left + " ticks");
                    check(!LurkerDirector.freeCandidates(server).contains(p), "nothing should come for them for 15 minutes");
                    h.succeed();
                }));
            }));
        });
    }

    // ------------------------------------------------------------------ groups

    @GameTest(template = "empty", batch = "group", timeoutTicks = 200)
    public static void inAGroupItPicksTheWeakestOrTheEdge(GameTestHelper h) {
        logged(h, () -> {
            flatten(h, 24);
            MinecraftServer server = h.getLevel().getServer();
            // Steve 1, Steve 2 and Alex 1 stand together; Alex 2 is at the edge; Steve 2 is hurt.
            ServerPlayer steve1 = player(h, 1.5, 1.5);
            ServerPlayer steve2 = player(h, 3.5, 1.5);
            ServerPlayer alex1 = player(h, 2.5, 3.5);
            ServerPlayer alex2 = player(h, 12.5, 2.5);
            kickOtherPlayers(server, steve1, steve2, alex1, alex2);
            for (ServerPlayer p : List.of(steve1, steve2, alex1, alex2)) {
                p.setHealth(20F);
            }
            steve2.setHealth(7F);
            List<ServerPlayer> group = List.of(steve1, steve2, alex1, alex2);
            java.util.Map<ServerPlayer, Integer> picks = new java.util.HashMap<>();
            RandomSource r = RandomSource.create(42);
            for (int i = 0; i < 1000; i++) {
                picks.merge(LurkerDirector.pickInGroup(group, r), 1, Integer::sum);
            }
            int weak = picks.getOrDefault(steve2, 0);
            int edge = picks.getOrDefault(alex2, 0);
            check(weak > 420, "the hurt one should be picked about half the time, got " + weak + "/1000");
            check(edge > 360, "the one at the edge should be picked about 40% of the time, got " + edge + "/1000");
            check(weak + edge > 850, "the weakest or the edge should be its pick most of the time, got " + (weak + edge));
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "group2", timeoutTicks = 600)
    public static void midAttackItSwitchesToTheEasierOne(GameTestHelper h) {
        logged(h, () -> {
            flatten(h, 24);
            MinecraftServer server = h.getLevel().getServer();
            server.setDifficulty(Difficulty.NORMAL, true);
            ServerPlayer far = player(h, 1.5, 1.5);      // its target, 14 blocks from it
            ServerPlayer near = player(h, 12.5, 1.5);    // in the group, right next to it
            kickOtherPlayers(server, far, near);
            h.runAfterDelay(80, () -> logged(h, () -> {
                far.setHealth(20F);
                near.setHealth(20F);
                FogLurkerSavedData data = FogLurkerSavedData.get(server);
                LurkerDifficulty before = data.difficulty();
                data.setDifficulty(LurkerDifficulty.MEDIUM);
                FogLurkerEntity l;
                try {
                    l = hunter(h, far, 15.5, 1.5);
                } finally {
                    data.setDifficulty(before);
                }
                l.brain().setSwitchesTargets(true);
                check(l.brain().targetId().equals(far.getUUID()), "it should start on its own target");
                h.succeedWhen(() -> {
                    check(l.brain().targetId().equals(near.getUUID()), "it should switch to the much closer player ("
                            + l.brain().describe() + ")");
                    check(near.getHealth() < 20F, "it should bite the closer player");
                    check(far.getHealth() >= 20F, "the far one should be untouched");
                    check(!LurkerDirector.freeCandidates(server).contains(near), "the one it now hunts counts as hunted");
                    l.brain().vanish(true);
                });
            }));
        });
    }

    // ------------------------------------------------------------------ Gemini

    /** One request the fake Gemini server saw. */
    private record SeenRequest(String model, String path, String key, String body) {
    }

    /**
     * Runs the real Gemini client against a fake Gemini server on this machine (nothing reaches Google):
     * fallbacks, the thinking retry, a rejected key, rate limits, safety blocks, garbage replies, a hung
     * server, and that the key only ever travels in the header.
     */
    // The game-test server ticks far faster than 20/s, so this waits on wall-clock work with a big tick budget.
    @GameTest(template = "empty", batch = "gemini", timeoutTicks = 100000)
    public static void geminiClientHandlesEverything(GameTestHelper h) {
        LurkerDirector.naturalSpawnsPaused = true;
        java.util.concurrent.atomic.AtomicReference<String> outcome = new java.util.concurrent.atomic.AtomicReference<>();
        Thread runner = new Thread(() -> {
            try {
                geminiScenarios();
                outcome.set("OK");
            } catch (Throwable t) {
                outcome.set(t.getMessage() == null ? t.toString() : t.getMessage());
            }
        }, "FogLurker-GeminiTest");
        runner.setDaemon(true);
        runner.start();
        h.succeedWhen(() -> {
            String o = outcome.get();
            check(o != null, "the Gemini scenarios are still running");
            check(o.equals("OK"), o);
        });
    }

    private static void geminiScenarios() throws Exception {
        List<SeenRequest> seen = java.util.Collections.synchronizedList(new ArrayList<>());
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "FakeGemini");
            t.setDaemon(true);
            return t;
        }));
        server.createContext("/models/", ex -> {
            String path = ex.getRequestURI().toString();
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String model = path.substring("/models/".length(), path.indexOf(':'));
            seen.add(new SeenRequest(model, path, ex.getRequestHeaders().getFirst("x-goog-api-key"), body));
            int status = 200;
            String reply;
            switch (model) {
                case "gone" -> {
                    status = 404;
                    reply = "{\"error\":{\"code\":404,\"message\":\"models/gone is not found\",\"status\":\"NOT_FOUND\"}}";
                }
                case "ok-model" -> reply = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"let me think\",\"thought\":true},"
                        + "{\"text\":\"hey come here\"}]},\"finishReason\":\"STOP\"}]}";
                case "thinker" -> {
                    if (body.contains("thinkingBudget")) {
                        status = 400;
                        reply = "{\"error\":{\"code\":400,\"message\":\"Budget 0 is invalid. This model only works in thinking mode.\","
                                + "\"status\":\"INVALID_ARGUMENT\"}}";
                    } else {
                        reply = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]}}]}";
                    }
                }
                case "badkey" -> {
                    status = 400;
                    reply = "{\"error\":{\"code\":400,\"message\":\"API key not valid. Please pass a valid API key.\","
                            + "\"status\":\"INVALID_ARGUMENT\",\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.ErrorInfo\","
                            + "\"reason\":\"API_KEY_INVALID\",\"domain\":\"googleapis.com\"}]}}";
                }
                case "busy" -> {
                    status = 429;
                    reply = "{\"error\":{\"code\":429,\"message\":\"You exceeded your current quota\",\"status\":\"RESOURCE_EXHAUSTED\","
                            + "\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\",\"retryDelay\":\"37s\"}]}}";
                }
                case "private" -> {
                    status = 403;
                    reply = "{\"error\":{\"code\":403,\"message\":\"Permission denied on this model\",\"status\":\"PERMISSION_DENIED\"}}";
                }
                case "flaky" -> {
                    status = 502;
                    reply = "<html><body>Bad Gateway</body></html>";
                }
                case "safety" -> reply = "{\"candidates\":[{\"finishReason\":\"SAFETY\",\"index\":0}]}";
                case "blocked" -> reply = "{\"promptFeedback\":{\"blockReason\":\"PROHIBITED_CONTENT\"}}";
                case "overthink" -> reply = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"hmm\",\"thought\":true}]},"
                        + "\"finishReason\":\"MAX_TOKENS\"}]}";
                case "garbage" -> reply = "{\"candidates\":\"what\"}";
                case "hang" -> {
                    try {
                        Thread.sleep(6_000); // longer than the 2 s test timeout
                    } catch (InterruptedException ignored) {
                    }
                    reply = "{}";
                }
                default -> {
                    status = 500;
                    reply = "{}";
                }
            }
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        String previous = ApiKeyStore.storedKey();
        String key = "AIzaFAKE_test_key_0123456789abcdefXYZ";
        try {
            com.foglurker.chat.GeminiClient.setBaseUrlForTests("http://127.0.0.1:" + server.getAddress().getPort() + "/models/");
            com.foglurker.chat.GeminiClient.clearBackoff();
            ApiKeyStore.set(key);

            // Fallback past a missing model; thought parts are not part of the answer; the key is in the header only.
            var r = gem(List.of("gone", "ok-model"), key);
            check(r.text().orElse("").equals("hey come here"), "fallback/thought filtering gave " + r);
            check(seen.size() == 2 && seen.get(0).model().equals("gone") && seen.get(1).model().equals("ok-model"),
                    "expected gone then ok-model, saw " + seen.stream().map(SeenRequest::model).toList());
            for (SeenRequest q : seen) {
                check(key.equals(q.key()), "the key must be sent in the x-goog-api-key header");
                check(!q.path().contains(key) && !q.path().contains("key="), "the key leaked into the URL: " + q.path());
                check(q.body().contains("\"thinkingBudget\":0") && q.body().contains("\"maxOutputTokens\":1024"),
                        "unexpected request body " + q.body());
                check(!q.body().contains(key), "the key leaked into the request body");
            }

            // A model that must think: retried once without the budget, and remembered.
            seen.clear();
            check(gem(List.of("thinker"), key).text().orElse("").equals("ok"), "thinking retry failed");
            check(seen.size() == 2 && !seen.get(1).body().contains("thinkingBudget") && seen.get(1).body().contains("4096"),
                    "the retry should drop the budget and allow room to think");
            seen.clear();
            check(gem(List.of("thinker"), key).text().isPresent() && seen.size() == 1, "it should remember the model must think");

            // A model the project cannot use (403) is skipped; a 502 HTML page is skipped.
            seen.clear();
            check(gem(List.of("private", "flaky", "ok-model"), key).text().isPresent() && seen.size() == 3,
                    "403 and 502 should fall through to the next model");
            check(com.foglurker.chat.GeminiClient.available(List.of("ok-model")), "a model-specific 403 must not disable the key");

            // Rate limit: tries the next model, and skips the limited one for as long as Google asked.
            seen.clear();
            check(gem(List.of("busy", "ok-model"), key).text().isPresent(), "a 429 should fall through to the next model");
            check(com.foglurker.chat.GeminiClient.available(List.of("busy", "ok-model")), "one limited model must not pause Gemini");
            seen.clear();
            check(gem(List.of("busy", "ok-model"), key).text().isPresent() && seen.size() == 1
                    && seen.get(0).model().equals("ok-model"), "the limited model should be skipped, saw "
                    + seen.stream().map(SeenRequest::model).toList());
            check(!com.foglurker.chat.GeminiClient.available(List.of("busy")), "with every model limited it should wait");
            String paused = com.foglurker.chat.GeminiClient.pausedReason(List.of("busy"));
            check(paused != null && paused.matches("rate limited by Google; retrying in 3[0-8]s"),
                    "it should wait the 37 s Google asked for: " + paused);
            seen.clear();
            var limited = gem(List.of("busy"), key);
            check(limited.status() == 429 && !limited.text().isPresent() && seen.isEmpty(),
                    "while limited it should not ask at all");
            com.foglurker.chat.GeminiClient.clearBackoff();
            check(com.foglurker.chat.GeminiClient.available(List.of("busy")), "clearBackoff should lift the limit");

            // A bad key: stops at once (no point asking other models), pauses, and says why without the key.
            seen.clear();
            var bad = gem(List.of("badkey", "ok-model"), key);
            check(!bad.text().isPresent() && seen.size() == 1, "an invalid key should not be tried on every model");
            check(!com.foglurker.chat.GeminiClient.available(List.of("ok-model")), "a rejected key should pause Gemini");
            String why = com.foglurker.chat.GeminiClient.pausedReason(List.of("ok-model"));
            check(why != null && why.contains("API key not valid") && !why.contains(key), "pause reason: " + why);
            String other = "AIzaOTHER_test_key_0123456789abcdefXYZ";
            ApiKeyStore.set(other);
            check(com.foglurker.chat.GeminiClient.available(List.of("ok-model")), "setting a different key should work straight away");
            ApiKeyStore.set(key);
            check(gem(List.of("ok-model"), key).text().isPresent(), "an explicit test of the key still runs while paused");
            check(com.foglurker.chat.GeminiClient.available(List.of("ok-model")), "a key that works again un-pauses Gemini");

            // Empty answers say why.
            check(gem(List.of("safety"), key).error().contains("SAFETY"), "safety block should be reported");
            check(gem(List.of("blocked"), key).error().contains("PROHIBITED_CONTENT"), "prompt block should be reported");
            check(gem(List.of("overthink"), key).error().contains("token limit"), "MAX_TOKENS should be reported");
            check(gem(List.of("garbage"), key).error().contains("Unreadable"), "garbage should be reported");
            check(gem(List.of("nope-500"), key).status() == 500, "a 500 on the last model should be reported");

            // A key that could never be sent is refused before any request.
            seen.clear();
            var weird = gem(List.of("ok-model"), "AIza key with spaces 0123456789");
            check(!weird.text().isPresent() && seen.isEmpty(), "an impossible key must not be sent");
            check(!ApiKeyStore.isPlausible("short") && !ApiKeyStore.isPlausible("AIzaéabcdefghijklmnopqrstu")
                    && ApiKeyStore.isPlausible(key), "key format check");
            check(ApiKeyStore.masked("shortkey12345").equals("****"), "short keys must be fully masked");

            // A hung server: the request times out and the future still completes.
            long t0 = System.currentTimeMillis();
            var hung = gem(List.of("hang"), key);
            long took = System.currentTimeMillis() - t0;
            check(!hung.text().isPresent() && took < com.foglurker.chat.GeminiClient.overallTimeoutSeconds() * 1000L,
                    "a hung server should fail within the time limit (" + took + " ms, " + hung + ")");

            // Model names from the config are cleaned up; a bad one can never change the URL.
            check(com.foglurker.chat.GeminiClient.cleanModels(java.util.Arrays.asList("models/gemini-2.5-flash", " gemini-2.5-flash ",
                    "../evil", "a/b", "x?key=1", "", null)).equals(List.of("gemini-2.5-flash")), "model name cleaning");

            // What Gemini writes is cleaned before it reaches chat.
            var ctx = new MimicChat.Context("Steve", "Alex", null, "north", false, List.of());
            String clean = MimicChat.sanitize("**<Steve> hey​ come‮ here§c!**\nsecond line", ctx);
            check(clean.equals("hey come here!"), "sanitize gave \"" + clean + "\"");
            String emoji = MimicChat.clip("a".repeat(119) + "😀", 120);
            check(emoji.equals("a".repeat(119)), "clip split an emoji");
            check(MimicChat.sanitize("\"\u0007\"", ctx).isEmpty(), "control characters should be removed");

            // parse() never throws on nonsense.
            for (String junk : new String[]{"", "null", "[]", "{\"candidates\":[1]}", "{\"candidates\":[{\"content\":{\"parts\":[5]}}]}", "<html>"}) {
                var p = com.foglurker.chat.GeminiClient.parse(200, junk);
                check(!p.text().isPresent(), "junk reply produced text: " + junk);
            }
        } finally {
            com.foglurker.chat.GeminiClient.setBaseUrlForTests(null);
            com.foglurker.chat.GeminiClient.clearBackoff();
            ApiKeyStore.set(previous);
            server.stop(0);
        }
    }

    private static com.foglurker.chat.GeminiClient.Result gem(List<String> models, String key) throws Exception {
        var f = com.foglurker.chat.GeminiClient.generate(models, key, "system", "user", 1.0F);
        var r = f.get(com.foglurker.chat.GeminiClient.overallTimeoutSeconds() + 5L, java.util.concurrent.TimeUnit.SECONDS);
        check(r != null, "no result");
        return r;
    }

    // ------------------------------------------------------------------ the bite

    private static net.minecraft.world.entity.vehicle.Boat boatAt(GameTestHelper h, double rx, double rz) {
        Vec3 at = h.absoluteVec(new Vec3(rx, 0, rz));
        var boat = new net.minecraft.world.entity.vehicle.Boat(net.minecraft.world.entity.EntityType.BOAT, h.getLevel());
        boat.setPos(at.x, at.y, at.z);
        h.getLevel().addFreshEntity(boat);
        return boat;
    }

    @GameTest(template = "empty", batch = "bite", timeoutTicks = 200)
    public static void theBiteSlowsAndMauls(GameTestHelper h) {
        logged(h, () -> {
            flatten(h, 16);
            MinecraftServer server = h.getLevel().getServer();
            ServerPlayer easy = player(h, 1.5, 1.5);
            ServerPlayer hard = player(h, 1.5, 5.5);
            ServerPlayer rower = player(h, 1.5, 9.5);
            kickOtherPlayers(server, easy, hard, rower);
            var rowboat = boatAt(h, 1.5, 9.5);
            h.runAfterDelay(80, () -> logged(h, () -> {
                FogLurkerSavedData data = FogLurkerSavedData.get(server);
                LurkerDifficulty before = data.difficulty();
                var mauled = com.foglurker.registry.ModEffects.MAULED.get();
                var slow = net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN;
                try {
                    // Every mode: Slowness I for 10 seconds, but you can still jump and row away.
                    data.setDifficulty(LurkerDifficulty.EASY);
                    easy.setHealth(20F);
                    FogLurkerEntity a = hunter(h, easy, 3.5, 1.5);
                    check(a.doHurtTarget(easy), "the easy bite did not land");
                    var s1 = easy.getEffect(slow);
                    check(s1 != null && s1.getAmplifier() == 0 && s1.getDuration() > 190 && s1.getDuration() <= 200,
                            "easy: expected Slowness I for 10 s, got " + s1);
                    check(!easy.hasEffect(mauled), "easy bites must not maul");
                    check(easy.startRiding(boatAt(h, 3.5, 3.5)), "on easy you can still get into a boat");
                    a.brain().vanish(false);

                    // Hard: Slowness I and Mauled: no jumping, no boats.
                    data.setDifficulty(LurkerDifficulty.HARD);
                    hard.setHealth(20F);
                    FogLurkerEntity b = hunter(h, hard, 3.5, 5.5);
                    check(b.doHurtTarget(hard), "the hard bite did not land");
                    check(Math.abs(hard.getHealth() - 8F) < 0.01F, "hard bite should still deal 6 hearts, health " + hard.getHealth());
                    var s2 = hard.getEffect(slow);
                    check(s2 != null && s2.getAmplifier() == 0 && s2.getDuration() > 190, "hard: no Slowness I (" + s2 + ")");
                    var m = hard.getEffect(mauled);
                    check(m != null && m.getDuration() > 190 && m.getDuration() <= 200, "hard: no 10 s Mauled (" + m + ")");
                    hard.setDeltaMovement(Vec3.ZERO);
                    hard.jumpFromGround();
                    check(hard.getDeltaMovement().y <= 0.0, "a mauled player still jumped (vy " + hard.getDeltaMovement().y + ")");
                    hard.setSprinting(true);
                    hard.setYRot(37F);
                    hard.setDeltaMovement(Vec3.ZERO);
                    hard.jumpFromGround();
                    check(hard.getDeltaMovement().length() < 1e-4, "a mauled sprint-jump still gave a boost (" + hard.getDeltaMovement() + ")");
                    hard.setSprinting(false);
                    var boat = boatAt(h, 3.5, 7.5);
                    check(!hard.startRiding(boat), "a mauled player got into a boat");
                    check(!hard.isPassenger(), "a mauled player is sitting in a boat");
                    hard.removeEffect(mauled);
                    hard.jumpFromGround();
                    check(hard.getDeltaMovement().y > 0.3, "once it wears off you can jump again");
                    check(hard.startRiding(boat), "once it wears off you can get into a boat again");
                    hard.stopRiding();
                    b.brain().vanish(false);

                    // Hard, already in a boat: the bite drags you out.
                    check(rower.startRiding(rowboat), "test setup: could not seat the rower");
                    rower.setHealth(20F);
                    FogLurkerEntity c = hunter(h, rower, 3.5, 9.5);
                    check(c.doHurtTarget(rower), "the bite on the rower did not land");
                    check(!rower.isPassenger(), "a hard bite should drag you out of your boat");
                    c.brain().vanish(false);
                } finally {
                    data.setDifficulty(before);
                    for (var b : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.vehicle.Boat.class,
                            new net.minecraft.world.phys.AABB(h.absolutePos(net.minecraft.core.BlockPos.ZERO)).inflate(20))) {
                        b.ejectPassengers();
                        b.discard();
                    }
                }
                h.succeed();
            }));
        });
    }

    @GameTest(template = "empty", batch = "windup", timeoutTicks = 400)
    public static void deathwishScreamsBeforeItKills(GameTestHelper h) {
        logged(h, () -> {
            flatten(h, 16);
            MinecraftServer server = h.getLevel().getServer();
            server.setDifficulty(Difficulty.NORMAL, true);
            ServerPlayer p = player(h, 1.5, 1.5);
            kickOtherPlayers(server, p);
            h.runAfterDelay(80, () -> logged(h, () -> {
                FogLurkerSavedData data = FogLurkerSavedData.get(server);
                LurkerDifficulty before = data.difficulty();
                data.setDifficulty(LurkerDifficulty.DEATHWISH);
                p.setHealth(20F);
                FogLurkerEntity l;
                try {
                    l = hunter(h, p, 2.7, 1.5); // already within reach
                } finally {
                    data.setDifficulty(before);
                }
                h.runAfterDelay(LurkerBrain.DEATHWISH_WINDUP - 8, () -> logged(h, () ->
                        check(p.isAlive() && p.getHealth() >= 20F, "deathwish bit before its wind-up was over")));
                h.runAfterDelay(LurkerBrain.DEATHWISH_WINDUP - 4, () -> h.succeedWhen(() -> {
                    check(p.isDeadOrDying(), "after the wind-up it should kill (" + l.brain().describe() + ")");
                    l.brain().vanish(false);
                }));
            }));
        });
    }

    @GameTest(template = "empty", batch = "bell2", timeoutTicks = 600)
    public static void bellsLoseTheirPowerAndEscapesEscalate(GameTestHelper h) {
        logged(h, () -> {
            flatten(h, 24);
            MinecraftServer server = h.getLevel().getServer();
            server.setDifficulty(Difficulty.NORMAL, true);
            ServerPlayer p = player(h, 1.5, 1.5);
            kickOtherPlayers(server, p);
            LurkerDirector.resetBells();
            FogLurkerSavedData data = FogLurkerSavedData.get(server);
            var profile = data.profile(p.getUUID());
            check(Math.abs(LurkerDirector.attackChance(LurkerDifficulty.EASY, profile) - 0.2) < 1e-9, "fresh players get the base chance");
            profile.escapes = 7;
            check(Math.abs(LurkerDirector.attackChance(LurkerDifficulty.EASY, profile) - 0.5) < 1e-9, "the bonus is capped at +30%");
            check(LurkerDirector.attackChance(LurkerDifficulty.DEATHWISH, profile) <= 1.0, "chance never goes above 100%");
            profile.escapes = 0;
            h.runAfterDelay(80, () -> logged(h, () -> {
                LurkerDifficulty before = data.difficulty();
                data.setDifficulty(LurkerDifficulty.MEDIUM);
                FogLurkerEntity first = hunter(h, p, 14.5, 1.5);
                FogLurkerEntity second = null;
                try {
                    check(LurkerDirector.onBell(h.getLevel(), p.blockPosition(), p) == 1, "the first bell should work");
                    check(first.brain().phase() == LurkerBrain.Phase.FLEE, "after the first bell it should flee");
                    check(profile.escapes == 1, "escaping with a bell should count as an escape, got " + profile.escapes);
                    check(Math.abs(LurkerDirector.attackChance(LurkerDifficulty.MEDIUM, profile) - (1.0 / 3.0 + 0.1)) < 1e-9,
                            "one escape = +10%");
                    second = hunter(h, p, 14.5, 3.5);
                    check(LurkerDirector.onBell(h.getLevel(), p.blockPosition(), p) == 0, "a second bell so soon should not work");
                    check(second.brain().phase() == LurkerBrain.Phase.ATTACK, "it should keep coming through the bell");
                } finally {
                    data.setDifficulty(before);
                }
                FogLurkerEntity hunting = second;
                p.setHealth(20F);
                h.succeedWhen(() -> {
                    check(p.getHealth() < 20F, "it has not reached the player yet (" + hunting.brain().describe() + ")");
                    check(profile.escapes == 0, "landing a hit should reset the escapes, still " + profile.escapes);
                    hunting.brain().vanish(false);
                    LurkerDirector.resetBells();
                });
            }));
        });
    }

    @GameTest(template = "empty", batch = "gate", timeoutTicks = 200)
    public static void neverTwoAtOnceForOnePlayer(GameTestHelper h) {
        logged(h, () -> {
            flatten(h, 16);
            MinecraftServer server = h.getLevel().getServer();
            ServerPlayer hunted = player(h, 1.5, 1.5);
            ServerPlayer free = player(h, 1.5, 6.5);
            kickOtherPlayers(server, hunted, free);
            FogLurkerEntity l = hunter(h, hunted, 6.5, 1.5);
            List<ServerPlayer> c = LurkerDirector.freeCandidates(server);
            check(!c.contains(hunted), "a player who is already being hunted was offered a second one");
            check(c.contains(free), "the other player should still be up for a visit");
            l.brain().vanish(true);
            h.runAfterDelay(5, () -> logged(h, () -> {
                check(!LurkerDirector.freeCandidates(server).contains(hunted), "it should not come straight back for the same player");
                h.succeed();
            }));
        });
    }

    @GameTest(template = "empty", batch = "chat", timeoutTicks = 100)
    public static void chatLearningCanBeTurnedOff(GameTestHelper h) {
        logged(h, () -> {
            MinecraftServer server = h.getLevel().getServer();
            ServerPlayer p = player(h, 1.5, 1.5);
            FogLurkerSavedData data = FogLurkerSavedData.get(server);
            var profile = data.profile(p.getUUID());
            profile.addChat("omg wait for me");
            profile.addChat("brb");
            String name = p.getGameProfile().getName();
            com.foglurker.learning.RecentChat.add(name, "omg wait for me");
            var cmds = server.getCommands().getDispatcher();
            cmds.execute("fglurk chatlearning off", p.createCommandSourceStack());
            check(profile.chatLearningOff, "chatlearning off did not stick");
            check(profile.chatSamples.isEmpty(), "turning it off should forget what it kept");
            check(com.foglurker.learning.RecentChat.last(16).stream().noneMatch(l -> l.startsWith("<" + name + "> ")),
                    "their lines are still in the recent chat it sends to Gemini");
            profile.addChat("this must not be kept");
            check(profile.chatSamples.isEmpty(), "it learned from chat after it was turned off");
            var saved = com.foglurker.learning.PlayerBehaviorProfile.load(profile.save());
            check(saved.chatLearningOff, "the choice must survive a restart");
            cmds.execute("fglurk chatlearning on", p.createCommandSourceStack());
            profile.addChat("ok back on");
            check(profile.chatSamples.size() == 1, "it should learn again after turning it back on");
            h.succeed();
        });
    }

    /** Runs {@code then} as soon as {@code done} is true, or after {@code maxTicks} ticks, whichever is first. */
    private static void waitUntil(GameTestHelper h, int maxTicks, java.util.function.BooleanSupplier done, Runnable then) {
        if (maxTicks <= 0 || done.getAsBoolean()) {
            then.run();
        } else {
            h.runAfterDelay(1, () -> waitUntil(h, maxTicks - 1, done, then));
        }
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
            check(server.getCommands().getDispatcher().execute("foglurker-macarena 1", p.createCommandSourceStack().withPermission(0)) == 0
                    && h.getLevel().getEntities(com.foglurker.registry.ModEntities.FOG_LURKER.get(), FogLurkerEntity::isDancing).isEmpty(),
                    "/foglurker-macarena must be cheats only (and say so)");
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

    @GameTest(template = "empty", batch = "force", timeoutTicks = 1400)
    public static void forceHideStalkAttackAndEndGrace(GameTestHelper h) {
        logged(h, () -> {
            flatten(h, 30);
            ServerLevel level = h.getLevel();
            MinecraftServer server = level.getServer();
            ServerPlayer p = player(h, 1.5, 1.5);
            kickOtherPlayers(server, p);
            var d = server.getCommands().getDispatcher();
            var op = p.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            var noCheats = p.createCommandSourceStack().withPermission(0).withSuppressedOutput();
            check(d.execute("foglurker endgrace", noCheats) == 0, "endgrace must be cheats only");
            int out = LurkerDirector.active().size();
            check(d.execute("foglurker force attack", noCheats) == 0 && LurkerDirector.active().size() == out,
                    "force must be cheats only");
            // Without cheats the command is still found (no "Incorrect argument for command"), it just refuses.
            check(d.parse("foglurker force attack", noCheats).getExceptions().isEmpty()
                    && d.parse("foglurker force attack", noCheats).getContext().getCommand() != null,
                    "players without cheats should get a clear refusal, not a parse error");

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
                // Up to 13 s to walk to the grass (it can appear 15-55 blocks away) and lie down.
                h.runAfterDelay(260, () -> logged(h, () -> {
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
                    // Give it up to 2 s to notice (it checks every tick; the test server runs far faster than 20/s).
                    waitUntil(h, 40, () -> stalker.isRemoved() || stalker.isLying(), () -> logged(h, () -> {
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
                // A voice with no opt-in on record is never kept: a start-up sweep deletes it, and leaves opted-in players alone.
                UUID orphan = UUID.randomUUID();
                UUID keeper = UUID.randomUUID();
                VoiceConsent.optIn(keeper);
                java.nio.file.Path orphanFile = SecureStorage.voiceRoot().resolve(orphan.toString()).resolve("1-a.snip");
                java.nio.file.Path keeperFile = SecureStorage.voiceRoot().resolve(keeper.toString()).resolve("1-a.snip");
                SecureStorage.writeEncrypted(orphanFile, new byte[]{1, 2, 3, 4});
                SecureStorage.writeEncrypted(keeperFile, new byte[]{1, 2, 3, 4});
                com.foglurker.voice.VoiceRetention.onStart(now + day);
                check(!java.nio.file.Files.exists(orphanFile) && !java.nio.file.Files.exists(orphanFile.getParent()),
                        "a snippet with no opt-in on record must be deleted at start-up");
                check(java.nio.file.Files.exists(keeperFile), "the sweep must not touch an opted-in player's snippets");
                check(!VoiceConsent.hasUnsavedChanges(), "consent changes should be on disk");
                VoiceConsent.optOut(keeper);
                check(!java.nio.file.Files.exists(keeperFile), "opting out deletes the snippets");
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
        String realKey = ApiKeyStore.storedKey(); // put back afterwards so tests never delete a real key
        ApiKeyStore.set(key);
        check(key.equals(ApiKeyStore.get()), "API key did not round-trip");
        String onDisk = new String(Files.readAllBytes(SecureStorage.root().resolve("gemini_api_key.enc")),
                StandardCharsets.ISO_8859_1);
        check(!onDisk.contains("AIza"), "API key is stored in plain text");
        check(ApiKeyStore.masked(key).equals("AIza…cdef"), "masking is wrong: " + ApiKeyStore.masked(key));
        ApiKeyStore.set(null);
        check(!Files.exists(SecureStorage.root().resolve("gemini_api_key.enc")), "clearing did not delete the key file");
        ApiKeyStore.set(realKey);

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
