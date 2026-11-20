package com.foglurker.forms.client;

import com.foglurker.forms.FogLurker;
import com.foglurker.forms.Form;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.wolf.Wolf;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * One invisible "puppet" per figure (a cow, a sheep, a copy of a player...), never added to the world, kept in
 * step with the figure so the real vanilla renderer draws it: a cow is pixel-for-pixel a cow, a copied player has
 * their real skin. Only the head angle is ever different from how it was placed.
 */
public final class PuppetCache {
    private record Key(Form form, @Nullable UUID mimic, boolean listed) {
    }

    private static final class Puppet {
        Key key;
        LivingEntity entity;
    }

    /** Weak keys: a figure that is gone takes its puppet with it. */
    private static final Map<FogLurker, Puppet> PUPPETS = new WeakHashMap<>();
    /** Full profiles (with skin data) seen this session, so a copy never flashes a default skin. */
    private static final Map<UUID, GameProfile> KNOWN_PROFILES = new HashMap<>();

    private PuppetCache() {
    }

    /** The puppet for this figure, made or remade as needed; null for the crawler (or if it cannot be made). */
    public static @Nullable LivingEntity get(FogLurker lurker) {
        Form form = lurker.getForm();
        if (form == Form.CRAWLER || !(lurker.level() instanceof ClientLevel level)) {
            PUPPETS.remove(lurker);
            return null;
        }
        UUID mimic = form == Form.PLAYER ? lurker.getMimicUuid() : null;
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        boolean listed = mimic != null && conn != null && conn.getPlayerInfo(mimic) != null;
        Key key = new Key(form, mimic, listed);
        Puppet p = PUPPETS.get(lurker);
        if (p == null || !p.key.equals(key) || p.entity.level() != level) {
            LivingEntity made = create(level, lurker, form);
            if (made == null) {
                return null;
            }
            p = new Puppet();
            p.key = key;
            p.entity = made;
            PUPPETS.put(lurker, p);
        }
        sync(lurker, p.entity);
        return p.entity;
    }

    private static @Nullable LivingEntity create(ClientLevel level, FogLurker lurker, Form form) {
        if (form == Form.PLAYER) {
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
                return new MimicDummyPlayer(level, known);
            }
            // Not online: fetch their skin from Mojang by name, the way a player head does.
            GameProfile bare = new GameProfile(id != null ? id : UUID.randomUUID(), name.isEmpty() ? "Player" : name);
            MimicDummyPlayer dummy = new MimicDummyPlayer(level, bare);
            if (!name.isEmpty()) {
                UUID fetchedId = bare.id();
                Minecraft mc = Minecraft.getInstance();
                CompletableFuture.supplyAsync(() -> mc.services().profileResolver().fetchByName(name), Util.nonCriticalIoPool())
                        .thenAcceptAsync(found -> {
                            GameProfile filled = found.orElse(null);
                            if (filled != null && filled.properties().containsKey("textures")) {
                                KNOWN_PROFILES.put(fetchedId, filled);
                            }
                            dummy.setSkinProfile(filled);
                        }, mc).exceptionally(t -> null); // offline or not found: their default skin it is
            }
            return dummy;
        }
        EntityType<?> type = form.vanilla();
        if (type == null) {
            return null;
        }
        Entity e = type.create(level, EntitySpawnReason.LOAD);
        if (!(e instanceof LivingEntity living)) {
            return null;
        }
        living.setSilent(true);
        if (living instanceof Mob mob) {
            mob.setNoAi(true);
        }
        if (living instanceof Wolf wolf && form == Form.DOG) {
            wolf.setTame(true, false); // a tamed dog: red collar
            wolf.setHealth(wolf.getMaxHealth()); // a healthy dog holds its tail high
        }
        return living;
    }

    /** Same place, same body angle, the figure's head angle; standing still. */
    private static void sync(FogLurker src, LivingEntity dst) {
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
        dst.setOnGround(true);
        dst.tickCount = src.tickCount;
        dst.setInvisible(src.isInvisible());
        if (!Objects.equals(dst.getCustomName(), src.getCustomName())) {
            dst.setCustomName(src.getCustomName()); // a name tag on the figure shows on the puppet
        }
        if (dst instanceof MimicDummyPlayer player) {
            player.setLook(0x7F, net.minecraft.world.entity.HumanoidArm.RIGHT); // every skin layer (hat, sleeves...)
        }
    }
}
