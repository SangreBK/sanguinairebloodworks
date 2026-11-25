package com.sanguinaire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sanguinaire.Sanguinaire;
import com.sanguinaire.conscript.ConscriptEntity;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.AbstractIllager;

/**
 * The Conscript: Nico's model ({@link ModModelLayers#CONSCRIPT}) animated like a vindicator/pillager by the vanilla
 * illager model. Arms are crossed (and the weapon hidden) while an axe-wielder is at ease, like a vindicator; otherwise
 * the held item (weapon, potion, bread, golden apple) is shown in hand.
 */
public class ConscriptRenderer extends IllagerRenderer<ConscriptEntity> {
    private static final ResourceLocation TEXTURE = Sanguinaire.id("textures/entity/conscript.png");

    public ConscriptRenderer(EntityRendererProvider.Context context) {
        super(context, new IllagerModel<>(context.bakeLayer(ModModelLayers.CONSCRIPT)), 0.5F);
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()) {
            @Override
            public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, ConscriptEntity entity,
                               float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                               float netHeadYaw, float headPitch) {
                if (entity.getArmPose() != AbstractIllager.IllagerArmPose.CROSSED) {
                    super.render(poseStack, buffer, packedLight, entity, limbSwing, limbSwingAmount, partialTick,
                            ageInTicks, netHeadYaw, headPitch);
                }
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(ConscriptEntity entity) {
        return TEXTURE;
    }
}
