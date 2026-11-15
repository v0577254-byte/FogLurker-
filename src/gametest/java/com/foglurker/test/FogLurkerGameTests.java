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

    /** Leftover mock players from earlier tests would otherwise be picked as "friends" to copy. */
    private static void kickOtherPlayers(MinecraftServer server, ServerPlayer... keep) {
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
        check(LurkerDifficulty.byId("medium") == LurkerDifficulty.MEDIUM, "byId");
        data.setDifficulty(before);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "disguises", timeoutTicks = 500)
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
            check(LurkerDirector.active().isEmpty(), "vanished creatures are still tracked");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "mimic", timeoutTicks = 1400)
    public static void mimicWalksUpGreetsAndLures(GameTestHelper h) {
        logged(h, () -> mimicWalksUpGreetsAndLuresImpl(h));
    }

    private static void mimicWalksUpGreetsAndLuresImpl(GameTestHelper h) throws Exception {
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
            check(!l.isRemoved(), "the mimic vanished before luring (phase " + l.brain().phase() + ")");
            check(l.brain().phase().ordinal() >= LurkerBrain.Phase.LURE.ordinal(),
                    "still in phase " + l.brain().phase() + " at distance " + l.distanceTo(target));
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
                check(l.getDisguise() == Disguise.NONE, "it has not revealed itself yet (phase " + l.brain().phase()
                        + ", " + l.distanceTo(p) + " blocks away)");
                check(p.getHealth() <= 10.01F, "no 5-heart hit landed yet (health " + p.getHealth() + ")");
                l.brain().vanish(false);
            });
        }));
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
