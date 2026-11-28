package com.foglurker.mixin;

import com.foglurker.world.DryLandStructure;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Structure.class)
public abstract class VillageWellPlacementMixin {
   @Inject(method = "generate", at = @At("RETURN"), cancellable = true)
   private void foglurker$checkVillageWells(
      RegistryAccess registries,
      ChunkGenerator generator,
      BiomeSource biomes,
      RandomState random,
      StructureTemplateManager templates,
      long seed,
      ChunkPos chunkPos,
      int references,
      LevelHeightAccessor height,
      Predicate<Holder<Biome>> validBiome,
      CallbackInfoReturnable<StructureStart> cir
   ) {
      StructureStart start = (StructureStart)cir.getReturnValue();
      List<StructurePiece> pieces = start.getPieces();
      List<StructurePiece> filtered = null;

      for (int i = 0; i < pieces.size(); i++) {
         StructurePiece piece = pieces.get(i);
         if (piece instanceof PoolElementStructurePiece poolPiece && poolPiece.getElement() instanceof FogWellTemplateAccessor accessor) {
            Object template = accessor.foglurker$getTemplate().left().orElse(null);
            if (template != null
               && "foglurker:fog_well_village".equals(template.toString())
               && !DryLandStructure.onDryLand(generator, height, random, List.of(piece))) {
               if (filtered == null) {
                  filtered = new ArrayList<>(pieces.subList(0, i));
               }
               continue;
            }
         }

         if (filtered != null) {
            filtered.add(piece);
         }
      }

      if (filtered != null) {
         cir.setReturnValue(
            filtered.isEmpty() ? StructureStart.INVALID_START : new StructureStart(start.getStructure(), chunkPos, references, new PiecesContainer(filtered))
         );
      }
   }
}
