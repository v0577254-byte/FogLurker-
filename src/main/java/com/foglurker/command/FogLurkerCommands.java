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
    private static final List<String> DISGUISE_NAMES =
            Stream.concat(Stream.of("random"), Arrays.stream(Disguise.values()).map(Disguise::id)).toList();
    private static final SuggestionProvider<CommandSourceStack> DISGUISES = (ctx, b) -> SharedSuggestionProvider.suggest(DISGUISE_NAMES, b);
    private static final List<String> BEHAVIOR_NAMES = List.of("random", "attack", "watch");
    private static final SuggestionProvider<CommandSourceStack> BEHAVIORS = (ctx, b) -> SharedSuggestionProvider.suggest(BEHAVIOR_NAMES, b);
    /** /foglurker locate <name>: the mod's structures by short name. */
    private static final java.util.Map<String, net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.structure.Structure>> LOCATE_ALIASES =
            java.util.Map.of("tower", com.foglurker.world.FogStructures.LOOKOUT_TOWER,
                    "campfire", structureKey("abandoned_campfire"),
                    "castle", structureKey("fortified_castle"),
                    "castle-abandoned", structureKey("abandoned_castle"),
                    "bunker", structureKey("hunters_bunker"),
                    "well", structureKey("fog_well"));

    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.structure.Structure> structureKey(String path) {
        return net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE, FogLurkerMod.id(path));
    }

    @javax.annotation.Nullable
    public static net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.structure.Structure> resolveLocateAlias(String alias) {
        return LOCATE_ALIASES.get(alias.toLowerCase(java.util.Locale.ROOT));
    }

    private FogLurkerCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("foglurker")
                .then(Commands.literal("locate") // (shown to everyone, like the other admin commands: cheats() says what is needed)
                        .then(Commands.argument("structure", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(LOCATE_ALIASES.keySet(), b))
                                .executes(cheats(ctx -> locate(ctx.getSource(), StringArgumentType.getString(ctx, "structure"))))))
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
                        .then(forceSpawnNode())
                        .then(forceNode("hide", LurkerDirector.Forced.HIDE))
                        .then(forceNode("stalk", LurkerDirector.Forced.STALK))
                        .then(forceNode("attack", LurkerDirector.Forced.ATTACK))
                        .then(Commands.literal("meet")
                                .executes(cheats(ctx -> forceMeet(ctx, ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(cheats(ctx -> forceMeet(ctx, EntityArgument.getPlayer(ctx, "target"))))))
                        .then(Commands.literal("sleep-paralysis")
                                .executes(cheats(ctx -> forceSleepParalysis(ctx, ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(cheats(ctx -> forceSleepParalysis(ctx, EntityArgument.getPlayer(ctx, "target")))))))
                .then(Commands.literal("spawntimer")
                        .executes(cheats(FogLurkerCommands::spawnTimer)))
                .then(Commands.literal("fog")
                        .executes(FogLurkerCommands::showFog)
                        .then(Commands.literal("default").requires(FogLurkerCommands::isAdmin).executes(FogLurkerCommands::modeFog))
                        .then(Commands.argument("thickness", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100))
                                .requires(FogLurkerCommands::isAdmin)
                                .executes(ctx -> setFog(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "thickness")))))
                .then(Commands.literal("naturalspawning")
                        .executes(ctx -> naturalSpawning(ctx, null))
                        .then(Commands.literal("on").requires(FogLurkerCommands::isAdmin).executes(ctx -> naturalSpawning(ctx, true)))
                        .then(Commands.literal("off").requires(FogLurkerCommands::isAdmin).executes(ctx -> naturalSpawning(ctx, false))))
                .then(Commands.literal("setgeminiapikey")
                        .requires(FogLurkerCommands::isAdmin)
                        .then(Commands.argument("key", StringArgumentType.greedyString())
                                .executes(ctx -> safeSetKey(ctx, StringArgumentType.getString(ctx, "key")))))
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

        // /afk (also /AFK): away from the keyboard right now; again to come back.
        d.register(Commands.literal("afk").executes(FogLurkerCommands::afk));
        d.register(Commands.literal("AFK").executes(FogLurkerCommands::afk));
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
        // Dry ground only, like every other place it appears (never in, on or next to water).
        var ground = com.foglurker.brain.SpotFinder.ground(level, net.minecraft.util.Mth.floor(spot.x),
                net.minecraft.util.Mth.floor(spot.z), p.getBlockY(), true);
        if (ground == null) {
            ctx.getSource().sendFailure(Component.literal("Nowhere dry for it to dance here (it needs dry ground in front of you)."));
            return 0;
        }
        spot = net.minecraft.world.phys.Vec3.atBottomCenterOf(ground);
        FogLurkerEntity dancer = com.foglurker.registry.ModEntities.FOG_LURKER.get().create(level);
        if (dancer == null) {
            ctx.getSource().sendFailure(Component.literal("Could not make the dancer here."));
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

    /** /afk: AFK now (it leaves you alone), or back again if you already are. */
    private static int afk(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (com.foglurker.world.AfkTracker.isAfk(p)) {
            com.foglurker.world.AfkTracker.comeBack(p);
            return 1;
        }
        String refused = com.foglurker.world.AfkTracker.goAfk(p);
        if (refused != null) {
            ctx.getSource().sendFailure(Component.literal(refused));
            return 0;
        }
        return 1;
    }

    /** It never goes after someone who is AFK, not even when told to. @return true if they are AFK (and the caller was told) */
    private static boolean refuseAfk(CommandSourceStack src, ServerPlayer target) {
        if (!com.foglurker.world.AfkTracker.isAfk(target)) {
            return false;
        }
        src.sendFailure(Component.literal(target.getGameProfile().getName() + " is AFK, so it leaves them alone. "
                + "It works as soon as they move or look around again (or type /afk to come back)."));
        return true;
    }

    private static int force(CommandContext<CommandSourceStack> ctx, ServerPlayer target, LurkerDirector.Forced what) {
        if (refuseAfk(ctx.getSource(), target)) {
            return 0;
        }
        if (what == LurkerDirector.Forced.ATTACK) {
            // It would appear and leave at once: say why instead of claiming it is hunting them.
            String why = target.serverLevel().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL
                    ? "the world is on Peaceful (set the difficulty to Easy or higher)"
                    : target.isCreative() ? target.getGameProfile().getName() + " is in Creative mode"
                    : target.isSpectator() ? target.getGameProfile().getName() + " is a spectator"
                    : null;
            if (why != null) {
                ctx.getSource().sendFailure(Component.literal("It cannot attack: " + why + "."));
                return 0;
            }
        }
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
        reply(ctx.getSource(), Component.literal("The Fog Lurker " + doing + " " + target.getGameProfile().getName()
                + " (" + Math.round(lurker.distanceTo(target)) + " blocks away).").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    /** /foglurker force meet [player]: the Deathwish night meeting, now, 50/50 whether it attacks. */
    /** /foglurker fog: how thick the fog that is always there is. */
    private static int showFog(CommandContext<CommandSourceStack> ctx) {
        FogLurkerSavedData data = FogLurkerSavedData.get(ctx.getSource().getServer());
        int level = data.alwaysFog();
        reply(ctx.getSource(), Component.literal("The fog that is always there: " + level
                + (data.alwaysFogFollowsMode() ? " (the " + data.difficulty().displayName() + " default)" : " (set with /foglurker fog; "
                + data.difficulty().displayName() + "'s own is " + data.difficulty().alwaysFog() + ", /foglurker fog default goes back to it)")
                + ". 0 = none, 100 = thickest. It thickens on its own when it attacks.")
                .withStyle(ChatFormatting.GRAY));
        return level;
    }

    /** /foglurker fog <0-100>: sets it for everyone in this world, at once. */
    private static int setFog(CommandContext<CommandSourceStack> ctx, int level) {
        var server = ctx.getSource().getServer();
        FogLurkerSavedData.get(server).setAlwaysFog(level);
        com.foglurker.world.PlayerEvents.sendAlwaysFogToAll(server);
        ctx.getSource().sendSuccess(() -> PREFIX.copy().append(Component.literal("The fog is now " + level
                + (level == 0 ? " (no fog unless it attacks)" : level == 100 ? " (the thickest)" : "") + " for everyone in this world.")
                .withStyle(ChatFormatting.GRAY)), true);
        return 1;
    }

    /** /foglurker fog default: back to the mode's own fog (15/25/45/55/60). */
    private static int modeFog(CommandContext<CommandSourceStack> ctx) {
        var server = ctx.getSource().getServer();
        FogLurkerSavedData data = FogLurkerSavedData.get(server);
        data.useModeFog();
        com.foglurker.world.PlayerEvents.sendAlwaysFogToAll(server);
        ctx.getSource().sendSuccess(() -> PREFIX.copy().append(Component.literal("The fog follows the mode again: "
                + data.alwaysFog() + " (" + data.difficulty().displayName() + ").").withStyle(ChatFormatting.GRAY)), true);
        return data.alwaysFog();
    }

    /** The next time they go to sleep, sleep paralysis (see SleepParalysis). */
    private static int forceSleepParalysis(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        if (refuseAfk(ctx.getSource(), target)) {
            return 0; // like spawn and force: never sent after someone who is away from the keyboard
        }
        com.foglurker.world.SleepParalysis.forceNext(target);
        boolean self = ctx.getSource().getEntity() == target;
        String text = self
                ? "Done! the next time you go to sleep you will experience sleep paralysis"
                : "Done! the next time " + target.getGameProfile().getName() + " goes to sleep he/she will experience sleep paralysis";
        ctx.getSource().sendSuccess(() -> Component.literal(text), false); // exactly this, no prefix
        return 1;
    }

    private static int forceMeet(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        if (refuseAfk(ctx.getSource(), target)) {
            return 0;
        }
        boolean attack = target.serverLevel().getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL
                && !target.isCreative() && !target.isSpectator() // it could never keep that threat
                && target.getRandom().nextBoolean();
        FogLurkerEntity lurker = LurkerDirector.spawnNightMeeting(target, attack);
        if (lurker == null) {
            ctx.getSource().sendFailure(Component.literal("Nowhere dry for it to appear near " + target.getGameProfile().getName() + "."));
            return 0;
        }
        reply(ctx.getSource(), Component.literal("The Fog Lurker steps out in front of " + target.getGameProfile().getName()
                + " (" + Math.round(lurker.distanceTo(target)) + " blocks away). Will it keep its promise? "
                + (attack ? "No." : "Yes.")).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int endGrace(CommandContext<CommandSourceStack> ctx) {
        FogLurkerSavedData data = FogLurkerSavedData.get(ctx.getSource().getServer());
        if (!data.inGrace()) {
            // Already over: ending it again would throw away the time already counted toward the next roll.
            reply(ctx.getSource(), Component.literal("The grace period is already over.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        data.endGrace();
        String rest = LurkerDirector.isResting(ctx.getSource().getServer())
                ? " (it was killed: the clock only starts after its rest, in " + time(LurkerDirector.restTicksLeft(ctx.getSource().getServer())) + ")"
                : "";
        String next = data.naturalSpawning()
                ? "First spawn roll in " + FogLurkerConfig.COMMON.spawnRollMinutes.get() + " minutes" + rest + "."
                : "Natural spawning is off, so nothing rolls until /foglurker naturalspawning on.";
        ctx.getSource().sendSuccess(() -> PREFIX.copy().append(Component.literal(
                "The grace period is over. " + next).withStyle(ChatFormatting.DARK_RED)), true);
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
        if (on == null && !profile.chatLearningOff && !FogLurkerConfig.COMMON.learnChat.get()) {
            p.sendSystemMessage(PREFIX.copy().append(Component.literal(
                    "This server has chat learning switched off, so nothing is learned from anyone's chat.")
                    .withStyle(ChatFormatting.GRAY)));
            return 0;
        }
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
            p.sendSystemMessage(PREFIX.copy().append(Component.literal("It will no longer learn from your chat on this world/server. Forgot "
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

    /** /foglurker locate <tower|campfire|castle|castle-abandoned|bunker|well>: like /locate, with a click-to-teleport. */
    private static int locate(CommandSourceStack src, String alias) {
        var key = resolveLocateAlias(alias);
        if (key == null) {
            src.sendFailure(Component.literal("Unknown Fog Lurker structure: " + alias + ". Try: " + String.join(", ", LOCATE_ALIASES.keySet())));
            return 0;
        }
        net.minecraft.server.level.ServerLevel level = src.getLevel();
        var holder = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getHolder(key);
        net.minecraft.core.BlockPos origin = net.minecraft.core.BlockPos.containing(src.getPosition());
        var found = holder.isPresent()
                ? level.getChunkSource().getGenerator().findNearestMapStructure(level, net.minecraft.core.HolderSet.direct(holder.get()), origin, 100, false)
                : null;
        if (found == null) {
            src.sendFailure(Component.translatable("commands.locate.structure.not_found", key.location().toString()));
            return 0;
        }
        net.minecraft.core.BlockPos pos = found.getFirst();
        long dx = (long) pos.getX() - origin.getX();
        long dz = (long) pos.getZ() - origin.getZ();
        int distance = net.minecraft.util.Mth.floor(Math.sqrt((double) (dx * dx + dz * dz)));
        String command = "/tp @s " + pos.getX() + " ~ " + pos.getZ();
        Component coordinates = net.minecraft.network.chat.ComponentUtils.wrapInSquareBrackets(
                        Component.translatable("chat.coordinates", pos.getX(), "~", pos.getZ()))
                .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND, command))
                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("chat.coordinates.tooltip"))));
        src.sendSuccess(() -> Component.translatable("commands.locate.structure.success", key.location().toString(), coordinates, distance), false);
        return distance;
    }

    /** /foglurker force spawn [disguise] [attack|watch|random] [player]: the same as spawn, in the force family. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> forceSpawnNode() {
        var disguise = Commands.argument("disguise", StringArgumentType.word()).suggests(DISGUISES)
                .executes(cheats(ctx -> spawn(ctx, ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "disguise"), "random")));
        for (String behavior : BEHAVIOR_NAMES) {
            disguise.then(Commands.literal(behavior)
                    .executes(cheats(ctx -> spawn(ctx, ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "disguise"), behavior)))
                    .then(Commands.argument("target", EntityArgument.player())
                            .executes(cheats(ctx -> spawn(ctx, EntityArgument.getPlayer(ctx, "target"), StringArgumentType.getString(ctx, "disguise"), behavior)))));
        }
        disguise.then(Commands.argument("target", EntityArgument.player())
                .executes(cheats(ctx -> spawn(ctx, EntityArgument.getPlayer(ctx, "target"), StringArgumentType.getString(ctx, "disguise"), "random")))
                .then(Commands.argument("behavior", StringArgumentType.word()).suggests(BEHAVIORS)
                        .executes(cheats(ctx -> spawn(ctx, EntityArgument.getPlayer(ctx, "target"),
                                StringArgumentType.getString(ctx, "disguise"), StringArgumentType.getString(ctx, "behavior"))))));
        return Commands.literal("spawn")
                .executes(cheats(ctx -> spawn(ctx, ctx.getSource().getPlayerOrException(), "random", "random")))
                .then(disguise);
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String disguiseArg,
                             String behaviorArg) {
        CommandSourceStack src = ctx.getSource();
        if (refuseAfk(src, target)) {
            return 0;
        }
        Disguise disguise = null;
        if (!disguiseArg.equalsIgnoreCase("random")) {
            disguise = Disguise.byName(disguiseArg);
            if (disguise == null) {
                src.sendFailure(Component.literal("Unknown disguise '" + disguiseArg + "'. Try: " + String.join(", ", DISGUISE_NAMES)));
                return 0;
            }
        }
        Boolean attack = switch (behaviorArg.toLowerCase(Locale.ROOT)) {
            case "attack" -> Boolean.TRUE;
            case "watch", "passive" -> Boolean.FALSE;
            case "random", "any" -> null;
            default -> {
                src.sendFailure(Component.literal("Unknown behaviour '" + behaviorArg + "'. Use attack, watch or random."));
                yield Boolean.FALSE; // not used: the command stops below
            }
        };
        if (!List.of("attack", "watch", "passive", "random", "any").contains(behaviorArg.toLowerCase(Locale.ROOT))) {
            return 0;
        }
        FogLurkerEntity lurker = LurkerDirector.spawn(target, disguise, attack);
        if (lurker == null || lurker.brain() == null) {
            src.sendFailure(Component.literal("Could not find anywhere for it to appear near " + target.getGameProfile().getName()
                    + " (it needs dry, loaded ground nearby, away from water)."));
            return 0;
        }
        var enc = lurker.brain().encounter();
        String as = switch (enc.mode()) {
            case MIMIC -> "wearing " + enc.copiedName() + "'s face";
            case DOPPELGANGER -> "wearing their own face";
            case DOG -> (FogLurkerSavedData.get(src.getServer()).profile(target.getUUID()).dogUuid != null ? "as their dog" : "as a dog")
                    + (enc.dogName() != null ? " \"" + enc.dogName() + "\"" : "");
            case WATCHER -> "as itself, the Fog Lurker in its real shape, watching from the fog";
            default -> "hiding as a " + enc.disguise().id();
        };
        boolean canHurt = target.serverLevel().getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL
                && !target.isCreative() && !target.isSpectator();
        reply(src, Component.literal("It is coming for " + target.getGameProfile().getName() + ", " + as
                + ". Will attack: " + (!enc.willAttack() ? "no" : canHurt ? "yes"
                : "no (it cannot hurt them now: Peaceful, Creative or spectator)") + ".").withStyle(ChatFormatting.GRAY));
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
            // This command is for everyone: nothing about hunts or roll times, only what everyone was already told.
            var server = ctx.getSource().getServer();
            String now = LurkerDirector.isResting(server) ? "It was killed; the fog lifts for another " + time(LurkerDirector.restTicksLeft(server)) + "."
                    : !data.naturalSpawning() ? "Natural spawning is off: it only comes when summoned."
                    : "It can come at any time.";
            reply(ctx.getSource(), Component.literal("The grace period is over. " + now).withStyle(ChatFormatting.DARK_RED));
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
                    ? FogLurkerConfig.COMMON.spawnChance.get() <= 0
                    ? "Natural spawning is ON, but spawnChance is 0 in the config, so it never appears on its own; only summons work."
                    : "Natural spawning is ON: it appears on its own (a roll every " + FogLurkerConfig.COMMON.spawnRollMinutes.get()
                    + " minutes after the grace period)."
                    : "Natural spawning is OFF: it only comes when summoned with /foglurker spawn or /foglurker force.")
                    .withStyle(ChatFormatting.GRAY));
            return data.naturalSpawning() ? 1 : 0;
        }
        boolean wasOn = data.naturalSpawning();
        data.setNaturalSpawning(on);
        reply(src, Component.literal(on
                ? (wasOn ? "Natural spawning is already ON. " : "Natural spawning is now ON. ")
                + (data.inGrace() ? "It starts rolling when the grace period ends."
                : LurkerDirector.isResting(src.getServer()) ? "It was killed: the clock starts again after its rest, in "
                + time(LurkerDirector.restTicksLeft(src.getServer())) + "."
                : data.nextSpawnTicks() >= 0 ? "The next roll is in " + time(data.nextSpawnTicks())
                + (LurkerDirector.rollsRunning(src.getServer()) ? "" : " (paused: everyone is AFK, being hunted, in their 15-minute peace or outside the Overworld)") + "."
                : "The first roll is in " + FogLurkerConfig.COMMON.spawnRollMinutes.get() + " minutes.")
                : "Natural spawning is now OFF. It will only come when summoned with /foglurker spawn or /foglurker force. "
                + "Anything already out there stays until it leaves (/foglurker despawn sends it away).")
                .withStyle(on ? ChatFormatting.RED : ChatFormatting.GRAY));
        return 1;
    }

    private static int spawnTimer(CommandContext<CommandSourceStack> ctx) {
        FogLurkerSavedData data = FogLurkerSavedData.get(ctx.getSource().getServer());
        CommandSourceStack src = ctx.getSource();
        if (LurkerDirector.isResting(src.getServer())) {
            reply(src, Component.literal("It was killed and rests for another " + time(LurkerDirector.restTicksLeft(src.getServer()))
                    + "; until then nothing appears on its own and nothing haunts (admin spawn commands still work)."
                    + (data.naturalSpawning() ? "" : " Natural spawning is off, so after that it still only comes when summoned."))
                    .withStyle(ChatFormatting.DARK_AQUA));
            return 0;
        }
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
        boolean empty = src.getServer().getPlayerList().getPlayerCount() == 0;
        boolean paused = !LurkerDirector.rollsRunning(src.getServer());
        reply(src, Component.literal("Next spawn roll in: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(time(next)).withStyle(ChatFormatting.WHITE))
                .append(empty ? Component.literal(" (paused: nobody is online)").withStyle(ChatFormatting.GRAY)
                        : paused ? Component.literal(" (paused: nobody it could visit right now: everyone is AFK, being hunted, in their 15-minute peace or outside the Overworld)").withStyle(ChatFormatting.GRAY)
                        : Component.empty())
                .append(data.nextIsGuaranteed()
                        ? Component.literal(" (guaranteed: the last roll did not bring it)").withStyle(ChatFormatting.RED)
                        : Component.literal(" (" + Math.round(FogLurkerConfig.COMMON.spawnChance.get() * 100) + "% chance)")
                        .withStyle(ChatFormatting.GRAY)));
        return next / 20;
    }

    /** Nothing may escape this command: Minecraft would log the whole command line, key included. */
    private static int safeSetKey(CommandContext<CommandSourceStack> ctx, String raw) {
        try {
            return setKey(ctx, raw);
        } catch (RuntimeException e) {
            FogLurkerMod.LOGGER.error("Setting the Gemini API key failed ({})", e.getClass().getSimpleName());
            ctx.getSource().sendFailure(Component.literal("Could not set the key (" + e.getClass().getSimpleName() + ")."));
            return 0;
        }
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
        } catch (RuntimeException e) {
            // Caught here so Minecraft never logs the failed command line, which contains the key.
            FogLurkerMod.LOGGER.error("Could not store Gemini API key ({})", e.getClass().getSimpleName());
            src.sendFailure(Component.literal("Could not save the key (" + e.getClass().getSimpleName() + ")."));
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
        if (key == null && ApiKeyStore.storedKeyBusy()) {
            reply(src, Component.literal("The saved Gemini API key cannot be opened right now (another program may have "
                    + "it open). Try again in a minute; nothing needs to be set again.").withStyle(ChatFormatting.GOLD));
            return 0;
        }
        if (key == null && ApiKeyStore.storedKeyUnreadable()) {
            reply(src, Component.literal("A Gemini API key is saved but cannot be read (the secure folder's key changed or the "
                    + "file is damaged). Set it again with /foglurker setgeminiapikey <key>.").withStyle(ChatFormatting.GOLD));
            return 0;
        }
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
                int status = result == null ? 0 : result.status();
                // Advice that fits the failure: only a refusal points at the key.
                boolean local = why != null && (why.contains("set it again") || why.contains("model names"));
                String advice = local ? "Fix that, then test again with /foglurker geministatus."
                        : status == 0 && why != null && (why.contains("did not answer") || why.contains("too slow")
                                || why.contains("timed out") || why.contains("too long"))
                        ? "Google's models are busy right now; the key may be fine. Try again in a few minutes, or raise requestTimeoutSeconds "
                        + "under [gemini] in config/foglurker-common.toml."
                        : status == 0 ? "Could not reach Google (network or timeout); the key may be fine, try again later."
                        : status == 429 ? "Google is rate limiting this key for now; it waits and tries again by itself."
                        : status >= 500 ? "Google's side is having trouble; try again later."
                        : GeminiClient.isKeyRefusal(result)
                        ? "Check the key, and that the Generative Language API, billing and your region are allowed for it."
                        : status == 400 ? "Google refused the request for these models; try other model names in the config."
                        : "The key is kept; check it and set it again if needed.";
                reply(src, Component.literal("Gemini test failed: " + GeminiClient.stripEnd(why) + ". " + advice)
                        .withStyle(ChatFormatting.RED));
            }
        }));
    }

    private static Component describe(LurkerDifficulty d) {
        String damage = d.isOneShot() ? "a bite takes a whole health bar (good armour blocks part of it)"
                : (fmtHearts(d.heartsPerHit()) + " per hit (good armour blocks part of it)");
        String attacks = switch (d) {
            case VERY_EASY -> "rarely attacks, mostly only watches";
            case EASY -> "attacks rarely";
            case MEDIUM -> "attacks on 1 of every 3 appearances";
            case HARD -> "attacks on 1 of every 2 appearances";
            case DEATHWISH -> "attacks on 3 of every 4 appearances, 50% faster";
        };
        return Component.literal(d.displayName()).withStyle(d == LurkerDifficulty.DEATHWISH ? ChatFormatting.DARK_RED : ChatFormatting.WHITE)
                .append(Component.literal(": " + attacks + ", " + damage + ", " + d.graceMinutes()
                        + " min grace period. It has " + d.lurkerHearts() + " hearts.").withStyle(ChatFormatting.GRAY));
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
        com.foglurker.world.PlayerEvents.sendAlwaysFogToAll(ctx.getSource().getServer()); // the mode's own fog, unless set
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
                l.brain().dismiss(); // sent away by an admin: not an escape, so no "you got away" and no peace
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
        if (!VoiceBridge.isActive()) {
            p.sendSystemMessage(Component.literal("(Simple Voice Chat is not running on this server, so nothing would be recorded "
                    + "until it is.)").withStyle(ChatFormatting.DARK_GRAY));
        }
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
        if (!VoiceConsent.optIn(p.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("The voice consent list cannot be opened right now (another program "
                    + "may have it open). Nothing was changed; please try again in a minute."));
            return 0;
        }
        p.sendSystemMessage(Component.literal("great! you have now opted in use the command ").withStyle(ChatFormatting.GREEN)
                .append(suggest("/fglurk optout"))
                .append(Component.literal(" to cancel it ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("NOTE: this will delete all your voice snippets that have been stored")
                        .withStyle(ChatFormatting.YELLOW)));
        warnIfUnsaved(p);
        if (!VoiceBridge.isActive()) {
            p.sendSystemMessage(Component.literal("(Simple Voice Chat is not running on this server, so nothing is recorded until it is.)")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return 1;
    }

    private static int declineTerms(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        boolean wasIn = VoiceConsent.isOptedIn(p.getUUID());
        // Always opt out, even when the list cannot be read right now (then wasIn is unknown): saying no must
        // never leave an old opt-in standing.
        int deleted = VoiceConsent.optOut(p.getUUID());
        p.sendSystemMessage(Component.literal("we are sad that you chose this if you want to opt in then use ")
                .withStyle(ChatFormatting.GRAY).append(suggest("/fglurk optin")));
        if (wasIn || deleted > 0) {
            p.sendSystemMessage(Component.literal("You were opted in before, so you are now opted out and "
                    + deleted + " stored snippet(s) were deleted.").withStyle(ChatFormatting.DARK_GRAY));
        }
        warnIfUnsaved(p);
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
        warnIfUnsaved(p);
        return 1;
    }

    private static void warnIfUnsaved(ServerPlayer p) {
        if (VoiceConsent.hasUnsavedChanges()) {
            p.sendSystemMessage(Component.literal("Note: the server could not write this to its disk yet. It applies now and the "
                    + "server keeps trying; if you see this again, tell the server owner.").withStyle(ChatFormatting.GOLD));
        }
    }

    private static int voiceStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (!VoiceConsent.isReadable()) {
            p.sendSystemMessage(PREFIX.copy().append(Component.literal("The voice consent list cannot be opened right now "
                    + "(another program may have it open). Nothing is recorded or played meanwhile; try again in a minute.")
                    .withStyle(ChatFormatting.GOLD)));
            return 0;
        }
        boolean in = VoiceConsent.isOptedIn(p.getUUID());
        int count = in ? SnippetStore.count(p.getUUID()) : 0;
        p.sendSystemMessage(PREFIX.copy().append(Component.literal(in
                ? "You are opted in. Stored snippets: " + count + "."
                : "You are opted out. Nothing of your voice is stored.").withStyle(ChatFormatting.GRAY)));
        p.sendSystemMessage(Component.literal(!VoiceBridge.isActive() ? "Simple Voice Chat: not running"
                : VoiceBridge.hasVoiceClient(p.getUUID()) ? "Simple Voice Chat: running"
                : "Simple Voice Chat: running, but your game is not connected to it, so nothing of your voice can be recorded")
                .withStyle(ChatFormatting.DARK_GRAY));
        return in ? 1 : 0;
    }

    /** Clickable line used for the one-time join notice. */
    public static Component termsLink() {
        return suggest("/flurk voicesnippets terms");
    }
}
