package com.foglurker.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;

import javax.annotation.Nullable;

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
    @Nullable
    private volatile GameProfile skinProfile;

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
    @Nullable
    protected PlayerInfo getPlayerInfo() {
        var conn = Minecraft.getInstance().getConnection();
        return conn == null ? null : conn.getPlayerInfo(this.getUUID());
    }

    /** The real player this copies, if it is you. */
    @Nullable
    private AbstractClientPlayer copiedSelf() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getUUID().equals(this.getUUID()) ? mc.player : null;
    }

    @Override
    public ResourceLocation getSkinTextureLocation() {
        AbstractClientPlayer me = copiedSelf();
        if (me != null) {
            return me.getSkinTextureLocation();
        }
        PlayerInfo info = getPlayerInfo();
        if (info != null) {
            return info.getSkinLocation();
        }
        // Not in the player list (they logged off): ask Mojang for their skin like a player head does. Looked up
        // once per profile, not every frame (the lookup hashes and allocates).
        GameProfile profile = profileForSkin();
        if (profile != cachedSkinFor || cachedSkin == null) {
            cachedSkin = Minecraft.getInstance().getSkinManager().getInsecureSkinLocation(profile);
            cachedSkinFor = profile;
        }
        return cachedSkin;
    }

    @Nullable
    private GameProfile cachedSkinFor;
    @Nullable
    private ResourceLocation cachedSkin;

    @Override
    public String getModelName() {
        AbstractClientPlayer me = copiedSelf();
        if (me != null) {
            return me.getModelName();
        }
        PlayerInfo info = getPlayerInfo();
        if (info != null) {
            return info.getModelName();
        }
        var tex = Minecraft.getInstance().getSkinManager().getInsecureSkinInformation(profileForSkin())
                .get(com.mojang.authlib.minecraft.MinecraftProfileTexture.Type.SKIN);
        if (tex != null) {
            String model = tex.getMetadata("model");
            return model == null ? "default" : model;
        }
        return DefaultPlayerSkin.getSkinModelName(getUUID());
    }

    @Override
    public boolean isSkinLoaded() {
        AbstractClientPlayer me = copiedSelf();
        return me != null ? me.isSkinLoaded() : super.isSkinLoaded();
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
