package com.sanguinaire.client.render;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.conscript.ConscriptEntity;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Placeholder look for the Conscript: the vanilla illager body with its own uniform texture
 * ({@code textures/entity/conscript.png}), holding its weapon at all times like a pillager.
 */
public class ConscriptRenderer extends IllagerRenderer<ConscriptEntity> {
    private static final ResourceLocation TEXTURE = Sanguinaire.id("textures/entity/conscript.png");

    public ConscriptRenderer(EntityRendererProvider.Context context) {
        super(context, new IllagerModel<>(context.bakeLayer(ModelLayers.PILLAGER)), 0.5F);
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(ConscriptEntity entity) {
        return TEXTURE;
    }
}
