package com.foglurker.crawler;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * The Fog Lurker's pale crawler on Minecraft 1.21.11, as a still figure for videos: its texture and crouched
 * posture, a head that follows your eyes, no movement and no sounds. {@code /foglurker spawn} puts one at your feet.
 */
@Mod(CrawlerMod.MOD_ID)
public final class CrawlerMod {
    public static final String MOD_ID = "foglurker";

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);

    public static final RegistryObject<EntityType<Crawler>> CRAWLER = ENTITIES.register("crawler",
            () -> EntityType.Builder.of(Crawler::new, MobCategory.MISC)
                    .sized(0.9F, 1.3F).eyeHeight(0.9F).clientTrackingRange(10)
                    .build(ENTITIES.key("crawler")));

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public CrawlerMod(FMLJavaModLoadingContext context) {
        ENTITIES.register(context.getModBusGroup());
        EntityAttributeCreationEvent.BUS.addListener(e -> e.put(CRAWLER.get(), Crawler.createAttributes().build()));
        RegisterCommandsEvent.BUS.addListener(CrawlerMod::registerCommands);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("foglurker")
                .then(Commands.literal("spawn")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> spawn(ctx.getSource()))));
    }

    /** Right at your feet, its body facing you (step back and you are face to face). */
    private static int spawn(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        Vec3 at = src.getPosition();
        Crawler crawler = CRAWLER.get().create(level, EntitySpawnReason.COMMAND);
        if (crawler == null) {
            src.sendFailure(Component.literal("The Fog Lurker could not be spawned here."));
            return 0;
        }
        float facing = src.getRotation().y + 180F;
        crawler.snapTo(at.x, at.y, at.z, facing, 0F);
        crawler.setYHeadRot(facing);
        crawler.setYBodyRot(facing);
        level.addFreshEntity(crawler);
        src.sendSuccess(() -> Component.literal("The Fog Lurker is here.").withStyle(ChatFormatting.DARK_RED), true);
        return 1;
    }
}
