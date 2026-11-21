package com.foglurker.brain;

import com.foglurker.config.FogLurkerConfig;
import com.foglurker.entity.FogLurkerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Villagers sense it. While it is near (any form, even disguised) they run into their houses and hide, the way they do
 * when a bell rings: that is the bell's own signal, refreshed every second while it stays near. When it leaves, they
 * come back out a little later. So villagers suddenly running inside is a warning.
 */
public final class VillagersHide {
    /** Villagers within this many blocks of it hide. */
    public static final double RANGE = 32;
    /** ...and within this many while it attacks (they hear it). */
    public static final double ATTACK_RANGE = 48;

    private VillagersHide() {
    }

    /** Called every tick of an appearance; acts once a second. */
    public static void tick(FogLurkerEntity mob) {
        if (mob.tickCount % 20 != 0 || mob.isRemoved() || !FogLurkerConfig.COMMON.villagersHide.get()
                || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        double range = attacking(mob) ? ATTACK_RANGE : RANGE;
        long now = level.getGameTime();
        for (Villager v : level.getEntitiesOfClass(Villager.class, mob.getBoundingBox().inflate(range),
                v -> v.isAlive() && !v.isSleeping() && v.distanceToSqr(mob) <= range * range)) {
            v.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, now);
        }
        // At night, while it is about: the villagers by a fog well, and those at a warded stronghold's gate, go inside.
        if (level.isDarkOutside() && mob.tickCount % 40 == 0) {
            java.util.List<net.minecraft.core.BlockPos> places = new java.util.ArrayList<>(
                    com.foglurker.world.FogStructures.fogWellsNear(level, mob.blockPosition(), STRUCTURE_RANGE));
            places.addAll(com.foglurker.world.FogStructures.strongholdGatesNear(level, mob.blockPosition(), STRUCTURE_RANGE));
            for (net.minecraft.core.BlockPos at : places) {
                hideAround(level, at, PLACE_RADIUS, now);
            }
        }
    }

    /** It is this close to a fog well or a stronghold's gate: the villagers there hide. */
    public static final int STRUCTURE_RANGE = 64;
    /** ...within this many blocks of the well or the gate. */
    public static final double PLACE_RADIUS = 16;

    /** Every villager within {@code radius} of {@code at} hides, as when a bell rings. @return how many */
    public static int hideAround(ServerLevel level, net.minecraft.core.BlockPos at, double radius, long now) {
        int n = 0;
        for (Villager v : level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(at).inflate(radius),
                v -> v.isAlive() && !v.isSleeping() && v.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) <= radius * radius)) {
            v.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, now);
            n++;
        }
        return n;
    }

    /** Its attacking form, or about to be. */
    public static boolean attacking(FogLurkerEntity mob) {
        LurkerBrain b = mob.brain();
        return mob.isAttackForm() || (b != null && (b.phase() == LurkerBrain.Phase.ATTACK || b.phase() == LurkerBrain.Phase.TRANSFORM));
    }
}
