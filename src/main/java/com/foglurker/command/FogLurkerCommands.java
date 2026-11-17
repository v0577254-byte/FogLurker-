package com.foglurker.command;

import com.foglurker.FogLurkerMod;
import com.foglurker.brain.LurkerBrain;
import com.foglurker.brain.LurkerDirector;
import com.foglurker.chat.GeminiClient;
import com.foglurker.config.FogLurkerConfig;
import com.foglurker.config.LurkerDifficulty;
import com.foglurker.entity.Disguise;
import com.foglurker.entity.FogLurkerEntity;
import com.foglurker.security.ApiKeyStore;
import com.foglurker.voice.SnippetStore;
import com.foglurker.voice.VoiceBridge;
import com.foglurker.voice.VoiceConsent;
import com.foglurker.world.FogLurkerSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * <pre>
 * /foglurker spawn [player] [disguise] [attack|watch]   (cheats only)
 * /foglurker gracetimer
 * /foglurker spawntimer                                 (cheats only)
 * /foglurker setgeminiapikey &lt;key&gt;                      (operators / singleplayer host)
 * /foglurker difficulty [very_easy|easy|medium|hard|deathwish]
 * /flurk voicesnippets terms        (also /fglurk ...)
 * /fglurk optin
 * /fglurk optout
 * </pre>
 */
@Mod.EventBusSubscriber(modid = FogLurkerMod.MOD_ID)
public final class FogLurkerCommands {
    private static final Component PREFIX = Component.literal("[Fog Lurker] ").withStyle(ChatFormatting.DARK_GRAY);

    private static final SuggestionProvider<CommandSourceStack> DIFFICULTIES = (ctx, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(LurkerDifficulty.values()).map(LurkerDifficulty::id), b);
    private static final SuggestionProvider<CommandSourceStack> DISGUISES = (ctx, b) ->
            SharedSuggestionProvider.suggest(Stream.concat(Stream.of("random"),
                    Arrays.stream(Disguise.values()).map(Disguise::id)), b);
    private static final SuggestionProvider<CommandSourceStack> BEHAVIORS = (ctx, b) ->
            SharedSuggestionProvider.suggest(List.of("random", "attack", "watch"), b);

    private FogLurkerCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("foglurker")
                .then(Commands.literal("spawn")
                        .executes(cheats(ctx -> spawn(ctx, ctx.getSource().getPlayerOrException(), "random", "random")))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(cheats(ctx -> spawn(ctx, EntityArgument.getPlayer(ctx, "target"), "random", "random")))
                                .then(Commands.argument("disguise", StringArgumentType.word()).suggests(DISGUISES)
                                        .executes(cheats(ctx -> spawn(ctx, EntityArgument.getPlayer(ctx, "target"),
                                                StringArgumentType.getString(ctx, "disguise"), "random")))
                                        .then(Commands.argument("behavior", StringArgumentType.word()).suggests(BEHAVIORS)
                                                .executes(cheats(ctx -> spawn(ctx, EntityArgument.getPlayer(ctx, "target"),
                                                        StringArgumentType.getString(ctx, "disguise"),
                                                        StringArgumentType.getString(ctx, "behavior"))))))))
                .then(Commands.literal("gracetimer").executes(FogLurkerCommands::graceTimer))
                .then(Commands.literal("endgrace")
                        .executes(cheats(FogLurkerCommands::endGrace)))
                .then(Commands.literal("force")
                        .then(forceNode("hide", LurkerDirector.Forced.HIDE))
                        .then(forceNode("stalk", LurkerDirector.Forced.STALK))
                        .then(forceNode("attack", LurkerDirector.Forced.ATTACK))
                        .then(Commands.literal("meet")
                                .executes(cheats(ctx -> forceMeet(ctx, ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(cheats(ctx -> forceMeet(ctx, EntityArgument.getPlayer(ctx, "target")))))))
                .then(Commands.literal("spawntimer")
                        .executes(cheats(FogLurkerCommands::spawnTimer)))
                .then(Commands.literal("naturalspawning")
                        .executes(ctx -> naturalSpawning(ctx, null))
                        .then(Commands.literal("on").requires(FogLurkerCommands::isAdmin).executes(ctx -> naturalSpawning(ctx, true)))
                        .then(Commands.literal("off").requires(FogLurkerCommands::isAdmin).executes(ctx -> naturalSpawning(ctx, false))))
                .then(Commands.literal("setgeminiapikey")
                        .requires(FogLurkerCommands::isAdmin)
                        .then(Commands.argument("key", StringArgumentType.greedyString())
                                .executes(ctx -> setKey(ctx, StringArgumentType.getString(ctx, "key")))))
                .then(Commands.literal("geministatus")
                        .requires(FogLurkerCommands::isAdmin)
                        .executes(FogLurkerCommands::geminiStatus))
                .then(Commands.literal("difficulty")
                        .executes(FogLurkerCommands::showDifficulty)
                        .then(Commands.argument("mode", StringArgumentType.greedyString())
                                .requires(FogLurkerCommands::isAdmin)
                                .suggests(DIFFICULTIES)
                                .executes(ctx -> setDifficulty(ctx, StringArgumentType.getString(ctx, "mode")))))
                .then(Commands.literal("despawn")
                        .executes(cheats(FogLurkerCommands::despawn))));

        // TEMPORARY: delete this one line (and the debug package) before release.
        d.register(Commands.literal("foglurker").then(com.foglurker.debug.DebugCommand.node()));

        // Cheats only: it does the Macarena. /foglurker-macarena [loops], also /foglurker macarena [loops]
        d.register(macarena(Commands.literal("foglurker-macarena")));
        d.register(Commands.literal("foglurker").then(macarena(Commands.literal("macarena"))));

        // The voice snippet commands answer to both spellings.
        d.register(voiceRoot("flurk"));
        d.register(voiceRoot("fglurk"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> macarena(LiteralArgumentBuilder<CommandSourceStack> node) {
        return node
                .executes(cheats(ctx -> macarena(ctx, 3)))
                .then(Commands.argument("loops", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 20))
                        .executes(cheats(ctx -> macarena(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "loops")))));
    }

    private static int macarena(CommandContext<CommandSourceStack> ctx, int loops) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        net.minecraft.server.level.ServerLevel level = p.serverLevel();
        net.minecraft.world.phys.Vec3 look = p.getViewVector(1F).multiply(1, 0, 1);
        look = look.lengthSqr() < 1e-4 ? new net.minecraft.world.phys.Vec3(0, 0, 1) : look.normalize();
        net.minecraft.world.phys.Vec3 spot = p.position().add(look.scale(3.5));
        var ground = com.foglurker.brain.SpotFinder.ground(level, net.minecraft.util.Mth.floor(spot.x),
                net.minecraft.util.Mth.floor(spot.z), p.getBlockY());
        if (ground != null) {
            spot = net.minecraft.world.phys.Vec3.atBottomCenterOf(ground);
        }
        FogLurkerEntity dancer = com.foglurker.registry.ModEntities.FOG_LURKER.get().create(level);
        if (dancer == null) {
            return 0;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(p.getZ() - spot.z, p.getX() - spot.x)) - 90F; // face you
        dancer.moveTo(spot.x, spot.y, spot.z, yaw, 0F);
        dancer.setYHeadRot(yaw);
        dancer.setYBodyRot(yaw);
        dancer.startMacarena(loops);
        level.addFreshEntity(dancer);
        reply(ctx.getSource(), Component.literal("The Fog Lurker does the Macarena ("
                + loops + (loops == 1 ? " loop" : " loops") + ").").withStyle(ChatFormatting.LIGHT_PURPLE));
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> forceNode(String name, LurkerDirector.Forced what) {
        return Commands.literal(name)
                .executes(cheats(ctx -> force(ctx, ctx.getSource().getPlayerOrException(), what)))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(cheats(ctx -> force(ctx, EntityArgument.getPlayer(ctx, "target"), what))));
    }

    private static int force(CommandContext<CommandSourceStack> ctx, ServerPlayer target, LurkerDirector.Forced what) {
        FogLurkerEntity lurker = LurkerDirector.spawnPale(target, what);
        if (lurker == null) {
            ctx.getSource().sendFailure(Component.literal("Nowhere dry for it to appear near " + target.getGameProfile().getName() + "."));
            return 0;
        }
        String doing = switch (what) {
            case HIDE -> "is lying in the grass, watching";
            case STALK -> "is stalking them (it drops flat whenever they look)";
            case ATTACK -> "is hunting them. Now.";
        };
        reply(ctx.getSource(), Component.literal("The pale crawler " + doing + " " + target.getGameProfile().getName()
                + " (" + Math.round(lurker.distanceTo(target)) + " blocks away).").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    /** /foglurker force meet [player]: the Deathwish night meeting, now, 50/50 whether it attacks. */
    private static int forceMeet(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        boolean attack = target.serverLevel().getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL
                && target.getRandom().nextBoolean();
        FogLurkerEntity lurker = LurkerDirector.spawnNightMeeting(target, attack);
        if (lurker == null) {
            ctx.getSource().sendFailure(Component.literal("Nowhere dry for it to appear near " + target.getGameProfile().getName() + "."));
            return 0;
        }
        reply(ctx.getSource(), Component.literal("The pale crawler steps out in front of " + target.getGameProfile().getName()
                + " (" + Math.round(lurker.distanceTo(target)) + " blocks away). Will it keep its promise? "
                + (attack ? "No." : "Yes.")).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int endGrace(CommandContext<CommandSourceStack> ctx) {
        FogLurkerSavedData.get(ctx.getSource().getServer()).endGrace();
        ctx.getSource().sendSuccess(() -> PREFIX.copy().append(Component.literal(
                "The grace period is over. It is out there. First spawn roll in "
                        + FogLurkerConfig.COMMON.spawnRollMinutes.get() + " minutes.").withStyle(ChatFormatting.DARK_RED)), true);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> voiceRoot(String name) {
        return Commands.literal(name)
                .then(Commands.literal("voicesnippets")
                        .then(Commands.literal("terms")
                                .executes(FogLurkerCommands::showTerms)
                                .then(Commands.literal("accept").executes(FogLurkerCommands::acceptTerms))
                                .then(Commands.literal("decline").executes(FogLurkerCommands::declineTerms)))
                        .then(Commands.literal("status").executes(FogLurkerCommands::voiceStatus)))
                .then(Commands.literal("optin").executes(FogLurkerCommands::optIn))
                .then(Commands.literal("optout").executes(FogLurkerCommands::optOut))
                .then(Commands.literal("chatlearning")
                        .executes(ctx -> chatLearning(ctx, null))
                        .then(Commands.literal("on").executes(ctx -> chatLearning(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> chatLearning(ctx, false))));
    }

    /**
     * /fglurk chatlearning [on|off]: whether it may learn how you type. Off forgets every message it
     * kept from you and keeps your chat out of what it sends to Gemini.
     */
    private static int chatLearning(CommandContext<CommandSourceStack> ctx, Boolean on) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        FogLurkerSavedData data = FogLurkerSavedData.get(p.server);
        var profile = data.profile(p.getUUID());
        if (on == null) {
            p.sendSystemMessage(PREFIX.copy().append(Component.literal(profile.chatLearningOff
                    ? "It is not learning from your chat. Turn it back on with "
                    : "It learns how you type from your chat (" + profile.chatSamples.size() + " messages kept). Turn it off with ")
                    .withStyle(ChatFormatting.GRAY)).append(suggest(profile.chatLearningOff ? "/fglurk chatlearning on" : "/fglurk chatlearning off")));
            return profile.chatLearningOff ? 0 : 1;
        }
        if (on) {
            profile.chatLearningOff = false;
            p.sendSystemMessage(PREFIX.copy().append(Component.literal(FogLurkerConfig.COMMON.learnChat.get()
                    ? "It will learn how you type again."
                    : "Turned on for you, but this server has chat learning switched off, so nothing is learned.")
                    .withStyle(ChatFormatting.GRAY)));
        } else {
            int forgotten = profile.chatSamples.size();
            profile.chatLearningOff = true;
            profile.chatSamples.clear();
            com.foglurker.learning.RecentChat.forget(p.getGameProfile().getName());
            p.sendSystemMessage(PREFIX.copy().append(Component.literal("It will no longer learn from your chat. Forgot "
                    + forgotten + " message" + (forgotten == 1 ? "" : "s") + " it had kept.").withStyle(ChatFormatting.GRAY)));
        }
        data.setDirty();
        return 1;
    }

    /**
     * Cheats-only commands stay visible to everyone and say what is needed, instead of Minecraft hiding
     * them and answering a confusing "Incorrect argument for command".
     */
    private static com.mojang.brigadier.Command<CommandSourceStack> cheats(com.mojang.brigadier.Command<CommandSourceStack> c) {
        return ctx -> {
            if (!ctx.getSource().hasPermission(2)) {
                ctx.getSource().sendFailure(Component.literal("That command needs cheats. Singleplayer: press Esc, Open to LAN, "
                        + "set Allow Cheats to ON, Start LAN World (or make the world with cheats on). On a server, ask an operator."));
                return 0;
            }
            return c.run(ctx);
        };
    }

    /** Operators, the console, or the host of a singleplayer / LAN world. */
    private static boolean isAdmin(CommandSourceStack s) {
        if (s.hasPermission(2)) {
            return true;
        }
        return s.getEntity() instanceof ServerPlayer p && s.getServer().isSingleplayerOwner(p.getGameProfile());
    }

    private static void reply(CommandSourceStack s, Component msg) {
        s.sendSuccess(() -> PREFIX.copy().append(msg), false);
    }

    private static String time(long ticks) {
        long secs = Math.max(0, ticks) / 20;
        long h = secs / 3600;
        long m = secs % 3600 / 60;
        long sec = secs % 60;
        return h > 0 ? String.format(Locale.ROOT, "%dh %02dm %02ds", h, m, sec)
                : String.format(Locale.ROOT, "%dm %02ds", m, sec);
    }

    // =================================================================== /foglurker

    private static int spawn(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String disguiseArg,
                             String behaviorArg) {
        CommandSourceStack src = ctx.getSource();
        Disguise disguise = null;
        if (!disguiseArg.equalsIgnoreCase("random")) {
            disguise = Disguise.byName(disguiseArg);
            if (disguise == null) {
                src.sendFailure(Component.literal("Unknown disguise '" + disguiseArg + "'. Try: player, dog, cow, sheep, pig, chicken, zombie, wolf, watcher, crawler"));
                return 0;
            }
        }
        Boolean attack = switch (behaviorArg.toLowerCase(Locale.ROOT)) {
            case "attack" -> Boolean.TRUE;
            case "watch", "passive" -> Boolean.FALSE;
            default -> null;
        };
        FogLurkerEntity lurker = LurkerDirector.spawn(target, disguise, attack);
        if (lurker == null || lurker.brain() == null) {
            src.sendFailure(Component.literal("Could not find anywhere for it to appear near " + target.getGameProfile().getName()
                    + " (needs loaded ground 15-55 blocks away)."));
            return 0;
        }
        var enc = lurker.brain().encounter();
        String as = switch (enc.mode()) {
            case MIMIC -> "wearing " + enc.copiedName() + "'s face";
            case DOPPELGANGER -> "wearing their own face";
            case DOG -> "as their dog" + (enc.dogName() != null ? " \"" + enc.dogName() + "\"" : "");
            case WATCHER -> "as itself, the pale crawler, watching from the fog";
            default -> "hiding as a " + enc.disguise().id();
        };
        reply(src, Component.literal("It is coming for " + target.getGameProfile().getName() + ", " + as
                + ". Will attack: " + (enc.willAttack() ? "yes" : "no") + ".").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int graceTimer(CommandContext<CommandSourceStack> ctx) {
        FogLurkerSavedData data = FogLurkerSavedData.get(ctx.getSource().getServer());
        long left = data.graceRemainingTicks();
        if (left > 0) {
            reply(ctx.getSource(), Component.literal("Grace period: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(time(left)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" left (" + data.difficulty().displayName()
                            + " mode). It is still learning how you play.").withStyle(ChatFormatting.GRAY)));
        } else {
            reply(ctx.getSource(), Component.literal("The grace period is over. It is out there.")
                    .withStyle(ChatFormatting.DARK_RED));
        }
        return (int) Math.min(Integer.MAX_VALUE, left / 20);
    }

    /**
     * /foglurker naturalspawning [on|off]: whether it appears on its own. Off, it only comes when
     * summoned with /foglurker spawn or /foglurker force. Saved with the world.
     */
    private static int naturalSpawning(CommandContext<CommandSourceStack> ctx, Boolean on) {
        CommandSourceStack src = ctx.getSource();
        FogLurkerSavedData data = FogLurkerSavedData.get(src.getServer());
        if (on == null) {
            reply(src, Component.literal(data.naturalSpawning()
                    ? "Natural spawning is ON: it appears on its own (a roll every " + FogLurkerConfig.COMMON.spawnRollMinutes.get()
                    + " minutes after the grace period)."
                    : "Natural spawning is OFF: it only comes when summoned with /foglurker spawn or /foglurker force.")
                    .withStyle(ChatFormatting.GRAY));
            return data.naturalSpawning() ? 1 : 0;
        }
        data.setNaturalSpawning(on);
        reply(src, Component.literal(on
                ? "Natural spawning is now ON. " + (data.inGrace() ? "It starts rolling when the grace period ends."
                : "The first roll is in " + FogLurkerConfig.COMMON.spawnRollMinutes.get() + " minutes.")
                : "Natural spawning is now OFF. It will only come when summoned with /foglurker spawn or /foglurker force. "
                + "Anything already out there stays until it leaves (/foglurker despawn sends it away).")
                .withStyle(on ? ChatFormatting.RED : ChatFormatting.GRAY));
        return 1;
    }

    private static int spawnTimer(CommandContext<CommandSourceStack> ctx) {
        FogLurkerSavedData data = FogLurkerSavedData.get(ctx.getSource().getServer());
        CommandSourceStack src = ctx.getSource();
        if (!data.naturalSpawning()) {
            reply(src, Component.literal("Natural spawning is off (/foglurker naturalspawning on to turn it back on). "
                    + "It only comes when summoned.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        List<FogLurkerEntity> active = LurkerDirector.active();
        if (!active.isEmpty()) {
            StringBuilder who = new StringBuilder();
            for (FogLurkerEntity l : active) {
                LurkerBrain b = l.brain();
                if (b != null) {
                    ServerPlayer t = src.getServer().getPlayerList().getPlayer(b.targetId());
                    who.append(t != null ? t.getGameProfile().getName() : "?").append(" (").append(b.phase().name().toLowerCase(Locale.ROOT)).append(") ");
                }
            }
            boolean running = LurkerDirector.rollsRunning(src.getServer()) && !data.inGrace() && data.nextSpawnTicks() >= 0;
            reply(src, Component.literal("It is out there right now, hunting: " + who.toString().trim()
                    + (running ? ". Next roll for everyone else in " + time(data.nextSpawnTicks()) + "."
                    : ". The timer restarts when it leaves.")).withStyle(ChatFormatting.RED));
            return 0;
        }
        if (data.inGrace()) {
            reply(src, Component.literal("Still in the grace period (" + time(data.graceRemainingTicks())
                    + " left). The spawn timer starts when it ends.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        int next = data.nextSpawnTicks();
        if (next < 0) {
            reply(src, Component.literal("The spawn timer is about to start.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        reply(src, Component.literal("Next spawn roll in: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(time(next)).withStyle(ChatFormatting.WHITE))
                .append(data.nextIsGuaranteed()
                        ? Component.literal(" (guaranteed: the last roll missed)").withStyle(ChatFormatting.RED)
                        : Component.literal(" (" + Math.round(FogLurkerConfig.COMMON.spawnChance.get() * 100) + "% chance)")
                        .withStyle(ChatFormatting.GRAY)));
        return next / 20;
    }

    private static int setKey(CommandContext<CommandSourceStack> ctx, String raw) {
        CommandSourceStack src = ctx.getSource();
        String key = raw.trim();
        try {
            if (key.equalsIgnoreCase("clear") || key.equalsIgnoreCase("remove") || key.equalsIgnoreCase("none")) {
                ApiKeyStore.set(null);
                GeminiClient.clearBackoff();
                if (ApiKeyStore.isSet()) {
                    reply(src, Component.literal("The saved Gemini API key was removed, but the server's FOGLURKER_GEMINI_API_KEY "
                            + "environment variable still holds one, so Gemini stays on. Remove that variable to turn it off.")
                            .withStyle(ChatFormatting.GOLD));
                } else {
                    reply(src, Component.literal("Gemini API key removed. It will use its built-in phrases instead.")
                            .withStyle(ChatFormatting.GRAY));
                }
                return 1;
            }
            if (!ApiKeyStore.isPlausible(key)) {
                // Never echo what was typed: it may be a real key with a typo.
                src.sendFailure(Component.literal("That does not look like a Gemini API key: it should be one word of 20 or more "
                        + "letters, digits and symbols with no spaces (get one at aistudio.google.com/apikey)."));
                return 0;
            }
            ApiKeyStore.set(key);
            GeminiClient.clearBackoff();
        } catch (IOException e) {
            FogLurkerMod.LOGGER.error("Could not store Gemini API key", e);
            src.sendFailure(Component.literal("Could not save the key: " + e.getMessage()));
            return 0;
        }
        reply(src, Component.literal("Gemini API key " + ApiKeyStore.masked(key)
                + " saved (encrypted) in the secure foglurker_secure folder. Testing it...").withStyle(ChatFormatting.GRAY));
        testGemini(src);
        return 1;
    }

    private static int geminiStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String key = ApiKeyStore.get();
        if (key == null) {
            reply(src, Component.literal("No Gemini API key set. It uses built-in phrases that copy each player's typing style. "
                    + "Set one with /foglurker setgeminiapikey <key>.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        reply(src, Component.literal("Gemini key " + ApiKeyStore.masked(key)
                + (ApiKeyStore.fromEnvironment() ? " (from the FOGLURKER_GEMINI_API_KEY environment variable)" : " (saved, encrypted)")
                + ", models " + String.join(", ", GeminiClient.models()) + ". Testing...").withStyle(ChatFormatting.GRAY));
        String paused = GeminiClient.pausedReason();
        if (paused != null) {
            reply(src, Component.literal("Paused: " + paused + ".").withStyle(ChatFormatting.GOLD));
        }
        testGemini(src);
        return 1;
    }

    private static void testGemini(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        GeminiClient.test().whenComplete((result, err) -> server.execute(() -> {
            if (err == null && result != null && result.text().isPresent()) {
                reply(src, Component.literal("The Gemini key works. It will now write its own chat messages.")
                        .withStyle(ChatFormatting.GREEN));
            } else {
                String why = err != null ? "request failed" : result == null ? "no answer" : result.error();
                reply(src, Component.literal("Gemini test failed: " + why
                        + ". The key is kept; check it and set it again if needed.").withStyle(ChatFormatting.RED));
            }
        }));
    }

    private static Component describe(LurkerDifficulty d) {
        String damage = d.isOneShot() ? "one-shots" : (fmtHearts(d.heartsPerHit()) + " per hit");
        String attacks = switch (d) {
            case VERY_EASY -> "rarely attacks, mostly only watches";
            case EASY -> "attacks rarely";
            case MEDIUM -> "attacks on 1 of every 3 appearances";
            case HARD -> "attacks on 1 of every 2 appearances";
            case DEATHWISH -> "attacks on 3 of every 4 appearances, 50% faster";
        };
        return Component.literal(d.displayName()).withStyle(d == LurkerDifficulty.DEATHWISH ? ChatFormatting.DARK_RED : ChatFormatting.WHITE)
                .append(Component.literal(": " + attacks + ", " + damage + ", " + d.graceMinutes()
                        + " min grace period.").withStyle(ChatFormatting.GRAY));
    }

    private static String fmtHearts(float hearts) {
        return (hearts == Math.floor(hearts) ? String.valueOf((int) hearts) : String.valueOf(hearts))
                + (hearts == 1 ? " heart" : " hearts");
    }

    private static int showDifficulty(CommandContext<CommandSourceStack> ctx) {
        LurkerDifficulty d = FogLurkerSavedData.get(ctx.getSource().getServer()).difficulty();
        reply(ctx.getSource(), Component.literal("Mode: ").withStyle(ChatFormatting.GRAY).append(describe(d)));
        if (isAdmin(ctx.getSource())) {
            MutableComponent options = Component.literal("Change it: ").withStyle(ChatFormatting.DARK_GRAY);
            for (LurkerDifficulty o : LurkerDifficulty.values()) {
                options.append(Component.literal("[" + o.id() + "] ").withStyle(Style.EMPTY
                        .withColor(o == d ? ChatFormatting.WHITE : ChatFormatting.GRAY)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/foglurker difficulty " + o.id()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, describe(o)))));
            }
            ctx.getSource().sendSuccess(() -> options, false);
        }
        return d.ordinal() + 1;
    }

    private static int setDifficulty(CommandContext<CommandSourceStack> ctx, String raw) {
        LurkerDifficulty d = LurkerDifficulty.byId(raw);
        if (d == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown mode '" + raw + "'. Use very_easy, easy, medium, hard or deathwish."));
            return 0;
        }
        FogLurkerSavedData data = FogLurkerSavedData.get(ctx.getSource().getServer());
        data.setDifficulty(d);
        ctx.getSource().sendSuccess(() -> PREFIX.copy().append(Component.literal("Mode set to ").withStyle(ChatFormatting.GRAY))
                .append(describe(d)), true);
        if (data.inGrace()) {
            reply(ctx.getSource(), Component.literal("Grace period left: " + time(data.graceRemainingTicks()) + ".")
                    .withStyle(ChatFormatting.GRAY));
        }
        return d.ordinal() + 1;
    }

    private static int despawn(CommandContext<CommandSourceStack> ctx) {
        int n = 0;
        for (FogLurkerEntity l : LurkerDirector.active()) {
            if (l.brain() != null) {
                l.brain().vanish(false);
            } else {
                l.discard();
            }
            n++;
        }
        int count = n;
        reply(ctx.getSource(), Component.literal(count == 0 ? "Nothing to send away." : "It is gone. For now.")
                .withStyle(ChatFormatting.GRAY));
        return count;
    }

    // =================================================================== /flurk, /fglurk

    private static int showTerms(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        p.sendSystemMessage(PREFIX.copy().append(Component.literal("Voice snippets").withStyle(ChatFormatting.GOLD)));
        p.sendSystemMessage(Component.literal(VoiceConsent.TERMS).withStyle(ChatFormatting.WHITE));
        p.sendSystemMessage(Component.empty()
                .append(button("[YES]", ChatFormatting.GREEN, "/flurk voicesnippets terms accept", "I agree"))
                .append(Component.literal("   "))
                .append(button("[NO]", ChatFormatting.RED, "/flurk voicesnippets terms decline", "No thanks")));
        if (VoiceConsent.isOptedIn(p.getUUID())) {
            p.sendSystemMessage(Component.literal("(You are currently opted in.)").withStyle(ChatFormatting.DARK_GRAY));
        }
        return 1;
    }

    private static MutableComponent button(String text, ChatFormatting color, String command, String hover) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    private static MutableComponent suggest(String command) {
        return Component.literal(command).withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to type " + command))));
    }

    private static int acceptTerms(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        VoiceConsent.optIn(p.getUUID());
        p.sendSystemMessage(Component.literal("great! you have now opted in use the command ").withStyle(ChatFormatting.GREEN)
                .append(suggest("/fglurk optout"))
                .append(Component.literal(" to cancel it ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("NOTE: this will delete all your voice snippets that have been stored")
                        .withStyle(ChatFormatting.YELLOW)));
        if (!VoiceBridge.isActive()) {
            p.sendSystemMessage(Component.literal("(Simple Voice Chat is not running on this server, so nothing is recorded until it is.)")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return 1;
    }

    private static int declineTerms(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        boolean wasIn = VoiceConsent.isOptedIn(p.getUUID());
        int deleted = wasIn ? VoiceConsent.optOut(p.getUUID()) : 0;
        p.sendSystemMessage(Component.literal("we are sad that you chose this if you want to opt in then use ")
                .withStyle(ChatFormatting.GRAY).append(suggest("/fglurk optin")));
        if (wasIn) {
            p.sendSystemMessage(Component.literal("You were opted in before, so you are now opted out and "
                    + deleted + " stored snippet(s) were deleted.").withStyle(ChatFormatting.DARK_GRAY));
        }
        return 1;
    }

    private static int optIn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        p.sendSystemMessage(PREFIX.copy().append(Component.literal("By opting in you agree to the terms: ")
                .withStyle(ChatFormatting.GRAY)).append(Component.literal(VoiceConsent.TERMS).withStyle(ChatFormatting.WHITE)));
        return acceptTerms(ctx);
    }

    private static int optOut(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        int deleted = VoiceConsent.optOut(p.getUUID());
        p.sendSystemMessage(PREFIX.copy().append(Component.literal("You have opted out. " + deleted
                + " voice snippet(s) were deleted and nothing more will be recorded. Opt back in any time with ")
                .withStyle(ChatFormatting.GRAY)).append(suggest("/fglurk optin")));
        if (VoiceConsent.hasUnsavedChanges()) {
            p.sendSystemMessage(Component.literal("Note: the server could not write this to its disk yet. You are opted out now and it "
                    + "keeps trying; if you see this again, tell the server owner.").withStyle(ChatFormatting.GOLD));
        }
        return 1;
    }

    private static int voiceStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        boolean in = VoiceConsent.isOptedIn(p.getUUID());
        int count = in ? SnippetStore.count(p.getUUID()) : 0;
        p.sendSystemMessage(PREFIX.copy().append(Component.literal(in
                ? "You are opted in. Stored snippets: " + count + "."
                : "You are opted out. Nothing of your voice is stored.").withStyle(ChatFormatting.GRAY)));
        p.sendSystemMessage(Component.literal(VoiceBridge.isActive() ? "Simple Voice Chat: running" : "Simple Voice Chat: not running")
                .withStyle(ChatFormatting.DARK_GRAY));
        return in ? 1 : 0;
    }

    /** Clickable line used for the one-time join notice. */
    public static Component termsLink() {
        return suggest("/flurk voicesnippets terms");
    }
}
