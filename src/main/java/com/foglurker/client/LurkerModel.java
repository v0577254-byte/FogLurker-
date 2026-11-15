package com.foglurker.client;

import com.foglurker.entity.FogLurkerEntity;
import com.foglurker.entity.MacarenaDance;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** The tall watcher form: a player-shaped thing that does not move like a player. Unless it is doing the Macarena. */
public class LurkerModel extends PlayerModel<FogLurkerEntity> {
    private static final float PI = Mth.PI;

    /** Arm rotations {xRot, yRot, zRot} for the right and left arm. */
    private record ArmPose(float rx, float ry, float rz, float lx, float ly, float lz) {
    }

    // The arm each count moves to (the other arm holds where it was).
    private static final float[] DOWN = {0F, 0F, 0F};
    private static final float[][] RIGHT_MOVES = {
            {-PI / 2, 0F, 0F},          // 1  arm out, palm down
            {-PI / 2 + 0.15F, 0F, -0.35F}, // 3  palm up
            {-1.9F, -0.95F, 0F},        // 5  hand to the other shoulder
            {-3.35F, 0F, 0.55F},        // 7  hand behind the head
            {-0.55F, -0.55F, 0.35F},    // 9  hand to the other hip
            {0.55F, 0F, -0.15F},        // 11 hand to the back pocket
    };
    private static final ArmPose[] POSES = buildPoses();

    public LurkerModel(ModelPart root) {
        super(root, false);
    }

    /** Builds the 16 counts: right, left, right, left... then hold for the wiggle and the hop. */
    private static ArmPose[] buildPoses() {
        ArmPose[] poses = new ArmPose[MacarenaDance.COUNTS];
        float[] right = DOWN;
        float[] left = DOWN;
        for (int c = 0; c < MacarenaDance.COUNTS; c++) {
            if (c < 12) {
                float[] move = RIGHT_MOVES[c / 2];
                if (c % 2 == 0) {
                    right = move;
                } else {
                    left = new float[]{move[0], -move[1], -move[2]}; // mirror for the left arm
                }
            }
            poses[c] = new ArmPose(right[0], right[1], right[2], left[0], left[1], left[2]);
        }
        return poses;
    }

    @Override
    public void setupAnim(FogLurkerEntity e, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (e.isDancing()) {
            dance(e.danceTime(ageInTicks - (int) ageInTicks));
            return;
        }
        // Head hangs at a wrong angle and slowly rolls.
        this.head.zRot = 0.35F + Mth.sin(ageInTicks * 0.05F) * 0.25F;
        if (e.isAggressive()) {
            // Long arms reaching for you.
            this.rightArm.xRot = -1.45F + Mth.sin(ageInTicks * 0.6F) * 0.08F;
            this.leftArm.xRot = -1.45F + Mth.cos(ageInTicks * 0.6F) * 0.08F;
            this.rightArm.yRot = -0.1F;
            this.leftArm.yRot = 0.1F;
        } else {
            // Arms hanging limp, barely swinging.
            this.rightArm.xRot *= 0.3F;
            this.leftArm.xRot *= 0.3F;
            this.rightArm.zRot = 0.08F;
            this.leftArm.zRot = -0.08F;
        }
        copyLayers();
    }

    private void dance(float t) {
        float countF = t / MacarenaDance.TICKS_PER_COUNT;
        int count = Math.floorMod((int) Math.floor(countF), MacarenaDance.COUNTS);
        float within = countF - (float) Math.floor(countF);
        ArmPose from = POSES[Math.floorMod(count - 1, MacarenaDance.COUNTS)];
        ArmPose to = POSES[count];
        float k = ease(Math.min(1F, within * 2.5F)); // snap into each pose in the first part of the count

        rightArm.xRot = Mth.lerp(k, from.rx(), to.rx());
        rightArm.yRot = Mth.lerp(k, from.ry(), to.ry());
        rightArm.zRot = Mth.lerp(k, from.rz(), to.rz());
        leftArm.xRot = Mth.lerp(k, from.lx(), to.lx());
        leftArm.yRot = Mth.lerp(k, from.ly(), to.ly());
        leftArm.zRot = Mth.lerp(k, from.lz(), to.lz());

        // Legs planted, a little bounce in the knees on every beat.
        rightLeg.xRot = 0F;
        leftLeg.xRot = 0F;
        rightLeg.yRot = 0F;
        leftLeg.yRot = 0F;
        float bounce = Mth.abs(Mth.sin(countF * PI)) * 0.6F;

        // Counts 13-15: hip wiggle. Count 16: hop.
        float sway = count >= 12 && count <= 14 ? Mth.sin(countF * PI) * 1.4F : 0F;
        float hop = count == 15 ? Mth.sin(within * PI) * 5F : 0F;
        float lift = bounce + hop;
        body.zRot = -sway * 0.06F;
        // Absolute positions: the base model does not reset x every frame.
        body.x = sway;
        rightLeg.x = -1.9F + sway;
        leftLeg.x = 1.9F + sway;
        rightArm.x = -5.0F + sway;
        leftArm.x = 5.0F + sway;
        head.x = sway * 0.6F;
        head.y -= lift;
        body.y -= lift;
        rightArm.y -= lift;
        leftArm.y -= lift;
        rightLeg.y -= lift;
        leftLeg.y -= lift;

        // Head nods to the beat, still crooked.
        head.xRot = 0.15F * Mth.sin(countF * 2 * PI);
        head.yRot = 0F;
        head.zRot = 0.3F;
        copyLayers();
    }

    private static float ease(float x) {
        return x * x * (3 - 2 * x);
    }

    private void copyLayers() {
        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightPants.copyFrom(this.rightLeg);
        this.leftPants.copyFrom(this.leftLeg);
    }
}
