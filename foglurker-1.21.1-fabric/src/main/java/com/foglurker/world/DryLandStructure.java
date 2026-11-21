package com.foglurker.world;

import com.foglurker.FogLurkerMod;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One of the mod's structures, kept off water: it is laid out as usual (by the structure inside, a jigsaw), then the
 * ground under every piece of it, and a few blocks round, is checked in the world generator's own terrain. If any of it
 * is under water (the sea, a river, or a lake or pond in the terrain itself), the structure is not built there at all. Everything else
 * (biomes, how the ground is blended in, the generation step) is the inner structure's.
 *
 * <pre>{"type": "foglurker:dry_land", "structure": { ...an ordinary "minecraft:jigsaw" structure... }}</pre>
 */
public final class DryLandStructure extends Structure {
    public static final MapCodec<DryLandStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Structure.DIRECT_CODEC.fieldOf("structure").forGetter(DryLandStructure::inner)).apply(i, DryLandStructure::new));

    public static final StructureType<DryLandStructure> TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
            FogLurkerMod.id("dry_land"), (StructureType<DryLandStructure>) () -> CODEC);

    /** Called from the mod's initializer, so the type is registered before any data pack is read. */
    public static void register() {
    }

    /** How far round each piece the ground must be dry too (so no wall stands at the water's edge). */
    public static final int MARGIN = 3;
    /** Checked every this many blocks across each piece (and always at its edges). */
    private static final int STEP = 6;

    private final Structure inner;

    public DryLandStructure(Structure inner) {
        super(new StructureSettings(inner.biomes(), inner.spawnOverrides(), inner.step(), inner.terrainAdaptation()));
        this.inner = inner;
    }

    public Structure inner() {
        return inner;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        Optional<GenerationStub> stub = inner.findValidGenerationPoint(context);
        if (stub.isEmpty()) {
            return stub;
        }
        // Lay it out once, look at where its pieces would stand, and hand on that same layout (laying it out again
        // would draw new random numbers and could give a different one).
        StructurePiecesBuilder pieces = stub.get().getPiecesBuilder();
        if (!onDryLand(context.chunkGenerator(), context.heightAccessor(), context.randomState(), pieces.build().pieces())) {
            return Optional.empty();
        }
        return Optional.of(new GenerationStub(stub.get().position(), Either.right(pieces)));
    }

    /** No water under any of these pieces, or within {@link #MARGIN} blocks of them. */
    public static boolean onDryLand(ChunkGenerator generator, LevelHeightAccessor height, RandomState random, List<StructurePiece> pieces) {
        // Each column is worked out from the terrain noise all the way down, so each spot is looked at only once (the
        // pieces and their margins overlap, and the grid is lined up with the world so they share spots).
        Set<Long> seen = new HashSet<>();
        for (StructurePiece piece : pieces) {
            BoundingBox b = piece.getBoundingBox();
            for (int x : across(b.minX() - MARGIN, b.maxX() + MARGIN)) {
                for (int z : across(b.minZ() - MARGIN, b.maxZ() + MARGIN)) {
                    if (seen.add(((long) x << 32) ^ (z & 0xFFFFFFFFL)) && wetAt(generator, height, random, x, z)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Whether the top of the generator's terrain here (before anything is built or grown) is water or another fluid. */
    public static boolean wetAt(ChunkGenerator generator, LevelHeightAccessor height, RandomState random, int x, int z) {
        NoiseColumn column = generator.getBaseColumn(x, z, height, random);
        for (int y = height.getMaxBuildHeight() - 1; y >= height.getMinBuildHeight(); y--) {
            BlockState state = column.getBlock(y);
            if (!state.isAir()) {
                return !state.getFluidState().isEmpty();
            }
        }
        return false;
    }

    /** From {@code lo} to {@code hi}: both ends, and every multiple of {@link #STEP} between (no gap is wider than that). */
    private static int[] across(int lo, int hi) {
        int first = Math.floorDiv(lo, STEP) * STEP + STEP;
        int inner = first < hi ? (hi - 1 - first) / STEP + 1 : 0;
        int[] out = new int[inner + 2];
        out[0] = lo;
        for (int k = 0; k < inner; k++) {
            out[k + 1] = first + k * STEP;
        }
        out[inner + 1] = hi;
        return out;
    }

    @Override
    public StructureType<?> type() {
        return TYPE;
    }
}
