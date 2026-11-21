package com.foglurker.world;

import com.foglurker.FogLurkerMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * The Fog Lurker's own structures, and what they change: it haunts its hotspots (the lookout tower, the abandoned
 * castle, the hunters' bunker) more at night, sleep there is more often sleep paralysis, the villagers by a fog well
 * or a warded stronghold's gate hide when it is about at night, and from the top of a lookout tower you see it out at
 * the edge of the fog.
 */
public final class FogStructures {
    public static final TagKey<Structure> LURKER_HOTSPOTS = TagKey.create(Registries.STRUCTURE, FogLurkerMod.id("lurker_hotspots"));
    public static final TagKey<Structure> WARDED_STRONGHOLDS = TagKey.create(Registries.STRUCTURE, FogLurkerMod.id("warded_strongholds"));
    public static final TagKey<Structure> FOG_WELLS = TagKey.create(Registries.STRUCTURE, FogLurkerMod.id("fog_wells"));
    public static final ResourceKey<Structure> LOOKOUT_TOWER = ResourceKey.create(Registries.STRUCTURE, FogLurkerMod.id("lookout_tower"));

    /** All six, for the debug command and the tests. */
    public static final List<String> ALL = List.of("lookout_tower", "abandoned_campfire", "abandoned_castle", "fortified_castle",
            "hunters_bunker", "fog_well");

    /** Game tests: pretend a position is (or is not) inside structures with this tag. Null in real play. */
    @Nullable
    public static volatile BiPredicate<BlockPos, TagKey<Structure>> testInside;
    /** Game tests: fog wells and stronghold gates to use instead of looking in the world. Null in real play. */
    @Nullable
    public static volatile List<BlockPos> testWells;
    @Nullable
    public static volatile List<BlockPos> testGates;

    private FogStructures() {
    }

    /** Inside one of the pieces of a structure with this tag. */
    public static boolean inside(ServerLevel level, BlockPos pos, TagKey<Structure> tag) {
        BiPredicate<BlockPos, TagKey<Structure>> test = testInside;
        if (test != null) {
            return test.test(pos, tag);
        }
        return level.isLoaded(pos) && level.structureManager().getStructureWithPieceAt(pos, tag).isValid();
    }

    public static boolean inHotspot(ServerLevel level, BlockPos pos) {
        return inside(level, pos, LURKER_HOTSPOTS);
    }

    /**
     * Up in a lookout tower: inside it and at least 8 blocks above the ground around it. From up there it shows itself
     * out at the edge of the fog.
     */
    public static boolean highInLookout(ServerLevel level, BlockPos pos) {
        BiPredicate<BlockPos, TagKey<Structure>> test = testInside;
        boolean inTower = test != null ? test.test(pos, LURKER_HOTSPOTS)
                : level.isLoaded(pos) && level.structureManager().getStructureWithPieceAt(pos, LOOKOUT_TOWER).isValid();
        if (!inTower) {
            return false;
        }
        int ground = Integer.MAX_VALUE;
        for (int[] d : new int[][]{{8, 0}, {-8, 0}, {0, 8}, {0, -8}}) {
            ground = Math.min(ground, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX() + d[0], pos.getZ() + d[1]));
        }
        return pos.getY() - ground >= 8;
    }

    /** Fog wells within {@code radius}: standalone ones, and the ones built into villages. Their middle. */
    public static List<BlockPos> fogWellsNear(ServerLevel level, BlockPos pos, int radius) {
        List<BlockPos> test = testWells;
        if (test != null) {
            return test.stream().filter(w -> w.closerThan(pos, radius)).toList();
        }
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<BlockPos> out = new ArrayList<>();
        for (StructureStart start : startsNear(level, pos, radius,
                s -> registry.wrapAsHolder(s).is(FOG_WELLS) || registry.wrapAsHolder(s).is(StructureTags.VILLAGE))) {
            boolean well = registry.wrapAsHolder(start.getStructure()).is(FOG_WELLS);
            for (StructurePiece piece : start.getPieces()) {
                if (well || pieceIs(piece, "fog_well_village")) {
                    BlockPos c = piece.getBoundingBox().getCenter();
                    if (c.closerThan(pos, radius)) {
                        out.add(c);
                    }
                    if (well) {
                        break;
                    }
                }
            }
        }
        return out;
    }

    /** The gates of warded strongholds within {@code radius}: the middle of each gatehouse. */
    public static List<BlockPos> strongholdGatesNear(ServerLevel level, BlockPos pos, int radius) {
        List<BlockPos> test = testGates;
        if (test != null) {
            return test.stream().filter(w -> w.closerThan(pos, radius)).toList();
        }
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<BlockPos> out = new ArrayList<>();
        for (StructureStart start : startsNear(level, pos, radius, s -> registry.wrapAsHolder(s).is(WARDED_STRONGHOLDS))) {
            for (StructurePiece piece : start.getPieces()) {
                if (pieceIs(piece, "gatehouse")) {
                    BlockPos c = piece.getBoundingBox().getCenter();
                    if (c.closerThan(pos, radius)) {
                        out.add(c);
                    }
                }
            }
        }
        return out;
    }

    private static boolean pieceIs(StructurePiece piece, String template) {
        return piece instanceof PoolElementStructurePiece p && p.getElement().toString().contains("foglurker:")
                && p.getElement().toString().contains(template);
    }

    /** Structure starts touching the loaded chunks within {@code radius} (each only once). */
    private static List<StructureStart> startsNear(ServerLevel level, BlockPos pos, int radius,
                                                   java.util.function.Predicate<Structure> which) {
        List<StructureStart> out = new ArrayList<>();
        int r = (radius >> 4) + 1;
        ChunkPos c = new ChunkPos(pos);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (!level.hasChunk(c.x + dx, c.z + dz)) {
                    continue;
                }
                for (StructureStart s : level.structureManager().startsForStructure(new ChunkPos(c.x + dx, c.z + dz), which)) {
                    if (s.isValid() && !out.contains(s) && s.getBoundingBox().inflatedBy(radius).isInside(pos)) {
                        out.add(s);
                    }
                }
            }
        }
        return out;
    }

    /** "inside a lurker hotspot", "near a fog well"... for the debug command. */
    public static String describe(ServerLevel level, BlockPos pos) {
        List<String> where = new ArrayList<>();
        if (inside(level, pos, LURKER_HOTSPOTS)) {
            where.add("inside a lurker hotspot" + (highInLookout(level, pos) ? " (high up a lookout tower)" : ""));
        }
        if (inside(level, pos, WARDED_STRONGHOLDS)) {
            where.add("inside a warded stronghold");
        }
        if (!fogWellsNear(level, pos, 16).isEmpty()) {
            where.add("within 16 blocks of a fog well");
        }
        return where.isEmpty() ? "not in or near any Fog Lurker structure" : String.join(", ", where);
    }

    /** The bounding box of a start, for messages. */
    public static String box(BoundingBox b) {
        return b.minX() + " " + b.minY() + " " + b.minZ() + " .. " + b.maxX() + " " + b.maxY() + " " + b.maxZ();
    }
}
