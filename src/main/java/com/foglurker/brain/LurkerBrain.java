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
import com.foglurker.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.ChatFormatting;
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
        WATCHER,
        /** True form, creeping closer from hiding spot to hiding spot; drops flat whenever you look. */
        STALKER,
        /**
         * Deathwish, at night: you walk right into it. It greets you by name and promises not to hurt you.
         * Half the time it means it. The other half: "just kidding", "RUN!", a 5 second head start, and
         * one bite kills. Get away and it leaves you alone for 15 minutes.
         */
        NIGHT_MEETING
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
    /** False where there are no trees at all (a desert): then it never talks about "the woods". */
    private boolean woodsHasTrees = true;
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
    @Nullable
    private Vec3 coverSpot;
    private boolean coverSearched;
    @Nullable
    private Vec3 stalkTo;
    @Nullable
    private BlockPos fakeMine;
    /** A watcher leaves when you come this close (smaller when it started at your window or down a tunnel). */
    private double watchFleeDistance = 22;
    private double bestToWoods = Double.MAX_VALUE;
    private int lureStuck;

    // doppelganger mirroring
    private double lastTargetY;
    private boolean lastTargetOnGround;
    private boolean lastTargetSwinging;

    // attack
    private int hits;
    /** This encounter already counted as an escape. */
    private boolean escaped;
    private int attackCooldown;

    // counterplay: soul-fire wards, and whether you are looking at it
    private boolean wasWatched;

    // water: its weakness in the attacking form
    private int waterTicks;
    private int targetWaterTicks;

    // chasing you up towers and through walls
    private boolean directChase;
    private int pushTicks;
    @Nullable
    private BlockPos breaking;
    private int breakProgress;
    private int breakTotal;
    private double bestAttackDist = Double.MAX_VALUE;
    private int attackStall;

    // stuck detection while closing in
    private double bestApproach = Double.MAX_VALUE;
    private int noProgress;
    private int detours;
    @Nullable
    private Vec3 detour;

    /** Who it is hunting now, when it switched to someone else in the group during an attack. */
    @Nullable
    private UUID huntId;
    /** Most creatures go for the easiest prey in a group; a few stay fixated on their first target. */
    private boolean switchesTargets;
    private int lastSwitch = -1000;
    /** Everyone it has sent fog to, so all of it clears when it leaves. */
    private final java.util.Set<UUID> fogged = new java.util.HashSet<>();

    public LurkerBrain(FogLurkerEntity mob, Encounter enc) {
        this.mob = mob;
        this.enc = enc;
        this.random = mob.getRandom();
        this.switchesTargets = random.nextFloat() < 0.8F;
    }

    /** Tests: make it always (or never) switch to an easier target in a group. */
    public void setSwitchesTargets(boolean on) {
        this.switchesTargets = on;
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
        return huntId != null ? huntId : enc.targetId();
    }

    /** One-line state summary for debugging. */
    public String describe() {
        var nav = mob.getNavigation();
        var path = nav.getPath();
        return phase + " at " + mob.blockPosition().toShortString() + (mob.isInWater() ? " (in water)" : "")
                + ", path " + (path == null ? "none" : path.isDone() ? "done" : (path.getNextNodeIndex() + "/" + path.getNodeCount()
                + (path.canReach() ? "" : " partial"))) + ", stuck " + noProgress + ", detours " + detours;
    }

    private ServerLevel level() {
        return (ServerLevel) mob.level();
    }

    private MinecraftServer server() {
        return level().getServer();
    }

    @Nullable
    private ServerPlayer target() {
        return server().getPlayerList().getPlayer(targetId());
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
                holdFavouriteIfEmpty();
            }
            case DOG -> mob.setDisguiseName(enc.dogName());
            default -> mob.setDisguiseName(null);
        }
        animalStats = data.entityStats().get(enc.disguise().learnKey());
        sprintPref = copiedProfile != null ? random.nextFloat() < copiedProfile.sprintRatio : random.nextBoolean();

        ServerPlayer t = target();
        Vec3 origin = t != null ? t.position() : mob.position();
        ServerPlayer copied = copiedPlayer();
        Vec3 avoid = copied != null && copied.level() == mob.level() && copied != t ? copied.position() : null;
        SpotFinder.Woods w = SpotFinder.findWoods(level(), origin, avoid, random);
        woods = w.pos();
        woodsDir = w.direction();
        woodsHasTrees = w.canopy() > 0;
        woodsStartDistance = Math.max(8, origin.distanceTo(woods));

        int maxTicks = FogLurkerConfig.COMMON.encounterMaxMinutes.get() * 60 * 20;
        lifetime = enc.mode() == Mode.WATCHER ? Math.min(maxTicks, 20 * 120) : maxTicks;
        phase = switch (enc.mode()) {
            case MIMIC, DOG, ZOMBIE -> Phase.STALK;
            case NIGHT_MEETING -> Phase.GREET;
            default -> Phase.LURK;
        };
        nextVoiceAt = 60;
        if (t != null) {
            lastTargetY = t.getY();
            watchFleeDistance = Math.min(22, mob.distanceTo(t) * 0.6);
        }
    }

    private void setPhase(Phase p) {
        phase = p;
        phaseAge = 0;
        if (p == Phase.FLEE) {
            mob.setFlag(FogLurkerEntity.FLAG_SITTING, false); // a dog interrupted mid-greeting runs, not sits
        }
        bestApproach = Double.MAX_VALUE;
        noProgress = 0;
        stall = 0;
        wanderTarget = null;
        if (p != Phase.GREET) {
            mob.setCrouching(false);
            crouchSpam = 0;
        }
        mob.setLying(false);
        bestToWoods = Double.MAX_VALUE;
        lureStuck = 0;
        detour = null; // a side-step planned for the old phase must not steer the new one
        detours = 0; // each phase gets its own two tries at walking around an obstacle
        queuedIntent = null; // a line planned for the last phase does not belong in this one
        if (p == Phase.TRANSFORM || p == Phase.ATTACK || p == Phase.FLEE) {
            pendingChat = null; // the mask is off: a line it was still "typing" is never sent
            heardPending = null; // ...nor a reply to something said before
            chatGen++; // ...and one still being written is thrown away when it arrives
        }
        clearFakeMine();
        clearBreaking();
    }

    /** Takes down the crack marks of a block it was digging through, and forgets the progress. */
    private void clearBreaking() {
        if (breaking != null) {
            level().destroyBlockProgress(mob.getId(), breaking, -1);
            breaking = null;
            breakProgress = 0;
        }
    }

    /** Takes down the crack marks of its pretend mining. */
    private void clearFakeMine() {
        if (fakeMine != null) {
            level().destroyBlockProgress(mob.getId() + 1_000_000, fakeMine, -1);
            fakeMine = null;
        }
    }

    // =================================================================== main loop

    public void tick() {
        if (ended) {
            return;
        }
        boolean switchedBack = false;
        age++;
        phaseAge++;
        ServerPlayer t = target();
        if (huntId != null && (t == null || !t.isAlive() || t.level() != mob.level() || t.isSpectator()
                || t.isCreative() || com.foglurker.world.AfkTracker.isAfk(t))) {
            UUID lost = huntId;
            switchedBack = true;
            if (t == null && phase == Phase.ATTACK && FogLurkerConfig.COMMON.escapeEscalation.get()
                    && !lost.equals(enc.targetId())) { // (its first victim's logout is counted just below)
                // the one it switched to logged out mid-attack: that counts as getting away, for them
                PlayerBehaviorProfile gone = savedData().profile(lost);
                gone.escapes = Math.min(gone.escapes + 1, 10);
                savedData().setDirty();
            }
            huntId = null; // the one it switched to is gone: back to the first target...
            forgetChatForVictimChange();
            t = target();
            if (LurkerDirector.isHuntedByAnother(enc.targetId(), this)
                    || t != null && LurkerDirector.leftAloneTicks(t) > 0) {
                vanish(false); // ...unless another one has them by now (never two at once), or they earned their peace
                return;
            }
        }
        // (Not right after a switch back: that first victim was not being hunted when they left.)
        if (t == null && !switchedBack && (phase == Phase.ATTACK || phase == Phase.TRANSFORM || meetingChase)
                && FogLurkerConfig.COMMON.escapeEscalation.get() && !escaped) {
            // Logged out with its teeth almost in them: that counts as getting away (it comes back angrier),
            // but it is not the night meeting's earned peace.
            escaped = true;
            PlayerBehaviorProfile gone = savedData().profile(targetId());
            gone.escapes = Math.min(gone.escapes + 1, 10);
            savedData().setDirty();
        }
        // Creative counts as gone, like spectator: nothing comes for a player it could never hurt.
        if (t == null || !t.isAlive() || t.level() != mob.level() || t.isSpectator() || t.isCreative()) {
            if (t != null && (t.isSpectator() || t.isCreative())) {
                meetingChase = false; // switching game mode is not outrunning it: no 15-minute pass
            }
            vanish(false);
            return;
        }
        if (com.foglurker.world.AfkTracker.isAfk(t)) {
            // They are away from the keyboard: it leaves them alone. (Not an escape, so no 15-minute pass.)
            meetingChase = false;
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
        keepFogAlive(t);
        if (suffersWater()) {
            return;
        }
        deliverChat(t);
        if (queuedIntent != null && heardPending == null && !chatInFlight && pendingChat == null) {
            MimicChat.Intent next = queuedIntent;
            queuedIntent = null;
            say(t, next, null);
        }
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
                holdFavouriteIfEmpty();
            }
        }

        switch (phase) {
            case STALK -> stalk(t, dist);
            case APPROACH -> approach(t, dist);
            case GREET -> {
                if (enc.mode() == Mode.NIGHT_MEETING) {
                    nightMeeting(t, dist);
                } else {
                    greet(t, dist);
                }
            }
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
        watchProgress(t, dist);
        double speed = switch (enc.mode()) {
            case DOG -> speedFor(5.0);
            case ZOMBIE -> speedFor(2.3);
            default -> sprintPref ? speedFor(5.6) : speedFor(4.3);
        };
        goTo(t, speed);
        if (enc.mode() == Mode.ZOMBIE) {
            mob.setAggressive(true);
            stare(t);
            if (phaseAge > 20 * 90) {
                vanish(false); // even standing right next to someone in soul fire or water, it gives up eventually
            } else if (revealDistance(t, dist) < 3.0) {
                if (canAttack(t) && enc.willAttack()) {
                    if (canRevealNow(t)) {
                        setPhase(Phase.TRANSFORM);
                    }
                } else {
                    vanish(false); // gone right before it touches you
                }
            }
            return;
        }
        // A familiar voice calling from somewhere out in the trees, before you can see anyone.
        if (enc.mode() == Mode.MIMIC && dist > 18 && phaseAge % 160 == 60) {
            speak(enc.copiedId(), AudioFx.Style.UNCANNY, false);
        }
        if (dist < 16) {
            setPhase(Phase.APPROACH);
        } else if (phaseAge > 20 * 90) {
            vanish(false);
        }
    }

    private void approach(ServerPlayer t, double dist) {
        fog(t, 0.2F);
        watchProgress(t, dist);
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
        } else if (dist <= (enc.mode() == Mode.DOG ? 7.0 : 9.0) + 5) {
            lureStuck++; // they are following but it cannot get closer to the woods
        }
        if (toWoods < 4.0 || lureStuck > 20 * 30) {
            setPhase(Phase.CLIMAX);
            return;
        }
        boolean dog = enc.mode() == Mode.DOG;
        double lead = dog ? 7.0 : 9.0;
        if (dist <= lead + 5) {
            clearFakeMine(); // they came closer: it stopped "mining"
        }
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
            stall++;
            if (!pretendMine(dog ? -1 : stall % 300)) {
                stare(t);
            }
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
        double near = revealDistance(t, dist);
        if ((near <= 6 || (phaseAge > 20 * 25 && near < 16)) && canRevealNow(t)) {
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
            case STALKER -> stalker(t, dist);
            default -> herd(t, dist);
        }
    }

    private void watcher(ServerPlayer t, double dist) {
        fog(t, 0.35F);
        // It does not stand about in the open: it slinks to tall grass, ferns or a bush and lies down
        // in it like a dog, legs tucked under, only its head and eyes showing.
        if (!coverSearched) {
            coverSearched = true;
            coverSpot = SpotFinder.findCover(level(), mob.blockPosition(), t.position(), 14,
                    Math.min(24, Math.max(watchFleeDistance + 3, dist - 2)));
        }
        boolean goingToCover = coverSpot != null && mob.position().distanceToSqr(coverSpot) > 0.8 && phaseAge < 400;
        if (goingToCover) {
            mob.setLying(false);
            goTo(coverSpot, speedFor(2.6));
        } else {
            halt();
            mob.setLying(mob.getDisguise() == Disguise.CRAWLER);
        }
        stare(t);
        if (isLookedAtBy(t)) {
            lookedAt++;
            if (lookedAt == 1) {
                mob.playSound(ModSounds.WHISPER.get(), 1.0F, 0.7F);
            }
        } else if (age % 4 == 0 && lookedAt > 0) {
            lookedAt--; // brief glances spread over minutes do not add up
        }
        if (dist < watchFleeDistance || lookedAt > 30) {
            if (enc.willAttack() && canAttack(t) && canRevealNow(t)) {
                setPhase(Phase.TRANSFORM); // you looked for too long
            } else {
                vanish(false);
            }
        }
    }

    /**
     * Stalking: it creeps closer through cover while you are not looking. The moment you look its
     * way it drops flat into the grass and holds still. Close enough, it either attacks or is gone.
     */
    private void stalker(ServerPlayer t, double dist) {
        fog(t, 0.3F + (float) Mth.clamp((24 - dist) / 24, 0, 1) * 0.35F);
        boolean seen = isWatchedBy(t);
        if (seen) {
            halt();
            mob.setLying(true);
            stare(t);
            if (++lookedAt > 100 && dist < 10) {
                vanish(false); // you stared it down
            }
            return;
        }
        mob.setLying(false);
        lookedAt = Math.max(0, lookedAt - 1);
        boolean arrived = stalkTo != null && mob.position().distanceToSqr(stalkTo) < 2;
        if ((stalkTo == null && phaseAge % 20 == 0) || arrived || phaseAge % 120 == 0) { // never search every tick
            double inner = Math.max(5, dist - 7);
            stalkTo = SpotFinder.findSpawn(level(), t, inner, inner + 4, mob.position().subtract(t.position()), true, true, random);
        }
        if (stalkTo != null) {
            goTo(stalkTo, speedFor(3.2 * difficulty().speedMultiplier()));
        }
        stare(t);
        if (dist < 5) {
            if (enc.willAttack() && canAttack(t) && canRevealNow(t)) {
                mob.playSound(ModSounds.SCREAM.get(), 2.0F, 1.0F);
                ModNetwork.send(t, new ScarePacket(50));
                huntNow();
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
        } else if (enc.willAttack() && canAttack(t) && dist > 16 && !isLookedAtBy(t)) {
            goTo(t, speedFor(2.0)); // it means it: it creeps closer whenever you look away
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
                if (canRevealNow(t)) {
                    setPhase(Phase.TRANSFORM);
                }
            } else {
                vanish(false);
            }
        }
    }

    /** When the current wander target was picked. */
    private int wanderSince;

    private void herd(ServerPlayer t, double dist) {
        fog(t, 0.22F);
        boolean lookedAtNow = isLookedAtBy(t);
        double walk = speedFor(Mth.clamp(animalStats.moveSpeed, 0.6F, 3.0F));
        // Real animals glance at players now and then. This one stares a little too often.
        if (dist < 24 && random.nextFloat() < Math.min(0.95F, animalStats.lookAtPlayer + 0.5F)) {
            stare(t);
        }
        if (wanderTarget != null) {
            // Reached it, or stuck short of it (a fence): stop trying after 3 s of no path or 20 s in all.
            if (mob.position().distanceToSqr(wanderTarget) < 1.5 || mob.getNavigation().isDone() && age - wanderSince > 60
                    || age - wanderSince > 400) {
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
                wanderSince = age;
                wanderTarget = mob.position().add(toward.scale(step))
                        .add((random.nextDouble() - 0.5) * 3, 0, (random.nextDouble() - 0.5) * 3);
            } else if (random.nextFloat() < animalStats.moveFraction + 0.2F) {
                wanderSince = age;
                wanderTarget = mob.position().add((random.nextDouble() - 0.5) * 10, 0, (random.nextDouble() - 0.5) * 10);
            } else {
                idleUntil = age + 40 + random.nextInt(60);
            }
            if (wanderTarget != null) {
                BlockPos g = SpotFinder.ground(level(), Mth.floor(wanderTarget.x), Mth.floor(wanderTarget.z), mob.getBlockY());
                wanderSince = age;
                wanderTarget = g == null ? null : Vec3.atBottomCenterOf(g);
                if (wanderTarget == null) {
                    idleUntil = age + 40 + random.nextInt(60); // boxed in: stand a while instead of searching every tick
                }
            }
        }

        if (enc.willAttack() && canAttack(t)) {
            boolean backTurned = !lookedAtNow && t.getViewVector(1F).dot(mob.position().subtract(t.position()).normalize()) < 0;
            double near = revealDistance(t, dist);
            if ((near < 5 || (near < 10 && backTurned && phaseAge > 20 * 20 && isolated(t))) && canRevealNow(t)) {
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
            nextVoiceAt = age; // the reveal is never silenced by the wait between ordinary lines
            speak(voice, AudioFx.Style.DEMONIC, true);
        }
        if (phaseAge >= 36) {
            mob.setFlag(FogLurkerEntity.FLAG_TRANSFORMING | FogLurkerEntity.FLAG_ANGRY | FogLurkerEntity.FLAG_SITTING, false);
            mob.setDisguise(Disguise.CRAWLER);
            mob.clearEquipment();
            mob.setAggressive(true);
            mob.playSound(ModSounds.SCREAM.get(), 2.0F, 1.0F);
            setPhase(canAttack(t) ? Phase.ATTACK : Phase.FLEE);
        }
    }

    private void attack(ServerPlayer t, double dist) {
        ServerPlayer easier = easierPrey(t, dist);
        if (easier != null) {
            LurkerDirector.noteLeft(t.getUUID()); // it left this one: no new creature straight back to them
            forgetChatForVictimChange();
            directChase = false; // a straight line that worked for the last victim means nothing for this one
            leftId = t.getUUID();
            leftAt = age;
            huntId = easier.getUUID();
            lastSwitch = age;
            windupUntil = age + DEATHWISH_WINDUP; // a new victim gets the same warning before a Deathwish bite
            bestAttackDist = Double.MAX_VALUE;
            attackStall = 0;
            clearBreaking(); // the wall it was clawing toward the last victim: no cracks left, no half-dug block
            pushTicks = 0;
            mob.playSound(ModSounds.ATTACK.get(), 1.4F, 0.6F);
            ModNetwork.send(easier, new ScarePacket(40));
            t = easier;
            dist = mob.distanceTo(easier);
        }
        fog(t, 0.85F);
        if (!canAttack(t)) {
            setPhase(Phase.FLEE);
            return;
        }
        mob.setAggressive(true);
        stare(t);
        // Eye contact: look at it and it charges; look away and it slows, so you have to run blind.
        boolean watched = isWatchedBy(t);
        double eye = !FogLurkerConfig.COMMON.eyeContactRule.get() ? 1.0 : watched ? 1.25 : 0.6;
        if (watched && !wasWatched && FogLurkerConfig.COMMON.eyeContactRule.get()) {
            mob.playSound(ModSounds.ATTACK.get(), 1.6F, 0.7F);
        }
        wasWatched = watched;
        double chase = speedFor(7.2 * difficulty().speedMultiplier() * eye);
        boolean targetInWater = SpotFinder.isInOrOnWater(t);
        boolean warded = !targetInWater && isWarded(t);
        if (warded || targetInWater) {
            clearBreaking(); // it stopped digging toward you: no cracks left on the wall
        }
        if (warded) {
            // Soul fire: it circles just outside the light and cannot touch you.
            targetWaterTicks++;
            double keep = Wards.radius() + 2.5;
            if (dist < keep - 1) {
                Vec3 away = mob.position().subtract(t.position()).multiply(1, 0, 1);
                away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
                goTo(t.position().add(away.scale(keep + 1)), chase);
            } else if (dist > keep + 2) {
                goTo(t, chase);
            } else {
                halt();
            }
        } else if (targetInWater) {
            // It cannot follow you into water. It paces the closest shore and screams.
            targetWaterTicks++;
            // (Once a second, or every 5 ticks once it got there: no shore at all would otherwise mean a search every tick.)
            if (age - lastRepath >= 20 || mob.getNavigation().isDone() && age - lastRepath >= 5) {
                lastRepath = age;
                Vec3 shore = SpotFinder.nearestDryGround(level(), t.position(), mob.position(), 12);
                if (shore != null) {
                    mob.getNavigation().moveTo(shore.x, shore.y, shore.z, chase);
                } else {
                    halt();
                }
            }
        } else {
            targetWaterTicks = 0;
            chase(t, chase);
            dig(t);
        }
        if (warded || targetInWater) {
            if (targetWaterTicks % 100 == 20) {
                mob.playSound(ModSounds.SCREAM.get(), 1.5F, 0.7F);
            }
            if (targetWaterTicks > 20 * 20) {
                noteEscape(t);
                vanish(false); // it gives up... this time
                return;
            }
        }
        if (age % 30 == 0) {
            mob.playSound(ModSounds.HEARTBEAT.get(), 1.0F, 1.2F);
        }
        // Deathwish bites kill in one hit, so it always announces itself first: a screech, then 1.5 s.
        // (A night-meeting chase already gave a "RUN!" and a 5 second head start instead; but if you hit it
        // during the greeting and it lunges, that warning never came, so it still screeches and winds up.)
        boolean warned = meetingChase;
        boolean windingUp = !warned && difficulty() == LurkerDifficulty.DEATHWISH
                && (phaseAge < DEATHWISH_WINDUP || age < windupUntil);
        if (!warned && difficulty() == LurkerDifficulty.DEATHWISH && (phaseAge == 1 || age == windupUntil - DEATHWISH_WINDUP + 1)) {
            mob.playSound(ModSounds.SCREAM.get(), 2.0F, 0.55F);
        }
        // Claws still reach you from the shore if you only waded in.
        double reachSqr = 2.3 * 2.3;
        if (!warded && !windingUp && mob.distanceToSqr(t) <= reachSqr && attackCooldown == 0 && mob.canAttackNow() && mob.hasLineOfSight(t)) {
            // Raised and facing it. (Vanilla checks the shield before damage immunity, so a raised, facing shield
            // is what stopped the bite.)
            boolean shielded = t.isBlocking() && t.isDamageSourceBlocked(mob.damageSources().mobAttack(mob));
            boolean bit = mob.doHurtTarget(t);
            if (!bit && shielded) {
                // Teeth on a raised shield: it wrenches the shield aside like an axe does (5 s), so a shield buys
                // one bite, not a whole fight.
                t.disableShield(true);
            }
            if (bit) {
                hits++;
                escaped = false;
                PlayerBehaviorProfile caught = savedData().profile(t.getUUID());
                if (caught.escapes != 0) {
                    caught.escapes = 0; // it got you: the grudge is settled
                    savedData().setDirty();
                }
            }
            // 3 s / 2.5 s / 2 s / 1.75 s / 1.5 s between hits depending on the mode.
            attackCooldown = difficulty().attackCooldownTicks();
        }
        if (dist < bestAttackDist - 0.5) {
            bestAttackDist = dist;
            attackStall = 0;
        } else if (breaking == null && !warded && !targetInWater) {
            attackStall++;
        }
        if (hits >= difficulty().maxHitsPerEncounter()) {
            setPhase(Phase.FLEE);
        } else if (phaseAge > 20 * 120 || attackStall > 20 * 30 || dist > 48) {
            vanish(false); // a long hunt, or 30 s without getting any closer
        }
    }

    /**
     * Normal pathfinding when it can reach you. When it cannot (you towered up, dug down, or walled
     * yourself in) and you are close, it heads straight for you: walls and towers get climbed, holes
     * get dropped into, and {@link #dig} handles the rest.
     */
    private void chase(ServerPlayer t, double speed) {
        // Every 5 ticks (20 while heading straight in). Never every tick: a target behind a wall leaves the path
        // "done" at once, and a full path search each tick is expensive. In between it walks straight at you.
        int every = directChase ? 20 : 5;
        if (age - lastRepath >= every) {
            lastRepath = age;
            boolean pathed = mob.getNavigation().moveTo(t, speed);
            var path = mob.getNavigation().getPath();
            boolean unreachable = !pathed || path == null || !path.canReach();
            double horizontal = Math.sqrt(Mth.square(t.getX() - mob.getX()) + Mth.square(t.getZ() - mob.getZ()));
            directChase = unreachable && horizontal < 6;
            if (directChase || !pathed) {
                mob.getNavigation().stop();
            }
        }
        if (directChase || mob.getNavigation().isDone()) {
            mob.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), speed);
        }
    }

    /** Breaks whatever stands between it and you, except protected blocks (deepslate, obsidian, bedrock...). */
    private void dig(ServerPlayer t) {
        if (!FogLurkerConfig.COMMON.breakBlocks.get() || !ForgeEventFactory.getMobGriefingEvent(level(), mob)) {
            clearBreaking(); // switched off mid-dig: no cracks left behind
            return;
        }
        ServerLevel level = level();
        if (breaking != null) {
            BlockState state = level.getBlockState(breaking);
            if (state.isAir() || mob.distanceToSqr(Vec3.atCenterOf(breaking)) > 3.5 * 3.5 || !canBreak(breaking)) {
                level.destroyBlockProgress(mob.getId(), breaking, -1);
                breaking = null;
                return;
            }
            breakProgress++;
            mob.getLookControl().setLookAt(Vec3.atCenterOf(breaking));
            if (breakProgress % 5 == 0) {
                mob.swing(InteractionHand.MAIN_HAND);
                level.playSound(null, breaking, state.getSoundType().getHitSound(), SoundSource.HOSTILE, 1.0F, 0.6F);
            }
            int stage = Math.min(9, breakProgress * 10 / breakTotal);
            if (breakProgress == 1 || stage != Math.min(9, (breakProgress - 1) * 10 / breakTotal)) {
                level.destroyBlockProgress(mob.getId(), breaking, stage); // only when the crack grows (it goes to every player)
            }
            if (breakProgress >= breakTotal) {
                level.destroyBlockProgress(mob.getId(), breaking, -1);
                level.destroyBlock(breaking, true, mob);
                breaking = null;
                pushTicks = 0;
                attackStall = 0;
            }
            return;
        }
        // Pressing against something without managing to climb it (a ceiling stops the climb).
        boolean pushing = mob.horizontalCollision && Math.abs(mob.getDeltaMovement().y) < 0.03;
        pushTicks = pushing ? pushTicks + 1 : Math.max(0, pushTicks - 1);
        double dy = t.getY() - mob.getY();
        double horizontal = Math.sqrt(Mth.square(t.getX() - mob.getX()) + Mth.square(t.getZ() - mob.getZ()));
        BlockPos feet = mob.blockPosition();
        BlockPos pick = null;
        boolean stalled = attackStall > 40 && !mob.isClimbing();
        if (dy < -1.5 && mob.onGround() && (horizontal < 1.5 || stalled && horizontal < 3)) {
            pick = solid(feet.below()) ? feet.below() : null;          // you are under it: dig down
        } else if (dy > 1.5 && !mob.isClimbing() && (horizontal < 1.5 || stalled && horizontal < 3)) {
            pick = solid(feet.above(2)) ? feet.above(2) : null;        // you are above a ceiling: dig up
        }
        if (pick == null && (pushTicks > 20 || stalled)) {
            Direction dir = Direction.getNearest(t.getX() - mob.getX(), 0, t.getZ() - mob.getZ());
            BlockPos front = feet.relative(dir);
            // tunnel two high toward you, or down at an angle if you are below
            pick = solid(front.above()) ? front.above() : solid(front) ? front
                    : dy < -1 && solid(front.below()) ? front.below() : null;
        }
        if (pick != null && canBreak(pick)) {
            float hardness = level.getBlockState(pick).getDestroySpeed(level, pick);
            breaking = pick;
            breakProgress = 0;
            breakTotal = (int) Mth.clamp(15 + hardness * 12, 15, 120);
        }
    }

    private boolean solid(BlockPos pos) {
        BlockState s = level().getBlockState(pos);
        return !s.isAir() && !s.getCollisionShape(level(), pos).isEmpty();
    }

    /** Never deepslate/obsidian/bedrock (tag), nothing as hard as obsidian, nothing holding items or data. */
    public boolean canBreak(BlockPos pos) {
        ServerLevel level = level();
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || s.is(ModTags.UNBREAKABLE) || !s.getFluidState().isEmpty()) {
            return false;
        }
        for (Direction d : Direction.values()) {
            if (!level.getFluidState(pos.relative(d)).isEmpty()) {
                return false; // breaking it would let lava or water pour in on you
            }
        }
        float hardness = s.getDestroySpeed(level, pos);
        if (hardness < 0 || hardness >= 50.0F || level.getBlockEntity(pos) != null) {
            return false;
        }
        return ForgeHooks.canEntityDestroy(level, pos, mob);
    }

    private FogLurkerSavedData savedData() {
        return FogLurkerSavedData.get(server());
    }

    // ------------------------------------------------------------------ the night meeting (Deathwish)

    public static final int MEETING_HELLO = 20;
    public static final int MEETING_DONT_RUN = 70;
    public static final int MEETING_WONT_HURT = 120;
    public static final int MEETING_KIDDING = 170;
    public static final int MEETING_RUN = 200;
    /** "RUN!" ... and 5 seconds later it comes. */
    public static final int MEETING_CHASE = MEETING_RUN + 100;
    /** If it meant it, it leaves this long after its last word. */
    public static final int MEETING_LEAVE = 220;
    /** How long you are left alone after getting away from the chase. */
    public static final long MEETING_IMMUNITY_TICKS = 15L * 60 * 20;
    /** The chase has started (so getting away from it counts). */
    private boolean meetingChase;

    /** The meeting's lines already said (a bit per line), so each is said once even if a tick was skipped. */
    private int meetingSaid;

    /** True once: the first tick at or after {@code at} in the meeting. */
    private boolean due(int at) {
        int bit = switch (at) {
            case MEETING_HELLO -> 1;
            case MEETING_DONT_RUN -> 2;
            case MEETING_WONT_HURT -> 4;
            case MEETING_KIDDING -> 8;
            default -> 16;
        };
        if (phaseAge >= at && (meetingSaid & bit) == 0) {
            meetingSaid |= bit;
            return true;
        }
        return false;
    }

    private void nightMeeting(ServerPlayer t, double dist) {
        fog(t, 0.6F);
        halt();
        mob.setLying(false);
        stare(t);
        if (due(MEETING_HELLO)) {
            meetingSay("hello " + t.getGameProfile().getName());
        } else if (due(MEETING_DONT_RUN)) {
            meetingSay("please dont run");
        } else if (due(MEETING_WONT_HURT)) {
            meetingSay("i will not hurt you");
        }
        if (!enc.willAttack() || !canAttack(t)) {
            if (phaseAge >= MEETING_LEAVE) {
                meetingChase = false; // (e.g. switched to Peaceful after "RUN!": nobody outran anything)
                vanish(false); // it meant it this time
            }
            return;
        }
        if (due(MEETING_KIDDING)) {
            meetingSay("just kidding");
        } else if (due(MEETING_RUN)) {
            meetingSay("RUN!");
            meetingChase = true; // from here on, getting away (outrunning it, a bell, a scare burst) earns the peace
            t.displayClientMessage(Component.literal("RUN!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
            mob.playSound(ModSounds.SCREAM.get(), 2.0F, 0.6F);
            ModNetwork.send(t, new ScarePacket(60));
        } else if (phaseAge >= MEETING_CHASE) {
            huntNow(); // 5 seconds are up
        }
    }

    /** It speaks as itself, "fogstalker", to everyone close enough to hear. */
    private void meetingSay(String text) {
        Component line = Component.literal("fogstalker: ").withStyle(ChatFormatting.DARK_RED)
                .append(Component.literal(text).withStyle(text.equals("RUN!")
                        ? new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD} : new ChatFormatting[]{ChatFormatting.GRAY}));
        for (ServerPlayer p : level().players()) {
            if (p.distanceTo(mob) < 32) {
                p.sendSystemMessage(line);
            }
        }
        mob.playSound(ModSounds.WHISPER.get(), 1.2F, 0.7F);
    }

    /** Until this age it cannot bite (a Deathwish wind-up after switching to someone new). */
    private int windupUntil = -1;

    /** Ticks at the start of a Deathwish attack in which it cannot bite yet. */
    public static final int DEATHWISH_WINDUP = 30;

    /** The target got away from an attack: it remembers, and is a little more likely to attack next time. */
    private void noteEscape(@Nullable ServerPlayer t) {
        if (t == null || escaped || !FogLurkerConfig.COMMON.escapeEscalation.get()) {
            return;
        }
        escaped = true;
        PlayerBehaviorProfile p = savedData().profile(t.getUUID());
        p.escapes = Math.min(p.escapes + 1, 10);
        savedData().setDirty();
    }

    /**
     * The world is closing (save and quit, or the server stops) during this visit. For its target that is the
     * same as logging out: a hunt counts as getting away. Runs before the final save.
     */
    public void onServerStopping() {
        if ((phase == Phase.ATTACK || phase == Phase.TRANSFORM || meetingChase) && !escaped
                && FogLurkerConfig.COMMON.escapeEscalation.get()) {
            escaped = true;
            PlayerBehaviorProfile gone = savedData().profile(targetId());
            gone.escapes = Math.min(gone.escapes + 1, 10);
            savedData().setDirty();
        }
    }

    /** Skips straight to hunting in its attacking form (used by tests). */
    public void huntNow() {
        mob.setDisguise(Disguise.CRAWLER);
        mob.clearEquipment();
        mob.setAggressive(true);
        setPhase(Phase.ATTACK);
    }

    /** Distance that counts for "close enough to reveal": height does not help you if you only towered up. */
    private double revealDistance(ServerPlayer t, double dist) {
        double dy = t.getY() - mob.getY();
        if (dy > 2) {
            return Math.sqrt(Mth.square(t.getX() - mob.getX()) + Mth.square(t.getZ() - mob.getZ()));
        }
        return dist;
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
        if (mob.isInWater()) {
            setPhase(Phase.FLEE); // it will not show its real shape in the water
        } else if (t != null && enc.willAttack() && canAttack(t) && canRevealNow(t)) {
            setPhase(Phase.TRANSFORM);
        } else {
            // Its skin slips for a moment, then it runs.
            mob.playSound(ModSounds.SCREAM.get(), 2.0F, 1.0F);
            mob.setDisguise(Disguise.CRAWLER);
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
        if (player.getUUID().equals(enc.targetId()) && enc.willAttack() && canAttack(player) && canRevealNow(player)
                && phase != Phase.TRANSFORM && phase != Phase.ATTACK && phase != Phase.FLEE) {
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
        LurkerDirector.noteLeft(targetId()); // at once: a flop lies there a moment, and nothing else may roll onto them
        if (meetingChase) {
            ServerPlayer survivor = target();
            // Only someone it could really have caught got away: not after a switch to Peaceful, Creative or AFK.
            if (survivor != null && survivor.isAlive() && canAttack(survivor)) {
                LurkerDirector.leaveAlone(survivor, MEETING_IMMUNITY_TICKS); // you outran it
                survivor.displayClientMessage(Component.literal("You got away. It will leave you alone... for now.")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            }
        }
        ServerLevel level = level();
        if (!screaming && mob.isAttackForm() && mob.onGround() && !mob.isInWater()) {
            // Spotted in its real shape: it does not run. It flops flat on the ground like a pancake.
            VoiceBridge.stop(mob);
            if (breaking != null) {
                level.destroyBlockProgress(mob.getId(), breaking, -1);
            }
            if (fakeMine != null) {
                level.destroyBlockProgress(mob.getId() + 1_000_000, fakeMine, -1); // no cracks left behind
                fakeMine = null;
            }
            ServerPlayer seen = target();
            if (seen != null) {
                ModNetwork.send(seen, new FogPacket(0F));
            }
            clearFog();
            mob.playSound(ModSounds.WHISPER.get(), 0.8F, 0.6F);
            mob.beginFlop();
            return;
        }
        double h = mob.getBbHeight();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + h / 2, mob.getZ(),
                40, mob.getBbWidth() / 2, h / 2, mob.getBbWidth() / 2, 0.02);
        level.sendParticles(ParticleTypes.SQUID_INK, mob.getX(), mob.getY() + h / 2, mob.getZ(),
                12, mob.getBbWidth() / 2, h / 2, mob.getBbWidth() / 2, 0.01);
        mob.playSound(screaming ? ModSounds.SCREAM.get() : ModSounds.VANISH.get(), 1.5F, 0.8F);
        VoiceBridge.stop(mob);
        if (breaking != null) {
            level.destroyBlockProgress(mob.getId(), breaking, -1);
        }
        if (fakeMine != null) {
            level.destroyBlockProgress(mob.getId() + 1_000_000, fakeMine, -1);
        }
        ServerPlayer t = target();
        if (t != null) {
            ModNetwork.send(t, new FogPacket(0F));
        }
        clearFog();
        mob.discard();
    }

    // =================================================================== helpers

    /**
     * If it has not got any closer for 10 seconds (a fence, a cliff, a river it will not cross), it
     * slips to a spot closer to you, but only when neither spot can be seen.
     */
    private void watchProgress(ServerPlayer t, double dist) {
        if (dist < bestApproach - 1.0) {
            bestApproach = dist;
            noProgress = 0;
            return;
        }
        // With no path at all it gives up on the current route twice as fast.
        noProgress += mob.getNavigation().isDone() ? 2 : 1;
        if (noProgress < 200) {
            return;
        }
        noProgress = 0;
        bestApproach = Double.MAX_VALUE;
        if (SpotFinder.canSee(t, mob.getEyePosition())) {
            // You are watching, so no tricks: walk around the obstacle, and if that fails too,
            // fade into the fog and come back another time.
            if (detours++ >= 2) {
                vanish(false);
                return;
            }
            Vec3 to = t.position().subtract(mob.position()).multiply(1, 0, 1).normalize();
            Vec3 side = new Vec3(-to.z, 0, to.x).scale(random.nextBoolean() ? 8 : -8).add(to.scale(4));
            BlockPos g = SpotFinder.ground(level(), Mth.floor(mob.getX() + side.x), Mth.floor(mob.getZ() + side.z), mob.getBlockY());
            detour = g == null ? null : Vec3.atBottomCenterOf(g);
            return;
        }
        double near = Math.max(6, Math.min(14, dist - 4));
        Vec3 spot = SpotFinder.findSpawn(level(), t, near, near + 6, mob.position().subtract(t.position()), true, false, random);
        if (spot != null && !SpotFinder.canSee(t, spot.add(0, 1.5, 0)) && spot.distanceTo(t.position()) < dist) {
            mob.teleportTo(spot.x, spot.y, spot.z);
            mob.getNavigation().stop();
            lastRepath = -100;
        } else if (detours++ >= 2) {
            vanish(false); // nowhere to go: it never just stands there forever
        }
    }

    /** It knows its weakness: it never sheds its swimming-capable skin while you or it are in water. */
    private boolean canRevealNow(ServerPlayer t) {
        return !mob.isInWater() && !SpotFinder.isInOrOnWater(t) && !isWarded(t);
    }

    /** Per player: [age checked, 1 if warded]. Checking a bystander does not throw away the target's answer. */
    private final java.util.Map<UUID, int[]> wardCache = new java.util.HashMap<>();

    /** Is the target standing by soul fire? Checked once a second. */
    private boolean isWarded(ServerPlayer t) {
        int[] c = wardCache.get(t.getUUID());
        if (c == null || age - c[0] >= 20) {
            c = new int[]{age, Wards.isWarded(t) ? 1 : 0};
            wardCache.put(t.getUUID(), c);
        }
        return c[1] == 1;
    }

    /** Wider than "staring straight at it": anything within about 30 degrees of where you look counts. */
    private boolean isWatchedBy(ServerPlayer p) {
        Vec3 eyePos = p.getEyePosition();
        Vec3 to = new Vec3(mob.getX(), mob.getY(0.5), mob.getZ()).subtract(eyePos);
        double len = to.length();
        if (len > 64 || len < 1e-3) {
            return false;
        }
        return p.getViewVector(1F).dot(to.scale(1 / len)) > 0.85 && p.hasLineOfSight(mob);
    }

    /** A line written for one victim is never said to another (it quotes them and uses their name). */
    private void forgetChatForVictimChange() {
        pendingChat = null;
        queuedIntent = null;
        heardPending = null;
        chatGen++;
    }

    /** Sent away by an admin command: it leaves, but nobody "got away" from it. */
    public void dismiss() {
        dismiss(false);
    }

    /** As {@link #dismiss()}, screaming (/kill). */
    public void dismiss(boolean screaming) {
        meetingChase = false;
        vanish(screaming);
    }

    /** True once it has left (vanishing, flopping away or dead): nothing reaches it any more. */
    public boolean isEnded() {
        return ended;
    }

    /** Someone else killed one of its kind: it leaves too, and nobody "got away" from it. */
    public void leaveBecauseKilled() {
        meetingChase = false;
        vanish(true);
    }

    /**
     * Hit by a scare burst from The Protector of the Fog.
     *
     * @param flee true: it shrieks and runs; false: it holds on, weakened (slower, weaker bites, bites less
     *             often, and it glows so you can see where it is) for 15 seconds
     */
    public void onScareBurst(boolean flee) {
        if (ended) {
            return;
        }
        if (flee) {
            mob.playSound(ModSounds.SCREAM.get(), 2.0F, 1.5F);
            if (phase == Phase.TRANSFORM) {
                mob.setFlag(FogLurkerEntity.FLAG_TRANSFORMING | FogLurkerEntity.FLAG_ANGRY | FogLurkerEntity.FLAG_SITTING, false);
                mob.setDisguise(Disguise.CRAWLER);
                mob.clearEquipment();
            }
            if (phase != Phase.FLEE) {
                setPhase(Phase.FLEE);
            }
            return;
        }
        mob.playSound(ModSounds.HURT.get(), 1.5F, 0.6F);
        mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 300, 1));
        mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 300, 0));
        mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 300, 0));
    }

    /** It died (a player killed it): tidy up everything it left in the world. */
    public void onDeath() {
        if (ended) {
            return;
        }
        ended = true;
        VoiceBridge.stop(mob);
        if (breaking != null) {
            level().destroyBlockProgress(mob.getId(), breaking, -1);
        }
        clearFakeMine();
        clearFog();
    }

    /** A bell rung nearby: whatever it was doing, it runs. */
    public void onBell() {
        if (ended || phase == Phase.FLEE) {
            return;
        }
        if (phase == Phase.ATTACK || phase == Phase.TRANSFORM || meetingChase) { // (a bell in the RUN! head start too)
            noteEscape(target());
        }
        mob.playSound(ModSounds.SCREAM.get(), 2.0F, 1.3F);
        if (phase == Phase.TRANSFORM) {
            mob.setFlag(FogLurkerEntity.FLAG_TRANSFORMING | FogLurkerEntity.FLAG_ANGRY | FogLurkerEntity.FLAG_SITTING, false);
            mob.setDisguise(Disguise.CRAWLER);
            mob.clearEquipment();
        }
        setPhase(Phase.FLEE);
    }

    /**
     * The attacking form cannot swim. In water it sinks, thrashes toward the shore, takes damage,
     * and after three seconds the water drives it away.
     *
     * @return true if it was driven off this tick
     */
    private boolean suffersWater() {
        if (!mob.isAttackForm() || !mob.isInWater()) {
            waterTicks = 0;
            return false;
        }
        waterTicks++;
        if (waterTicks % 10 == 1) {
            level().sendParticles(ParticleTypes.SPLASH, mob.getX(), mob.getY() + 0.5, mob.getZ(), 20, 0.5, 0.3, 0.5, 0.2);
            mob.playSound(ModSounds.HURT.get(), 1.2F, 0.5F + random.nextFloat() * 0.3F);
        }
        if (waterTicks % 20 == 1) {
            Vec3 shore = SpotFinder.nearestDryGround(level(), mob.position(), mob.position(), 10);
            if (shore != null) {
                mob.getMoveControl().setWantedPosition(shore.x, shore.y, shore.z, speedFor(3.0));
            }
        }
        if (waterTicks % 20 == 0) {
            mob.hurt(mob.damageSources().generic(), 4.0F);
        }
        if (!ended && waterTicks > 60) {
            vanish(true);
        }
        return ended;
    }

    private boolean canAttack(ServerPlayer t) {
        return !t.isCreative() && !t.isSpectator() && t.isAlive()
                && mob.level().getDifficulty() != Difficulty.PEACEFUL
                && !com.foglurker.world.AfkTracker.isAfk(t); // never someone who is away from the keyboard
    }

    /**
     * In a group it goes for whoever is easiest: someone much closer to it than its target, or someone
     * clearly weaker (3+ hearts less) who is not much further away. Only players it could actually reach
     * (not in water, not inside a soul-fire ward) and who are with its target (within 16 blocks) count.
     *
     * @return the player to switch to, or null to keep going
     */
    @Nullable
    private ServerPlayer easierPrey(ServerPlayer t, double dist) {
        if (!switchesTargets || enc.mode() == Mode.NIGHT_MEETING || age % 10 != 0 || age - lastSwitch < 60) {
            return null;
        }
        float targetHp = t.getHealth() + t.getAbsorptionAmount();
        ServerPlayer best = null;
        double bestScore = Double.MAX_VALUE;
        for (ServerPlayer p : level().players()) {
            if (p == t || !canAttack(p) || p.isCreative() || p.distanceTo(t) > 16
                    || p.getUUID().equals(enc.copiedId()) // never the friend whose face it is wearing
                    || SpotFinder.isInOrOnWater(p) || isWarded(p)
                    || LurkerDirector.isHunted(p.getUUID()) // another one already has them: never two at once
                    || LurkerDirector.leftAloneTicks(p) > 0 // they earned 15 minutes of peace
                    || (p.getUUID().equals(leftId) && age - leftAt < 1200)) { // not straight back to the one it left
                continue;
            }
            double d = mob.distanceTo(p);
            float hp = p.getHealth() + p.getAbsorptionAmount();
            boolean muchCloser = d < 10 && d < dist - 4;
            boolean muchWeaker = hp <= targetHp - 6F && d < 16 && d < dist + 4;
            if (!muchCloser && !muchWeaker) {
                continue;
            }
            double score = d + hp * 0.75; // near and hurt is best
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    private boolean isolated(ServerPlayer t) {
        for (Player other : mob.level().players()) {
            // (an AFK player or a fake player is no company)
            if (other != t && !other.isSpectator() && !(other instanceof net.minecraftforge.common.util.FakePlayer)
                    && !(other instanceof ServerPlayer sp && com.foglurker.world.AfkTracker.isAfk(sp)) && other.distanceTo(t) < 24) {
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
     * Speed modifier for a ground speed in blocks/s. Square root on purpose: Mob.setSpeed also sets the mob's
     * forward input (setZza), so vanilla mob ground speed grows with the square of the speed value. 43.17 is
     * vanilla's walk: blocks/s = 43.17 x speed x input. A player walks with speed 0.1 and input 1.0 (4.317 blocks/s);
     * a mob's input equals its speed, so for a mob blocks/s = 43.17 x speed squared.
     */
    private double speedFor(double blocksPerSecond) {
        double attr = mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return Math.sqrt(blocksPerSecond / 43.17) / Math.max(0.01, attr);
    }

    private void goTo(Entity e, double speed) {
        if (detour != null) {
            if (mob.position().distanceToSqr(detour) < 2.0 || noProgress > 120) {
                detour = null;
            } else {
                goTo(detour, speed);
                return;
            }
        }
        // Re-plan at most every 10 ticks while a path runs, and every 5 when none does (a target it cannot
        // reach would otherwise cost a full path search every tick).
        if (mob.distanceTo(e) > 40) {
            goTo(e.position(), speed); // in hops; that method throttles itself (setting lastRepath here would block it)
            return;
        }
        if (age - lastRepath < (mob.getNavigation().isInProgress() ? 10 : 5)) {
            mob.getNavigation().setSpeedModifier(speed);
            return;
        }
        lastRepath = age;
        if (!mob.getNavigation().moveTo(e, speed)) {
            mob.getMoveControl().setWantedPosition(e.getX(), e.getY(), e.getZ(), speed);
        }
    }

    private void goTo(Vec3 pos, double speed) {
        // Re-plan at most every 10 ticks while a path runs, and every 5 when none does (a target it cannot
        // reach would otherwise cost a full path search every tick).
        if (age - lastRepath < (mob.getNavigation().isInProgress() ? 10 : 5)) {
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

    /** How thick the fog should be for its target right now (set by whatever it is doing). */
    private float fogLevel;
    /** What was last sent, and to whom. */
    private float fogSent = -1F;
    @Nullable
    private UUID fogSentTo;

    /**
     * Sets the fog for what it is doing now. A big change (it attacks) goes out at once; otherwise
     * {@link #keepFogAlive} repeats it every second, whatever it is doing.
     */
    private void fog(ServerPlayer t, float intensity) {
        fogLevel = intensity;
        if (Math.abs(nightFog(intensity) - fogSent) >= 0.2F || !t.getUUID().equals(fogSentTo)) {
            sendFog(t);
        }
    }

    private float nightFog(float intensity) {
        return mob.level().isNight() ? Math.min(1F, intensity * difficulty().nightFogMultiplier()) : intensity; // thicker at night
    }

    private void sendFog(ServerPlayer t) {
        float f = nightFog(fogLevel);
        ModNetwork.send(t, new FogPacket(f));
        fogSent = f;
        fogSentTo = t.getUUID();
        fogged.add(t.getUUID());
    }

    /** Every second, even while it thrashes in water or does something without its own fog. */
    private void keepFogAlive(ServerPlayer t) {
        if (age % 20 == 1 && fogLevel > 0F) {
            sendFog(t);
        }
    }

    /** Clears the fog for everyone it hunted this time (not for anyone another creature is still fogging). */
    private void clearFog() {
        for (UUID id : fogged) {
            ServerPlayer p = server().getPlayerList().getPlayer(id);
            if (p != null && !LurkerDirector.foggedByAnother(id, this)) {
                ModNetwork.send(p, new FogPacket(0F));
            }
        }
        fogged.clear();
    }

    /** With an empty hand it holds whatever the real player holds most (their pickaxe, their sword...). */
    private void holdFavouriteIfEmpty() {
        if (copiedProfile == null || !mob.getMainHandItem().isEmpty()) {
            return;
        }
        String id = copiedProfile.favoriteItem();
        ResourceLocation key = id == null ? null : ResourceLocation.tryParse(id);
        Item item = key == null ? null : ForgeRegistries.ITEMS.getValue(key);
        if (item != null && item != Items.AIR) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
        }
    }

    /**
     * While it waits for you it "mines" a block like a player killing time, cracks and all, then
     * stops halfway, goes completely still, and stares at you.
     *
     * @param cycle position in a 300 tick cycle (negative = never)
     * @return true while it is busy mining and should not be turned to face you
     */
    private boolean pretendMine(int cycle) {
        if (cycle < 0 || cycle < 150 || cycle > 215) {
            if (fakeMine != null) {
                level().destroyBlockProgress(mob.getId() + 1_000_000, fakeMine, -1);
                fakeMine = null;
            }
            return false;
        }
        if (fakeMine == null) {
            BlockPos feet = mob.blockPosition();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                for (int dy = 1; dy >= -1; dy--) {
                    BlockPos p = feet.relative(d).above(dy);
                    if (solid(p) && level().getBlockEntity(p) == null) {
                        fakeMine = p;
                        break;
                    }
                }
                if (fakeMine != null) {
                    break;
                }
            }
            if (fakeMine == null) {
                return false;
            }
        }
        int t = cycle - 150;
        if (t < 45) {
            mob.getLookControl().setLookAt(Vec3.atCenterOf(fakeMine));
            if (t % 5 == 0) {
                mob.swing(InteractionHand.MAIN_HAND);
            }
            if (t % 8 == 0) {
                level().destroyBlockProgress(mob.getId() + 1_000_000, fakeMine, Math.min(5, t / 8)); // only when the stage changes
            }
            return true;
        }
        // ...then it just stops, and looks at you.
        return false;
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

    /** The victim it last switched away from, and when: it does not choose to switch straight back for a minute
     * (if the one it switched to disappears, it does go back to its first victim). */
    @Nullable
    private UUID leftId;
    private int leftAt;

    /** A planned line that came while another was still being written; said as soon as that one is out. */
    @Nullable
    private MimicChat.Intent queuedIntent;

    /** Bumped when the mask comes off or it switches victim: chat written before that is never sent. */
    private int chatGen;

    private void say(ServerPlayer t, MimicChat.Intent intent, @Nullable String heard) {
        if (!FogLurkerConfig.COMMON.impersonateInChat.get() || enc.copiedName() == null) {
            return;
        }
        if (chatInFlight || pendingChat != null) {
            if (heard == null) {
                queuedIntent = intent; // not lost: said after the current line
            }
            return;
        }
        chatInFlight = true;
        int gen = chatGen;
        MimicChat.Context ctx = new MimicChat.Context(enc.copiedName(), t.getGameProfile().getName(), copiedProfile,
                woodsDir, woodsHasTrees, mob.level().isNight(), List.copyOf(said));
        MinecraftServer server = server();
        // Their words go to Gemini only if the server keeps chat at all (learnChat) and they did not opt out.
        boolean shareHeard = heard == null
                || (FogLurkerConfig.COMMON.learnChat.get() && !savedData().profile(t.getUUID()).chatLearningOff);
        MimicChat.compose(ctx, intent, heard, shareHeard, random).whenComplete((text, err) -> server.execute(() -> {
            chatInFlight = false;
            if (err != null || text == null || text.isBlank() || ended || gen != chatGen) {
                return; // failed, or written for a moment that has passed (it revealed itself or changed victim)
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
        if (!FogLurkerConfig.COMMON.impersonateInChat.get()) {
            return; // turned off while the line was being written
        }
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
        // Remembered as context for the next line, unless the copied player keeps their chat out of it.
        PlayerBehaviorProfile copiedProf = enc.copiedId() == null ? null : savedData().profileOrNull(enc.copiedId());
        if (FogLurkerConfig.COMMON.learnChat.get() && (copiedProf == null || !copiedProf.chatLearningOff)) {
            RecentChat.add(enc.copiedName(), text);
        }
    }
}
