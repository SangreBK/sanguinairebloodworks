package com.sanguinaire.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Rotting Abomination (design §4.2): an uneven, stitched-together biped with three arms and two heads, combining
 * the zombie, husk and drowned. Hunched and lopsided, with mismatched limbs.
 */
public class RottingAbominationModel extends EntityModel<Player> {
    private final ModelPart root;
    private final ModelPart zombieHead;
    private final ModelPart huskHead;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart extraArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public RottingAbominationModel(ModelPart root) {
        this.root = root;
        this.zombieHead = root.getChild("zombie_head");
        this.huskHead = root.getChild("husk_head");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.extraArm = root.getChild("extra_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -12.0F, -4.0F, 12, 12, 8),
                PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, 0.3F, 0.0F, 0.05F));
        root.addOrReplaceChild("zombie_head", CubeListBuilder.create().texOffs(0, 20).addBox(-3.5F, -7.0F, -3.5F, 7, 7, 7),
                PartPose.offset(-3.0F, 1.5F, -3.0F));
        root.addOrReplaceChild("husk_head", CubeListBuilder.create().texOffs(28, 20).addBox(-3.0F, -6.0F, -3.0F, 6, 6, 6),
                PartPose.offset(4.0F, 2.5F, -2.0F));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 34).addBox(-3.0F, -2.0F, -2.0F, 4, 14, 4),
                PartPose.offset(-7.0F, 3.0F, -1.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(16, 34).addBox(-1.0F, -2.0F, -1.5F, 3, 12, 3),
                PartPose.offset(6.5F, 3.5F, -1.0F));
        root.addOrReplaceChild("extra_arm", CubeListBuilder.create().texOffs(28, 34).addBox(-1.5F, -1.0F, -1.5F, 3, 13, 3),
                PartPose.offset(5.0F, 7.0F, 2.5F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(40, 0).addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4),
                PartPose.offset(-3.0F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(40, 34).addBox(-1.5F, 0.0F, -1.5F, 3, 12, 3),
                PartPose.offset(3.0F, 12.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(Player player, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float yaw = netHeadYaw * Mth.DEG_TO_RAD;
        float pitch = headPitch * Mth.DEG_TO_RAD;
        zombieHead.yRot = yaw * 0.7F;
        zombieHead.xRot = pitch;
        zombieHead.zRot = -0.15F + Mth.sin(ageInTicks * 0.05F) * 0.08F;
        huskHead.yRot = yaw * 0.4F + 0.2F;
        huskHead.xRot = pitch * 0.5F + 0.1F;
        huskHead.zRot = 0.35F + Mth.sin(ageInTicks * 0.04F + 1.3F) * 0.1F;

        // A lurching, uneven gait.
        float stride = Mth.cos(limbSwing * 0.5F) * limbSwingAmount;
        rightLeg.xRot = stride;
        leftLeg.xRot = -stride * 0.7F;

        // Arms reach forward like a zombie's, each with its own rhythm.
        rightArm.xRot = -1.35F + Mth.cos(limbSwing * 0.5F + Mth.PI) * 0.3F * limbSwingAmount + Mth.sin(ageInTicks * 0.06F) * 0.05F;
        leftArm.xRot = -1.2F + Mth.cos(limbSwing * 0.5F) * 0.3F * limbSwingAmount;
        rightArm.zRot = 0.1F;
        leftArm.zRot = -0.15F;
        extraArm.xRot = 0.5F + Mth.sin(ageInTicks * 0.2F) * 0.3F;
        extraArm.zRot = -0.7F + Mth.cos(ageInTicks * 0.15F) * 0.15F;

        if (attackTime > 0.0F) {
            float swing = Mth.sin(attackTime * Mth.PI);
            rightArm.xRot -= swing * 1.2F;
            leftArm.xRot -= swing * 0.8F;
            extraArm.xRot -= swing * 1.5F;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int light, int overlay,
                               float red, float green, float blue, float alpha) {
        root.render(poseStack, consumer, light, overlay, red, green, blue, alpha);
    }
}
