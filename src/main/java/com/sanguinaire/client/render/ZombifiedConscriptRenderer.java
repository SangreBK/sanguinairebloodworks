package com.sanguinaire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sanguinaire.Sanguinaire;
import com.sanguinaire.client.model.ZombifiedConscriptModel;
import com.sanguinaire.conscript.ZombifiedConscript;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Nico's Conscript model with the zombified texture ({@code textures/entity/zombified_conscript.png}); shakes while being cured. */
public class ZombifiedConscriptRenderer extends MobRenderer<ZombifiedConscript, ZombifiedConscriptModel> {
    private static final ResourceLocation TEXTURE = Sanguinaire.id("textures/entity/zombified_conscript.png");

    public ZombifiedConscriptRenderer(EntityRendererProvider.Context context) {
        super(context, new ZombifiedConscriptModel(context.bakeLayer(ModModelLayers.CONSCRIPT)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(ZombifiedConscript entity) {
        return TEXTURE;
    }

    @Override
    public void render(ZombifiedConscript entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight) {
        com.sanguinaire.client.model.ConscriptModel.setPotionsShown(getModel().root(), entity.getPotions());
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    protected void scale(ZombifiedConscript entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F); // same as every illager
    }

    @Override
    protected boolean isShaking(ZombifiedConscript entity) {
        return super.isShaking(entity) || entity.isCuring();
    }
}
