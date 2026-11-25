package com.sanguinaire.client.render;

import com.sanguinaire.Sanguinaire;
import net.minecraft.client.model.geom.ModelLayerLocation;

/** Model layers for the custom transformation creatures (registered in {@code ClientModEvents}). */
public final class ModModelLayers {
    public static final ModelLayerLocation GOATMAN = new ModelLayerLocation(Sanguinaire.id("goatman"), "main");
    public static final ModelLayerLocation ROTTING_ABOMINATION = new ModelLayerLocation(Sanguinaire.id("rotting_abomination"), "main");
    public static final ModelLayerLocation CONSCRIPT = new ModelLayerLocation(Sanguinaire.id("conscript"), "main");
    public static final ModelLayerLocation FADEHOUND = new ModelLayerLocation(Sanguinaire.id("fadehound"), "main");

    private ModModelLayers() {
    }
}
