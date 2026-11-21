package com.foglurker.client;

import com.foglurker.entity.Disguise;
import com.foglurker.entity.FogLurkerEntity;
import com.foglurker.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import org.jetbrains.annotations.Nullable;

/**
 * While you sleep, for one second: a copy of you (your own skin) rises from under the foot of your bed, only its head
 * above the mattress, looking at you. It exists on your screen alone and is gone the moment you get up.
 */
public final class UnderBed {
    @Nullable
    private static FogLurkerEntity figure;
    private static int ticksLeft;

    private UnderBed() {
    }

    /** Is this the figure under the bed (it never shows a name tag)? */
    public static boolean is(Entity e) {
        return e != null && e == figure;
    }

    @Nullable
    public static FogLurkerEntity figure() {
        return figure;
    }

    public static boolean showing() {
        return figure != null;
    }

    public static void show(int ticks) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || !player.isSleeping() || figure != null) {
            return;
        }
        BlockPos bed = player.getSleepingPos().orElse(null);
        if (bed == null) {
            return;
        }
        BlockState state = level.getBlockState(bed);
        if (!(state.getBlock() instanceof BedBlock)) {
            return;
        }
        Direction facing = state.getValue(BedBlock.FACING); // from the foot towards the head
        BlockPos head = state.getValue(BedBlock.PART) == BedPart.HEAD ? bed : bed.relative(facing);
        BlockPos foot = head.relative(facing.getOpposite());
        FogLurkerEntity e = ModEntities.FOG_LURKER.get().create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
        if (e == null) {
            return;
        }
        e.setId(-1_700_000);
        e.setDisguise(Disguise.PLAYER);
        e.setMimic(player.getUUID(), player.getGameProfile().name());
        // In the foot of the bed, a little towards its end, sunk into it so only the head shows above the mattress.
        double x = foot.getX() + 0.5 - facing.getStepX() * 0.2;
        double z = foot.getZ() + 0.5 - facing.getStepZ() * 0.2;
        double y = foot.getY() - 0.8;
        float yaw = facing.toYRot(); // facing the head of the bed: you
        e.snapTo(x, y, z, yaw, -12F); // looking up at you a little
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
        e.yBodyRotO = yaw;
        e.yHeadRotO = yaw;
        e.xRotO = -12F;
        e.setNoGravity(true);
        e.setSilent(true);
        level.addEntity(e);
        figure = e;
        ticksLeft = Math.max(1, Math.min(100, ticks));
    }

    public static void tick(Minecraft mc) {
        if (figure == null) {
            return;
        }
        LocalPlayer player = mc.player;
        if (--ticksLeft <= 0 || player == null || !player.isSleeping() || mc.level == null || figure.level() != mc.level) {
            clear();
        }
    }

    public static void clear() {
        FogLurkerEntity e = figure;
        figure = null;
        ticksLeft = 0;
        if (e != null && e.level() instanceof ClientLevel level) {
            level.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
        }
    }
}
