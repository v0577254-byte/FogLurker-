package com.foglurker.client;

import com.foglurker.entity.FogLurkerEntity;
import com.foglurker.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Things that are only in your head: a Fog Lurker that exists on your client alone, placed at the
 * edge of your screen. The moment you turn to look straight at it, it is gone, without a sound.
 */
public final class Apparitions {
    private static final List<FogLurkerEntity> ACTIVE = new ArrayList<>();
    private static int nextId = -1_600_000;

    private Apparitions() {
    }

    public static int count() {
        return ACTIVE.size();
    }

    /** Where the first one stands, or null (used by the dev showcase). */
    public static Vec3 firstPos() {
        return ACTIVE.isEmpty() ? null : ACTIVE.get(0).position();
    }

    public static void clear() {
        ACTIVE.clear();
    }

    /** @return true if a place for it was found */
    public static boolean spawn() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || ACTIVE.size() >= 2) {
            return false;
        }
        // Half the horizontal field of view; it goes at ~85% of the way to the edge of the screen.
        double vfov = Math.toRadians(mc.options.fov().get());
        double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        double halfH = Math.atan(Math.tan(vfov / 2) * aspect);
        // Prefer a spot you could actually see (behind a hill it never gets the "look and it is gone" moment),
        // but if the woods or hills hide every spot, any good spot beats showing nothing.
        double[] fallback = null;
        for (int attempt = 0; attempt < 12; attempt++) {
            double side = (attempt % 2 == 0 ? 1 : -1) * halfH * (0.8 + player.getRandom().nextDouble() * 0.1);
            double yaw = Math.toRadians(player.getYRot()) + side;
            double dist = 9 + player.getRandom().nextDouble() * 9;
            double x = player.getX() - Math.sin(yaw) * dist;
            double z = player.getZ() + Math.cos(yaw) * dist;
            BlockPos ground = standable(level, BlockPos.containing(x, player.getY(), z));
            if (ground == null) {
                continue;
            }
            Vec3 chest = new Vec3(x, ground.getY() + 0.9, z);
            if (level.clip(new net.minecraft.world.level.ClipContext(player.getEyePosition(), chest,
                    net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE, player))
                    .getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                if (fallback == null) {
                    fallback = new double[]{x, ground.getY(), z};
                }
                continue;
            }
            return place(level, player, x, ground.getY(), z);
        }
        return fallback != null && place(level, player, fallback[0], fallback[1], fallback[2]);
    }

    private static boolean place(ClientLevel level, LocalPlayer player, double x, double y, double z) {
        FogLurkerEntity e = ModEntities.FOG_LURKER.get().create(level);
        if (e == null) {
            return false;
        }
        int id = nextId--;
        e.setId(id);
        float face = (float) (Mth.atan2(player.getZ() - z, player.getX() - x) * Mth.RAD_TO_DEG) - 90F;
        e.moveTo(x, y, z, face, 0F);
        e.setYHeadRot(face);
        e.setYBodyRot(face);
        e.yBodyRotO = face;
        e.yHeadRotO = face;
        e.setSilent(true);
        level.putNonPlayerEntity(id, e);
        ACTIVE.add(e);
        return true;
    }

    private static BlockPos standable(ClientLevel level, BlockPos near) {
        for (int dy = 4; dy >= -5; dy--) {
            BlockPos p = near.above(dy);
            if (level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)
                    && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                    && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                    && level.getFluidState(p).isEmpty()) {
                return p;
            }
        }
        return null;
    }

    public static void tick(Minecraft mc) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            ACTIVE.clear();
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1F);
        for (Iterator<FogLurkerEntity> it = ACTIVE.iterator(); it.hasNext(); ) {
            FogLurkerEntity e = it.next();
            Vec3 to = new Vec3(e.getX(), e.getY(0.5), e.getZ()).subtract(eye);
            double len = to.length();
            boolean lookedAt = len > 1e-3 && look.dot(to.scale(1 / len)) > 0.94; // within ~20 degrees of the crosshair
            if (lookedAt || len < 5 || e.tickCount > 240 || e.isRemoved() || e.level() != level) {
                level.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
                it.remove();
            }
        }
    }
}
