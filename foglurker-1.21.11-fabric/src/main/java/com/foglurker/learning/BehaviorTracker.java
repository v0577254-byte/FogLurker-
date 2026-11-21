package com.foglurker.learning;

import com.foglurker.FogLurkerMod;
import com.foglurker.brain.LurkerDirector;
import com.foglurker.config.FogLurkerConfig;
import com.foglurker.entity.Disguise;
import com.foglurker.world.FogLurkerSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Silently studies every player (movement, crouch spam, bunny hopping, look-around, held items,
 * chat style, their dog) and every nearby animal, so the creature can copy them convincingly.
 */
public final class BehaviorTracker {
    private static final int WINDOW_TICKS = 1200;
    private static final Map<UUID, Window> WINDOWS = new HashMap<>();

    public static void onServerStopped() {
        WINDOWS.clear();
        WATCHED.clear(); // mob ids and positions from this world mean nothing in the next one
    }
    private static final Map<UUID, Watch> WATCHED = new HashMap<>();

    private BehaviorTracker() {
    }

    private static final class Window {
        int ticks, moving, sprint, sneak, idle, jumps, sneakToggles, nearTicks, nearSneakToggles, swings, chats;
        int yawSamples, friendSamples;
        float yawSum, friendDistSum;
        double lastX, lastY, lastZ;
        float lastYaw;
        boolean init, lastOnGround, lastSneak, lastSwinging, nearFriend;
        float nearestFriend = Float.MAX_VALUE;
    }

    private static final class Watch {
        Vec3 lastPos;
        long lastSeen;
        int idleSamples;
    }

    /** Every server player tick, at the end (ModEvents). */
    public static void onPlayerTick(ServerPlayer p) {
        if (p instanceof FakePlayer || p.isSpectator()) {
            return;
        }
        Window w = WINDOWS.computeIfAbsent(p.getUUID(), k -> new Window());
        double dx = p.getX() - w.lastX;
        double dy = p.getY() - w.lastY;
        double dz = p.getZ() - w.lastZ;
        if (!w.init) {
            w.init = true;
            dx = dy = dz = 0;
            w.lastYaw = p.getYHeadRot();
        }
        double horiz = Math.sqrt(dx * dx + dz * dz);
        boolean moving = horiz > 0.03 && horiz < 2.0;
        w.ticks++;
        if (moving) {
            w.moving++;
            if (p.isSprinting()) {
                w.sprint++;
            }
        } else {
            w.idle++;
            float dyaw = Math.abs(Mth.wrapDegrees(p.getYHeadRot() - w.lastYaw));
            if (dyaw < 60) {
                w.yawSum += dyaw;
                w.yawSamples++;
            }
        }
        boolean sneaking = p.isShiftKeyDown();
        if (sneaking) {
            w.sneak++;
        }
        if (sneaking != w.lastSneak) {
            w.sneakToggles++;
            if (w.nearFriend) {
                w.nearSneakToggles++;
            }
        }
        if (w.lastOnGround && !p.onGround() && dy > 0.2 && moving) {
            w.jumps++;
        }
        if (p.swinging && !w.lastSwinging && horiz <= 0.03) { // idle swings: standing about (not walking, flying or riding)
            w.swings++;
        }

        if (w.ticks % 10 == 0) {
            float nearest = Float.MAX_VALUE;
            for (Player other : p.level().players()) {
                if (other != p && !other.isSpectator()) {
                    nearest = Math.min(nearest, other.distanceTo(p));
                }
            }
            w.nearFriend = nearest < 10F;
            w.nearestFriend = nearest;
            if (w.nearFriend) {
                w.friendDistSum += nearest;
                w.friendSamples++;
            }
        }
        if (w.nearFriend) {
            w.nearTicks++;
        }

        if (w.ticks % 20 == 0) {
            ItemStack held = p.getMainHandItem();
            if (!held.isEmpty()) {
                var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem());
                if (key != null) {
                    FogLurkerSavedData.get(p.level().getServer()).profile(p.getUUID()).bumpHeldItem(key.toString(), 1F);
                }
            }
        }
        if ((w.ticks + (p.getId() & 31)) % 40 == 0 && p.level() instanceof ServerLevel level) {
            watchEntities(level, p);
        }

        w.lastX = p.getX();
        w.lastY = p.getY();
        w.lastZ = p.getZ();
        w.lastYaw = p.getYHeadRot();
        w.lastOnGround = p.onGround();
        w.lastSneak = sneaking;
        w.lastSwinging = p.swinging;

        if (w.ticks >= WINDOW_TICKS) {
            commit(p, w);
            WINDOWS.put(p.getUUID(), carryOver(w));
        }
    }

    private static Window carryOver(Window old) {
        Window w = new Window();
        w.init = true;
        w.lastX = old.lastX;
        w.lastY = old.lastY;
        w.lastZ = old.lastZ;
        w.lastYaw = old.lastYaw;
        w.lastOnGround = old.lastOnGround;
        w.lastSneak = old.lastSneak;
        w.lastSwinging = old.lastSwinging;
        w.nearFriend = old.nearFriend;
        return w;
    }

    private static float ema(float current, float sample, float alpha) {
        return current + (sample - current) * alpha;
    }

    private static void commit(ServerPlayer p, Window w) {
        FogLurkerSavedData data = FogLurkerSavedData.get(p.level().getServer());
        PlayerBehaviorProfile prof = data.profile(p.getUUID());
        float a = prof.windows < 5 ? 0.35F : 0.15F;
        prof.lastKnownName = p.getGameProfile().name();
        float seconds = w.ticks / 20F;
        if (w.moving > 40) {
            prof.sprintRatio = ema(prof.sprintRatio, w.sprint / (float) w.moving, a);
            prof.jumpRate = ema(prof.jumpRate, w.jumps / (w.moving / 20F), a);
        }
        prof.sneakRatio = ema(prof.sneakRatio, w.sneak / (float) w.ticks, a);
        prof.idleRatio = ema(prof.idleRatio, w.idle / (float) w.ticks, a);
        if (w.nearTicks > 100) {
            prof.crouchSpamRate = ema(prof.crouchSpamRate, w.nearSneakToggles / (w.nearTicks / 20F), a);
        }
        if (w.friendSamples > 5) {
            prof.friendDistance = ema(prof.friendDistance, Mth.clamp(w.friendDistSum / w.friendSamples, 1.5F, 8F), a);
        }
        if (w.yawSamples > 40) {
            prof.lookJitter = ema(prof.lookJitter, w.yawSum / w.yawSamples, a);
        }
        prof.idleSwingRate = ema(prof.idleSwingRate, Math.min(2F, w.swings / seconds), a);
        prof.chatRate = ema(prof.chatRate, w.chats / (seconds / 60F), a);
        prof.windows++;
        prof.lastSeenMillis = System.currentTimeMillis();
        data.setDirty();
    }

    private static void watchEntities(ServerLevel level, ServerPlayer p) {
        FogLurkerSavedData data = FogLurkerSavedData.get(p.level().getServer());
        EntityBehaviorStats stats = data.entityStats();
        long now = level.getGameTime();
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24),
                m -> m.isAlive() && Disguise.of(m) != null);
        // Their own dogs first, so a dog in a crowded farm is never crowded out of the 8 it looks at (one pass, no sort).
        List<Mob> ordered = new java.util.ArrayList<>(Math.min(mobs.size(), 16));
        List<Mob> others = new java.util.ArrayList<>(8);
        for (Mob m : mobs) {
            if (m instanceof Wolf w && w.isTame() && p.getUUID().equals((w.getOwnerReference() == null ? null : w.getOwnerReference().getUUID()))) {
                ordered.add(m);
            } else if (others.size() < 8) {
                others.add(m);
            }
        }
        ordered.addAll(others);
        mobs = ordered;
        int n = 0;
        for (Mob m : mobs) {
            if (n++ >= 8) {
                break;
            }
            Disguise kind = Disguise.of(m);
            Watch watch = WATCHED.computeIfAbsent(m.getUUID(), k -> new Watch());
            Vec3 pos = m.position();
            if (watch.lastPos != null && now - watch.lastSeen > 0 && now - watch.lastSeen < 200) {
                float secs = (now - watch.lastSeen) / 20F;
                double dist = pos.subtract(watch.lastPos).horizontalDistance();
                float speed = (float) (dist / secs);
                boolean moving = speed > 0.12F;
                float finishedIdle = 0;
                if (moving) {
                    if (watch.idleSamples > 0) {
                        finishedIdle = watch.idleSamples * secs;
                    }
                    watch.idleSamples = 0;
                } else {
                    watch.idleSamples++;
                }
                double ddx = p.getX() - m.getX();
                double ddz = p.getZ() - m.getZ();
                float yawTo = (float) (Mth.atan2(ddz, ddx) * (180F / Math.PI)) - 90F;
                boolean looking = Math.abs(Mth.wrapDegrees(yawTo - m.getYHeadRot())) < 25F
                        && m.distanceToSqr(p) < 16 * 16;
                stats.observe(kind.learnKey(), moving, speed, looking, finishedIdle);
            }
            watch.lastPos = pos;
            watch.lastSeen = now;

            if (m instanceof Wolf wolf && wolf.isTame() && p.getUUID().equals((wolf.getOwnerReference() == null ? null : wolf.getOwnerReference().getUUID()))) {
                PlayerBehaviorProfile prof = data.profile(p.getUUID());
                boolean named = wolf.hasCustomName();
                // Prefer remembering a named dog over unnamed ones.
                // A remembered dog not seen with them for a whole day (dead, given away) gives way to the one here.
                // Not just "not loaded": a dog left at home in another dimension must not flip back and forth.
                long dayClock = p.level().getServer().overworld().getGameTime();
                if (prof.dogSeenAt == 0) {
                    prof.dogSeenAt = dayClock; // a save from before this was tracked: start the clock now
                }
                boolean rememberedGone = prof.dogUuid != null && !prof.dogUuid.equals(wolf.getUUID())
                        && !(((net.minecraft.server.level.ServerLevel) wolf.level()).getEntity(prof.dogUuid) instanceof Wolf)
                        && dayClock - prof.dogSeenAt > 24000;
                // Replace only when there is no dog yet, it is the same dog, the old one is gone, or a named dog
                // turns up while the remembered one has no name (named dogs win; two dogs never swap back and forth).
                boolean same = wolf.getUUID().equals(prof.dogUuid);
                if (same) {
                    prof.dogSeenAt = dayClock;
                }
                if (prof.dogUuid == null || same || rememberedGone || (named && prof.dogName == null)) {
                    prof.dogSeenAt = dayClock;
                    prof.dogUuid = wolf.getUUID();
                    prof.dogName = named ? wolf.getCustomName().getString() : null;
                    prof.dogCollar = wolf.getCollarColor().getId();
                    prof.dogSitRatio = ema(prof.dogSitRatio, wolf.isInSittingPose() ? 1F : 0F, 0.1F);
                }
            }
        }
        data.setDirty();
        if (WATCHED.size() > 512) {
            Iterator<Map.Entry<UUID, Watch>> it = WATCHED.entrySet().iterator();
            while (it.hasNext()) {
                if (now - it.next().getValue().lastSeen > 2400) {
                    it.remove();
                }
            }
        }
    }

    /** A chat message that was really sent (after every other mod had its say). */
    public static void onChat(ServerPlayer p, String text) {
        if (p instanceof FakePlayer) {
            return; // automation, not a person: nothing to learn and no profile to make
        }
        // Chat can be decorated off-thread; do all bookkeeping on the server thread.
        p.level().getServer().execute(() -> {
            FogLurkerSavedData data = FogLurkerSavedData.get(p.level().getServer());
            var known = data.profileOrNull(p.getUUID()); // only read here: an opted-out check must not create a profile
            boolean optedOut = known != null && known.chatLearningOff;
            if (!optedOut && FogLurkerConfig.COMMON.learnChat.get()) {
                RecentChat.add(p.getGameProfile().name(), text); // what it shows Gemini as "recent chat"
            }
            Window w = WINDOWS.get(p.getUUID());
            if (w != null) {
                w.chats++;
            }
            if (FogLurkerConfig.COMMON.learnChat.get() && !optedOut) {
                data.profile(p.getUUID()).addChat(text);
                data.setDirty();
            }
            LurkerDirector.onPlayerChat(p, text);
        });
    }

    /** Trying to sleep in a bed (before vanilla's own checks). */
    public static void onSleep(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos) {
        if (player instanceof ServerPlayer p && !(p instanceof FakePlayer) && pos != null) {
            // This event comes before vanilla's own checks (too far, blocked, monsters, daytime). Only a bed that
            // really became theirs counts: they fell asleep in it, or it is now their respawn point.
            net.minecraft.core.BlockPos bed = pos.immutable();
            var cfgBefore = p.getRespawnConfig();
            net.minecraft.core.BlockPos spawnBefore = cfgBefore == null ? null : cfgBefore.respawnData().pos();
            var spawnDimBefore = cfgBefore == null ? null : cfgBefore.respawnData().dimension();
            var server = p.level().getServer();
            server.schedule(new net.minecraft.server.TickTask(server.getTickCount(), () -> {
                boolean theirs = p.isSleeping() && p.getSleepingPos().map(bed::equals).orElse(false)
                        // (or this click just made it their spawn: clicking the bed that already was does not count)
                        || p.getRespawnConfig() != null && bed.equals(p.getRespawnConfig().respawnData().pos())
                        && !(bed.equals(spawnBefore) && spawnDimBefore == p.level().dimension())
                        && p.getRespawnConfig().respawnData().dimension() == p.level().dimension();
                if (!p.hasDisconnected() && theirs) {
                    FogLurkerSavedData data = FogLurkerSavedData.get(server);
                    data.profile(p.getUUID()).sleptAt(p.level().dimension().identifier().toString(), bed);
                    data.setDirty();
                }
            }));
        }
    }

    /** Remembers which storage blocks a player opens with their own hands (a book only ever goes in one of those). */
    public static void onUseBlock(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos) {
        var be = player.level().getBlockEntity(pos);
        // Only storage and furnaces (where a book can go): hoppers and dispensers must not push their chests out of the list.
        if (player instanceof ServerPlayer p && !(p instanceof FakePlayer)
                && (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity
                || be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity
                || be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity)) {
            FogLurkerSavedData data = FogLurkerSavedData.get(p.level().getServer());
            data.profile(p.getUUID()).openedContainer(pos);
            data.setDirty();
        }
    }

    /** A menu opened for a player (ServerPlayerMixin). */
    public static void onContainer(ServerPlayer player, net.minecraft.world.inventory.AbstractContainerMenu menu) {
        // Only storage (chests, barrels, shulker boxes) marks a home: not crafting tables, furnaces or villagers.
        boolean storage = (menu instanceof net.minecraft.world.inventory.ChestMenu chest
                && !(chest.getContainer() instanceof net.minecraft.world.inventory.PlayerEnderChestContainer)) // an ender chest goes anywhere
                || menu instanceof net.minecraft.world.inventory.ShulkerBoxMenu;
        if (storage && player instanceof ServerPlayer p && !(p instanceof FakePlayer)) {
            FogLurkerSavedData data = FogLurkerSavedData.get(p.level().getServer());
            data.profile(p.getUUID()).usedContainerAt(p.level().dimension().identifier().toString(), p.blockPosition());
            data.setDirty();
        }
    }

    public static void onLogout(ServerPlayer player) {
        WINDOWS.remove(player.getUUID());
        // Seen today, even after a visit too short to finish a learning window.
        if (player instanceof ServerPlayer p && !(p instanceof FakePlayer)) {
            FogLurkerSavedData data = FogLurkerSavedData.get(p.level().getServer());
            PlayerBehaviorProfile prof = data.profileOrNull(p.getUUID());
            if (prof != null) {
                prof.lastSeenMillis = System.currentTimeMillis();
                prof.lastKnownName = p.getGameProfile().name();
                data.setDirty();
            }
        }
    }
}
