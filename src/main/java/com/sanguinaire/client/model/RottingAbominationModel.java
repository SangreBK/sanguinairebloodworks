package com.sanguinaire.client.model;

import com.sanguinaire.client.GoatmanAnimationClient;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

/**
 * Nico's Rotting Abomination model (Blockbench 5.2.2) with his keyframe animations
 * ({@link RottingAbominationAnimations}). Drawn with the normal or the vomiting texture, plus a glowing layer for the
 * eyes (see {@code TransformationRenderer}).
 * <p>
 * Two controllers, like the Goatman and the Fade Hound but with arms instead of legs: the <b>body</b> (left arm,
 * torso) blends IDLE / MOVE_BODY and the <b>arms</b> (right and back arm) blend IDLE_ARMS / MOVE_ARMS, both by walking
 * speed. While an attack plays, only {@link RottingAbominationAnimations#ATTACK} runs (the creature is rooted then).
 * Walk cycles follow the distance walked.
 */
public class RottingAbominationModel extends HierarchicalModel<Player> {
    /** Vanilla limb swing covered by one walking cycle (as for the Goatman). */
    private static final float LIMB_SWING_PER_CYCLE = Mth.TWO_PI / 0.6662F;
    private static final float MOVE_CYCLE_SECONDS = 1.5F;

    private final Vector3f cache = new Vector3f();
    private final ModelPart root;

    public RottingAbominationModel(ModelPart root) {
        this.root = root;
    }

    @Override
    public ModelPart root() {
        return root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();
        PartDefinition main = partdefinition.addOrReplaceChild("main", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
        PartDefinition rightarm = main.addOrReplaceChild("rightarm", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.0F, -28.0F, 9.0F, 0.0F, 0.0F, 1.0472F));
        PartDefinition upperright = rightarm.addOrReplaceChild("upperright", CubeListBuilder.create().texOffs(128, 42).addBox(-15.0F, -5.0F, -13.0F, 15.0F, 20.0F, 20.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition lowerright = rightarm.addOrReplaceChild("lowerright", CubeListBuilder.create().texOffs(0, 96).addBox(-5.0F, 0.0F, -36.0F, 12.0F, 12.0F, 40.0F, new CubeDeformation(0.0F))
        .texOffs(70, 178).addBox(-6.0F, 9.0F, -43.0F, 8.0F, 4.0F, 15.0F, new CubeDeformation(0.0F))
        .texOffs(174, 138).addBox(-6.0F, -1.0F, -51.0F, 10.0F, 4.0F, 20.0F, new CubeDeformation(0.0F))
        .texOffs(174, 162).addBox(2.0F, 4.0F, -43.0F, 10.0F, 4.0F, 15.0F, new CubeDeformation(0.0F)), PartPose.offset(-7.0F, 15.0F, -4.0F));
        PartDefinition torso = main.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 48).addBox(-16.0F, 1.0F, -16.5F, 32.0F, 16.0F, 32.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -16.0F, 6.5F));
        PartDefinition upper = torso.addOrReplaceChild("upper", CubeListBuilder.create().texOffs(0, 0).addBox(-16.0F, -16.0F, -29.0F, 32.0F, 16.0F, 32.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 1.0F, 12.5F));
        PartDefinition leftarm = main.addOrReplaceChild("leftarm", CubeListBuilder.create(), PartPose.offset(16.0F, -27.0F, 3.0F));
        PartDefinition lowerleft = leftarm.addOrReplaceChild("lowerleft", CubeListBuilder.create().texOffs(104, 96).addBox(-6.0F, 0.0F, -26.0F, 12.0F, 12.0F, 30.0F, new CubeDeformation(0.0F)), PartPose.offset(6.0F, 15.0F, -4.0F));
        PartDefinition claw3_r1 = lowerleft.addOrReplaceChild("claw3_r1", CubeListBuilder.create().texOffs(174, 138).addBox(-5.0F, -2.0F, -10.0F, 10.0F, 4.0F, 20.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 6.0F, -36.0F, 0.0F, 0.0F, 1.5708F));
        PartDefinition upperleft = leftarm.addOrReplaceChild("upperleft", CubeListBuilder.create().texOffs(128, 42).addBox(0.0F, -5.0F, -13.0F, 15.0F, 20.0F, 20.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition backarm = main.addOrReplaceChild("backarm", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -22.0F, 15.0F, 2.028F, -0.828F, -1.6184F));
        PartDefinition upperback = backarm.addOrReplaceChild("upperback", CubeListBuilder.create().texOffs(128, 42).addBox(0.0F, -5.0F, -13.0F, 15.0F, 20.0F, 20.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition lowerback = backarm.addOrReplaceChild("lowerback", CubeListBuilder.create().texOffs(104, 96).addBox(-6.0F, 0.0F, -26.0F, 12.0F, 12.0F, 30.0F, new CubeDeformation(0.0F)), PartPose.offset(6.0F, 15.0F, -4.0F));

        return LayerDefinition.create(meshdefinition, 256, 256);
    }

    @Override
    public void setupAnim(Player player, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float attack = GoatmanAnimationClient.attackSeconds(player, ageInTicks);
        if (attack >= 0.0F) {
            play(RottingAbominationAnimations.ATTACK, toMillis(attack), 1.0F);
            return; // no idle or walk while attacking
        }
        float moving = Mth.clamp(limbSwingAmount * 1.6F, 0.0F, 1.0F);
        long idleMillis = toMillis(ageInTicks / 20.0F);
        long walkMillis = toMillis(limbSwing / LIMB_SWING_PER_CYCLE * MOVE_CYCLE_SECONDS);
        // Body controller.
        play(RottingAbominationAnimations.IDLE, idleMillis, 1.0F - moving);
        play(RottingAbominationAnimations.MOVE_BODY, walkMillis, moving);
        // Arms controller.
        play(RottingAbominationAnimations.IDLE_ARMS, idleMillis, 1.0F - moving);
        play(RottingAbominationAnimations.MOVE_ARMS, walkMillis, moving);
    }

    private void play(AnimationDefinition animation, long millis, float weight) {
        if (weight > 0.001F) {
            KeyframeAnimations.animate(this, animation, millis, weight, cache);
        }
    }

    private static long toMillis(float seconds) {
        return (long) (seconds * 1000.0F);
    }
}
