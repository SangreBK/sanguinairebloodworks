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
 * Nico's Goatman model (Blockbench 5.2.2) with his keyframe animations ({@link GoatmanAnimations}).
 * <p>
 * Two independent "controllers" run every frame, both on top of the rest pose:
 * <ul>
 *     <li><b>Body</b>: {@link GoatmanAnimations#ATTACK} or {@link GoatmanAnimations#ROAR} while one is playing,
 *     otherwise a blend of {@link GoatmanAnimations#IDLE} and {@link GoatmanAnimations#MOVE_BODY} by walking speed.</li>
 *     <li><b>Legs</b>: a blend of {@link GoatmanAnimations#IDLE_LEGS} and {@link GoatmanAnimations#MOVE_LEGS} by
 *     walking speed, always, so an attack or roar never freezes the stride.</li>
 * </ul>
 * The two never animate the same parts, so their offsets simply add up. Walking animations are timed by the
 * distance walked (vanilla's limb swing), so the stride speeds up when sprinting. The head then turns towards
 * where the player is looking.
 */
public class GoatmanModel extends HierarchicalModel<Player> {
    /** Vanilla limb swing covered by one walking cycle (the period of a vanilla biped's leg swing). */
    private static final float LIMB_SWING_PER_CYCLE = Mth.TWO_PI / 0.6662F;
    private static final float MOVE_CYCLE_SECONDS = 1.5F;

    private final Vector3f cache = new Vector3f();
    private final ModelPart root;
    private final ModelPart head;

    public GoatmanModel(ModelPart root) {
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

        PartDefinition main = partdefinition.addOrReplaceChild("main", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition head = main.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0.0F, -54.2764F, -3.9783F));

        PartDefinition nose = head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(0, 25).addBox(-4.5F, -3.5F, -11.0F, 9.0F, 7.0F, 16.0F, new CubeDeformation(0.0F))
        .texOffs(85, 0).addBox(-4.0F, -3.0F, -10.0F, 8.0F, 6.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.5529F, -2.7535F, 0.9599F, 0.0F, 0.0F));

        PartDefinition left_horn = head.addOrReplaceChild("left_horn", CubeListBuilder.create().texOffs(70, 13).addBox(-1.5F, -4.1667F, -1.5F, 3.0F, 9.0F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(50, 41).addBox(1.5F, -4.1667F, -1.5F, 6.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(70, 0).addBox(4.5F, -1.1667F, -1.5F, 3.0F, 10.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.74F, -7.1218F, 2.0502F, -0.6545F, 0.0F, 0.0F));

        PartDefinition righthorn = head.addOrReplaceChild("righthorn", CubeListBuilder.create().texOffs(70, 13).mirror().addBox(-1.5F, -4.1667F, -1.5F, 3.0F, 9.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false)
        .texOffs(50, 41).mirror().addBox(-7.5F, -4.1667F, -1.5F, 6.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false)
        .texOffs(70, 0).mirror().addBox(-7.5F, -1.1667F, -1.5F, 3.0F, 10.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-2.74F, -7.1218F, 2.0502F, -0.6545F, 0.0F, 0.0F));

        PartDefinition torso = main.addOrReplaceChild("torso", CubeListBuilder.create(), PartPose.offset(0.0F, -33.0F, 0.0F));

        PartDefinition uppertorso = torso.addOrReplaceChild("uppertorso", CubeListBuilder.create().texOffs(0, 0).addBox(-9.5F, -15.0F, -5.0F, 19.0F, 15.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -7.0F, 0.0F));

        PartDefinition lowertorso = torso.addOrReplaceChild("lowertorso", CubeListBuilder.create().texOffs(50, 25).addBox(-4.0F, -7.0F, -3.0F, 8.0F, 10.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition leftarm = main.addOrReplaceChild("leftarm", CubeListBuilder.create().texOffs(24, 48).addBox(0.0F, -2.5732F, -2.0732F, 5.0F, 25.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(44, 65).addBox(3.0F, -2.5732F, 2.9268F, 0.0F, 20.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(9.5F, -52.4268F, 0.0732F));

        PartDefinition rightupperhair2_r1 = leftarm.addOrReplaceChild("rightupperhair2_r1", CubeListBuilder.create().texOffs(44, 65).addBox(0.0F, -2.0F, -2.5F, 0.0F, 20.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0F, 7.4268F, 0.4268F, 0.0F, 1.5708F, 0.0F));

        PartDefinition leftarmlower = leftarm.addOrReplaceChild("leftarmlower", CubeListBuilder.create().texOffs(0, 48).addBox(-3.0F, -10.1951F, 0.4482F, 6.0F, 25.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(2.5F, 21.7683F, 2.125F));

        PartDefinition claw_r1 = leftarmlower.addOrReplaceChild("claw_r1", CubeListBuilder.create().texOffs(58, 0).addBox(5.0F, -6.0F, -1.5F, 4.0F, 12.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, 19.2317F, -2.1982F, 0.0F, -1.5708F, 0.0F));

        PartDefinition claw_r2 = leftarmlower.addOrReplaceChild("claw_r2", CubeListBuilder.create().texOffs(58, 0).addBox(-2.0F, 0.0F, -6.0F, 4.0F, 12.0F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(58, 0).addBox(-2.0F, 0.0F, -1.0F, 4.0F, 12.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5F, 13.2317F, 1.875F, 0.0F, 1.5708F, 0.0F));

        PartDefinition rightarm = main.addOrReplaceChild("rightarm", CubeListBuilder.create().texOffs(24, 48).mirror().addBox(-5.0F, -2.5732F, -2.0732F, 5.0F, 25.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false)
        .texOffs(44, 65).mirror().addBox(-3.0F, -2.5732F, 2.9268F, 0.0F, 20.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-9.5F, -52.4268F, 0.0732F));

        PartDefinition leftupperhair2_r1 = rightarm.addOrReplaceChild("leftupperhair2_r1", CubeListBuilder.create().texOffs(44, 65).mirror().addBox(0.0F, -2.0F, -2.5F, 0.0F, 20.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-7.0F, 7.4268F, 0.4268F, 0.0F, -1.5708F, 0.0F));

        PartDefinition rightarmlower = rightarm.addOrReplaceChild("rightarmlower", CubeListBuilder.create().texOffs(0, 48).mirror().addBox(-3.0F, -10.1951F, 0.4482F, 6.0F, 25.0F, 6.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-2.5F, 21.7683F, 2.125F));

        PartDefinition claw_r3 = rightarmlower.addOrReplaceChild("claw_r3", CubeListBuilder.create().texOffs(58, 0).mirror().addBox(-9.0F, -6.0F, -1.5F, 4.0F, 12.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.5F, 19.2317F, -2.1982F, 0.0F, 1.5708F, 0.0F));

        PartDefinition claw_r4 = rightarmlower.addOrReplaceChild("claw_r4", CubeListBuilder.create().texOffs(58, 0).mirror().addBox(-2.0F, 0.0F, -6.0F, 4.0F, 12.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false)
        .texOffs(58, 0).mirror().addBox(-2.0F, 0.0F, -1.0F, 4.0F, 12.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-2.5F, 13.2317F, 1.875F, 0.0F, -1.5708F, 0.0F));

        PartDefinition leftleg = main.addOrReplaceChild("leftleg", CubeListBuilder.create().texOffs(44, 48).addBox(-3.0F, -7.5F, -3.5F, 6.0F, 15.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(7.0F, -26.5F, 0.0F));

        PartDefinition lowerleftleg = leftleg.addOrReplaceChild("lowerleftleg", CubeListBuilder.create().texOffs(44, 65).addBox(0.25F, 3.25F, 3.375F, 0.0F, 20.0F, 5.0F, new CubeDeformation(0.0F))
        .texOffs(70, 41).addBox(-2.25F, -1.75F, 0.625F, 4.0F, 25.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.25F, 3.25F, 2.875F));

        PartDefinition rightleg = main.addOrReplaceChild("rightleg", CubeListBuilder.create().texOffs(44, 48).mirror().addBox(-3.0F, -7.5F, -3.5F, 6.0F, 15.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-7.0F, -26.5F, 0.0F));

        PartDefinition lowerrightleg = rightleg.addOrReplaceChild("lowerrightleg", CubeListBuilder.create().texOffs(44, 65).mirror().addBox(-0.25F, 3.25F, 3.375F, 0.0F, 20.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false)
        .texOffs(70, 41).mirror().addBox(-1.75F, -1.75F, 0.625F, 4.0F, 25.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-0.25F, 3.25F, 2.875F));

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
        float roar = GoatmanAnimationClient.roarSeconds(player, ageInTicks);
        if (attack >= 0.0F) {
            play(GoatmanAnimations.ATTACK, toMillis(attack), 1.0F);
        } else if (roar >= 0.0F) {
            play(GoatmanAnimations.ROAR, toMillis(roar), 1.0F);
        } else {
            play(GoatmanAnimations.IDLE, idleMillis, 1.0F - moving);
            play(GoatmanAnimations.MOVE_BODY, walkMillis, moving);
        }

        // Legs controller.
        play(GoatmanAnimations.IDLE_LEGS, idleMillis, 1.0F - moving);
        play(GoatmanAnimations.MOVE_LEGS, walkMillis, moving);

        // Look where the player looks, on top of whatever the animation does with the head.
        head.yRot += Mth.clamp(netHeadYaw, -70.0F, 70.0F) * Mth.DEG_TO_RAD;
        head.xRot += headPitch * 0.7F * Mth.DEG_TO_RAD;
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
