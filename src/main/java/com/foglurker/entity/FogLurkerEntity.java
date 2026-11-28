package com.foglurker.entity;

import com.foglurker.brain.Encounter;
import com.foglurker.brain.LurkerBrain;
import com.foglurker.brain.LurkerDirector;
import com.foglurker.config.LurkerDifficulty;
import com.foglurker.registry.ModDamageTypes;
import com.foglurker.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The Fog Lurker. All of its "thinking" lives in {@link LurkerBrain}; this class holds the synced
 * look (which disguise, whose skin, collar colour...) and the vanilla plumbing.
 */
public class FogLurkerEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> DATA_DISGUISE =
            SynchedEntityData.defineId(FogLurkerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> DATA_MIMIC_UUID =
            SynchedEntityData.defineId(FogLurkerEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> DATA_MIMIC_NAME =
            SynchedEntityData.defineId(FogLurkerEntity.class, EntityDataSerializers.STRING);
    /** Skin layer bits (hat, jacket, sleeves...) in the low byte, main arm in bit 8. */
    private static final EntityDataAccessor<Integer> DATA_SKIN =
            SynchedEntityData.defineId(FogLurkerEntity.class, EntityDataSerializers.INT);
    /** Sheep wool colour or dog collar colour. */
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(FogLurkerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_FLAGS =
            SynchedEntityData.defineId(FogLurkerEntity.class, EntityDataSerializers.BYTE);

    public static final int FLAG_TRANSFORMING = 1;
    public static final int FLAG_SITTING = 2;
    public static final int FLAG_ANGRY = 4;
    public static final int FLAG_DANCING = 8;
    public static final int FLAG_FLOPPED = 16;
    /** Lying down in cover with its legs tucked under it (a watcher hiding in grass and bushes). */
    public static final int FLAG_LYING = 32;
    /** A sheep copy with its head down, eating grass (shown on the client like a real sheep). */
    public static final int FLAG_GRAZING = 64;
    private static final int FLOP_TICKS = 24;

    /** Replaced by the client setup so the entity can drive its client-side disguise model. */
    public static Consumer<FogLurkerEntity> clientTicker = e -> {
    };

    @Nullable
    private LurkerBrain brain;
    // sinking into the ground when spotted
    private int flopLeft;
    private int clientFlopStart = -1;

    // /foglurker-macarena
    private int danceTick;
    private int danceTicksLeft;
    private int clientDanceStart;
    private boolean clientWasDancing;

    /** Tick of the last hit; hits are spaced by the mode's cooldown so it can never spam attacks. */
    private int lastAttackTick = -100_000;

    public FogLurkerEntity(EntityType<? extends FogLurkerEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setCanPickUpLoot(false);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setDropChance(slot, 0.0F);
        }
        if (this.getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
        }
        applyMovementRules(Disguise.CRAWLER);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0D) // Medium; each encounter sets its own mode's health
                // 0.32 makes a "walk" order move exactly as fast as a walking player.
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 96.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7D)
                .add(Attributes.ARMOR, 4.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_DISGUISE, Disguise.CRAWLER.ordinal());
        this.entityData.define(DATA_MIMIC_UUID, Optional.empty());
        this.entityData.define(DATA_MIMIC_NAME, "");
        this.entityData.define(DATA_SKIN, 0x7F | (1 << 8));
        this.entityData.define(DATA_VARIANT, 0);
        this.entityData.define(DATA_FLAGS, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        // Every form can stay afloat except the attacking form: water is its weakness.
        this.goalSelector.addGoal(0, new FloatGoal(this) {
            @Override
            public boolean canUse() {
                return !isAttackForm() && super.canUse();
            }
        });
        this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));
    }

    // ------------------------------------------------------------------ encounter

    /** Called by the director right before the entity is added to the world. */
    public void begin(Encounter encounter) {
        this.brain = new LurkerBrain(this, encounter);
        // 50 / 75 / 100 / 125 / 175 full hearts, by mode.
        var health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(encounter.difficulty().lurkerHearts() * 2.0D);
        }
        this.setHealth(this.getMaxHealth());
        this.brain.start();
    }

    @Nullable
    public LurkerBrain brain() {
        return brain;
    }

    public LurkerDifficulty difficulty() {
        return brain != null ? brain.difficulty() : LurkerDifficulty.MEDIUM;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (isDancing()) {
            tickDance();
            return;
        }
        if (isFlopped()) {
            this.getNavigation().stop();
            return;
        }
        if (this.isNoAi()) {
            if (brain != null && !brain.isEnded()) {
                brain.dismiss(); // a hunting creature frozen with /data: it leaves instead of holding a hunt forever
            }
            return; // {NoAI:1b}: a harmless statue, e.g. for map makers
        }
        if (brain == null) {
            // Summoned by hand (/summon): plan an encounter against the nearest player.
            if (this.level() instanceof ServerLevel && this.tickCount > 1 && !LurkerDirector.adopt(this)) {
                this.discard();
            }
            return;
        }
        brain.tick();
        com.foglurker.brain.VillagersHide.tick(this); // villagers sense it and run inside
    }

    /** Like a spider: in its attacking form it climbs any wall it runs into, including the tower you built. */
    private boolean climbing;
    /** Climbing up a column of poured water (a bucket spill is not a body of water: it goes straight up it). */
    private boolean climbingSpill;
    /** Walking through a poured bucket: not slowed or pushed by it. */
    private boolean inSpillTravel;
    private int lastSpillJumpTick = -100;

    /**
     * The attacking form plans its path knowing water: a real body of water stays off-limits, but a single poured
     * bucket is just ground to walk through (it is not fooled by a puddle).
     */
    @Override
    protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
        return new GroundPathNavigation(this, level) {
            @Override
            protected net.minecraft.world.level.pathfinder.PathFinder createPathFinder(int maxVisitedNodes) {
                this.nodeEvaluator = new net.minecraft.world.level.pathfinder.WalkNodeEvaluator() {
                    @Override
                    public BlockPathTypes getBlockPathType(net.minecraft.world.level.BlockGetter getter, int x, int y, int z) {
                        BlockPathTypes type = super.getBlockPathType(getter, x, y, z);
                        if (!FogLurkerEntity.this.isAttackForm()) {
                            return type;
                        }
                        if (type == BlockPathTypes.WATER) {
                            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
                            if (com.foglurker.brain.SpotFinder.isWaterBodyBlock(getter, pos)) {
                                return type;
                            }
                            // A spill: what is under it decides.
                            if (!getter.getBlockState(pos).getCollisionShape(getter, pos).isEmpty()) {
                                return BlockPathTypes.BLOCKED;
                            }
                            net.minecraft.core.BlockPos below = pos.below();
                            net.minecraft.world.level.material.FluidState belowFluid = getter.getFluidState(below);
                            if (com.foglurker.brain.SpotFinder.isWaterBodyBlock(getter, below)) {
                                return BlockPathTypes.WATER;
                            }
                            if (!belowFluid.isEmpty() && !belowFluid.is(net.minecraft.tags.FluidTags.WATER)) {
                                return BlockPathTypes.LAVA;
                            }
                            BlockState belowState = getter.getBlockState(below);
                            if (belowState.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) || belowState.is(net.minecraft.tags.BlockTags.CAMPFIRES)) {
                                return BlockPathTypes.DAMAGE_FIRE;
                            }
                            if (belowState.is(net.minecraft.world.level.block.Blocks.CACTUS) || belowState.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)) {
                                return BlockPathTypes.DAMAGE_OTHER;
                            }
                            if (belowState.getCollisionShape(getter, below).isEmpty()) {
                                return BlockPathTypes.OPEN;
                            }
                            return com.foglurker.brain.SpotFinder.hasAdjacentWaterBody(getter, pos) ? BlockPathTypes.WATER_BORDER : BlockPathTypes.WALKABLE;
                        }
                        if (type == BlockPathTypes.WATER_BORDER
                                && !com.foglurker.brain.SpotFinder.hasAdjacentWaterBody(getter, new net.minecraft.core.BlockPos(x, y, z))) {
                            return BlockPathTypes.WALKABLE; // only a spill beside it
                        }
                        return type;
                    }
                };
                this.nodeEvaluator.setCanPassDoors(true);
                return new net.minecraft.world.level.pathfinder.PathFinder(this.nodeEvaluator, maxVisitedNodes);
            }
        };
    }

    @Override
    public boolean isAffectedByFluids() {
        return !this.inSpillTravel && super.isAffectedByFluids();
    }

    @Override
    public void jumpInFluid(net.minecraftforge.fluids.FluidType type) {
        if (type == net.minecraftforge.common.ForgeMod.WATER_TYPE.get() && isAttackForm() && this.onGround()
                && !com.foglurker.brain.SpotFinder.isInWaterBody(this)) {
            // In a spill it hops over the lip of the water like any step, but no faster than twice a second.
            if (this.tickCount - this.lastSpillJumpTick >= 10) {
                this.lastSpillJumpTick = this.tickCount;
                this.jumpFromGround();
            }
            return;
        }
        super.jumpInFluid(type);
    }

    @Override
    public boolean isPushedByFluid(net.minecraftforge.fluids.FluidType type) {
        return !(type == net.minecraftforge.common.ForgeMod.WATER_TYPE.get() && isAttackForm()
                && !com.foglurker.brain.SpotFinder.isInWaterBody(this)) && super.isPushedByFluid(type);
    }

    private void updateClimbingState() {
        boolean inWaterBody = isAttackForm() && com.foglurker.brain.SpotFinder.isInWaterBody(this);
        net.minecraft.core.BlockPos pos = this.blockPosition();
        boolean inVerticalSpill = this.level().getFluidState(pos.above()).is(net.minecraft.tags.FluidTags.WATER)
                || !this.onGround() && this.level().getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)
                && this.level().getFluidState(pos.below()).is(net.minecraft.tags.FluidTags.WATER);
        this.climbingSpill = isAttackForm() && !isFlopped() && !inWaterBody && this.isInWater()
                && this.brain != null && this.brain.wantsClimbSpill() && inVerticalSpill;
        this.climbing = isAttackForm() && !isFlopped() && !inWaterBody && (this.horizontalCollision || this.climbingSpill);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            boolean dancing = isDancing();
            if (dancing && !clientWasDancing) {
                clientDanceStart = this.tickCount;
            }
            clientWasDancing = dancing;
            if (isFlopped() && clientFlopStart < 0) {
                clientFlopStart = this.tickCount;
            }
            if (dancing) {
                this.yBodyRot = this.getYHeadRot(); // turn the whole body on the hop
            }
            clientTicker.accept(this);
        } else {
            updateClimbingState();
            if ((this.tickCount + this.getId()) % 10 == 0) {
                noteWhoSeesItsRealShape();
            }
            if (flopLeft > 0 && --flopLeft == 0) {
                if (this.level() instanceof ServerLevel level) {
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, getX(), getY() + 0.1, getZ(),
                            14, 0.5, 0.05, 0.5, 0.01);
                }
                this.discard();
            }
        }
    }

    @Override
    public boolean onClimbable() {
        return this.climbing || super.onClimbable();
    }

    /** How much faster than a spider it goes up a wall (vanilla climbing is about 0.12 blocks a tick). */
    public static final double CLIMB_BOOST = 1.65;

    @Override
    public void travel(net.minecraft.world.phys.Vec3 input) {
        if (!this.level().isClientSide) {
            updateClimbingState();
        }
        // Through a poured bucket it walks as on dry ground (not slowed, not floated).
        this.inSpillTravel = isAttackForm() && !this.isInLava() && this.isInWater() && !com.foglurker.brain.SpotFinder.isInWaterBody(this);
        try {
            super.travel(input);
        } finally {
            this.inSpillTravel = false;
        }
        // Minecraft resets a climber's speed to its own fixed value every tick: scale that up while it is on a wall.
        if (this.climbing && this.horizontalCollision) {
            net.minecraft.world.phys.Vec3 v = getDeltaMovement();
            if (v.y > 0) {
                setDeltaMovement(v.x, v.y * CLIMB_BOOST, v.z);
            }
        } else if (this.climbingSpill && !this.horizontalCollision) {
            net.minecraft.world.phys.Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x, Math.max(v.y, 0.22), v.z); // up the column of water
        }
    }

    public boolean isClimbing() {
        return this.climbing;
    }

    // ------------------------------------------------------------------ lying in cover / flopping flat

    public boolean isLying() {
        return hasFlag(FLAG_LYING);
    }

    public void setLying(boolean lying) {
        if (lying != isLying()) {
            setFlag(FLAG_LYING, lying);
            refreshDimensions();
        }
    }

    public boolean isFlopped() {
        return hasFlag(FLAG_FLOPPED);
    }

    /** Spotted: it flops flat on the ground like a pancake, lies there a moment, and is gone. */
    public void beginFlop() {
        setFlag(FLAG_FLOPPED, true);
        setInvulnerable(true);
        this.getNavigation().stop();
        this.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        flopLeft = FLOP_TICKS;
    }

    /** Client: 0 = standing, 1 = fully under. */
    public float flopProgress(float partialTick) {
        if (!isFlopped() || clientFlopStart < 0) {
            return 0F;
        }
        // Fast: it is flat within a fifth of a second, then just lies there.
        return net.minecraft.util.Mth.clamp((this.tickCount + partialTick - clientFlopStart) / 4F, 0F, 1F);
    }

    // ------------------------------------------------------------------ the macarena

    public boolean isDancing() {
        return hasFlag(FLAG_DANCING);
    }

    /** Client: ticks (with partial) since the dance started, for the animation. */
    public float danceTime(float partialTick) {
        return this.tickCount + partialTick - clientDanceStart;
    }

    /** Harmless, invulnerable, dances {@code loops} times through, then vanishes. Never hunts. */
    public void startMacarena(int loops) {
        setDisguise(Disguise.CRAWLER); // the pale one stands up on its hind legs to dance
        setDisguiseName(null);
        clearEquipment();
        setFlag(FLAG_DANCING, true);
        setInvulnerable(true);
        danceTick = 0;
        danceTicksLeft = Math.max(1, loops) * MacarenaDance.CYCLE_TICKS;
    }

    private void tickDance() {
        this.getNavigation().stop();
        MacarenaDance.beat(this, danceTick);
        // Count 16: hop and turn a quarter to the left.
        if (danceTick % MacarenaDance.TICKS_PER_COUNT == 0 && MacarenaDance.count(danceTick) == 15) {
            float yaw = this.getYRot() - 90.0F;
            this.setYRot(yaw);
            this.setYHeadRot(yaw);
            this.setYBodyRot(yaw);
            if (this.onGround()) {
                this.getJumpControl().jump();
            }
        } else {
            this.setYHeadRot(this.getYRot());
            this.setYBodyRot(this.getYRot());
        }
        danceTick++;
        if (--danceTicksLeft <= 0 && this.level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(),
                    30, 0.3, 0.6, 0.3, 0.02);
            this.playSound(ModSounds.VANISH.get(), 1.0F, 1.2F);
            this.discard();
        }
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (!this.level().isClientSide) {
            LurkerDirector.onRemoved(this);
        }
    }

    // ------------------------------------------------------------------ synced look

    public Disguise getDisguise() {
        return Disguise.byId(this.entityData.get(DATA_DISGUISE));
    }

    public void setDisguise(Disguise disguise) {
        this.entityData.set(DATA_DISGUISE, disguise.ordinal());
        // A sitting dog or a grazing sheep that changes shape does not carry the pose into the new one.
        if (disguise != Disguise.DOG) {
            setFlag(FLAG_SITTING, false);
        }
        if (disguise != Disguise.SHEEP) {
            setFlag(FLAG_GRAZING, false);
        }
        if (disguise != Disguise.PLAYER) {
            setCrouching(false);
            this.setSwimming(false);
        }
        applyMovementRules(disguise);
        if (disguise.isAttackForm()) {
            // Its real shape has no name: otherwise a death message would say a friend (or a dog) killed you.
            this.setCustomName(null);
        }
        this.refreshDimensions();
    }

    /**
     * Anyone looking straight at it while it is itself (its pale shape, not a disguise) has now seen it: from then on
     * a faint picture of it shows whenever they sleep.
     */
    private void noteWhoSeesItsRealShape() {
        if (getDisguise() != Disguise.CRAWLER || this.isInvisible() || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        for (net.minecraft.server.level.ServerPlayer p : level.players()) {
            if (p.isSpectator() || p instanceof net.minecraftforge.common.util.FakePlayer || p.distanceToSqr(this) > 64 * 64) {
                continue;
            }
            Vec3 to = new Vec3(getX(), getY(0.5), getZ()).subtract(p.getEyePosition());
            double len = to.length();
            if (len > 1e-3 && p.getViewVector(1F).dot(to.scale(1 / len)) > 0.85 && p.hasLineOfSight(this)) {
                com.foglurker.world.SleepParalysis.sawRealShape(p);
            }
        }
    }

    public boolean isAttackForm() {
        return getDisguise().isAttackForm();
    }

    /**
     * Human forms happily swim across water like a player would; animals avoid it like animals do;
     * the attacking form will not set foot in it.
     */
    private void applyMovementRules(Disguise d) {
        boolean attackForm = d.isAttackForm();
        if (this.getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanFloat(!attackForm);
        }
        this.setPathfindingMalus(BlockPathTypes.WATER, attackForm ? -1.0F : d.isPassive() ? 8.0F : 0.0F);
        this.setPathfindingMalus(BlockPathTypes.WATER_BORDER, attackForm ? 16.0F : d.isPassive() ? 8.0F : 0.0F);
    }

    // ------------------------------------------------------------------ swimming (human forms)

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide) {
            driveSwimming();
        }
    }

    /** In a player's shape it sprint-swims exactly like a player: flat in the water and fast. */
    private void driveSwimming() {
        if (this.isNoAi()) {
            return;
        }
        boolean canSprintSwim = getDisguise() == Disguise.PLAYER;
        boolean wantSwim = canSprintSwim && this.isInWater() && this.getNavigation().isInProgress();
        if (wantSwim) {
            if (!this.isSwimming()) {
                this.setSwimming(true);
                this.setSprinting(true);
            }
            if (this.getPose() != Pose.SWIMMING) {
                this.setPose(Pose.SWIMMING); // every tick: copying your crouch must not stand it up mid-swim
            }
            Path path = this.getNavigation().getPath();
            if (path != null && !path.isDone()) {
                Vec3 next = path.getNextEntityPos(this);
                Vec3 dir = new Vec3(next.x - this.getX(), 0, next.z - this.getZ());
                if (dir.lengthSqr() > 1.0E-4) {
                    dir = dir.normalize();
                    // Sprint-swim push; with water drag this tops out near a player's ~5.6 blocks/s.
                    this.setDeltaMovement(this.getDeltaMovement().add(dir.x * 0.03, 0, dir.z * 0.03));
                }
            }
        } else if (this.isSwimming()) {
            this.setSwimming(false);
            this.setSprinting(false);
        }
        if (!wantSwim && this.getPose() == Pose.SWIMMING
                && this.level().noCollision(this, getDimensions(Pose.STANDING).makeBoundingBox(this.position()))) {
            this.setPose(Pose.STANDING); // tried every tick, so leaving water under a low ceiling cannot leave it flat
        }
    }

    /** How far through the swim animation the client should draw it (0..1). */
    public float swimAmountFor(float partialTick) {
        return this.getSwimAmount(partialTick);
    }

    @Nullable
    public UUID getMimicUuid() {
        return this.entityData.get(DATA_MIMIC_UUID).orElse(null);
    }

    public String getMimicName() {
        return this.entityData.get(DATA_MIMIC_NAME);
    }

    public void setMimic(@Nullable UUID id, String name) {
        this.entityData.set(DATA_MIMIC_UUID, Optional.ofNullable(id));
        this.entityData.set(DATA_MIMIC_NAME, name == null ? "" : name);
    }

    public int getSkinParts() {
        return this.entityData.get(DATA_SKIN) & 0xFF;
    }

    public HumanoidArm getMimicMainArm() {
        return (this.entityData.get(DATA_SKIN) >> 8 & 1) == 1 ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
    }

    public int getVariant() {
        return this.entityData.get(DATA_VARIANT);
    }

    public void setVariant(int variant) {
        this.entityData.set(DATA_VARIANT, variant);
    }

    public boolean hasFlag(int flag) {
        return (this.entityData.get(DATA_FLAGS) & flag) != 0;
    }

    public void setFlag(int flag, boolean on) {
        byte b = this.entityData.get(DATA_FLAGS);
        this.entityData.set(DATA_FLAGS, (byte) (on ? b | flag : b & ~flag));
    }

    public boolean isTransforming() {
        return hasFlag(FLAG_TRANSFORMING);
    }

    public void setCrouching(boolean crouch) {
        this.setShiftKeyDown(crouch);
        this.setPose(crouch ? Pose.CROUCHING : Pose.STANDING);
    }

    /** Copies a real player's skin layers, main arm, armour and held items. */
    public void copyLookOf(ServerPlayer player) {
        int parts = 0;
        for (PlayerModelPart part : PlayerModelPart.values()) {
            if (player.isModelPartShown(part)) {
                parts |= part.getMask();
            }
        }
        int arm = player.getMainArm() == HumanoidArm.RIGHT ? 1 : 0;
        this.entityData.set(DATA_SKIN, parts | (arm << 8));
        this.setLeftHanded(arm == 0);
        copyEquipmentOf(player);
    }

    public void copyEquipmentOf(LivingEntity source) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack theirs = source.getItemBySlot(slot);
            if (!ItemStack.matches(theirs, this.getItemBySlot(slot))) {
                this.setItemSlot(slot, theirs.copy());
            }
        }
    }

    public void clearEquipment() {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_DISGUISE.equals(key) || DATA_FLAGS.equals(key)) {
            this.refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        Disguise d = getDisguise();
        if (d == Disguise.PLAYER && pose == Pose.CROUCHING) {
            return EntityDimensions.scalable(0.6F, 1.5F);
        }
        if (isFlopped()) {
            return EntityDimensions.scalable(d.dimensions().width, 0.2F); // a pancake has no tall invisible box
        }
        if (d == Disguise.CRAWLER && isLying()) {
            return EntityDimensions.scalable(0.9F, 0.6F); // flat in the grass: no hitbox poking out
        }
        if (d == Disguise.PLAYER && pose == Pose.SWIMMING) {
            return EntityDimensions.scalable(0.6F, 0.6F);
        }
        return d.dimensions();
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dims) {
        if (getDisguise() == Disguise.PLAYER) {
            return pose == Pose.CROUCHING ? 1.27F : pose == Pose.SWIMMING ? 0.4F : 1.62F;
        }
        if (getDisguise() == Disguise.CRAWLER) {
            return isLying() ? 0.5F : 0.95F; // the head hangs low and forward
        }
        return dims.height * 0.85F;
    }

    // ------------------------------------------------------------------ combat

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) {
            return super.hurt(source, amount);
        }
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN)
                || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.SWEET_BERRY_BUSH)
                || source.is(DamageTypes.CACTUS) || source.is(DamageTypes.FREEZE)) {
            // (Cactus and powder snow too: a cactus moat must not be a free way to rest it for the whole server.)
            return false;
        }
        if (this.isInvulnerableTo(source)) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // /kill and the void: it simply melts back into the fog (no drops, no rest).
            if (brain != null) {
                brain.dismiss(true); // not an escape: no "you got away"
            } else {
                this.discard();
            }
            return true;
        }
        if (isFlopped() || isDancing()) {
            return false; // sinking away or dancing: untouchable, even for creative players
        }
        if (brain != null && source.getEntity() instanceof Player p) {
            brain.onHurtBy(p);
        }
        // The Protector of the Fog: 10 (15 on a critical hit) straight through its armour.
        if (source.is(DamageTypes.PLAYER_ATTACK) && source.getEntity() instanceof Player p && source.getDirectEntity() == p
                && p.getMainHandItem().getItem() instanceof com.foglurker.item.ProtectorOfTheFogItem
                && amount > 1.0F) { // (a sweep from hitting something next to it does exactly 1: that is not a Protector hit)
            return super.hurt(com.foglurker.registry.ModDamageTypes.source(p, com.foglurker.registry.ModDamageTypes.PROTECTOR),
                    amount * com.foglurker.item.ProtectorOfTheFogItem.LURKER_MULTIPLIER);
        }
        return super.hurt(source, amount);
    }

    /** True when enough time has passed since the last hit (3 s on very easy ... 1.5 s on deathwish; 50% longer while weakened). */
    public boolean canAttackNow() {
        int cooldown = difficulty().attackCooldownTicks();
        if (this.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS)) {
            cooldown = cooldown * 3 / 2;
        }
        return this.tickCount - lastAttackTick >= cooldown;
    }

    // ------------------------------------------------------------------ death

    /** The killing blow came from a player (only then does it drop anything). */
    private boolean killedByPlayer;

    @Override
    public void die(DamageSource source) {
        boolean serverSide = !this.level().isClientSide && !this.isRemoved() && !this.dead;
        if (serverSide) {
            killedByPlayer = source.getEntity() instanceof Player; // read by the loot drop inside super.die
        }
        super.die(source);
        // Everything else only once it really died: another mod can cancel the death, and then it must stay
        // exactly as it was (disguise, copied gear, no "killed" broadcast, no rest).
        if (serverSide && this.dead) {
            this.setDisguise(Disguise.CRAWLER); // whatever it was wearing, it dies as itself
            this.setFlag(FLAG_TRANSFORMING | FLAG_ANGRY | FLAG_SITTING | FLAG_LYING, false); // no shaking or hidden corpse
            this.clearEquipment();
            if (brain != null) {
                brain.onDeath();
                com.foglurker.brain.LurkerDirector.onKilled(this,
                        source.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null);
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHitByPlayer) {
        super.dropCustomDeathLoot(source, looting, recentlyHitByPlayer);
        if (!killedByPlayer || brain == null) {
            return; // only when a player landed the killing blow (not lava after a player's hit)
        }
        boolean night = !this.level().isDay(); // (the Nether and End have no daylight: there it counts as night)
        for (net.minecraft.world.item.ItemStack stack : LurkerLoot.roll(difficulty(), night, this.getRandom())) {
            this.spawnAtLocation(stack);
        }
    }

    @Override
    public int getExperienceReward() {
        return brain == null ? 0 : difficulty().lurkerHearts() / 2; // 25 / 37 / 50 / 62 / 87
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!canAttackNow()) {
            return false;
        }
        lastAttackTick = this.tickCount;
        LurkerDifficulty diff = difficulty();
        boolean oneShot = diff.isOneShot();
        DamageSource src = ModDamageTypes.source(this, oneShot ? ModDamageTypes.LURKER_ONESHOT : ModDamageTypes.LURKER);
        float damage;
        if (target instanceof LivingEntity living && oneShot) {
            // A whole health bar: it kills you outright unless your armour (or absorption hearts) saves you.
            damage = living.getMaxHealth();
        } else {
            damage = diff.damagePerHit();
            if (!(target instanceof Player)) {
                damage = Math.max(damage, 8.0F);
            }
        }
        if (this.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS)) {
            damage *= 0.6F; // weakened by a scare burst: 40% less, the Deathwish bite too
        }
        if (target instanceof LivingEntity living) {
            damage *= 1F - LurkerArmor.reduction(living); // only good armour stops it (see LurkerArmor)
        }
        this.swing(InteractionHand.MAIN_HAND);
        boolean hit = target.hurt(src, damage);
        if (hit) {
            this.playSound(ModSounds.ATTACK.get(), 1.0F, 0.8F + this.random.nextFloat() * 0.3F);
            if (target instanceof LivingEntity living) {
                living.knockback(0.6D, this.getX() - target.getX(), this.getZ() - target.getZ());
                if (isAttackForm()) {
                    chomp(living, diff);
                }
            }
            this.setLastHurtMob(target);
        }
        return hit;
    }

    /**
     * The bite goes into your leg: Slowness I for 10 seconds on every mode. On Hard and Deathwish it
     * also mauls the leg for those 10 seconds: no jumping, no getting into a boat, and it drags you
     * out of a boat you are sitting in on land.
     */
    private void chomp(LivingEntity victim, LurkerDifficulty diff) {
        if (!victim.isAlive() || com.foglurker.armor.FogArmor.hasFullSet(victim)) {
            return; // (a full set of Armour of the Fog shrugs off the bite's slowness and mauling)
        }
        victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
                200, 0, false, true, true), this);
        this.playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.0F, 0.6F);
        boolean mauls = diff == LurkerDifficulty.HARD || diff == LurkerDifficulty.DEATHWISH;
        if (mauls) {
            victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.foglurker.registry.ModEffects.MAULED.get(),
                    200, 0, false, true, true), this);
            if (victim.getVehicle() instanceof net.minecraft.world.entity.vehicle.Boat boat && !com.foglurker.brain.SpotFinder.isHullOnWaterBody(boat)) {
                victim.stopRiding(); // on land only: a boat that is afloat is your way out
            }
        }
        if (victim instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.displayClientMessage(net.minecraft.network.chat.Component.literal(mauls
                    ? "It bit into your leg. You can't jump."
                    : "It bit into your leg.").withStyle(net.minecraft.ChatFormatting.DARK_RED), true);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (brain != null && player instanceof ServerPlayer sp) {
            return brain.onInteract(sp) ? InteractionResult.sidedSuccess(this.level().isClientSide) : InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }

    // ------------------------------------------------------------------ sounds that match the disguise

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return switch (getDisguise()) {
            case COW -> SoundEvents.COW_AMBIENT;
            case SHEEP -> SoundEvents.SHEEP_AMBIENT;
            case PIG -> SoundEvents.PIG_AMBIENT;
            case CHICKEN -> SoundEvents.CHICKEN_AMBIENT;
            case ZOMBIE -> SoundEvents.ZOMBIE_AMBIENT;
            case WOLF -> SoundEvents.WOLF_AMBIENT;
            case DOG -> hasFlag(FLAG_ANGRY) ? SoundEvents.WOLF_GROWL
                    : this.random.nextInt(3) == 0 ? SoundEvents.WOLF_WHINE : SoundEvents.WOLF_PANT;
            case CRAWLER -> ModSounds.WHISPER.get();
            case PLAYER -> null; // players do not make idle noises
            default -> getDisguise().ambientSound();
        };
    }

    @Override
    public int getAmbientSoundInterval() {
        return switch (getDisguise()) {
            case PLAYER -> Integer.MAX_VALUE;
            case CRAWLER -> 160;
            case CHICKEN -> 100;
            default -> 120;
        };
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return switch (getDisguise()) {
            case COW -> SoundEvents.COW_HURT;
            case SHEEP -> SoundEvents.SHEEP_HURT;
            case PIG -> SoundEvents.PIG_HURT;
            case CHICKEN -> SoundEvents.CHICKEN_HURT;
            case ZOMBIE -> SoundEvents.ZOMBIE_HURT;
            case WOLF, DOG -> SoundEvents.WOLF_HURT;
            case PLAYER -> SoundEvents.PLAYER_HURT;
            case CRAWLER -> ModSounds.HURT.get();
            default -> getDisguise().hurtSound() != null ? getDisguise().hurtSound() : SoundEvents.GENERIC_HURT;
        };
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SCREAM.get();
    }

    @Override
    public float getVoicePitch() {
        float base = super.getVoicePitch();
        return switch (getDisguise()) {
            case CRAWLER -> base * 0.6F;
            case PLAYER -> base;
            // Animals sound almost right. Almost.
            default -> base * 0.9F;
        };
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        SoundEvent step = switch (getDisguise()) {
            case COW -> SoundEvents.COW_STEP;
            case SHEEP -> SoundEvents.SHEEP_STEP;
            case PIG -> SoundEvents.PIG_STEP;
            case CHICKEN -> SoundEvents.CHICKEN_STEP;
            case ZOMBIE -> SoundEvents.ZOMBIE_STEP;
            case WOLF, DOG -> SoundEvents.WOLF_STEP;
            default -> getDisguise().stepSound();
        };
        if (step != null) {
            this.playSound(step, 0.15F, 1.0F);
        } else {
            super.playStepSound(pos, state);
        }
    }

    // ------------------------------------------------------------------ lifecycle plumbing

    /** It never takes fall damage, so it happily drops down ledges a mob would normally walk around. */
    @Override
    public int getMaxFallDistance() {
        return 8;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void checkDespawn() {
        // The brain decides when it leaves.
    }

    /** Never written to disk: if the world closes mid-encounter, it is simply gone. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    /** Flopped or dancing it cannot be hurt, and a blast must not send the pancake (or the dancer) flying either. */
    @Override
    public boolean ignoreExplosion() {
        return isFlopped() || isDancing() || super.ignoreExplosion();
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Disguise", getDisguise().ordinal());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Disguise")) { // /summon passes no shape: keep the pale crawler, not ordinal 0 (a player)
            setDisguise(Disguise.byId(tag.getInt("Disguise")));
        }
    }

    /** Name shown in death messages: whoever it was pretending to be. */
    public void setDisguiseName(@Nullable String name) {
        this.setCustomName(name == null || name.isEmpty() ? null : Component.literal(name));
        this.setCustomNameVisible(false);
    }
}
