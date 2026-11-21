package com.foglurker.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.client.resources.PlayerSkin;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A never-spawned client-side copy of a real player, used only to draw the creature wearing their
 * skin. It shares the real player's UUID, so Minecraft finds their skin, cape and slim/wide arms.
 *
 * <p>Skin lookup, in order: if it copies <em>you</em>, exactly what your own character uses; else the
 * copied player's entry in the player list; else (they are not online any more) their skin straight
 * from Mojang. It never settles for a default skin while a real one can be found.</p>
 */
public class MimicDummyPlayer extends RemotePlayer {
    /** The copy under your bed shows no name tag (the name tag is the only thing this changes). */
    public boolean hideNameTag;

    @Override
    public boolean isInvisibleTo(net.minecraft.world.entity.player.Player viewer) {
        return hideNameTag || super.isInvisibleTo(viewer);
    }

    private int lastLook = -1;
    /** Their profile with skin data, fetched from Mojang when they are not in the player list. */
    private volatile @Nullable GameProfile skinProfile;

    public MimicDummyPlayer(ClientLevel level, GameProfile profile) {
        super(level, profile);
        this.noPhysics = true;
        this.setSilent(true);
    }

    void setSkinProfile(@Nullable GameProfile filled) {
        this.skinProfile = filled;
    }

    private GameProfile profileForSkin() {
        GameProfile p = skinProfile;
        return p != null ? p : getGameProfile();
    }

    /** Like the inherited lookup, but safe while disconnecting (the connection can already be gone). */
    @Override
    protected @Nullable PlayerInfo getPlayerInfo() {
        var conn = Minecraft.getInstance().getConnection();
        return conn == null ? null : conn.getPlayerInfo(this.getUUID());
    }

    /** The real player this copies, if it is you. */
    private @Nullable AbstractClientPlayer copiedSelf() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getUUID().equals(this.getUUID()) ? mc.player : null;
    }

    /** Their skin, cape and arm shape (slim or wide). */
    @Override
    public PlayerSkin getSkin() {
        AbstractClientPlayer me = copiedSelf();
        if (me != null) {
            return me.getSkin();
        }
        PlayerInfo info = getPlayerInfo();
        if (info != null) {
            return info.getSkin();
        }
        // Not in the player list (they logged off): ask Mojang for their skin like a player head does. Looked up
        // once per profile, not every frame; until it has arrived the lookup gives their default skin.
        // (Cached by Minecraft: asking every frame is cheap; until it has loaded this is their default skin.)
        return Minecraft.getInstance().getSkinManager().getInsecureSkin(profileForSkin());
    }

    public void setLook(int skinParts, HumanoidArm mainArm) {
        int look = skinParts | (mainArm == HumanoidArm.RIGHT ? 1 << 8 : 0);
        if (look == lastLook) {
            return;
        }
        lastLook = look;
        this.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) skinParts);
        this.entityData.set(DATA_PLAYER_MAIN_HAND, (byte) (mainArm == HumanoidArm.LEFT ? 0 : 1));
    }
}
