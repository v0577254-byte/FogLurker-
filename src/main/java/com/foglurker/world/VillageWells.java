package com.foglurker.world;

import com.foglurker.FogLurkerMod;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * A fog-choked well now and then among a plains, taiga or savanna village's houses (as well as the rarer ones out on
 * their own). It goes into those villages' house pools when the server starts, as rare as a rare house.
 */
@Mod.EventBusSubscriber(modid = FogLurkerMod.MOD_ID)
public final class VillageWells {
    public static final String TEMPLATE = "foglurker:fog_well_village";
    /** Out of the hundred-odd weight of a village's houses. */
    public static final int WEIGHT = 2;
    public static final List<String> VILLAGES = List.of("plains", "taiga", "savanna");

    private VillageWells() {
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        add(event.getServer());
    }

    /** @return how many village pools got the well */
    public static int add(MinecraftServer server) {
        Registry<StructureTemplatePool> pools = server.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        Holder<StructureProcessorList> none = server.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST)
                .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.PROCESSOR_LIST,
                        new ResourceLocation("minecraft", "empty")));
        int added = 0;
        for (String village : VILLAGES) {
            StructureTemplatePool pool = pools.get(new ResourceLocation("minecraft", "village/" + village + "/houses"));
            if (pool == null) {
                continue;
            }
            try {
                if (addTo(pool, StructurePoolElement.single(TEMPLATE, none).apply(StructureTemplatePool.Projection.RIGID), WEIGHT)) {
                    added++;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                FogLurkerMod.LOGGER.warn("Could not add the fog well to {} villages: {}", village, e.toString());
                return added; // every pool has the same fields: no use trying the others
            }
        }
        return added;
    }

    @SuppressWarnings("unchecked")
    private static boolean addTo(StructureTemplatePool pool, StructurePoolElement well, int weight) throws ReflectiveOperationException {
        Field templatesField = ObfuscationReflectionHelper.findField(StructureTemplatePool.class, "f_210560_");
        Field rawField = ObfuscationReflectionHelper.findField(StructureTemplatePool.class, "f_210559_");
        List<StructurePoolElement> templates = (List<StructurePoolElement>) templatesField.get(pool);
        if (templates.stream().anyMatch(e -> e.toString().contains(TEMPLATE))) {
            return false; // already there (the server started again in the same game)
        }
        for (int i = 0; i < weight; i++) {
            templates.add(well);
        }
        List<Pair<StructurePoolElement, Integer>> raw = new ArrayList<>((List<Pair<StructurePoolElement, Integer>>) rawField.get(pool));
        raw.add(Pair.of(well, weight));
        rawField.set(pool, raw);
        return true;
    }

    /** How many times a village pool lists the well (game tests). */
    @SuppressWarnings("unchecked")
    public static long count(MinecraftServer server, String village) throws ReflectiveOperationException {
        StructureTemplatePool pool = server.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL)
                .get(new ResourceLocation("minecraft", "village/" + village + "/houses"));
        Field templatesField = ObfuscationReflectionHelper.findField(StructureTemplatePool.class, "f_210560_");
        return pool == null ? 0 : ((List<StructurePoolElement>) templatesField.get(pool)).stream()
                .filter(e -> e.toString().contains(TEMPLATE)).count();
    }
}
