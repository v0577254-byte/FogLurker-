package com.foglurker.client;

import com.foglurker.entity.FogLurkerEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** The true form: a player-shaped thing that does not move like a player. */
public class LurkerModel extends PlayerModel<FogLurkerEntity> {
    public LurkerModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setupAnim(FogLurkerEntity e, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
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
        this.hat.copyFrom(this.head);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftSleeve.copyFrom(this.leftArm);
    }
}
