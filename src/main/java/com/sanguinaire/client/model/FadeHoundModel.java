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
 * FadeHound (design §4.3): a medium, dark, four-legged hound whose gaping mouth takes up most of its body, with a
 * long tongue hanging out and four glowing purple eyes. The eyes are drawn again as an emissive layer.
 */
public class FadeHoundModel extends EntityModel<Player> {
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart tongue;
    private final ModelPart rightFrontLeg;
    private final ModelPart leftFrontLeg;
    private final ModelPart rightHindLeg;
    private final ModelPart leftHindLeg;

    public FadeHoundModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.jaw = head.getChild("jaw");
        this.tongue = jaw.getChild("tongue");
        this.rightFrontLeg = root.getChild("right_front_leg");
        this.leftFrontLeg = root.getChild("left_front_leg");
        this.rightHindLeg = root.getChild("right_hind_leg");
        this.leftHindLeg = root.getChild("left_hind_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -6.0F, 8, 8, 12),
                PartPose.offset(0.0F, 16.0F, 2.0F));

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 20).addBox(-5.0F, -5.0F, -10.0F, 10, 5, 10)        // upper jaw
                        .texOffs(0, 48).addBox(-4.5F, 0.0F, -9.5F, 9, 1, 9)            // upper teeth
                        .texOffs(40, 11).addBox(-4.0F, -6.0F, -8.0F, 1, 1, 1)          // four eyes
                        .texOffs(40, 11).addBox(-2.0F, -6.0F, -9.0F, 1, 1, 1)
                        .texOffs(40, 11).addBox(1.0F, -6.0F, -9.0F, 1, 1, 1)
                        .texOffs(40, 11).addBox(3.0F, -6.0F, -8.0F, 1, 1, 1),
                PartPose.offset(0.0F, 11.0F, -3.0F));
        PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create()
                        .texOffs(0, 35).addBox(-5.0F, 0.0F, -10.0F, 10, 3, 10)
                        .texOffs(0, 48).addBox(-4.5F, -1.0F, -9.5F, 9, 1, 9),          // lower teeth
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.45F, 0.0F, 0.0F));
        jaw.addOrReplaceChild("tongue", CubeListBuilder.create().texOffs(40, 0).addBox(-1.0F, 0.0F, -10.0F, 2, 1, 10),
                PartPose.offsetAndRotation(0.0F, -0.5F, -6.0F, 0.5F, 0.0F, 0.0F));

        CubeListBuilder leg = CubeListBuilder.create().texOffs(44, 11).addBox(-1.5F, 0.0F, -1.5F, 3, 8, 3);
        root.addOrReplaceChild("right_front_leg", leg, PartPose.offset(-2.5F, 16.0F, -2.0F));
        root.addOrReplaceChild("left_front_leg", leg, PartPose.offset(2.5F, 16.0F, -2.0F));
        root.addOrReplaceChild("right_hind_leg", leg, PartPose.offset(-2.5F, 16.0F, 6.0F));
        root.addOrReplaceChild("left_hind_leg", leg, PartPose.offset(2.5F, 16.0F, 6.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(Player player, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.8F;
        head.xRot = headPitch * Mth.DEG_TO_RAD * 0.6F;

        float gait = Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
        rightFrontLeg.xRot = gait;
        leftHindLeg.xRot = gait;
        leftFrontLeg.xRot = -gait;
        rightHindLeg.xRot = -gait;

        float breathe = Mth.sin(ageInTicks * 0.12F) * 0.06F;
        float bite = attackTime > 0.0F ? Mth.sin(attackTime * Mth.PI) * 0.6F : 0.0F;
        jaw.xRot = 0.45F + breathe + bite;
        tongue.xRot = 0.5F + Mth.sin(ageInTicks * 0.1F) * 0.12F;
        tongue.yRot = Mth.sin(ageInTicks * 0.15F) * 0.3F + Mth.cos(limbSwing * 0.6662F) * 0.2F * limbSwingAmount;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int light, int overlay,
                               float red, float green, float blue, float alpha) {
        root.render(poseStack, consumer, light, overlay, red, green, blue, alpha);
    }
}
