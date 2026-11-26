package com.sanguinaire.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sanguinaire.leech.SanguineLeech;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Nico's Sanguine Leech model (Blockbench, seven segments from head {@code body1} to tail {@code body7}). The segments
 * sway from side to side in a wave running down the body, like a silverfish; faster and wider while swimming.
 */
public class SanguineLeechModel extends EntityModel<SanguineLeech> {
    private final ModelPart[] segments = new ModelPart[7];

    public SanguineLeechModel(ModelPart root) {
        for (int i = 0; i < segments.length; i++) {
            segments[i] = root.getChild("body" + (i + 1));
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        partdefinition.addOrReplaceChild("body1", CubeListBuilder.create().texOffs(0, 14).addBox(-1.5F, -1.0F, -1.0F, 3.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(18, 0).addBox(-1.5F, -1.0F, -2.0F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 22.0F, -7.0F));
        partdefinition.addOrReplaceChild("body2", CubeListBuilder.create().texOffs(12, 7).addBox(-2.0F, -1.0F, -1.0F, 4.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 21.0F, -5.0F));
        partdefinition.addOrReplaceChild("body3", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, 0.0F, -1.5F, 6.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 20.0F, -2.5F));
        partdefinition.addOrReplaceChild("body4", CubeListBuilder.create().texOffs(0, 7).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 21.0F, 0.5F));
        partdefinition.addOrReplaceChild("body5", CubeListBuilder.create().texOffs(12, 13).addBox(-1.0F, -1.0F, -1.5F, 2.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 22.0F, 3.5F));
        partdefinition.addOrReplaceChild("body6", CubeListBuilder.create().texOffs(0, 19).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 23.0F, 6.0F));
        partdefinition.addOrReplaceChild("body7", CubeListBuilder.create().texOffs(18, 4).addBox(-0.5F, 0.0F, -1.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 23.0F, 8.0F));

        return LayerDefinition.create(meshdefinition, 32, 32);
    }

    @Override
    public void setupAnim(SanguineLeech entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        boolean swimming = entity.isInWater();
        float speed = swimming ? 1.3F : 0.9F;
        float amplitude = swimming ? 1.5F : 1.0F;
        for (int i = 0; i < segments.length; i++) {
            float phase = ageInTicks * speed + i * 0.15F * Mth.PI * 2.0F;
            float fromCentre = Math.abs(i - 2); // body3, the thickest segment, sways least
            segments[i].yRot = Mth.cos(phase) * Mth.PI * 0.05F * (1.0F + fromCentre) * amplitude;
            segments[i].x = Mth.sin(phase) * Mth.PI * 0.12F * fromCentre * amplitude;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        for (ModelPart segment : segments) {
            segment.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }
}
