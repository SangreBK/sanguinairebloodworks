package com.sanguinaire.client;

import com.sanguinaire.item.VileCaneItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.client.model.FadeHoundModel;
import com.sanguinaire.client.model.GoatmanModel;
import com.sanguinaire.client.model.RottingAbominationModel;
import com.sanguinaire.client.render.ModModelLayers;
import com.sanguinaire.client.screen.ResearchStationScreen;
import com.sanguinaire.client.screen.SanguineAlembicScreen;
import com.sanguinaire.client.screen.SerumBriefcaseScreen;
import com.sanguinaire.client.screen.SerumSynthesizerScreen;
import com.sanguinaire.item.BloodSampleItem;
import com.sanguinaire.item.SerumItem;
import com.sanguinaire.registry.ModItems;
import com.sanguinaire.registry.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import com.sanguinaire.client.render.ResearchStationRenderer;
import com.sanguinaire.client.render.ConscriptRenderer;
import com.sanguinaire.registry.ModEntities;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import com.sanguinaire.registry.ModBlockEntities;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only mod-bus setup. {@code value = Dist.CLIENT} keeps this class (and the screen classes it references)
 * from ever being loaded on a dedicated server.
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    /** Tint index of the liquid layer ({@code layer1}) in the serum item model. */
    private static final int SERUM_LIQUID_TINT_INDEX = 1;

    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.SERUM_BRIEFCASE.get(), SerumBriefcaseScreen::new);
            // Vile Cane: 0 = cane model, 1 = sword model.
            ItemProperties.register(ModItems.VILE_CANE.get(), new ResourceLocation(Sanguinaire.MOD_ID, "sword_mode"),
                    (stack, level, entity, seed) -> VileCaneItem.isSwordMode(stack) ? 1.0F : 0.0F);
            MenuScreens.register(ModMenuTypes.RESEARCH_STATION.get(), ResearchStationScreen::new);
            MenuScreens.register(ModMenuTypes.SERUM_SYNTHESIZER.get(), SerumSynthesizerScreen::new);
            MenuScreens.register(ModMenuTypes.SANGUINE_ALEMBIC.get(), SanguineAlembicScreen::new);
        });
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.RESEARCH_STATION.get(), ResearchStationRenderer::new);
        event.registerEntityRenderer(ModEntities.CONSCRIPT.get(), ConscriptRenderer::new);
        event.registerEntityRenderer(ModEntities.NULLIFICATION_POTION.get(), ThrownItemRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ModModelLayers.GOATMAN, GoatmanModel::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.ROTTING_ABOMINATION, RottingAbominationModel::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.FADEHOUND, FadeHoundModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeyMappings.MUTATION_ABILITY);
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == SERUM_LIQUID_TINT_INDEX ? SerumItem.getLiquidColor(stack) : -1,
                ModItems.SERUM.get());
        event.register((stack, tintIndex) -> tintIndex == SERUM_LIQUID_TINT_INDEX ? BloodSampleItem.getLiquidColor(stack) : -1,
                ModItems.BLOOD_SAMPLE.get());
    }
}
