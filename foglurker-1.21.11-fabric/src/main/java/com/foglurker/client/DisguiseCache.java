package com.foglurker.client;

import com.foglurker.entity.Disguise;
import com.foglurker.entity.FogLurkerEntity;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;


import org.jetbrains.annotations.Nullable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Keeps one invisible "puppet" entity per creature (a cow, a wolf, a copy of your friend...) and
 * moves it in lock-step with the creature, so the real vanilla renderer can draw the disguise.
 */
public final class DisguiseCache {
    /** {@code listed}: the copied player was in the player list when the copy was made (rebuilt when that changes). */
    private record Key(Disguise disguise, @Nullable UUID mimic, int variant, boolean listed) {
    }

    private static final class Holder {
        Key key;
        LivingEntity dummy;
    }

    private static final Map<Integer, Holder> CACHE = new HashMap<>();

    private DisguiseCache() {
    }

    public static void tick(FogLurkerEntity lurker) {
        LivingEntity dummy = get(lurker);
        if (dummy != null) {
            sync(lurker, dummy, true);
        }
    }

    public static void forget(int entityId) {
        CACHE.remove(entityId);
    }

    /** True if the private swim fields can be reached (only affects how a swimming copy looks). */
    public static boolean swimReflectionWorks() {
        return !swimReflectionFailed; // (a mixin accessor now: it always works unless something broke it)
    }

    /** The last full profile (with skin data) seen in the player list, per player, so a copy never flashes a default skin. */
    private static final Map<UUID, GameProfile> KNOWN_PROFILES = new HashMap<>();

    public static void clear() {
        CACHE.clear(); // (KNOWN_PROFILES stays for the whole game session: skins are the same in every world)
    }

    public static void cleanup(ClientLevel level) {
        CACHE.entrySet().removeIf(e -> {
            Entity live = level.getEntity(e.getKey());
            return !(live instanceof FogLurkerEntity) || live.isRemoved();
        });
    }

    @Nullable
    public static LivingEntity get(FogLurkerEntity lurker) {
        Disguise d = lurker.getDisguise();
        if (d == Disguise.CRAWLER || !(lurker.level() instanceof ClientLevel level)) {
            CACHE.remove(lurker.getId());
            return null;
        }
        UUID mimic = d == Disguise.PLAYER ? lurker.getMimicUuid() : null;
        ClientPacketListener listConn = Minecraft.getInstance().getConnection();
        boolean listed = mimic != null && listConn != null && listConn.getPlayerInfo(mimic) != null;
        Key key = new Key(d, mimic, lurker.getVariant(), listed);
        Holder h = CACHE.get(lurker.getId());
        if (h == null || !h.key.equals(key) || h.dummy.level() != level) {
            LivingEntity dummy = create(level, lurker, d);
            if (dummy == null) {
                return null;
            }
            h = new Holder();
            h.key = key;
            h.dummy = dummy;
            CACHE.put(lurker.getId(), h);
            sync(lurker, dummy, false);
        }
        return h.dummy;
    }

    @Nullable
    private static LivingEntity create(ClientLevel level, FogLurkerEntity lurker, Disguise d) {
        if (d == Disguise.PLAYER) {
            UUID id = lurker.getMimicUuid();
            String name = lurker.getMimicName();
            ClientPacketListener conn = Minecraft.getInstance().getConnection();
            PlayerInfo info = id == null || conn == null ? null : conn.getPlayerInfo(id);
            if (info != null) {
                KNOWN_PROFILES.put(info.getProfile().id(), info.getProfile());
                return new MimicDummyPlayer(level, info.getProfile());
            }
            GameProfile known = id == null ? null : KNOWN_PROFILES.get(id);
            if (known != null) {
                return new MimicDummyPlayer(level, known); // seen earlier this session: no lookup, no default-skin flash
            }
            // Not online: fetch their skin from Mojang by name, the way a player head does.
            GameProfile bare = new GameProfile(id != null ? id : UUID.randomUUID(), name.isEmpty() ? "Player" : name);
            MimicDummyPlayer dummy = new MimicDummyPlayer(level, bare);
            if (!name.isEmpty()) {
                UUID fetchedId = bare.id();
                Minecraft mc = Minecraft.getInstance();
                java.util.concurrent.CompletableFuture.supplyAsync(() -> mc.services().profileResolver().fetchByName(name),
                        net.minecraft.util.Util.nonCriticalIoPool()).thenAcceptAsync(found -> {
                    // (back on the main thread) remembered, so the next world does not fetch and flash again
                    GameProfile filled = found.orElse(null);
                    if (filled != null && filled.properties().containsKey("textures")) {
                        KNOWN_PROFILES.put(fetchedId, filled);
                    }
                    dummy.setSkinProfile(filled);
                }, mc).exceptionally(t -> null); // offline or not found: their default skin it is
            }
            return dummy;
        }
        EntityType<?> type = d.renderType();
        if (type == null) {
            return null;
        }
        Entity e = type.create(level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
        if (!(e instanceof LivingEntity living)) {
            return null;
        }
        living.setSilent(true);
        if (living instanceof Mob mob) {
            mob.setNoAi(true);
        }
        if (living instanceof Sheep sheep) {
            sheep.setColor(DyeColor.byId(lurker.getVariant()));
        }
        if (living instanceof Wolf wolf && d == Disguise.DOG) {
            wolf.setTame(true, false);
            wolf.setHealth(wolf.getMaxHealth()); // a healthy dog wags its tail high
            ((com.foglurker.mixin.WolfInvoker) wolf).foglurker$setCollarColor(DyeColor.byId(lurker.getVariant()));
        }
        return living;
    }

    // LivingEntity.swimAmount / swimAmountO (private): copied so a stolen player visibly sprint-swims.
    private static boolean swimReflectionFailed;

    private static void copySwimAmount(LivingEntity src, LivingEntity dst) {
        if (swimReflectionFailed) {
            return;
        }
        try {
            var from = (com.foglurker.mixin.LivingEntityAccessor) src;
            var to = (com.foglurker.mixin.LivingEntityAccessor) dst;
            to.foglurker$setSwimAmount(from.foglurker$swimAmount());
            to.foglurker$setSwimAmountO(from.foglurker$swimAmountO());
        } catch (RuntimeException | LinkageError e) {
            swimReflectionFailed = true; // purely cosmetic; it still swims, just without the flat pose
        }
    }

    private static void sync(FogLurkerEntity src, LivingEntity dst, boolean ticking) {
        dst.setPos(src.getX(), src.getY(), src.getZ());
        dst.xo = src.xo;
        dst.yo = src.yo;
        dst.zo = src.zo;
        dst.xOld = src.xOld;
        dst.yOld = src.yOld;
        dst.zOld = src.zOld;
        dst.setYRot(src.getYRot());
        dst.yRotO = src.yRotO;
        dst.setXRot(src.getXRot());
        dst.xRotO = src.xRotO;
        dst.yBodyRot = src.yBodyRot;
        dst.yBodyRotO = src.yBodyRotO;
        dst.yHeadRot = src.yHeadRot;
        dst.yHeadRotO = src.yHeadRotO;
        dst.setOnGround(src.onGround());
        dst.tickCount = src.tickCount;
        dst.hurtTime = src.hurtTime;
        dst.hurtDuration = src.hurtDuration;
        dst.attackAnim = src.attackAnim;
        dst.oAttackAnim = src.oAttackAnim;
        dst.swinging = src.swinging;
        dst.swingTime = src.swingTime;
        dst.swingingArm = src.swingingArm;
        dst.setPose(src.getPose());
        dst.setShiftKeyDown(src.isShiftKeyDown());
        dst.setInvisible(src.isInvisible());
        if (ticking) {
            // Same maths vanilla uses for leg swing, driven by the creature's real movement.
            float moved = (float) Mth.length(src.getX() - src.xo, 0.0D, src.getZ() - src.zo);
            dst.walkAnimation.update(Math.min(moved * 4.0F, 1.0F), 0.4F, 1.0F);
        }
        if (dst instanceof MimicDummyPlayer player) {
            player.setLook(src.getSkinParts(), src.getMimicMainArm());
            dst.setSwimming(src.isSwimming());
            copySwimAmount(src, dst);
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack want = src.getItemBySlot(slot);
                if (!ItemStack.matches(want, player.getItemBySlot(slot))) {
                    player.setItemSlot(slot, want.copy());
                }
            }
        }
        if (dst instanceof Mob mob) {
            mob.setAggressive(src.isAggressive());
        }
        if (dst instanceof Wolf wolf) {
            wolf.setInSittingPose(src.hasFlag(FogLurkerEntity.FLAG_SITTING));
            boolean angry = src.hasFlag(FogLurkerEntity.FLAG_ANGRY);
            if (angry != wolf.isAngry()) {
                wolf.setPersistentAngerEndTime(angry ? wolf.level().getGameTime() + 400 : -1L);
            }
        }
        if (!Objects.equals(dst.getCustomName(), src.getCustomName()) && src.getDisguise() == Disguise.DOG) {
            dst.setCustomName(src.getCustomName());
        }
    }
}
