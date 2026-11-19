package com.foglurker.crawler.client;

import com.foglurker.crawler.CrawlerMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The attacking form: a pale, hairless thing on all fours. Arms far too long, reaching more than a
 * block ahead of its shoulders; grasshopper legs with the knees up by its ribs; a long hanging jaw
 * full of teeth. Same shape, texture layout (128x64) and animation as the 1.20.1 mod.
 */
public class CrawlerModel extends EntityModel<CrawlerRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(CrawlerMod.id("crawler"), "main");

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

    @Override
    public void setupAnim(CrawlerRenderState s) {
        super.setupAnim(s); // back to the rest pose
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

        // Head follows your eyes, with a crooked tilt and the odd sudden jerk.
        head.yRot = s.yRot * Mth.DEG_TO_RAD; // eyes on yours: the full turn, the body stays put
        head.xRot += s.xRot * Mth.DEG_TO_RAD;
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
