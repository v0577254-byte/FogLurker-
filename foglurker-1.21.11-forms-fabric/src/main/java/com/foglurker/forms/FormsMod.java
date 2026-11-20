package com.foglurker.forms;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The Fog Lurker on Minecraft 1.21.11 (Fabric) as a still figure for videos, in any of its shapes: a copy of a player
 * (their skin), cow, sheep, pig, chicken, zombie, wolf, dog, or the pale crawler itself. It never moves or makes a
 * sound, and only its head turns, to look you in the eyes. {@code /foglurker spawn <type>} puts one at your feet.
 */
public final class FormsMod implements ModInitializer {
    public static final String MOD_ID = "foglurker";

    private static final ResourceKey<EntityType<?>> FOG_LURKER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("fog_lurker"));

    public static final EntityType<FogLurker> FOG_LURKER = Registry.register(BuiltInRegistries.ENTITY_TYPE, FOG_LURKER_KEY,
            EntityType.Builder.<FogLurker>of(FogLurker::new, MobCategory.MISC)
                    .sized(0.9F, 1.3F).clientTrackingRange(10)
                    .build(FOG_LURKER_KEY));

    private static final SuggestionProvider<CommandSourceStack> FORMS = (ctx, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Form.values()).map(Form::id), b);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(FOG_LURKER, FogLurker.createAttributes());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("foglurker")
                .then(Commands.literal("spawn")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        // No type: the Fog Lurker itself, the pale crawler.
                        .executes(ctx -> spawn(ctx.getSource(), Form.CRAWLER.id(), null))
                        // player <name>: a copy of that player (their skin); online players only.
                        .then(Commands.literal("player")
                                .executes(ctx -> spawn(ctx.getSource(), Form.PLAYER.id(), null))
                                .then(Commands.argument("who", EntityArgument.player())
                                        .executes(ctx -> spawn(ctx.getSource(), Form.PLAYER.id(), EntityArgument.getPlayer(ctx, "who")))))
                        .then(Commands.argument("type", StringArgumentType.word()).suggests(FORMS)
                                .executes(ctx -> spawn(ctx.getSource(), StringArgumentType.getString(ctx, "type"), null)))));
    }

    /**
     * Right at your feet, its body facing you (step back and you are face to face). A player copy wears {@code who}'s
     * skin, or yours.
     */
    private static int spawn(CommandSourceStack src, String type, @Nullable ServerPlayer who) {
        Form form = Form.byId(type);
        if (form == null) {
            src.sendFailure(Component.literal("Unknown type \"" + type + "\". Types: "
                    + Arrays.stream(Form.values()).map(Form::id).collect(Collectors.joining(", "))));
            return 0;
        }
        ServerPlayer copied = who;
        if (form == Form.PLAYER && copied == null) {
            if (!(src.getEntity() instanceof ServerPlayer self)) {
                src.sendFailure(Component.literal("From the console, say whose copy: /foglurker spawn player <name>"));
                return 0;
            }
            copied = self;
        }
        ServerLevel level = src.getLevel();
        Vec3 at = src.getPosition();
        FogLurker lurker = FOG_LURKER.create(level, EntitySpawnReason.COMMAND);
        if (lurker == null) {
            src.sendFailure(Component.literal("The Fog Lurker could not be spawned here."));
            return 0;
        }
        lurker.setForm(form);
        if (copied != null) {
            UUID uuid = copied.getUUID();
            lurker.setMimic(uuid, copied.getGameProfile().name());
        }
        float facing = src.getRotation().y + 180F;
        lurker.snapTo(at.x, at.y, at.z, facing, 0F);
        lurker.setYHeadRot(facing);
        lurker.setYBodyRot(facing);
        level.addFreshEntity(lurker);
        String what = switch (form) {
            case PLAYER -> "wearing " + (copied == src.getEntity() ? "your" : copied.getGameProfile().name() + "'s") + " face";
            case CRAWLER -> "as itself";
            case DOG -> "as a dog";
            default -> "as a " + form.id();
        };
        src.sendSuccess(() -> Component.literal("The Fog Lurker is here, " + what + ".").withStyle(ChatFormatting.DARK_RED), true);
        return 1;
    }
}
