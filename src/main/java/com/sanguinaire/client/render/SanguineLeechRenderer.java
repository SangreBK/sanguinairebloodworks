package com.sanguinaire.client.render;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.client.model.SanguineLeechModel;
import com.sanguinaire.leech.SanguineLeech;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** Nico's leech textures; the eyes ({@code sanguine_leech_eyes.png}) glow in the dark like a spider's. */
public class SanguineLeechRenderer extends MobRenderer<SanguineLeech, SanguineLeechModel> {
    private static final ResourceLocation TEXTURE = Sanguinaire.id("textures/entity/sanguine_leech.png");
    private static final RenderType EYES = RenderType.eyes(Sanguinaire.id("textures/entity/sanguine_leech_eyes.png"));

    public SanguineLeechRenderer(EntityRendererProvider.Context context) {
        super(context, new SanguineLeechModel(context.bakeLayer(ModModelLayers.SANGUINE_LEECH)), 0.25F);
        addLayer(new LeechEyesLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(SanguineLeech entity) {
        return TEXTURE;
    }

    @Override
    protected float getFlipDegrees(SanguineLeech entity) {
        return 180.0F; // curls onto its back when it dies, like a silverfish
    }

    private static final class LeechEyesLayer extends EyesLayer<SanguineLeech, SanguineLeechModel> {
        LeechEyesLayer(RenderLayerParent<SanguineLeech, SanguineLeechModel> parent) {
            super(parent);
        }

        @Override
        public RenderType renderType() {
            return EYES;
        }
    }
}
