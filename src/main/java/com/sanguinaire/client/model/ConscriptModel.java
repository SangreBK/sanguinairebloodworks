package com.sanguinaire.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Nico's Conscript model (Blockbench, vindicator proportions and part names), used by both the Conscript (animated by
 * vanilla {@link net.minecraft.client.model.IllagerModel}) and the Zombified Conscript ({@link ZombifiedConscriptModel}).
 * <p>
 * Differences from the Blockbench export, needed for the illager animations: the nose is a child of the head (so it
 * turns with it), the head has an empty "hat" part (the illager model expects one), and the crossed arms are a single
 * part (same boxes as the export's "arms" + "mirrored").
 * <p>
 * The body carries a potion belt ({@code body/potionbelt/potion1..3}); the renderers hide the bottles the Conscript has
 * already thrown ({@link #setPotionsShown}).
 */
public final class ConscriptModel {
    private ConscriptModel() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));
        head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(24, 0)
                .addBox(-1.0F, -1.0F, -6.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -2.0F, 0.0F));
        head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 20)
                .addBox(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(0, 38).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 20.0F, 6.0F, new CubeDeformation(0.5F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition potionbelt = body.addOrReplaceChild("potionbelt", CubeListBuilder.create(), PartPose.offset(-0.2949F, 4.1405F, -3.1187F));
        potionbelt.addOrReplaceChild("belt_r1", CubeListBuilder.create().texOffs(34, 3)
                .addBox(-6.0F, -2.0F, -1.5F, 12.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.3949F, 0.9095F, 1.5937F, 0.0F, 0.0F, -0.9381F));
        PartDefinition potion1 = potionbelt.addOrReplaceChild("potion1", CubeListBuilder.create(), PartPose.offset(1.826F, -2.6912F, -0.0312F));
        potion1.addOrReplaceChild("beltpotion_r1", CubeListBuilder.create().texOffs(34, 7)
                .addBox(-2.25F, -1.975F, 0.875F, 2.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.1222F, -0.7272F, -0.875F, 0.0F, 0.0F, -0.9381F));
        PartDefinition potion2 = potionbelt.addOrReplaceChild("potion2", CubeListBuilder.create(), PartPose.offset(0.0521F, -0.2718F, -0.0312F));
        potion2.addOrReplaceChild("beltpotion_r2", CubeListBuilder.create().texOffs(34, 7)
                .addBox(-5.25F, -1.975F, 0.875F, 2.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.8961F, -3.1465F, -0.875F, 0.0F, 0.0F, -0.9381F));
        PartDefinition potion3 = potionbelt.addOrReplaceChild("potion3", CubeListBuilder.create(), PartPose.offset(-1.8697F, 2.3491F, -0.0312F));
        potion3.addOrReplaceChild("beltpotion_r3", CubeListBuilder.create().texOffs(34, 7)
                .addBox(-8.5F, -1.975F, 0.875F, 2.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.8179F, -5.7675F, -0.875F, 0.0F, 0.0F, -0.9381F));

        root.addOrReplaceChild("arms", CubeListBuilder.create()
                .texOffs(29, 39).addBox(-4.0F, 2.0F, -2.0F, 8.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(48, 52).addBox(-8.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(48, 52).mirror().addBox(4.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offsetAndRotation(0.0F, 2.95F, -1.05F, -0.7505F, 0.0F, 0.0F));

        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(29, 47).mirror()
                .addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(29, 47)
                .addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 22).mirror()
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(2.0F, 12.0F, 0.0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 22)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.0F, 12.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    /**
     * Shows as many belt bottles as there are potions left; the first one thrown is {@code potion1}, then 2, then 3.
     * The model is shared by every Conscript, so this is set before each one is drawn.
     */
    public static void setPotionsShown(ModelPart root, int potions) {
        ModelPart belt = root.getChild("body").getChild("potionbelt");
        for (int i = 1; i <= 3; i++) {
            belt.getChild("potion" + i).visible = i > 3 - potions;
        }
    }
}
