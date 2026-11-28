package com.foglurker.world;

import com.foglurker.FogLurkerMod;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
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
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One of the mod's structures, kept off water: it is laid out as usual (by the structure inside, a jigsaw), then the
 * ground under every piece of it, and a few blocks round, is checked in the world generator's own terrain. If any of it
 * is under water (the sea, a river, or a lake or pond: since 1.18 those are part of the terrain itself, not features
 * added later), the structure is not built there at all. Everything else
 * (biomes, how the ground is blended in, the generation step) is the inner structure's.
 *
 * <pre>{"type": "foglurker:dry_land", "structure": { ...an ordinary "minecraft:jigsaw" structure... }}</pre>
 */
public final class DryLandStructure extends Structure {
    public static final Codec<DryLandStructure> CODEC = RecordCodecBuilder.create(i -> i.group(
            Structure.DIRECT_CODEC.fieldOf("structure").forGetter(DryLandStructure::inner),
            Codec.INT.optionalFieldOf("ground").forGetter(DryLandStructure::ground),
            Codec.INT.optionalFieldOf("max_slope", 8).forGetter(DryLandStructure::maxSlope))
            .apply(i, DryLandStructure::new));

    public static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, FogLurkerMod.MOD_ID);
    public static final RegistryObject<StructureType<DryLandStructure>> TYPE = TYPES.register("dry_land", () -> () -> CODEC);

    /** How far round each piece the ground must be dry too (so no wall stands at the water's edge). */
    public static final int MARGIN = 3;
    /** Checked every this many blocks across each piece (and always at its edges). */
    private static final int STEP = 4;

    /** The most the ground under it may rise and fall (blocks) before a flatter spot is looked for. */
    public static final int DEFAULT_MAX_SLOPE = 8;
    /** Where else it may stand if the ground is too steep where it was laid out (blocks, in steps of the grid). */
    private static final int[][] NUDGES = {{0, 0}, {16, 0}, {-16, 0}, {0, 16}, {0, -16}, {16, 16}, {-16, -16}, {16, -16},
            {-16, 16}, {32, 0}, {-32, 0}, {0, 32}, {0, -32}};

    private final Structure inner;
    /**
     * Where its ground is: the first air above the ground, in blocks above the bottom of its first piece (for a
     * castle, the keep's hall floor over its crypt). Empty: placed as the inner structure says, not levelled.
     */
    private final Optional<Integer> ground;
    private final int maxSlope;

    public DryLandStructure(Structure inner, Optional<Integer> ground, int maxSlope) {
        super(new StructureSettings(inner.biomes(), inner.spawnOverrides(), inner.step(), inner.terrainAdaptation()));
        this.inner = inner;
        this.ground = ground;
        this.maxSlope = maxSlope;
    }

    public DryLandStructure(Structure inner) {
        this(inner, Optional.empty(), DEFAULT_MAX_SLOPE);
    }

    public Structure inner() {
        return inner;
    }

    public Optional<Integer> ground() {
        return ground;
    }

    public int maxSlope() {
        return maxSlope;
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
        List<StructurePiece> list = pieces.build().pieces();
        if (ground.isEmpty()) {
            if (!onDryLand(context.chunkGenerator(), context.heightAccessor(), context.randomState(), list)) {
                return Optional.empty();
            }
            return Optional.of(new GenerationStub(stub.get().position(), Either.right(pieces)));
        }
        // Stood on the ground as a whole, not by the one spot under its middle: at the middle height of the ground
        // under all of it, somewhere (here, or a little way off) the ground is flat enough and dry.
        java.util.Map<Long, Integer> heights = new java.util.HashMap<>();
        int movedX = 0;
        int movedZ = 0;
        for (int[] nudge : NUDGES) {
            int dx = nudge[0] - movedX;
            int dz = nudge[1] - movedZ;
            if (dx != 0 || dz != 0) {
                for (StructurePiece piece : list) {
                    shift(piece, dx, 0, dz);
                }
                movedX = nudge[0];
                movedZ = nudge[1];
            }
            int[] lowHigh = new int[2];
            int level = groundLevel(context, list, heights, lowHigh);
            if (lowHigh[1] - lowHigh[0] > maxSlope
                    || !onDryLand(context.chunkGenerator(), context.heightAccessor(), context.randomState(), list)) {
                continue;
            }
            int want = list.get(0).getBoundingBox().minY() + ground.get();
            for (StructurePiece piece : list) {
                shift(piece, 0, level - want, 0);
            }
            int groundY = level;
            for (StructurePiece piece : list) {
                if (piece instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pool) {
                    // The terrain round each piece is shaped to meet the structure's ground, not the bottom of its box.
                    ((com.foglurker.mixin.PoolPieceGroundAccessor) pool).foglurker$setGroundLevelDelta(groundY - piece.getBoundingBox().minY());
                }
            }
            net.minecraft.core.BlockPos at = stub.get().position().offset(movedX, level - want, movedZ);
            return Optional.of(new GenerationStub(at, Either.right(pieces)));
        }
        return Optional.empty();
    }

    /**
     * Moves a piece, and its jigsaw joints with it: the terrain is shaped round those too, so left behind they would
     * raise a lump (or dig a dent) where the piece used to be.
     */
    static void shift(StructurePiece piece, int dx, int dy, int dz) {
        piece.move(dx, dy, dz);
        if (piece instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pool) {
            List<net.minecraft.world.level.levelgen.structure.pools.JigsawJunction> joints = pool.getJunctions();
            for (int i = 0; i < joints.size(); i++) {
                var j = joints.get(i);
                joints.set(i, new net.minecraft.world.level.levelgen.structure.pools.JigsawJunction(j.getSourceX() + dx,
                        j.getSourceGroundY() + dy, j.getSourceZ() + dz, j.getDeltaY(), j.getDestProjection()));
            }
        }
    }

    /**
     * The middle height of the ground (first air above the terrain, before anything is built) under these pieces;
     * {@code lowHigh} gets the lowest and highest. Heights are kept in {@code heights} by column.
     */
    private static int groundLevel(GenerationContext context, List<StructurePiece> pieces, java.util.Map<Long, Integer> heights,
                                   int[] lowHigh) {
        java.util.List<Integer> all = new java.util.ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (StructurePiece piece : pieces) {
            BoundingBox b = piece.getBoundingBox();
            for (int x : across(b.minX(), b.maxX())) {
                for (int z : across(b.minZ(), b.maxZ())) {
                    long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
                    if (seen.add(key)) {
                        all.add(heights.computeIfAbsent(key, k -> context.chunkGenerator().getFirstFreeHeight(x, z,
                                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(),
                                context.randomState())));
                    }
                }
            }
        }
        java.util.Collections.sort(all);
        lowHigh[0] = all.get(0);
        lowHigh[1] = all.get(all.size() - 1);
        return all.get(all.size() / 2);
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
        return TYPE.get();
    }
}
