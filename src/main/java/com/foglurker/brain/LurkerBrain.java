package com.foglurker.brain;

import com.foglurker.chat.MimicChat;
import com.foglurker.config.FogLurkerConfig;
import com.foglurker.config.LurkerDifficulty;
import com.foglurker.entity.Disguise;
import com.foglurker.entity.FogLurkerEntity;
import com.foglurker.learning.EntityBehaviorStats;
import com.foglurker.learning.PlayerBehaviorProfile;
import com.foglurker.learning.RecentChat;
import com.foglurker.network.FogPacket;
import com.foglurker.network.ModNetwork;
import com.foglurker.network.ScarePacket;
import com.foglurker.registry.ModSounds;
import com.foglurker.voice.AudioFx;
import com.foglurker.voice.VoiceBridge;
import com.foglurker.world.FogLurkerSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runs one appearance of the Fog Lurker, from the first sighting to the moment it melts back into
 * the fog. Server side only.
 */
public final class LurkerBrain {

    public enum Mode {
        /** Wears another player's face and lures the target into the woods. */
        MIMIC,
        /** Wears the target's own face (used when they are alone). Mirrors what they do. */
        DOPPELGANGER,
        /** Is the target's own dog. Leads them into the woods like a dog that "found something". */
        DOG,
        /** Hides in a herd of cows / sheep / pigs / chickens / wolves, creeping closer. */
        HERD,
        /** Shambles with the zombies at night. */
        ZOMBIE,
        /** True form, far away, just watching. Gone when you look too long or come too close. */
        WATCHER
    }

    public enum Phase {
        STALK, APPROACH, GREET, LURE, CLIMAX, LURK, TRANSFORM, ATTACK, FLEE
    }

    private final FogLurkerEntity mob;
    private final Encounter enc;
    private final RandomSource random;
    private Phase phase;
    private int age;
    private int phaseAge;
    private int lifetime;
    private boolean ended;

    private Vec3 woods;
    private String woodsDir = "north";
    private double woodsStartDistance = 40;
    @Nullable
    private PlayerBehaviorProfile copiedProfile;
    private EntityBehaviorStats.Stats animalStats;

    // chat
    private final List<String> said = new ArrayList<>();
    private boolean chatInFlight;
    @Nullable
    private String pendingChat;
    private int pendingChatAt;
    @Nullable
    private String heardPending;
    private int replies;
    private int nextChatAt;

    // voice / mannerisms
    private int nextVoiceAt;
    private int crouchSpam;
    private boolean sprintPref;
    private int lastRepath = -100;

    // lure / lurk
    private int stall;
    private int reapproaches;
    private int lookedAt;
    private int idleUntil;
    @Nullable
    private Vec3 wanderTarget;
    private boolean saidArrived;
    private double bestToWoods = Double.MAX_VALUE;
    private int lureStuck;

    // doppelganger mirroring
    private double lastTargetY;
    private boolean lastTargetOnGround;
    private boolean lastTargetSwinging;

    // attack
    private int hits;
    private int attackCooldown;

    public LurkerBrain(FogLurkerEntity mob, Encounter enc) {
        this.mob = mob;
        this.enc = enc;
        this.random = mob.getRandom();
    }

    public LurkerDifficulty difficulty() {
        return enc.difficulty();
    }

    public Encounter encounter() {
        return enc;
    }

    public Phase phase() {
        return phase;
    }

    public UUID targetId() {
        return enc.targetId();
    }

    private ServerLevel level() {
        return (ServerLevel) mob.level();
    }

    private MinecraftServer server() {
        return level().getServer();
    }

    @Nullable
    private ServerPlayer target() {
        return server().getPlayerList().getPlayer(enc.targetId());
    }

    @Nullable
    private ServerPlayer copiedPlayer() {
        return enc.copiedId() == null ? null : server().getPlayerList().getPlayer(enc.copiedId());
    }

    // =================================================================== setup

    public void start() {
        FogLurkerSavedData data = FogLurkerSavedData.get(server());
        mob.setDisguise(enc.disguise());
        mob.setVariant(enc.variant());
        switch (enc.mode()) {
            case MIMIC, DOPPELGANGER -> {
                mob.setMimic(enc.copiedId(), enc.copiedName());
                mob.setDisguiseName(enc.copiedName());
                ServerPlayer copied = copiedPlayer();
                if (copied != null) {
                    mob.copyLookOf(copied);
                }
                copiedProfile = enc.copiedId() == null ? null : data.profileOrNull(enc.copiedId());
            }
            case DOG -> mob.setDisguiseName(enc.dogName());
            default -> mob.setDisguiseName(null);
        }
        animalStats = data.entityStats().get(enc.disguise() == Disguise.NONE ? "wolf" : enc.disguise().learnKey());
        sprintPref = copiedProfile != null ? random.nextFloat() < copiedProfile.sprintRatio : random.nextBoolean();

        ServerPlayer t = target();
        Vec3 origin = t != null ? t.position() : mob.position();
        ServerPlayer copied = copiedPlayer();
        Vec3 avoid = copied != null && copied.level() == mob.level() && copied != t ? copied.position() : null;
        SpotFinder.Woods w = SpotFinder.findWoods(level(), origin, avoid, random);
        woods = w.pos();
        woodsDir = w.direction();
        woodsStartDistance = Math.max(8, origin.distanceTo(woods));

        int maxTicks = FogLurkerConfig.COMMON.encounterMaxMinutes.get() * 60 * 20;
        lifetime = enc.mode() == Mode.WATCHER ? Math.min(maxTicks, 20 * 120) : maxTicks;
        phase = switch (enc.mode()) {
            case MIMIC, DOG, ZOMBIE -> Phase.STALK;
            default -> Phase.LURK;
        };
        nextVoiceAt = 60;
        if (t != null) {
            lastTargetY = t.getY();
        }
    }

    private void setPhase(Phase p) {
        phase = p;
        phaseAge = 0;
        stall = 0;
        wanderTarget = null;
        if (p != Phase.GREET) {
            mob.setCrouching(false);
            crouchSpam = 0;
        }
    }

    // =================================================================== main loop

    public void tick() {
        if (ended) {
            return;
        }
        age++;
        phaseAge++;
        ServerPlayer t = target();
        if (t == null || !t.isAlive() || t.level() != mob.level() || t.isSpectator()) {
            vanish(false);
            return;
        }
        double dist = mob.distanceTo(t);
        if (dist > 128 || (age > lifetime && phase != Phase.TRANSFORM && phase != Phase.ATTACK)) {
            vanish(false);
            return;
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        deliverChat(t);
        if (heardPending != null && !chatInFlight && pendingChat == null) {
            String heard = heardPending;
            heardPending = null;
            say(t, MimicChat.Intent.REPLY, heard);
        }
        if (enc.mode() == Mode.MIMIC && phase != Phase.TRANSFORM && phase != Phase.ATTACK && realOneWouldSee()) {
            vanish(false); // never get caught standing next to the real one
            return;
        }
        if (enc.mode() == Mode.MIMIC && age % 100 == 0 && mob.getDisguise() == Disguise.PLAYER) {
            ServerPlayer copied = copiedPlayer();
            if (copied != null) {
                mob.copyEquipmentOf(copied); // holds whatever the real one is holding right now
            }
        }

        switch (phase) {
            case STALK -> stalk(t, dist);
            case APPROACH -> approach(t, dist);
            case GREET -> greet(t, dist);
            case LURE -> lure(t, dist);
            case CLIMAX -> climax(t, dist);
            case LURK -> lurk(t, dist);
            case TRANSFORM -> transform(t);
            case ATTACK -> attack(t, dist);
            case FLEE -> flee(t);
        }
        if (!ended && mob.getDisguise() == Disguise.PLAYER) {
            mannerisms(t);
        }
    }

    // =================================================================== phases

    private void stalk(ServerPlayer t, double dist) {
        fog(t, 0.15F);
        double speed = switch (enc.mode()) {
            case DOG -> speedFor(5.0);
            case ZOMBIE -> speedFor(2.3);
            default -> sprintPref ? speedFor(5.6) : speedFor(4.3);
        };
        goTo(t, speed);
        if (enc.mode() == Mode.ZOMBIE) {
            mob.setAggressive(true);
            stare(t);
            if (dist < 3.0) {
                if (canAttack(t) && enc.willAttack()) {
                    setPhase(Phase.TRANSFORM);
                } else {
                    vanish(false); // gone right before it touches you
                }
            }
            return;
        }
        if (dist < 16) {
            setPhase(Phase.APPROACH);
        } else if (phaseAge > 20 * 90) {
            vanish(false);
        }
    }

    private void approach(ServerPlayer t, double dist) {
        fog(t, 0.2F);
        if (dist < 10) {
            stare(t);
        }
        double greetDist = enc.mode() == Mode.DOG ? 2.5
                : Mth.clamp((copiedProfile != null ? copiedProfile.friendDistance : 3.5) + 0.5, 2.5, 5.0);
        if (dist <= greetDist) {
            halt();
            setPhase(Phase.GREET);
            return;
        }
        goTo(t, enc.mode() == Mode.DOG ? speedFor(5.0) : speedFor(dist > 8 && sprintPref ? 5.6 : 4.3));
        if (phaseAge > 20 * 30) {
            setPhase(Phase.LURE); // could not reach them; start walking anyway
        }
    }

    private void greet(ServerPlayer t, double dist) {
        fog(t, 0.25F);
        halt();
        stare(t);
        if (enc.mode() == Mode.DOG) {
            if (phaseAge == 1) {
                mob.playSound(SoundEvents.WOLF_WHINE, 1.0F, 1.0F);
            }
            float sitChance = copiedProfileOfOwnerDogSit();
            if (phaseAge == 15 && random.nextFloat() < sitChance) {
                mob.setFlag(FogLurkerEntity.FLAG_SITTING, true);
            }
            if (phaseAge == 55) {
                mob.setFlag(FogLurkerEntity.FLAG_SITTING, false);
                mob.playSound(SoundEvents.WOLF_AMBIENT, 1.0F, 1.0F);
            }
            if (phaseAge >= 65) {
                setPhase(Phase.LURE);
            }
            return;
        }
        // Mimic: greet like the real player would.
        if (phaseAge == 1 && (copiedProfile == null ? random.nextBoolean() : copiedProfile.likesCrouchSpam())) {
            crouchSpam = 24 + random.nextInt(24);
        }
        if (phaseAge == 8) {
            say(t, MimicChat.Intent.GREET, null);
        }
        if (phaseAge == 30) {
            speak(enc.copiedId(), AudioFx.Style.NATURAL, false);
        }
        if (phaseAge == 75) {
            say(t, MimicChat.Intent.LURE, null);
        }
        if (phaseAge >= 95) {
            nextChatAt = age + 300 + random.nextInt(200);
            setPhase(Phase.LURE);
        }
    }

    private void lure(ServerPlayer t, double dist) {
        double toWoods = mob.position().distanceTo(woods);
        float progress = (float) Mth.clamp(1.0 - t.position().distanceTo(woods) / woodsStartDistance, 0, 1);
        fog(t, 0.3F + 0.4F * progress);
        // If the way into the woods is blocked (water, cliffs), wherever it got to becomes "the spot".
        if (toWoods < bestToWoods - 0.5) {
            bestToWoods = toWoods;
            lureStuck = 0;
        } else if (dist < 12) {
            lureStuck++;
        }
        if (toWoods < 4.0 || lureStuck > 20 * 30) {
            setPhase(Phase.CLIMAX);
            return;
        }
        boolean dog = enc.mode() == Mode.DOG;
        double lead = dog ? 7.0 : 9.0;
        if (dist <= lead) {
            stall = 0;
            double speed = dog ? speedFor(5.0) : speedFor(dist < 5 && sprintPref ? 5.6 : 4.3);
            goTo(woods, speed);
            if (age % 60 < 12) {
                stare(t); // glance back over the shoulder
            }
            if (!dog && age >= nextChatAt) {
                say(t, MimicChat.Intent.WAIT, null);
                nextChatAt = age + 400 + random.nextInt(400);
            }
        } else if (dist > lead + 5) {
            halt();
            stare(t);
            stall++;
            if (dog) {
                if (stall % 70 == 20) {
                    mob.playSound(random.nextBoolean() ? SoundEvents.WOLF_AMBIENT : SoundEvents.WOLF_WHINE, 1.0F, 1.0F);
                }
            } else {
                if (stall % 220 == 40) {
                    say(t, stall > 400 ? MimicChat.Intent.IMPATIENT : MimicChat.Intent.WAIT, null);
                }
                if (stall % 260 == 130) {
                    speak(enc.copiedId(), AudioFx.Style.NATURAL, random.nextFloat() < 0.25F);
                }
            }
            if (stall > 20 * 45 && dist > 32) {
                if (reapproaches++ < 2) {
                    setPhase(Phase.STALK); // go and fetch them
                } else {
                    vanish(false);
                }
            }
        } else {
            // Close enough to keep them interested; inch forward.
            stare(t);
            if (age % 40 == 0) {
                goTo(woods, speedFor(dog ? 3.0 : 2.5));
            }
        }
    }

    private void climax(ServerPlayer t, double dist) {
        fog(t, 0.7F);
        halt();
        boolean mimicking = enc.mode() == Mode.MIMIC;
        if (!(enc.willAttack() && canAttack(t))) {
            stare(t);
            if (mimicking && !saidArrived && phaseAge == 20) {
                saidArrived = true;
                say(t, MimicChat.Intent.ARRIVED, null);
            }
            if (isLookedAtBy(t)) {
                lookedAt++;
            }
            // It was never there.
            if (dist < 5 || (lookedAt > 40 && dist < 12) || phaseAge > 20 * 60) {
                vanish(false);
            }
            return;
        }
        // Stand facing deeper into the woods and let them walk up behind it.
        if (dist > 7) {
            Vec3 deeper = woods.add(woods.subtract(t.position()).normalize().scale(10));
            mob.getLookControl().setLookAt(deeper.x, deeper.y + 1.5, deeper.z);
            if (mimicking && phaseAge % 240 == 30) {
                say(t, MimicChat.Intent.ARRIVED, null);
            }
        } else {
            stare(t);
        }
        if (dist <= 6 || (phaseAge > 20 * 25 && dist < 16)) {
            if (mimicking && random.nextFloat() < 0.6F) {
                say(t, MimicChat.Intent.CREEPY, null);
            }
            setPhase(Phase.TRANSFORM);
        } else if (phaseAge > 20 * 70) {
            vanish(false);
        }
    }

    private void lurk(ServerPlayer t, double dist) {
        switch (enc.mode()) {
            case WATCHER -> watcher(t, dist);
            case DOPPELGANGER -> doppelganger(t, dist);
            default -> herd(t, dist);
        }
    }

    private void watcher(ServerPlayer t, double dist) {
        fog(t, 0.35F);
        halt();
        stare(t);
        if (isLookedAtBy(t)) {
            lookedAt++;
            if (lookedAt == 1) {
                mob.playSound(ModSounds.WHISPER.get(), 1.0F, 0.7F);
            }
        }
        if (dist < 22 || lookedAt > 30) {
            if (enc.willAttack() && canAttack(t)) {
                setPhase(Phase.TRANSFORM); // you looked for too long
            } else {
                vanish(false);
            }
        }
    }

    private void doppelganger(ServerPlayer t, double dist) {
        fog(t, 0.3F);
        stare(t);
        // Mirror them: crouch when they crouch, hold what they hold, jump when they jump, swing when they swing.
        mob.setCrouching(t.isShiftKeyDown());
        if (age % 10 == 0) {
            mob.copyEquipmentOf(t);
        }
        if (lastTargetOnGround && !t.onGround() && t.getY() - lastTargetY > 0.2) {
            mob.getJumpControl().jump();
        }
        if (t.swinging && !lastTargetSwinging) {
            mob.swing(InteractionHand.MAIN_HAND);
        }
        lastTargetY = t.getY();
        lastTargetOnGround = t.onGround();
        lastTargetSwinging = t.swinging;

        if (dist > 40) {
            goTo(t, speedFor(4.3));
        } else {
            halt();
        }
        if (phaseAge == 100 && random.nextBoolean()) {
            say(t, MimicChat.Intent.CREEPY, null);
        }
        if (age >= nextVoiceAt) {
            speak(enc.targetId(), AudioFx.Style.UNCANNY, false);
        }
        if (dist < 14) {
            if (enc.willAttack() && canAttack(t)) {
                setPhase(Phase.TRANSFORM);
            } else {
                vanish(false);
            }
        }
    }

    private void herd(ServerPlayer t, double dist) {
        fog(t, 0.22F);
        boolean lookedAtNow = isLookedAtBy(t);
        double walk = speedFor(Mth.clamp(animalStats.moveSpeed, 0.6F, 3.0F));
        // Real animals glance at players now and then. This one stares a little too often.
        if (dist < 24 && random.nextFloat() < Math.min(0.95F, animalStats.lookAtPlayer + 0.5F)) {
            stare(t);
        }
        if (wanderTarget != null) {
            if (mob.position().distanceToSqr(wanderTarget) < 1.5 || mob.getNavigation().isDone() && age - lastRepath > 20) {
                wanderTarget = null;
                float idle = animalStats.idleSeconds * (0.5F + random.nextFloat());
                idleUntil = age + (int) (Mth.clamp(idle, 1.5F, 20F) * 20);
            } else {
                goTo(wanderTarget, walk);
            }
        } else if (age >= idleUntil) {
            if (dist > 20 || (!lookedAtNow && random.nextFloat() < 0.6F)) {
                // Creep closer while they are not looking.
                Vec3 toward = t.position().subtract(mob.position()).normalize();
                double step = 2 + random.nextDouble() * 4;
                if (!enc.willAttack()) {
                    step = Math.min(step, Math.max(0, dist - 6));
                }
                wanderTarget = mob.position().add(toward.scale(step))
                        .add((random.nextDouble() - 0.5) * 3, 0, (random.nextDouble() - 0.5) * 3);
            } else if (random.nextFloat() < animalStats.moveFraction + 0.2F) {
                wanderTarget = mob.position().add((random.nextDouble() - 0.5) * 10, 0, (random.nextDouble() - 0.5) * 10);
            } else {
                idleUntil = age + 40 + random.nextInt(60);
            }
            if (wanderTarget != null) {
                BlockPos g = SpotFinder.ground(level(), Mth.floor(wanderTarget.x), Mth.floor(wanderTarget.z), mob.getBlockY());
                wanderTarget = g == null ? null : Vec3.atBottomCenterOf(g);
            }
        }

        if (enc.willAttack() && canAttack(t)) {
            boolean backTurned = !lookedAtNow && t.getViewVector(1F).dot(mob.position().subtract(t.position()).normalize()) < 0;
            if (dist < 5 || (dist < 10 && backTurned && phaseAge > 20 * 20 && isolated(t))) {
                setPhase(Phase.TRANSFORM);
            }
        } else if (dist < 4) {
            mob.playSound(ModSounds.WHISPER.get(), 1.0F, 0.6F);
            setPhase(Phase.FLEE);
        }
    }

    private void transform(ServerPlayer t) {
        fog(t, 0.9F);
        halt();
        stare(t);
        if (phaseAge == 1) {
            mob.setFlag(FogLurkerEntity.FLAG_TRANSFORMING, true);
            if (mob.getDisguise() == Disguise.DOG || mob.getDisguise() == Disguise.WOLF) {
                mob.setFlag(FogLurkerEntity.FLAG_ANGRY, true);
                mob.playSound(SoundEvents.WOLF_GROWL, 1.5F, 0.6F);
            }
            mob.playSound(ModSounds.REVEAL.get(), 2.0F, 1.0F);
            ModNetwork.send(t, new ScarePacket(70));
            UUID voice = enc.mode() == Mode.MIMIC ? enc.copiedId() : enc.targetId();
            speak(voice, AudioFx.Style.DEMONIC, true);
        }
        if (phaseAge >= 36) {
            mob.setFlag(FogLurkerEntity.FLAG_TRANSFORMING | FogLurkerEntity.FLAG_ANGRY | FogLurkerEntity.FLAG_SITTING, false);
            mob.setDisguise(Disguise.NONE);
            mob.clearEquipment();
            mob.setAggressive(true);
            mob.playSound(ModSounds.SCREAM.get(), 2.0F, 1.0F);
            setPhase(canAttack(t) ? Phase.ATTACK : Phase.FLEE);
        }
    }

    private void attack(ServerPlayer t, double dist) {
        fog(t, 0.85F);
        if (!canAttack(t)) {
            setPhase(Phase.FLEE);
            return;
        }
        mob.setAggressive(true);
        stare(t);
        double chase = speedFor(7.2 * difficulty().speedMultiplier());
        if (age - lastRepath >= 5 || mob.getNavigation().isDone()) {
            lastRepath = age;
            if (!mob.getNavigation().moveTo(t, chase)) {
                mob.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), chase);
            }
        }
        if (age % 30 == 0) {
            mob.playSound(ModSounds.HEARTBEAT.get(), 1.0F, 1.2F);
        }
        double reachSqr = 2.3 * 2.3;
        if (mob.distanceToSqr(t) <= reachSqr && attackCooldown == 0 && mob.hasLineOfSight(t)) {
            if (mob.doHurtTarget(t)) {
                hits++;
            }
            attackCooldown = difficulty() == LurkerDifficulty.DEATHWISH ? 12 : 20;
        }
        if (hits >= difficulty().maxHitsPerEncounter()) {
            setPhase(Phase.FLEE);
        } else if (phaseAge > 20 * 60 || dist > 48) {
            vanish(false);
        }
    }

    private void flee(ServerPlayer t) {
        fog(t, 0.4F);
        Vec3 away = mob.position().subtract(t.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
        double speed = mob.getDisguise().isAnimal() ? speedFor(4.5) : speedFor(8.0 * difficulty().speedMultiplier());
        goTo(mob.position().add(away.scale(16)), speed);
        if (phaseAge > 60 || (phaseAge > 15 && !SpotFinder.canSee(t, mob.getEyePosition()))) {
            vanish(false);
        }
    }

    // =================================================================== reactions

    public void onHurtBy(Player attacker) {
        if (ended || phase == Phase.TRANSFORM || phase == Phase.ATTACK || phase == Phase.FLEE) {
            return;
        }
        ServerPlayer t = target();
        if (t != null && enc.willAttack() && canAttack(t)) {
            setPhase(Phase.TRANSFORM);
        } else {
            // Its skin slips for a moment, then it runs.
            mob.playSound(ModSounds.SCREAM.get(), 2.0F, 1.0F);
            mob.setDisguise(Disguise.NONE);
            mob.clearEquipment();
            if (attacker instanceof ServerPlayer sp) {
                ModNetwork.send(sp, new ScarePacket(30));
            }
            setPhase(Phase.FLEE);
        }
    }

    /** @return true if the interaction was handled. */
    public boolean onInteract(ServerPlayer player) {
        if (ended) {
            return false;
        }
        if (mob.getDisguise() == Disguise.DOG && player.getUUID().equals(enc.copiedId())) {
            // Behave like a good dog... for now.
            mob.setFlag(FogLurkerEntity.FLAG_SITTING, !mob.hasFlag(FogLurkerEntity.FLAG_SITTING));
            return true;
        }
        if (player.getUUID().equals(enc.targetId()) && enc.willAttack() && canAttack(player)
                && phase != Phase.TRANSFORM && phase != Phase.ATTACK) {
            setPhase(Phase.TRANSFORM);
            return true;
        }
        return false;
    }

    public void onTargetChat(String text) {
        if (ended || replies >= 8) {
            return;
        }
        boolean talks = enc.mode() == Mode.MIMIC || enc.mode() == Mode.DOPPELGANGER;
        if (!talks || phase == Phase.TRANSFORM || phase == Phase.ATTACK || phase == Phase.FLEE) {
            return;
        }
        replies++;
        heardPending = text;
    }

    // =================================================================== endings

    public void vanish(boolean screaming) {
        if (ended) {
            return;
        }
        ended = true;
        ServerLevel level = level();
        double h = mob.getBbHeight();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + h / 2, mob.getZ(),
                40, mob.getBbWidth() / 2, h / 2, mob.getBbWidth() / 2, 0.02);
        level.sendParticles(ParticleTypes.SQUID_INK, mob.getX(), mob.getY() + h / 2, mob.getZ(),
                12, mob.getBbWidth() / 2, h / 2, mob.getBbWidth() / 2, 0.01);
        mob.playSound(screaming ? ModSounds.SCREAM.get() : ModSounds.VANISH.get(), 1.5F, 0.8F);
        VoiceBridge.stop(mob);
        ServerPlayer t = target();
        if (t != null) {
            ModNetwork.send(t, new FogPacket(0F));
        }
        mob.discard();
    }

    // =================================================================== helpers

    private boolean canAttack(ServerPlayer t) {
        return !t.isCreative() && !t.isSpectator() && t.isAlive()
                && mob.level().getDifficulty() != Difficulty.PEACEFUL;
    }

    private boolean isolated(ServerPlayer t) {
        for (Player other : mob.level().players()) {
            if (other != t && !other.isSpectator() && other.distanceTo(t) < 24) {
                return false;
            }
        }
        return true;
    }

    /** Is the real player it is impersonating close enough to see their double? */
    private boolean realOneWouldSee() {
        ServerPlayer real = copiedPlayer();
        if (real == null || real.level() != mob.level() || real.getUUID().equals(enc.targetId())) {
            return false;
        }
        return real.distanceTo(mob) < 40 && real.hasLineOfSight(mob);
    }

    private boolean isLookedAtBy(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 to = new Vec3(mob.getX(), mob.getY(0.6), mob.getZ()).subtract(eye);
        double len = to.length();
        if (len > 96 || len < 1e-3) {
            return false;
        }
        return p.getViewVector(1F).dot(to.scale(1 / len)) > 0.97 && p.hasLineOfSight(mob);
    }

    private float copiedProfileOfOwnerDogSit() {
        PlayerBehaviorProfile owner = enc.copiedId() == null ? null
                : FogLurkerSavedData.get(server()).profileOrNull(enc.copiedId());
        return owner == null ? 0.3F : owner.dogSitRatio;
    }

    /**
     * Converts a real-world ground speed (blocks per second) into a navigation speed modifier.
     * A mob's top speed scales with the square of its speed value, hence the square root.
     */
    private double speedFor(double blocksPerSecond) {
        double attr = mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return Math.sqrt(blocksPerSecond / 43.17) / Math.max(0.01, attr);
    }

    private void goTo(Entity e, double speed) {
        if (age - lastRepath < 10 && mob.getNavigation().isInProgress()) {
            mob.getNavigation().setSpeedModifier(speed);
            return;
        }
        lastRepath = age;
        if (mob.distanceTo(e) > 40) {
            goTo(e.position(), speed);
        } else if (!mob.getNavigation().moveTo(e, speed)) {
            mob.getMoveControl().setWantedPosition(e.getX(), e.getY(), e.getZ(), speed);
        }
    }

    private void goTo(Vec3 pos, double speed) {
        if (age - lastRepath < 10 && mob.getNavigation().isInProgress()) {
            mob.getNavigation().setSpeedModifier(speed);
            return;
        }
        lastRepath = age;
        Vec3 dest = pos;
        double d = mob.position().distanceTo(pos);
        if (d > 40) {
            // Long trips go in hops so pathfinding stays cheap.
            Vec3 mid = mob.position().add(pos.subtract(mob.position()).normalize().scale(32));
            BlockPos g = SpotFinder.ground(level(), Mth.floor(mid.x), Mth.floor(mid.z), mob.getBlockY());
            if (g != null) {
                dest = Vec3.atBottomCenterOf(g);
            }
        }
        if (!mob.getNavigation().moveTo(dest.x, dest.y, dest.z, speed)) {
            mob.getMoveControl().setWantedPosition(dest.x, dest.y, dest.z, speed);
        }
    }

    private void halt() {
        mob.getNavigation().stop();
    }

    private void stare(Entity e) {
        mob.getLookControl().setLookAt(e, 30.0F, 30.0F);
    }

    private void fog(ServerPlayer t, float intensity) {
        if (age % 20 == 1) {
            ModNetwork.send(t, new FogPacket(intensity));
        }
    }

    /** Little human habits copied from the real player: crouch spam, idle swings, bunny hops, looking around. */
    private void mannerisms(ServerPlayer t) {
        if (crouchSpam > 0) {
            crouchSpam--;
            if (crouchSpam % 4 == 0) {
                mob.setCrouching(!mob.isShiftKeyDown() && crouchSpam > 0);
            }
        }
        PlayerBehaviorProfile p = copiedProfile;
        float swingRate = p != null ? p.idleSwingRate : 0.05F;
        if (random.nextFloat() < swingRate / 20F * 0.5F) {
            mob.swing(InteractionHand.MAIN_HAND);
        }
        boolean moving = mob.getDeltaMovement().horizontalDistanceSqr() > 0.004;
        float jumpRate = p != null ? p.jumpRate : 0.1F;
        if (moving && sprintPref && mob.onGround() && random.nextFloat() < jumpRate / 20F) {
            mob.getJumpControl().jump();
        }
        float jitter = p != null ? p.lookJitter : 3F;
        if (!moving && phase == Phase.LURE && random.nextFloat() < jitter / 400F) {
            Vec3 look = mob.getEyePosition().add((random.nextDouble() - 0.5) * 16, (random.nextDouble() - 0.6) * 4,
                    (random.nextDouble() - 0.5) * 16);
            mob.getLookControl().setLookAt(look.x, look.y, look.z);
        }
    }

    private void speak(@Nullable UUID owner, AudioFx.Style style, boolean splice) {
        if (owner == null || age < nextVoiceAt) {
            return;
        }
        nextVoiceAt = age + 200 + random.nextInt(240);
        if (VoiceBridge.canSpeakAs(owner)) {
            VoiceBridge.speak(mob, owner, style, splice);
        }
    }

    private void say(ServerPlayer t, MimicChat.Intent intent, @Nullable String heard) {
        if (chatInFlight || pendingChat != null || enc.copiedName() == null) {
            return;
        }
        chatInFlight = true;
        MimicChat.Context ctx = new MimicChat.Context(enc.copiedName(), t.getGameProfile().getName(), copiedProfile,
                woodsDir, mob.level().isNight(), List.copyOf(said));
        MinecraftServer server = server();
        MimicChat.compose(ctx, intent, heard, random).whenComplete((text, err) -> server.execute(() -> {
            chatInFlight = false;
            if (err != null || text == null || text.isBlank() || ended) {
                return;
            }
            pendingChat = text;
            // Human typing time.
            pendingChatAt = age + 10 + Math.min(80, text.length() * 2);
        }));
    }

    private void deliverChat(ServerPlayer t) {
        if (pendingChat == null || age < pendingChatAt) {
            return;
        }
        String text = pendingChat;
        pendingChat = null;
        said.add(text);
        if (said.size() > 8) {
            said.remove(0);
        }
        ServerPlayer copied = copiedPlayer();
        Component name = copied != null ? copied.getDisplayName() : Component.literal(enc.copiedName());
        Component line = Component.translatable("chat.type.text", name, Component.literal(text));
        if (FogLurkerConfig.COMMON.chatVisibility.get() == FogLurkerConfig.ChatVisibility.EVERYONE) {
            server().getPlayerList().broadcastSystemMessage(line, false);
        } else {
            t.sendSystemMessage(line);
        }
        RecentChat.add(enc.copiedName(), text);
    }
}
