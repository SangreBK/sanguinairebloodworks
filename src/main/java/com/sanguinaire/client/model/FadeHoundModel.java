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
 * Nico's Fade Hound model (Blockbench 5.2.2) with his keyframe animations ({@link FadeHoundAnimations}). It is drawn
 * three times with the same pose, once per texture layer (solid, semi-transparent, glowing; see
 * {@code TransformationRenderer}).
 * <p>
 * Same two controllers as the Goatman: the <b>body</b> plays {@link FadeHoundAnimations#ATTACK} while a bite is
 * playing, otherwise IDLE / MOVE_BODY blended by walking speed; the <b>legs</b> always blend IDLE_LEGS / MOVE_LEGS,
 * so biting never stops the stride. Walk cycles follow the distance walked; the head turns towards where the player
 * looks.
 */
public class FadeHoundModel extends HierarchicalModel<Player> {
    /** Vanilla limb swing covered by one walking cycle (the period of a vanilla quadruped's leg swing). */
    private static final float LIMB_SWING_PER_CYCLE = Mth.TWO_PI / 0.6662F;
    private static final float MOVE_CYCLE_SECONDS = 1.5F;

    private final Vector3f cache = new Vector3f();
    private final ModelPart root;
    private final ModelPart head;

    public FadeHoundModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("main").getChild("head");
    }

    @Override
    public ModelPart root() {
        return root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition main = partdefinition.addOrReplaceChild("main", CubeListBuilder.create(), PartPose.offset(4.3333F, 10.0F, -9.1667F));

        PartDefinition leftleg = main.addOrReplaceChild("leftleg", CubeListBuilder.create().texOffs(47, 96).addBox(0.0F, -1.75F, -4.25F, 5.0F, 10.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(2.6667F, -5.75F, 2.4167F));

        PartDefinition lowerleft = leftleg.addOrReplaceChild("lowerleft", CubeListBuilder.create().texOffs(71, 77).addBox(-2.0F, -3.25F, -0.5F, 4.0F, 21.0F, 4.0F, new CubeDeformation(0.0F))
        .texOffs(87, 88).addBox(-4.0F, 17.3F, -7.5F, 8.0F, 3.0F, 11.0F, new CubeDeformation(0.0F)), PartPose.offset(2.0F, 1.5F, 3.25F));

        PartDefinition rightleg = main.addOrReplaceChild("rightleg", CubeListBuilder.create().texOffs(47, 96).mirror().addBox(-5.0F, -1.75F, -4.25F, 5.0F, 10.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-11.3333F, -5.75F, 2.4167F));

        PartDefinition lowerright = rightleg.addOrReplaceChild("lowerright", CubeListBuilder.create().texOffs(71, 77).mirror().addBox(-2.0F, -3.25F, -0.5F, 4.0F, 21.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false)
        .texOffs(87, 88).mirror().addBox(-4.0F, 17.3F, -7.5F, 8.0F, 3.0F, 11.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-2.0F, 1.5F, 3.25F));

        PartDefinition rightleg2 = main.addOrReplaceChild("rightleg2", CubeListBuilder.create().texOffs(47, 96).mirror().addBox(-5.0F, -1.75F, -4.25F, 5.0F, 10.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-10.3333F, -5.75F, 30.4167F));

        PartDefinition lowerright2 = rightleg2.addOrReplaceChild("lowerright2", CubeListBuilder.create().texOffs(71, 77).mirror().addBox(-2.0F, -3.25F, -0.5F, 4.0F, 21.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false)
        .texOffs(87, 88).mirror().addBox(-4.0F, 17.3F, -7.5F, 8.0F, 3.0F, 11.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-2.0F, 1.5F, 3.25F));

        PartDefinition leftleg2 = main.addOrReplaceChild("leftleg2", CubeListBuilder.create().texOffs(47, 96).addBox(0.0F, -1.75F, -4.25F, 5.0F, 10.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(1.6667F, -5.75F, 30.4167F));

        PartDefinition lowerleft2 = leftleg2.addOrReplaceChild("lowerleft2", CubeListBuilder.create().texOffs(71, 77).addBox(-2.0F, -3.25F, -0.5F, 4.0F, 21.0F, 4.0F, new CubeDeformation(0.0F))
        .texOffs(87, 88).addBox(-4.0F, 17.3F, -7.5F, 8.0F, 3.0F, 11.0F, new CubeDeformation(0.0F)), PartPose.offset(2.0F, 1.5F, 3.25F));

        PartDefinition head = main.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(-4.3333F, -5.3333F, -1.6667F));

        PartDefinition uppermaw = head.addOrReplaceChild("uppermaw", CubeListBuilder.create().texOffs(0, 57).addBox(-7.0F, -11.5F, -20.0F, 14.0F, 7.0F, 19.0F, new CubeDeformation(0.0F))
        .texOffs(0, 0).addBox(-7.0F, -4.5F, -40.0F, 14.0F, 17.0F, 40.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.3333F, 0.8333F));

        PartDefinition leftear = uppermaw.addOrReplaceChild("leftear", CubeListBuilder.create(), PartPose.offset(6.25F, -9.5F, -6.75F));

        PartDefinition earleft_r1 = leftear.addOrReplaceChild("earleft_r1", CubeListBuilder.create().texOffs(14, 85).addBox(-0.2401F, -3.0859F, 0.2262F, 14.0F, 7.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.6863F, -0.7356F, -0.8845F));

        PartDefinition rightear = uppermaw.addOrReplaceChild("rightear", CubeListBuilder.create(), PartPose.offset(-6.25F, -9.25F, -6.0F));

        PartDefinition earright_r1 = rightear.addOrReplaceChild("earright_r1", CubeListBuilder.create().texOffs(14, 85).mirror().addBox(-13.4F, -3.6431F, -0.2039F, 14.0F, 7.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.6863F, 0.7356F, 0.8845F));

        PartDefinition lowermaw = head.addOrReplaceChild("lowermaw", CubeListBuilder.create(), PartPose.offset(0.0F, 7.0F, 0.0F));

        PartDefinition lowermaw_r1 = lowermaw.addOrReplaceChild("lowermaw_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -8.5F, -20.0F, 14.0F, 17.0F, 40.0F, new CubeDeformation(-1.0F)), PartPose.offsetAndRotation(0.0F, -3.6667F, -19.1667F, 0.0F, 0.0F, -3.1416F));

        PartDefinition torso = main.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(66, 102).addBox(-6.0F, -6.5F, 9.5F, 12.0F, 13.0F, 13.0F, new CubeDeformation(0.0F)), PartPose.offset(-4.3333F, -2.0F, 8.6667F));

        PartDefinition uppertorso = torso.addOrReplaceChild("uppertorso", CubeListBuilder.create().texOffs(0, 94).addBox(-7.0F, -6.0F, -9.5F, 14.0F, 15.0F, 19.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.5F, 0.0F));

        PartDefinition tail = torso.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(92, 39).addBox(0.0F, -4.0F, 0.0F, 0.0F, 12.0F, 18.0F, new CubeDeformation(0.0F)), PartPose.offset(1.0F, -1.5F, 22.5F));

        PartDefinition tail2 = tail.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(56, 39).addBox(0.0F, -8.0F, 0.0F, 0.0F, 12.0F, 18.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.0F, 18.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    @Override
    public void setupAnim(Player player, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float moving = Mth.clamp(limbSwingAmount * 1.6F, 0.0F, 1.0F);
        long idleMillis = toMillis(ageInTicks / 20.0F);
        long walkMillis = toMillis(limbSwing / LIMB_SWING_PER_CYCLE * MOVE_CYCLE_SECONDS);

        // Body controller.
        float attack = GoatmanAnimationClient.attackSeconds(player, ageInTicks);
        if (attack >= 0.0F) {
            play(FadeHoundAnimations.ATTACK, toMillis(attack), 1.0F);
        } else {
            play(FadeHoundAnimations.IDLE, idleMillis, 1.0F - moving);
            play(FadeHoundAnimations.MOVE_BODY, walkMillis, moving);
        }

        // Legs controller.
        play(FadeHoundAnimations.IDLE_LEGS, idleMillis, 1.0F - moving);
        play(FadeHoundAnimations.MOVE_LEGS, walkMillis, moving);

        // The huge head follows the player's view, a little more stiffly than the Goatman's.
        head.yRot += Mth.clamp(netHeadYaw, -45.0F, 45.0F) * Mth.DEG_TO_RAD;
        head.xRot += Mth.clamp(headPitch, -40.0F, 40.0F) * 0.5F * Mth.DEG_TO_RAD;
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
