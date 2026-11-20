package com.foglurker.brain;

import com.foglurker.config.FogLurkerConfig;
import com.foglurker.entity.FogLurkerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

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
    }

    /** Its attacking form, or about to be. */
    public static boolean attacking(FogLurkerEntity mob) {
        LurkerBrain b = mob.brain();
        return mob.isAttackForm() || (b != null && (b.phase() == LurkerBrain.Phase.ATTACK || b.phase() == LurkerBrain.Phase.TRANSFORM));
    }
}
