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

import java.util.ArrayList;
import java.util.List;

/**
 * A fog-choked well now and then among a plains, taiga or savanna village's houses (as well as the rarer ones out on
 * their own). It goes into those villages' house pools when the server starts, as rare as a rare house.
 */
public final class VillageWells {
    public static final String TEMPLATE = "foglurker:fog_well_village";
    /** Out of the hundred-odd weight of a village's houses. */
    public static final int WEIGHT = 2;
    public static final List<String> VILLAGES = List.of("plains", "taiga", "savanna");

    private VillageWells() {
    }

    /** When the server is starting (ModEvents). */
    public static void onServerStarting(MinecraftServer server) {
        add(server);
    }

    /** @return how many village pools got the well */
    public static int add(MinecraftServer server) {
        Registry<StructureTemplatePool> pools = server.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        Holder<StructureProcessorList> none = server.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST)
                .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.PROCESSOR_LIST,
                        ResourceLocation.withDefaultNamespace("empty")));
        int added = 0;
        for (String village : VILLAGES) {
            StructureTemplatePool pool = pools.get(ResourceLocation.fromNamespaceAndPath("minecraft", "village/" + village + "/houses"));
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

    private static boolean addTo(StructureTemplatePool pool, StructurePoolElement well, int weight) throws ReflectiveOperationException {
        var acc = (com.foglurker.mixin.StructureTemplatePoolAccessor) pool;
        List<StructurePoolElement> templates = acc.foglurker$templates();
        if (templates.stream().anyMatch(e -> e.toString().contains(TEMPLATE))) {
            return false; // already there (the server started again in the same game)
        }
        for (int i = 0; i < weight; i++) {
            templates.add(well);
        }
        List<Pair<StructurePoolElement, Integer>> raw = new ArrayList<>(acc.foglurker$rawTemplates());
        raw.add(Pair.of(well, weight));
        acc.foglurker$setRawTemplates(raw);
        return true;
    }

    /** How many times a village pool lists the well (game tests). */
    public static long count(MinecraftServer server, String village) throws ReflectiveOperationException {
        StructureTemplatePool pool = server.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL)
                .get(ResourceLocation.fromNamespaceAndPath("minecraft", "village/" + village + "/houses"));
        return pool == null ? 0 : ((com.foglurker.mixin.StructureTemplatePoolAccessor) pool).foglurker$templates().stream()
                .filter(e -> e.toString().contains(TEMPLATE)).count();
    }
}
