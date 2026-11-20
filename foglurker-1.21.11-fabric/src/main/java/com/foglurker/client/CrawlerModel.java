package com.foglurker.client;

import com.foglurker.FogLurkerMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import com.foglurker.entity.MacarenaDance;
import net.minecraft.util.Mth;

/**
 * The attacking form: a pale, hairless thing on all fours. Arms far too long, reaching more than a
 * block ahead of its shoulders; grasshopper legs with the knees up by its ribs; a long hanging jaw
 * full of teeth. Texture layout must match tools/ texture generator (128x64).
 */
public class CrawlerModel extends EntityModel<CrawlerRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(FogLurkerMod.id("crawler"), "main");

    private final ModelPart body;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart rightArm;
    private final ModelPart rightForearm;
    private final ModelPart leftArm;
    private final ModelPart leftForearm;
    private final ModelPart rightLeg;
    private final ModelPart rightShin;
    private final ModelPart leftLeg;
    private final ModelPart leftShin;
    private final ModelPart rightHand;
    private final ModelPart leftHand;
    private final ModelPart rightFoot;
    private final ModelPart leftFoot;

    public CrawlerModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.neck = root.getChild("neck");
        this.head = neck.getChild("head");
        this.jaw = head.getChild("jaw");
        this.rightArm = root.getChild("right_arm");
        this.rightForearm = rightArm.getChild("forearm");
        this.leftArm = root.getChild("left_arm");
        this.leftForearm = leftArm.getChild("forearm");
        this.rightLeg = root.getChild("right_leg");
        this.rightShin = rightLeg.getChild("shin");
        this.leftLeg = root.getChild("left_leg");
        this.leftShin = leftLeg.getChild("shin");
        this.rightHand = rightForearm.getChild("hand");
        this.leftHand = leftForearm.getChild("hand");
        this.rightFoot = rightShin.getChild("foot");
        this.leftFoot = leftShin.getChild("foot");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Torso, sloping down from hunched shoulders to the hips.
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.5F, -3.0F, -1.0F, 9, 6, 16),
                PartPose.offsetAndRotation(0.0F, 7.0F, -6.0F, -0.22F, 0.0F, 0.0F));
        // Vertebrae poking through the skin.
        body.addOrReplaceChild("spine", CubeListBuilder.create()
                        .texOffs(50, 0).addBox(-1.0F, -4.5F, 1.0F, 2, 2, 3)
                        .texOffs(50, 0).addBox(-1.0F, -4.5F, 6.0F, 2, 2, 3)
                        .texOffs(50, 0).addBox(-1.0F, -4.5F, 11.0F, 2, 2, 3),
                PartPose.ZERO);

        // Neck hangs forward and down, head held low.
        PartDefinition neck = root.addOrReplaceChild("neck", CubeListBuilder.create()
                        .texOffs(0, 22).addBox(-2.5F, -2.5F, -6.0F, 5, 5, 6),
                PartPose.offsetAndRotation(0.0F, 6.5F, -6.5F, 0.55F, 0.0F, 0.0F));
        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(64, 0).addBox(-4.0F, -5.0F, -8.0F, 8, 7, 9)
                        // Long upper jaw.
                        .texOffs(64, 16).addBox(-3.0F, -1.0F, -15.0F, 6, 3, 7),
                PartPose.offsetAndRotation(0.0F, 0.5F, -5.5F, -0.75F, 0.0F, 0.0F));
        // Upper teeth: two bloody fangs at the front, a row down each side.
        CubeListBuilder upperTeeth = CubeListBuilder.create()
                .texOffs(108, 0).addBox(-2.5F, 2.0F, -15.0F, 1, 3, 1)
                .texOffs(100, 0).addBox(-0.5F, 2.0F, -15.0F, 1, 2, 1)
                .texOffs(108, 0).addBox(1.5F, 2.0F, -15.0F, 1, 3, 1);
        for (float z : new float[]{-13.0F, -11.0F, -9.0F}) {
            int u = z == -11.0F ? 104 : 100; // some clean, some bloody
            upperTeeth.texOffs(u, 0).addBox(-3.0F, 2.0F, z, 1, 2, 1);
            upperTeeth.texOffs(z == -13.0F ? 104 : 100, 0).addBox(2.0F, 2.0F, z, 1, 2, 1);
        }
        head.addOrReplaceChild("upper_teeth", upperTeeth, PartPose.ZERO);

        // The lower jaw: as long as the head again, hanging open.
        PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create()
                        .texOffs(0, 34).addBox(-3.0F, 0.0F, -10.0F, 6, 2, 10),
                PartPose.offsetAndRotation(0.0F, 2.5F, -6.0F, 0.55F, 0.0F, 0.0F));
        CubeListBuilder lowerTeeth = CubeListBuilder.create()
                .texOffs(104, 4).addBox(-1.5F, -2.0F, -10.0F, 1, 2, 1)
                .texOffs(100, 4).addBox(0.5F, -2.0F, -10.0F, 1, 2, 1);
        for (float z : new float[]{-9.0F, -7.0F, -5.0F, -3.0F}) {
            lowerTeeth.texOffs(z == -7.0F ? 104 : 100, 4).addBox(-3.0F, -2.0F, z, 1, 2, 1);
            lowerTeeth.texOffs(z == -9.0F ? 104 : 100, 4).addBox(2.0F, -2.0F, z, 1, 2, 1);
        }
        jaw.addOrReplaceChild("lower_teeth", lowerTeeth, PartPose.ZERO);

        // Arms: far too long. Elbows jut up and out, hands reach well past the head.
        addArm(root, "right_arm", -5.0F, 0.5F, false);
        addArm(root, "left_arm", 5.0F, -0.5F, true);
        // Legs: knees up beside the ribs like a grasshopper.
        addLeg(root, "right_leg", -3.5F, 0.35F, false);
        addLeg(root, "left_leg", 3.5F, -0.35F, true);

        return LayerDefinition.create(mesh, 128, 64);
    }

    private static void addArm(PartDefinition root, String name, float x, float zRot, boolean mirror) {
        PartDefinition arm = root.addOrReplaceChild(name, CubeListBuilder.create().mirror(mirror)
                        .texOffs(32, 34).addBox(-1.5F, -1.0F, -1.5F, 3, 13, 3),
                PartPose.offsetAndRotation(x, 6.5F, -5.0F, -0.75F, 0.0F, zRot));
        PartDefinition forearm = arm.addOrReplaceChild("forearm", CubeListBuilder.create().mirror(mirror)
                        .texOffs(44, 34).addBox(-1.0F, 0.0F, -1.0F, 2, 13, 2),
                PartPose.offsetAndRotation(0.0F, 11.5F, 0.0F, -0.1F, 0.0F, -0.4F * Math.signum(zRot)));
        PartDefinition hand = forearm.addOrReplaceChild("hand", CubeListBuilder.create().mirror(mirror)
                        .texOffs(22, 22).addBox(-1.5F, -0.5F, -3.5F, 3, 1, 4),
                PartPose.offsetAndRotation(0.0F, 12.5F, 0.0F, 0.85F, 0.0F, -0.1F * Math.signum(zRot)));
        // Three long grey fingers, splayed.
        float[] spread = {-0.35F, 0.0F, 0.35F};
        for (int i = 0; i < 3; i++) {
            hand.addOrReplaceChild("finger" + i, CubeListBuilder.create().mirror(mirror)
                            .texOffs(36, 22).addBox(-0.5F, -0.5F, -4.0F, 1, 1, 4),
                    PartPose.offsetAndRotation(-1.0F + i, 0.0F, -3.5F, 0.1F, spread[i], 0.0F));
        }
    }

    private static void addLeg(PartDefinition root, String name, float x, float zRot, boolean mirror) {
        PartDefinition leg = root.addOrReplaceChild(name, CubeListBuilder.create().mirror(mirror)
                        .texOffs(52, 34).addBox(-1.5F, -1.0F, -2.0F, 3, 11, 4),
                PartPose.offsetAndRotation(x, 10.0F, 6.5F, -1.3F, 0.0F, zRot));
        PartDefinition shin = leg.addOrReplaceChild("shin", CubeListBuilder.create().mirror(mirror)
                        .texOffs(66, 34).addBox(-1.0F, 0.0F, -1.0F, 2, 13, 2),
                PartPose.offsetAndRotation(0.0F, 9.5F, 0.0F, 1.75F, 0.0F, 0.0F));
        PartDefinition foot = shin.addOrReplaceChild("foot", CubeListBuilder.create().mirror(mirror)
                        .texOffs(22, 22).addBox(-1.5F, -0.5F, -3.5F, 3, 1, 4),
                PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, -0.45F, 0.0F, -zRot));
        for (int i = 0; i < 2; i++) {
            foot.addOrReplaceChild("toe" + i, CubeListBuilder.create().mirror(mirror)
                            .texOffs(36, 22).addBox(-0.5F, -0.5F, -3.0F, 1, 1, 3),
                    PartPose.offsetAndRotation(-0.5F + i, 0.0F, -3.5F, 0.0F, i == 0 ? -0.2F : 0.2F, 0.0F));
        }
    }

    /**
     * Lying in cover like a dog: belly on the ground, front and back legs folded flat underneath,
     * head low with its jaw nearly shut, eyes on you.
     */
    private void lieDown(float ageInTicks, float netHeadYaw, float headPitch) {
        float breathe = Mth.sin(ageInTicks * 0.06F) * 0.25F;
        body.setPos(0F, 20.6F - breathe * 0.4F, -5.0F);
        body.setRotation(0.03F, 0F, 0F);
        neck.setPos(0F, 19.5F - breathe * 0.3F, -5.5F);
        neck.setRotation(-0.55F, 0F, 0F);
        head.xRot = 0.45F + Mth.clamp(headPitch * Mth.DEG_TO_RAD * 0.3F, -0.2F, 0.15F);
        head.yRot = Mth.clamp(netHeadYaw * Mth.DEG_TO_RAD * 0.6F, -0.6F, 0.6F);
        head.zRot = 0.12F + Mth.sin(ageInTicks * 0.03F) * 0.05F;
        jaw.xRot = 0.06F + Math.max(0F, Mth.sin(ageInTicks * 0.05F)) * 0.05F;
        // Front legs folded back under the chest, hands flat.
        rightArm.setPos(-3.6F, 22.4F, -5.5F);
        rightArm.setRotation(Mth.HALF_PI, 0F, 0F);
        rightForearm.setRotation(-3.17F, 0F, 0F);
        rightHand.setRotation(1.6F, 0F, 0F);
        leftArm.setPos(3.6F, 22.4F, -5.5F);
        leftArm.setRotation(Mth.HALF_PI, 0F, 0F);
        leftForearm.setRotation(-3.17F, 0F, 0F);
        leftHand.setRotation(1.6F, 0F, 0F);
        // Back legs folded forward under the belly.
        rightLeg.setPos(-3.4F, 22.0F, 9.0F);
        rightLeg.setRotation(-Mth.HALF_PI, 0F, 0F);
        rightShin.setRotation(3.17F, 0F, 0F);
        rightFoot.setRotation(-1.6F, 0F, 0F);
        leftLeg.setPos(3.4F, 22.0F, 9.0F);
        leftLeg.setRotation(-Mth.HALF_PI, 0F, 0F);
        leftShin.setRotation(3.17F, 0F, 0F);
        leftFoot.setRotation(-1.6F, 0F, 0F);
    }

    /** Rears up on its hind legs and does the Macarena with its far-too-long arms. */
    private void dance(float t) {
        float countF = t / MacarenaDance.TICKS_PER_COUNT;
        int count = Math.floorMod((int) Math.floor(countF), MacarenaDance.COUNTS);
        float within = countF - (float) Math.floor(countF);
        float[] from = MacarenaDance.pose(count - 1);
        float[] to = MacarenaDance.pose(count);
        float k = Math.min(1F, within * 2.5F);
        k = k * k * (3 - 2 * k);
        float[] p = new float[8];
        for (int i = 0; i < 8; i++) {
            p[i] = Mth.lerp(k, from[i], to[i]);
        }

        float bounce = Mth.abs(Mth.sin(countF * Mth.PI)) * 0.8F;
        float sway = count >= 12 && count <= 14 ? Mth.sin(countF * Mth.PI) * 1.6F : 0F;
        float hop = count == 15 ? Mth.sin(within * Mth.PI) * 6F : 0F;
        float lift = bounce + hop;

        // Upright torso standing on bent hind legs (hips 18.5 px up, chest rising above them).
        body.setPos(sway, 5.5F - lift, 1.0F);
        body.setRotation(Mth.HALF_PI - 0.12F, 0F, -sway * 0.05F);
        rightLeg.setPos(-3.5F + sway, 6.0F - lift, 1.0F);
        rightLeg.setRotation(-0.6F, 0F, 0.12F);
        rightShin.xRot = 1.2F;
        leftLeg.setPos(3.5F + sway, 6.0F - lift, 1.0F);
        leftLeg.setRotation(-0.6F, 0F, -0.12F);
        leftShin.xRot = 1.2F;

        // Head on top, grinning, jaw chomping on every beat.
        neck.setPos(sway * 0.8F, -9.0F - lift, -0.5F);
        neck.setRotation(-1.0F, 0F, 0F);
        head.xRot = 1.0F + 0.12F * Mth.sin(countF * 2 * Mth.PI);
        head.yRot = 0F;
        head.zRot = 0.25F;
        jaw.xRot = 0.35F + 0.35F * Mth.abs(Mth.sin(countF * Mth.PI));

        // Shoulders at the top of the torso; the Macarena itself.
        rightArm.setPos(-5.5F + sway, -6.5F - lift, 0.5F);
        rightArm.setRotation(p[0], p[1], p[2]);
        rightForearm.setRotation(p[3], 0F, 0F);
        leftArm.setPos(5.5F + sway, -6.5F - lift, 0.5F);
        leftArm.setRotation(p[4], p[5], p[6]);
        leftForearm.setRotation(p[7], 0F, 0F);
    }

    @Override
    public void setupAnim(CrawlerRenderState s) {
        super.setupAnim(s); // back to the rest pose
        if (s.dancing) {
            dance(s.danceTime);
            return;
        }
        if (s.lying) {
            lieDown(s.ageInTicks, s.yRot, s.xRot);
            return;
        }
        boolean aggressive = s.aggressive;
        float walk = s.walkAnimationPos * 0.55F;
        float amount = Math.min(1.0F, s.walkAnimationSpeed * 1.6F);

        // Diagonal crawl: right arm with left leg, left arm with right leg.
        float a = Mth.cos(walk);
        float b = Mth.cos(walk + Mth.PI);
        rightArm.xRot += a * 0.55F * amount;
        leftArm.xRot += b * 0.55F * amount;
        rightForearm.xRot += Math.max(0, -a) * 0.5F * amount; // lift the hand on the way forward
        leftForearm.xRot += Math.max(0, -b) * 0.5F * amount;
        leftLeg.xRot += a * 0.45F * amount;
        rightLeg.xRot += b * 0.45F * amount;
        leftShin.xRot += Math.max(0, a) * 0.4F * amount;
        rightShin.xRot += Math.max(0, b) * 0.4F * amount;

        // Breathing, and a bob in the stride.
        float breathe = Mth.sin(s.ageInTicks * 0.08F) * 0.35F;
        body.y += breathe + Mth.abs(Mth.sin(walk)) * -0.8F * amount;
        neck.y += breathe;

        // Head follows you, with a crooked tilt and the odd sudden jerk.
        head.yRot = s.yRot * Mth.DEG_TO_RAD * 0.7F;
        head.xRot += s.xRot * Mth.DEG_TO_RAD * 0.5F;
        head.zRot = 0.2F + Mth.sin(s.ageInTicks * 0.03F) * 0.12F;
        if (((int) s.ageInTicks) % 53 < 2) {
            head.zRot += 0.45F;
        }

        // Uncanny idles. Standing dead still, a limb twitches now and then...
        // floorMod: apparitions use negative IDs, and a negative seed made these checks always true.
        float seed = Math.floorMod(s.seed, 1000) * 7.3F;
        if (amount < 0.05F) {
            float twitch = (s.ageInTicks + seed) % 67F;
            if (twitch < 3F) {
                rightArm.zRot += 0.22F;
                rightForearm.xRot -= 0.35F;
                leftLeg.yRot += 0.2F;
            }
            float twitch2 = (s.ageInTicks + seed * 2F) % 113F;
            if (twitch2 < 2F) {
                leftArm.xRot -= 0.3F;
                body.zRot += 0.06F;
            }
        }
        // ...and every twenty seconds or so its head snaps right round, holds, and snaps back.
        float snap = (s.ageInTicks + seed * 3F) % 430F;
        if (snap < 16F) {
            float k = snap < 3F ? snap / 3F : snap > 13F ? (16F - snap) / 3F : 1F;
            head.zRot += Mth.PI * k;
        }

        // Jaw: hangs open, gapes when it hunts, and quivers.
        float open = aggressive ? 0.95F : 0.5F;
        float quiver = aggressive ? Mth.sin(s.ageInTicks * 1.3F) * 0.1F : Mth.sin(s.ageInTicks * 0.07F) * 0.08F;
        jaw.xRot = open + quiver;
        // The bite: head dips to leg height and the jaw slams shut, then gapes again.
        if (s.attackTime > 0F) {
            float bite = Mth.sin(s.attackTime * Mth.PI);
            head.xRot += bite * 0.7F;
            neck.xRot += bite * 0.35F;
            jaw.xRot = Mth.lerp(bite, jaw.xRot, 0.02F);
            if (s.attackTime < 0.35F) {
                jaw.xRot = Mth.lerp(s.attackTime / 0.35F, 1.2F, jaw.xRot); // gape wide right before it snaps
            }
        }
    }
}
