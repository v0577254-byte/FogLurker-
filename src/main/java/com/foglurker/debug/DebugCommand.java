package com.foglurker.debug;

import com.foglurker.FogLurkerMod;
import com.foglurker.brain.LurkerBrain;
import com.foglurker.brain.LurkerDirector;
import com.foglurker.brain.SpotFinder;
import com.foglurker.chat.ChatStyle;
import com.foglurker.chat.GeminiClient;
import com.foglurker.chat.MimicChat;
import com.foglurker.config.FogLurkerConfig;
import com.foglurker.config.LurkerDifficulty;
import com.foglurker.entity.Disguise;
import com.foglurker.entity.FogLurkerEntity;
import com.foglurker.learning.EntityBehaviorStats;
import com.foglurker.learning.PlayerBehaviorProfile;
import com.foglurker.network.DebugPacket;
import com.foglurker.network.ModNetwork;
import com.foglurker.registry.ModDamageTypes;
import com.foglurker.registry.ModEntities;
import com.foglurker.registry.ModSounds;
import com.foglurker.security.ApiKeyStore;
import com.foglurker.security.SecureStorage;
import com.foglurker.voice.AudioFx;
import com.foglurker.voice.SnippetStore;
import com.foglurker.voice.VoiceBridge;
import com.foglurker.voice.VoiceConsent;
import com.foglurker.world.FogLurkerSavedData;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * TEMPORARY: {@code /foglurker debugeverything}. Runs every check it can on the server, asks your
 * client to check its side (renderer, model, textures, sounds), and prints what passed and failed
 * in chat. Delete this package, {@code DebugPacket}, {@code ClientDebug} and the two lines marked
 * TEMPORARY before releasing.
 */
public final class DebugCommand {
    private DebugCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("debugeverything")
                .requires(s -> s.getEntity() instanceof ServerPlayer p
                        && (s.hasPermission(2) || s.getServer().isSingleplayerOwner(p.getGameProfile())))
                .executes(DebugCommand::run);
    }

    /** Collects results and prints them. */
    private static final class Report {
        private final CommandSourceStack src;
        int passed;
        int failed;
        int skipped;

        Report(CommandSourceStack src) {
            this.src = src;
        }

        void section(String title) {
            send(Component.literal("— " + title + " —").withStyle(ChatFormatting.GOLD));
        }

        void pass(String what, String detail) {
            passed++;
            line("✔ ", ChatFormatting.GREEN, what, detail);
        }

        void fail(String what, String detail) {
            failed++;
            line("✘ ", ChatFormatting.RED, what, detail);
        }

        void skip(String what, String detail) {
            skipped++;
            line("• ", ChatFormatting.GRAY, what, detail);
        }

        void check(String what, boolean ok, String detail) {
            if (ok) {
                pass(what, detail);
            } else {
                fail(what, detail);
            }
        }

        /** Runs a check; any exception counts as a failure with its message. */
        void run(String what, CheckBody body) {
            try {
                body.run(this, what);
            } catch (Throwable t) {
                FogLurkerMod.LOGGER.error("debugeverything: '{}' threw", what, t);
                fail(what, t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage()));
            }
        }

        private void line(String mark, ChatFormatting color, String what, String detail) {
            MutableComponent c = Component.literal(mark).withStyle(color)
                    .append(Component.literal(what).withStyle(ChatFormatting.WHITE));
            if (detail != null && !detail.isEmpty()) {
                c.append(Component.literal(" — " + detail).withStyle(ChatFormatting.GRAY));
            }
            send(c);
        }

        void send(Component c) {
            src.sendSuccess(() -> c, false);
            FogLurkerMod.LOGGER.info("[debugeverything] {}", c.getString());
        }
    }

    @FunctionalInterface
    private interface CheckBody {
        void run(Report r, String what) throws Exception;
    }

    private static int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        MinecraftServer server = src.getServer();
        Report r = new Report(src);
        r.send(Component.literal("[Fog Lurker] debugeverything (temporary)").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));

        registration(r, player, server);
        modes(r, server);
        spawning(r, player);
        combatAndWater(r, player);
        learning(r, player, server);
        voiceAndSecurity(r, player);
        gemini(r, server, src);
        runtime(r);

        r.send(Component.literal("Server: " + r.passed + " passed, " + r.failed + " failed, " + r.skipped + " skipped")
                .withStyle(r.failed == 0 ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD));
        if (ModNetwork.CHANNEL.isRemotePresent(player.connection.connection)) {
            r.send(Component.literal("Asking your game to check its side (models, textures, sounds)...").withStyle(ChatFormatting.GRAY));
            ModNetwork.send(player, new DebugPacket());
        }
        return r.failed == 0 ? 1 : 0;
    }

    // ------------------------------------------------------------------ sections

    private static void registration(Report r, ServerPlayer player, MinecraftServer server) {
        r.section("Registration");
        r.run("Entity registered", (rep, w) -> {
            var key = ForgeRegistries.ENTITY_TYPES.getKey(ModEntities.FOG_LURKER.get());
            rep.check(w, FogLurkerMod.id("fog_lurker").equals(key), String.valueOf(key));
        });
        r.run("Entity attributes", (rep, w) -> rep.check(w, DefaultAttributes.hasSupplier(ModEntities.FOG_LURKER.get()),
                "max health, speed, follow range"));
        r.run("Sounds registered", (rep, w) -> {
            List<RegistryObject<?>> sounds = List.of(ModSounds.SCREAM, ModSounds.REVEAL, ModSounds.WHISPER, ModSounds.VANISH,
                    ModSounds.HEARTBEAT, ModSounds.HURT, ModSounds.ATTACK);
            long ok = sounds.stream().filter(RegistryObject::isPresent).count();
            rep.check(w, ok == sounds.size(), ok + "/" + sounds.size());
        });
        var damageTypes = server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        for (ResourceKey<DamageType> key : List.of(ModDamageTypes.LURKER, ModDamageTypes.LURKER_ONESHOT)) {
            r.run("Damage type " + key.location().getPath(), (rep, w) -> {
                var holder = damageTypes.getHolder(key);
                if (holder.isEmpty()) {
                    rep.fail(w, "missing from the data pack");
                    return;
                }
                Holder<DamageType> h = holder.get();
                boolean oneShot = key == ModDamageTypes.LURKER_ONESHOT;
                List<TagKey<DamageType>> want = oneShot
                        ? List.of(DamageTypeTags.BYPASSES_ARMOR, DamageTypeTags.BYPASSES_EFFECTS, DamageTypeTags.BYPASSES_ENCHANTMENTS,
                        DamageTypeTags.BYPASSES_RESISTANCE, DamageTypeTags.BYPASSES_SHIELD)
                        : List.of(DamageTypeTags.BYPASSES_ARMOR);
                List<String> missing = new ArrayList<>();
                for (TagKey<DamageType> t : want) {
                    if (!h.is(t)) {
                        missing.add(t.location().getPath());
                    }
                }
                rep.check(w, missing.isEmpty(), missing.isEmpty() ? (oneShot ? "ignores armour, effects, enchants, resistance, shields"
                        : "ignores armour") : "not tagged: " + missing);
            });
        }
        r.run("Mod files", (rep, w) -> {
            IModFileInfo file = ModList.get().getModFileById(FogLurkerMod.MOD_ID);
            String[] paths = {"assets/foglurker/lang/en_us.json", "assets/foglurker/sounds.json",
                    "assets/foglurker/textures/entity/crawler.png", "assets/foglurker/textures/entity/crawler_eyes.png",
                    "data/foglurker/damage_type/lurker.json", "data/foglurker/damage_type/lurker_oneshot.json",
                    "foglurker_logo.png"};
            List<String> missing = new ArrayList<>();
            for (String p : paths) {
                if (file == null || !Files.exists(file.getFile().findResource(p))) {
                    missing.add(p);
                }
            }
            rep.check(w, missing.isEmpty(), missing.isEmpty() ? paths.length + " resources present" : "missing " + missing);
        });
        r.run("Commands", (rep, w) -> {
            var root = server.getCommands().getDispatcher().getRoot();
            boolean ok = root.getChild("foglurker") != null && root.getChild("flurk") != null && root.getChild("fglurk") != null;
            rep.check(w, ok, "/foglurker, /flurk, /fglurk");
        });
        r.run("Your game has the mod", (rep, w) -> rep.check(w,
                ModNetwork.CHANNEL.isRemotePresent(player.connection.connection),
                "needed to see disguises and fog"));
    }

    private static void modes(Report r, MinecraftServer server) {
        r.section("Modes");
        FogLurkerSavedData data = FogLurkerSavedData.get(server);
        LurkerDifficulty d = data.difficulty();
        r.pass("Current mode", d.displayName() + ", grace left " + (data.graceRemainingTicks() / 20) + "s, next spawn "
                + (!data.naturalSpawning() ? "never (natural spawning off)"
                : (data.inGrace() ? "after grace" : data.nextSpawnTicks() < 0 ? "scheduling" : (data.nextSpawnTicks() / 20) + "s")
                + (data.nextIsGuaranteed() ? " (guaranteed)" : " (" + Math.round(FogLurkerConfig.COMMON.spawnChance.get() * 100) + "% chance)")));
        // name, grace min, hearts per hit (-1 = one shot), cooldown s, its own health in hearts, night fog
        Object[][] spec = {
                {LurkerDifficulty.VERY_EASY, 45, 1F, 3.0F, 50, 1.10F},
                {LurkerDifficulty.EASY, 30, 3F, 2.5F, 75, 1.15F},
                {LurkerDifficulty.MEDIUM, 20, 5F, 2.0F, 100, 1.25F},
                {LurkerDifficulty.HARD, 10, 6F, 1.75F, 125, 1.35F},
                {LurkerDifficulty.DEATHWISH, 5, -1F, 1.5F, 175, 1.50F},
        };
        for (Object[] row : spec) {
            LurkerDifficulty m = (LurkerDifficulty) row[0];
            boolean ok = m.graceMinutes() == (int) row[1]
                    && (m.isOneShot() ? (float) row[2] < 0 : m.heartsPerHit() == (float) row[2])
                    && Math.abs(m.attackCooldownSeconds() - (float) row[3]) < 1e-4
                    && m.lurkerHearts() == (int) row[4]
                    && Math.abs(m.nightFogMultiplier() - (float) row[5]) < 1e-4;
            r.check(m.displayName(), ok, "it has " + m.lurkerHearts() + " hearts; " + (m.isOneShot() ? "one shot" : m.heartsPerHit() + " hearts") + ", "
                    + m.attackCooldownSeconds() + "s between hits, " + m.graceMinutes() + " min grace, attacks "
                    + Math.round(m.attackChance() * 100) + "% of spawns" + (m.speedMultiplier() != 1 ? ", 50% faster" : ""));
        }
    }

    private static void spawning(Report r, ServerPlayer player) {
        r.section("Spawning (dry run near you, nothing is spawned)");
        ServerLevel level = player.serverLevel();
        for (Disguise d : Disguise.values()) {
            r.run(d.id(), (rep, w) -> {
                LurkerDirector.DryRun run = LurkerDirector.dryRun(player, d, false);
                if (run.spot() == null) {
                    rep.fail(w, "no dry, loaded ground near you to appear on");
                    return;
                }
                BlockPos feet = BlockPos.containing(run.spot());
                boolean dry = SpotFinder.isDry(level, feet);
                FogLurkerEntity probe = ModEntities.FOG_LURKER.get().create(level);
                probe.moveTo(run.spot());
                probe.begin(run.encounter());
                boolean shaped = probe.getDisguise() == d;
                probe.discard();
                rep.check(w, dry && shaped, "mode " + run.encounter().mode().name().toLowerCase(Locale.ROOT)
                        + ", spot " + Math.round(run.spot().distanceTo(player.position())) + " blocks away"
                        + (dry ? ", dry" : ", WET") + (shaped ? "" : ", wrong shape " + probe.getDisguise()));
            });
        }
        r.run("Never spawns on water", (rep, w) -> {
            BlockPos water = nearestWater(level, player.blockPosition(), 24);
            if (water == null) {
                rep.skip(w, "no water within 24 blocks to test against");
                return;
            }
            BlockPos above = water.above();
            boolean rejected = !SpotFinder.isDry(level, above)
                    && SpotFinder.ground(level, water.getX(), water.getZ(), water.getY(), true) == null;
            rep.check(w, rejected, "water at " + water.toShortString() + (rejected ? " correctly refused" : " was accepted!"));
        });
    }

    private static void combatAndWater(Report r, ServerPlayer player) {
        r.section("Combat & water");
        ServerLevel level = player.serverLevel();
        r.run("Hits respect the cooldown", (rep, w) -> {
            // A probe with this world's mode hits a second probe twice in the same tick.
            FogLurkerEntity a = ModEntities.FOG_LURKER.get().create(level);
            FogLurkerEntity b = ModEntities.FOG_LURKER.get().create(level);
            a.moveTo(player.position());
            b.moveTo(player.position());
            a.begin(LurkerDirector.dryRun(player, Disguise.COW, true).encounter());
            boolean first = a.doHurtTarget(b);
            boolean second = a.doHurtTarget(b);
            LurkerDifficulty d = a.difficulty();
            a.discard();
            b.discard();
            rep.check(w, first && !second, "first hit " + (first ? "landed" : "MISSED") + ", instant second hit "
                    + (second ? "LANDED" : "blocked") + " (" + d.displayName() + " waits " + d.attackCooldownSeconds() + "s)");
        });
        r.run("The bite", (rep, w) -> {
            LurkerDifficulty d = FogLurkerSavedData.get(player.server).difficulty();
            boolean mauls = d == LurkerDifficulty.HARD || d == LurkerDifficulty.DEATHWISH;
            rep.check(w, com.foglurker.registry.ModEffects.MAULED.isPresent(), "Slowness I for 10 s"
                    + (mauls ? " + Mauled (no jumping, no boats) on " + d.displayName() : " (Mauled only on Hard/Deathwish)"));
        });
        r.run("Damage source", (rep, w) -> {
            FogLurkerEntity a = ModEntities.FOG_LURKER.get().create(level);
            var src = ModDamageTypes.source(a, ModDamageTypes.LURKER);
            a.discard();
            rep.check(w, src.is(DamageTypeTags.BYPASSES_ARMOR), "builds and ignores armour");
        });
        r.run("Water rules", (rep, w) -> {
            FogLurkerEntity e = ModEntities.FOG_LURKER.get().create(level);
            e.setDisguise(Disguise.PLAYER);
            float human = e.getPathfindingMalus(BlockPathTypes.WATER);
            e.setDisguise(Disguise.COW);
            float animal = e.getPathfindingMalus(BlockPathTypes.WATER);
            e.setDisguise(Disguise.CRAWLER);
            float crawler = e.getPathfindingMalus(BlockPathTypes.WATER);
            e.discard();
            rep.check(w, human == 0 && crawler < 0 && animal > 0, "human form swims (cost " + human + "), animals avoid it ("
                    + animal + "), crawler refuses water (" + crawler + ")");
        });
        r.run("Protected blocks", (rep, w) -> {
            List<net.minecraft.world.level.block.Block> mustHold = List.of(net.minecraft.world.level.block.Blocks.DEEPSLATE,
                    net.minecraft.world.level.block.Blocks.COBBLED_DEEPSLATE, net.minecraft.world.level.block.Blocks.DEEPSLATE_DIAMOND_ORE,
                    net.minecraft.world.level.block.Blocks.OBSIDIAN, net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN,
                    net.minecraft.world.level.block.Blocks.BEDROCK, net.minecraft.world.level.block.Blocks.REINFORCED_DEEPSLATE);
            List<String> missing = new ArrayList<>();
            for (var b : mustHold) {
                if (!b.defaultBlockState().is(com.foglurker.registry.ModTags.UNBREAKABLE)) {
                    missing.add(ForgeRegistries.BLOCKS.getKey(b).getPath());
                }
            }
            boolean stoneOk = !net.minecraft.world.level.block.Blocks.STONE.defaultBlockState().is(com.foglurker.registry.ModTags.UNBREAKABLE);
            rep.check(w, missing.isEmpty() && stoneOk, missing.isEmpty() ? "never breaks deepslate, obsidian, bedrock... (breaking "
                    + (FogLurkerConfig.COMMON.breakBlocks.get() && level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)
                    ? "on" : "off") + ")" : "not protected: " + missing);
        });
        r.run("Soul fire wards", (rep, w) -> {
            boolean tagged = net.minecraft.world.level.block.Blocks.SOUL_TORCH.defaultBlockState().is(com.foglurker.registry.ModTags.WARDS)
                    && net.minecraft.world.level.block.Blocks.SOUL_LANTERN.defaultBlockState().is(com.foglurker.registry.ModTags.WARDS)
                    && !net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState().is(com.foglurker.registry.ModTags.WARDS);
            BlockPos ward = com.foglurker.brain.Wards.wardNear(level, player.blockPosition());
            rep.check(w, tagged, "radius " + com.foglurker.brain.Wards.radius() + ", you are "
                    + (ward != null ? "protected by one at " + ward.toShortString() : "not near one"));
        });
        r.run("You are in water now", (rep, w) -> rep.pass(w, SpotFinder.isInOrOnWater(player)
                ? "yes: the crawler cannot reach you here" : "no"));
    }

    private static void learning(Report r, ServerPlayer player, MinecraftServer server) {
        r.section("Learning");
        FogLurkerSavedData data = FogLurkerSavedData.get(server);
        PlayerBehaviorProfile p = data.profileOrNull(player.getUUID());
        if (p == null) {
            r.skip("Your habits", "nothing learned yet (play for a minute)");
        } else {
            r.pass("Your habits", String.format(Locale.ROOT, "%d min observed, sprint %d%%, crouch-spam %.2f/s, jumps %.2f/s, %d chat lines",
                    p.windows, Math.round(p.sprintRatio * 100), p.crouchSpamRate, p.jumpRate, p.chatSamples.size()));
            r.pass("Your dog", p.dogUuid == null ? "none seen yet" : (p.dogName != null ? "\"" + p.dogName + "\"" : "unnamed")
                    + ", collar colour " + p.dogCollar);
        }
        if (p != null) {
            r.pass("Your home", p.homeKnown ? p.homeX + " " + p.homeY + " " + p.homeZ + " (" + p.homeDim + ")"
                    : "not found yet (sleep in a bed or use your chests)");
        }
        r.pass("Where you are", com.foglurker.brain.SpotFinder.placeOf(player.serverLevel(), player,
                p != null && p.homeKnown ? p.home() : null).name().toLowerCase(Locale.ROOT));
        Map<String, EntityBehaviorStats.Stats> stats = data.entityStats().all();
        StringBuilder sb = new StringBuilder();
        stats.forEach((k, v) -> sb.append(k).append(" (").append(v.samples).append(") "));
        r.pass("Animals studied", stats.isEmpty() ? "none yet" : sb.toString().trim());
        r.run("Copies your typing", (rep, w) -> {
            ChatStyle style = p == null ? ChatStyle.casualDefault() : ChatStyle.analyze(p.chatSamples);
            String out = style.apply("Come here, I found something!", RandomSource.create());
            rep.check(w, !out.isBlank(), "\"Come here, I found something!\" -> \"" + out + "\"");
        });
        r.run("Chat lines", (rep, w) -> {
            if (GeminiClient.available()) {
                rep.skip(w, "Gemini is on; see the Gemini check below");
                return;
            }
            MimicChat.Context ctx = new MimicChat.Context(player.getGameProfile().getName(), "Friend",
                    p, "north", false, List.of());
            int ok = 0;
            for (MimicChat.Intent intent : MimicChat.Intent.values()) {
                String s = MimicChat.compose(ctx, intent, intent == MimicChat.Intent.REPLY ? "where are you" : null,
                        RandomSource.create()).get(2, TimeUnit.SECONDS);
                if (s != null && !s.isBlank()) {
                    ok++;
                }
            }
            rep.check(w, ok == MimicChat.Intent.values().length, ok + "/" + MimicChat.Intent.values().length + " kinds of message");
        });
    }

    private static void voiceAndSecurity(Report r, ServerPlayer player) {
        r.section("Voice & secure folder");
        boolean svc = ModList.get().isLoaded("voicechat");
        if (svc) {
            r.pass("Simple Voice Chat installed", "");
        } else {
            r.skip("Simple Voice Chat installed", "not installed: voices are off, everything else works");
        }
        if (VoiceBridge.isActive()) {
            r.pass("Voice server running", "");
        } else if (svc) {
            r.fail("Voice server running", "voice chat is installed but its server has not started");
        } else {
            r.skip("Voice server running", "no voice chat");
        }
        boolean in = VoiceConsent.isOptedIn(player.getUUID());
        r.pass("Your voice consent", in ? "opted in, " + SnippetStore.count(player.getUUID()) + " snippet(s) stored" : "opted out");
        r.run("Secure folder", (rep, w) -> {
            Path root = SecureStorage.root();
            String perms;
            try {
                perms = PosixFilePermissions.toString(Files.getPosixFilePermissions(root));
            } catch (UnsupportedOperationException e) {
                perms = "Windows (owner-only best effort)";
            }
            boolean ok = Files.isDirectory(root) && Files.isWritable(root)
                    && (perms.startsWith("Windows") || perms.equals("rwx------"));
            rep.check(w, ok, root.toAbsolutePath().normalize() + " " + perms);
        });
        r.run("Encryption", (rep, w) -> {
            byte[] plain = "fog".getBytes(StandardCharsets.UTF_8);
            byte[] enc = SecureStorage.encrypt(plain);
            boolean round = java.util.Arrays.equals(SecureStorage.decrypt(enc), plain);
            enc[enc.length - 1] ^= 1;
            boolean tamper;
            try {
                SecureStorage.decrypt(enc);
                tamper = false;
            } catch (java.io.IOException e) {
                tamper = true;
            }
            rep.check(w, round && tamper, "AES-GCM round trip " + (round ? "ok" : "BROKEN") + ", tampering "
                    + (tamper ? "detected" : "NOT detected"));
        });
        r.run("Snippet storage", (rep, w) -> rep.pass(w, SnippetStore.selfTest()));
        r.run("Voice effects", (rep, w) -> {
            short[] pcm = new short[48000];
            for (int i = 0; i < pcm.length; i++) {
                pcm[i] = (short) (Math.sin(i / 15.0) * 8000);
            }
            int natural = AudioFx.apply(pcm, AudioFx.Style.NATURAL).length;
            int uncanny = AudioFx.apply(pcm, AudioFx.Style.UNCANNY).length;
            int demonic = AudioFx.apply(pcm, AudioFx.Style.DEMONIC).length;
            int spliced = AudioFx.splice(List.of(pcm, pcm, pcm)).length;
            rep.check(w, natural > 0 && uncanny > natural && demonic > uncanny && spliced > 0,
                    String.format(Locale.ROOT, "natural %.1fs, uncanny %.1fs, demonic %.1fs, spliced %.1fs",
                            natural / 48000F, uncanny / 48000F, demonic / 48000F, spliced / 48000F));
        });
    }

    private static void gemini(Report r, MinecraftServer server, CommandSourceStack src) {
        r.section("Gemini");
        String key = ApiKeyStore.get();
        if (key == null) {
            r.skip("Gemini API key", "not set: built-in phrases are used");
            return;
        }
        r.pass("Gemini API key", ApiKeyStore.masked(key) + (ApiKeyStore.fromEnvironment() ? " (environment variable)" : " (saved, encrypted)")
                + ", models " + String.join(", ", GeminiClient.models()) + " (testing, result follows)");
        String paused = GeminiClient.pausedReason();
        if (paused != null) {
            r.skip("Gemini paused", paused);
        }
        GeminiClient.test().whenComplete((result, err) -> server.execute(() -> {
            boolean ok = err == null && result != null && result.text().isPresent();
            String detail = ok ? "replied" : err != null ? "request failed" : result == null ? "no answer" : result.error();
            src.sendSuccess(() -> Component.literal(ok ? "✔ " : "✘ ").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED)
                    .append(Component.literal("Gemini test").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" — " + detail).withStyle(ChatFormatting.GRAY)), false);
        }));
    }

    private static void runtime(Report r) {
        r.section("Right now");
        List<FogLurkerEntity> active = LurkerDirector.active();
        if (active.isEmpty()) {
            r.pass("Active creatures", "none");
        } else {
            StringBuilder sb = new StringBuilder();
            for (FogLurkerEntity l : active) {
                LurkerBrain b = l.brain();
                sb.append(l.getDisguise().id()).append(b != null ? "/" + b.phase().name().toLowerCase(Locale.ROOT) : "").append(' ');
            }
            r.pass("Active creatures", sb.toString().trim());
        }
        r.pass("Config", "spawn roll every " + FogLurkerConfig.COMMON.spawnRollMinutes.get() + " min at "
                + Math.round(FogLurkerConfig.COMMON.spawnChance.get() * 100) + "%, break blocks "
                + FogLurkerConfig.COMMON.breakBlocks.get() + ", hauntings "
                + (FogLurkerConfig.COMMON.hauntings.get() ? "every " + FogLurkerConfig.COMMON.hauntingMinSeconds.get() + "-"
                + FogLurkerConfig.COMMON.hauntingMaxSeconds.get() + "s" : "off") + ", chat "
                + FogLurkerConfig.COMMON.chatVisibility.get().name().toLowerCase(Locale.ROOT) + ", overworld only "
                + FogLurkerConfig.COMMON.overworldOnly.get());
    }

    private static BlockPos nearestWater(ServerLevel level, BlockPos center, int radius) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -6, -radius), center.offset(radius, 6, radius))) {
            if (level.isLoaded(p) && level.getFluidState(p).is(FluidTags.WATER) && level.getFluidState(p.above()).isEmpty()) {
                double d = p.distSqr(center);
                if (d < bestD) {
                    bestD = d;
                    best = p.immutable();
                }
            }
        }
        return best;
    }
}
