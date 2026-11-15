package com.foglurker.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.HumanoidArm;

/**
 * A never-spawned client-side copy of a real player, used only to draw the creature wearing their
 * skin. Because it shares the real player's UUID, Minecraft looks up their actual skin, cape and
 * slim/wide arms, and draws their name tag and team colours exactly as it would for them.
 */
public class MimicDummyPlayer extends RemotePlayer {
    private int lastLook = -1;

    public MimicDummyPlayer(ClientLevel level, GameProfile profile) {
        super(level, profile);
        this.noPhysics = true;
        this.setSilent(true);
    }

    public void setLook(int skinParts, HumanoidArm mainArm) {
        int look = skinParts | (mainArm == HumanoidArm.RIGHT ? 1 << 8 : 0);
        if (look == lastLook) {
            return;
        }
        lastLook = look;
        this.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) skinParts);
        this.entityData.set(DATA_PLAYER_MAIN_HAND, (byte) (mainArm == HumanoidArm.RIGHT ? 1 : 0));
    }
}
