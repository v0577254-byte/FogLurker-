package com.foglurker.forms;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/**
 * The Fog Lurker as a still figure for videos, in any of its shapes ({@link Form}): it never moves, never makes a
 * sound and its body never turns. Only its head follows you, looking you in the eyes. {@code /kill} removes it.
 */
public class FogLurker extends Mob {
    private static final EntityDataAccessor<Integer> FORM = SynchedEntityData.defineId(FogLurker.class, EntityDataSerializers.INT);
    /** Whose skin a player copy wears. */
    private static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> MIMIC =
            SynchedEntityData.defineId(FogLurker.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);
    private static final EntityDataAccessor<String> MIMIC_NAME = SynchedEntityData.defineId(FogLurker.class, EntityDataSerializers.STRING);
    /** How far away it still notices you. */
    private static final double STARE_RANGE = 64.0;

    public FogLurker(EntityType<? extends FogLurker> type, Level level) {
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
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FORM, Form.CRAWLER.ordinal());
        builder.define(MIMIC, Optional.empty());
        builder.define(MIMIC_NAME, "");
    }

    // ------------------------------------------------------------------ its shape

    public Form getForm() {
        return Form.byOrdinal(this.entityData.get(FORM));
    }

    public void setForm(Form form) {
        this.entityData.set(FORM, form.ordinal());
    }

    /** Copy this player's look (their skin, cape and arm shape). */
    public void setMimic(@Nullable UUID id, String name) {
        this.entityData.set(MIMIC, Optional.ofNullable(id).map(EntityReference::of));
        this.entityData.set(MIMIC_NAME, name == null ? "" : name);
    }

    public @Nullable UUID getMimicUuid() {
        return this.entityData.get(MIMIC).map(EntityReference::getUUID).orElse(null);
    }

    public String getMimicName() {
        return this.entityData.get(MIMIC_NAME);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (FORM.equals(key)) {
            refreshDimensions(); // a chicken is smaller than a cow
        }
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return getForm().dimensions();
    }

    // ------------------------------------------------------------------ it only stares

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
            player = level().getNearestPlayer(FogLurker.this, STARE_RANGE);
            return player != null && !player.isSpectator();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Player nearest = level().getNearestPlayer(FogLurker.this, STARE_RANGE);
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

    // ---- saved with the world

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putString("Form", getForm().id());
        UUID mimic = getMimicUuid();
        if (mimic != null) {
            out.store("Mimic", UUIDUtil.CODEC, mimic);
        }
        out.putString("MimicName", getMimicName());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        Form form = Form.byId(in.getStringOr("Form", "crawler"));
        setForm(form != null ? form : Form.CRAWLER);
        setMimic(in.read("Mimic", UUIDUtil.CODEC).orElse(null), in.getStringOr("MimicName", ""));
    }
}
