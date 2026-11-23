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
 * Goatman (design §4.1): a tall, bipedal monstrosity with long arms and legs, a goat-skull head with long curved
 * horns, a fur-covered body, hooves, and black claws. About 2.6 blocks tall.
 */
public class GoatmanModel extends EntityModel<Player> {
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public GoatmanModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -16.0F, -3.0F, 10, 16, 6),
                PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, 0.15F, 0.0F, 0.0F));

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(32, 0).addBox(-3.0F, -7.0F, -3.0F, 6, 7, 6)   // skull
                        .texOffs(32, 13).addBox(-2.0F, -5.0F, -7.0F, 4, 4, 4), // snout
                PartPose.offset(0.0F, -5.0F, -2.0F));
        PartDefinition rightHorn = head.addOrReplaceChild("right_horn",
                CubeListBuilder.create().texOffs(56, 0).addBox(-1.0F, -6.0F, -1.0F, 2, 6, 2),
                PartPose.offsetAndRotation(-2.5F, -6.0F, 0.0F, -0.5F, 0.0F, -0.45F));
        rightHorn.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(56, 8).addBox(-1.0F, -5.0F, -1.0F, 2, 5, 2),
                PartPose.offsetAndRotation(0.0F, -5.5F, 0.0F, -0.9F, 0.0F, 0.0F));
        PartDefinition leftHorn = head.addOrReplaceChild("left_horn",
                CubeListBuilder.create().texOffs(56, 0).mirror().addBox(-1.0F, -6.0F, -1.0F, 2, 6, 2),
                PartPose.offsetAndRotation(2.5F, -6.0F, 0.0F, -0.5F, 0.0F, 0.45F));
        leftHorn.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(56, 8).mirror().addBox(-1.0F, -5.0F, -1.0F, 2, 5, 2),
                PartPose.offsetAndRotation(0.0F, -5.5F, 0.0F, -0.9F, 0.0F, 0.0F));

        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                        .texOffs(0, 22).addBox(-2.0F, -1.0F, -1.5F, 3, 18, 3)
                        .texOffs(12, 22).addBox(-2.0F, 17.0F, -1.5F, 1, 3, 1)
                        .texOffs(12, 22).addBox(0.0F, 17.0F, -1.5F, 1, 3, 1),
                PartPose.offset(-6.5F, -4.0F, -1.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().mirror()
                        .texOffs(0, 22).addBox(-1.0F, -1.0F, -1.5F, 3, 18, 3)
                        .texOffs(12, 22).addBox(-1.0F, 17.0F, -1.5F, 1, 3, 1)
                        .texOffs(12, 22).addBox(1.0F, 17.0F, -1.5F, 1, 3, 1),
                PartPose.offset(6.5F, -4.0F, -1.0F));

        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                        .texOffs(16, 22).addBox(-1.5F, 0.0F, -1.5F, 3, 12, 3)
                        .texOffs(28, 22).addBox(-2.0F, 12.0F, -2.0F, 4, 2, 4),
                PartPose.offset(-2.5F, 10.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().mirror()
                        .texOffs(16, 22).addBox(-1.5F, 0.0F, -1.5F, 3, 12, 3)
                        .texOffs(28, 22).addBox(-2.0F, 12.0F, -2.0F, 4, 2, 4),
                PartPose.offset(2.5F, 10.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(Player player, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;

        float stride = Mth.cos(limbSwing * 0.6662F) * 1.2F * limbSwingAmount;
        rightLeg.xRot = stride;
        leftLeg.xRot = -stride;

        float idle = Mth.sin(ageInTicks * 0.067F) * 0.05F;
        rightArm.xRot = -stride * 0.8F - 0.25F + idle;
        leftArm.xRot = stride * 0.8F - 0.25F - idle;
        rightArm.zRot = 0.12F + Mth.cos(ageInTicks * 0.09F) * 0.04F;
        leftArm.zRot = -rightArm.zRot;

        if (attackTime > 0.0F) {
            // Overhead two-handed rake with the claws.
            float swing = Mth.sin(attackTime * Mth.PI);
            rightArm.xRot -= swing * 2.0F;
            leftArm.xRot -= swing * 1.4F;
            rightArm.zRot -= swing * 0.3F;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int light, int overlay,
                               float red, float green, float blue, float alpha) {
        root.render(poseStack, consumer, light, overlay, red, green, blue, alpha);
    }
}
