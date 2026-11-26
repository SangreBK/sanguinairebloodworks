package com.sanguinaire.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.sanguinaire.Sanguinaire;
import com.sanguinaire.mutation.TransformationManager;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
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
 * attacks can still be aimed, together with vanilla's attack-strength meter under it (so you can see when a creature
 * is in reach and the hit is charged). Nothing about the camera is touched.
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
        renderAttackIndicator(minecraft, graphics, event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * Vanilla's attack-strength meter under the crosshair (Gui#renderCrosshair), when the player's Attack Indicator
     * option is set to Crosshair: a filling bar while the attack recharges, and the sword icon when a living creature
     * is under the crosshair, within reach, and the attack is fully charged.
     */
    private static void renderAttackIndicator(Minecraft minecraft, GuiGraphics graphics, int width, int height) {
        if (minecraft.options.attackIndicator().get() != AttackIndicatorStatus.CROSSHAIR || minecraft.player == null) {
            return;
        }
        float strength = minecraft.player.getAttackStrengthScale(0.0F);
        boolean ready = false;
        if (minecraft.crosshairPickEntity instanceof LivingEntity target && strength >= 1.0F) {
            ready = minecraft.player.getCurrentItemAttackStrengthDelay() > 5.0F && target.isAlive();
        }
        int y = height / 2 - 7 + 16;
        int x = width / 2 - 8;
        if (ready) {
            graphics.blit(GUI_ICONS, x, y, 68, 94, 16, 16);
        } else if (strength < 1.0F) {
            int filled = (int) (strength * 17.0F);
            graphics.blit(GUI_ICONS, x, y, 36, 94, 16, 4);
            graphics.blit(GUI_ICONS, x, y, 52, 94, filled, 4);
        }
    }

    private static boolean hasOtherCrosshairMod() {
        if (otherCrosshairMod == null) {
            otherCrosshairMod = CROSSHAIR_MODS.stream().anyMatch(ModList.get()::isLoaded);
        }
        return otherCrosshairMod;
    }
}
