package com.foglurker.mixin;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** The fog well goes into the village house pools (VillageWells). */
@Mixin(StructureTemplatePool.class)
public interface StructureTemplatePoolAccessor {
    @Accessor("templates")
    ObjectArrayList<StructurePoolElement> foglurker$templates();

    @Accessor("rawTemplates")
    List<Pair<StructurePoolElement, Integer>> foglurker$rawTemplates();

    @Mutable
    @Accessor("rawTemplates")
    void foglurker$setRawTemplates(List<Pair<StructurePoolElement, Integer>> raw);
}
