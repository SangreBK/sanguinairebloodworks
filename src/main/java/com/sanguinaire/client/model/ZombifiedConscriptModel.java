package com.sanguinaire.client.model;

import com.sanguinaire.conscript.ZombifiedConscript;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Placeholder model for the Zombified Conscript: the vanilla illager body (baked from the pillager layer), with the
 * crossed arms hidden and both arms held out like a zombie's.
 */
public class ZombifiedConscriptModel extends HierarchicalModel<ZombifiedConscript> {
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;

    public ZombifiedConscriptModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.head.getChild("hat").visible = false;
        root.getChild("arms").visible = false;
        this.leftArm = root.getChild("left_arm");
        this.rightArm = root.getChild("right_arm");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
        this.leftArm.visible = true;
        this.rightArm.visible = true;
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(ZombifiedConscript entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        rightLeg.xRot = Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount * 0.5F;
        leftLeg.xRot = Mth.cos(limbSwing * 0.6662F + Mth.PI) * 1.4F * limbSwingAmount * 0.5F;
        rightLeg.yRot = 0.0F;
        leftLeg.yRot = 0.0F;
        rightArm.xRot = 0.0F;
        leftArm.xRot = 0.0F;
        rightArm.zRot = 0.0F;
        leftArm.zRot = 0.0F;
        AnimationUtils.animateZombieArms(leftArm, rightArm, entity.isAggressive(), attackTime, ageInTicks);
    }
}
