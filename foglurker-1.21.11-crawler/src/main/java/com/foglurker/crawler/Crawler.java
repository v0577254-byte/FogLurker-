package com.foglurker.crawler;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The pale crawler as a still figure: it never moves, never makes a sound and its body never turns. Only its head
 * follows you, looking you in the eyes. {@code /kill} removes it.
 */
public class Crawler extends Mob {
    /** How far away it still notices you. */
    private static final double STARE_RANGE = 64.0;

    public Crawler(EntityType<? extends Crawler> type, Level level) {
        super(type, level);
        setSilent(true);
        setInvulnerable(true); // a stray hit while filming must not hurt or push it (/kill still works)
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new StareGoal());
    }

    /** Head only: it keeps its eyes on the nearest player's eyes. */
    private final class StareGoal extends Goal {
        private Player player;

        StareGoal() {
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            player = level().getNearestPlayer(Crawler.this, STARE_RANGE);
            return player != null && !player.isSpectator();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Player nearest = level().getNearestPlayer(Crawler.this, STARE_RANGE);
            if (nearest != null) {
                player = nearest;
            }
            // Aims at the player's eye height (setLookAt(Entity) looks at the eyes).
            getLookControl().setLookAt(player, 20.0F, 40.0F);
        }
    }

    /** The body never turns to follow the head. */
    @Override
    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this) {
            @Override
            public void clientTick() {
            }
        };
    }

    /** The head may turn all the way round to keep watching you. */
    @Override
    public int getMaxHeadYRot() {
        return 180;
    }

    // ---- it never moves: no pushing, no knockback, no walking

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public void travel(Vec3 input) {
        super.travel(Vec3.ZERO);
        Vec3 v = getDeltaMovement();
        setDeltaMovement(0, v.y, 0); // it may only settle onto the ground
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    /** /kill removes it straight away (it takes no damage otherwise). */
    @Override
    public void kill(ServerLevel level) {
        discard();
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false;
    }
}
