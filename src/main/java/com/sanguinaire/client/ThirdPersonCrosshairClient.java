package com.sanguinaire.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.sanguinaire.Sanguinaire;
import com.sanguinaire.mutation.TransformationManager;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Shows the crosshair while transformed and viewing from behind (third person back), where vanilla hides it, so
 * attacks can still be aimed. Nothing about the camera is touched.
 * <p>
 * Kept deliberately unintrusive:
 * <ul>
 *     <li>It draws only after vanilla's own crosshair layer has run, so if another mod cancels or replaces that layer
 *     (Forge fires no Post event for a cancelled layer), nothing is drawn here either.</li>
 *     <li>First person and the front view are left to vanilla; the debug crosshair (F3), hidden GUI (F1) and
 *     spectator mode are respected.</li>
 *     <li>Camera mods that already draw their own third-person crosshair are detected and left alone.</li>
 * </ul>
 * The sprite and blend mode are vanilla's, so it looks identical to the first-person crosshair.
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID, value = Dist.CLIENT)
public final class ThirdPersonCrosshairClient {
    private static final ResourceLocation GUI_ICONS = new ResourceLocation("minecraft", "textures/gui/icons.png");
    private static final int SIZE = 15;
    /** Mods with their own third-person crosshair: drawing ours as well would double it. */
    private static final List<String> CROSSHAIR_MODS = List.of("shouldersurfing", "leawind_third_person", "betterthirdperson");

    private static Boolean otherCrosshairMod;

    private ThirdPersonCrosshairClient() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onOverlayPost(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.options.renderDebug
                || minecraft.player.isSpectator()
                || minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK
                || minecraft.getCameraEntity() != minecraft.player
                || !TransformationManager.isTransformed(minecraft.player)
                || hasOtherCrosshairMod()) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int x = (event.getWindow().getGuiScaledWidth() - SIZE) / 2;
        int y = (event.getWindow().getGuiScaledHeight() - SIZE) / 2;
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        graphics.blit(GUI_ICONS, x, y, 0, 0, SIZE, SIZE);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static boolean hasOtherCrosshairMod() {
        if (otherCrosshairMod == null) {
            otherCrosshairMod = CROSSHAIR_MODS.stream().anyMatch(ModList.get()::isLoaded);
        }
        return otherCrosshairMod;
    }
}
