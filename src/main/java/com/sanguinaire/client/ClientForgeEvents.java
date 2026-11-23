package com.sanguinaire.client;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.mutation.TransformationManager;
import com.sanguinaire.player.PlayerMutationData;
import com.sanguinaire.player.PlayerMutationProvider;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

import java.util.Optional;

/** Client-only Forge-bus handlers: transformation camera rules and the Weakened Hearts HUD. */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private static final ResourceLocation WEAKENED_HEARTS = Sanguinaire.id("textures/gui/weakened_hearts.png");
    private static final int HEART_SIZE = 9;
    private static final int TEXTURE_WIDTH = 64;
    private static final int TEXTURE_HEIGHT = 16;
    private static final ResourceLocation VANILLA_ICONS = new ResourceLocation("textures/gui/icons.png");
    /** Above this max health (two full rows of hearts) the compact health bar is used. */
    private static final float COMPACT_HEALTH_THRESHOLD = 40.0F;
    private static final int CONTAINER_U = 0;
    private static final int FILLED_U = 9;
    private static final int CONTAINER_BLINK_U = 18;
    private static final int FILLED_BLINK_U = 27;
    /** Width of the left half of a heart sprite; the right half is the remaining 4 pixels. */
    private static final int LEFT_HALF_WIDTH = 5;

    /** {@link ForgeGui#leftHeight} just before the vanilla health bar is drawn, i.e. the health bar's anchor. */
    private static int healthLeftHeight = -1;
    /** Whether vanilla's damage blink is in its "on" phase this frame (computed before vanilla updates it). */
    private static boolean healthBlink;
    /** Gui#tickCount captured before the health bar renders. */
    private static int guiTickCount;

    /* Private Gui fields read to keep the overlay in lock-step with vanilla's heart animation (SRG names). */
    private static final Field GUI_TICK_COUNT = findGuiField("f_92989_");
    private static final Field GUI_DISPLAY_HEALTH = findGuiField("f_92974_");
    private static final Field GUI_HEALTH_BLINK_TIME = findGuiField("f_92976_");

    private ClientForgeEvents() {
    }

    /**
     * While transformed the player is locked out of first person: F5 still cycles between the rear and front
     * third-person views, but first person is skipped until the transformation ends.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (TransformationManager.isTransformed(player) && minecraft.options.getCameraType().isFirstPerson()) {
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    @Nullable
    private static Field findGuiField(String srgName) {
        try {
            return ObfuscationReflectionHelper.findField(Gui.class, srgName);
        } catch (ObfuscationReflectionHelper.UnableToFindFieldException exception) {
            Sanguinaire.LOGGER.warn("Weakened Hearts overlay cannot read Gui field {}; hearts will not animate", srgName);
            return null;
        }
    }

    private static long readLong(@Nullable Field field, Object owner) {
        try {
            return field == null ? 0L : ((Number) field.get(owner)).longValue();
        } catch (IllegalAccessException exception) {
            return 0L;
        }
    }

    /** Research belongs to the world being left; never carry it into another world or server. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientResearchCache.clear();
    }

    @SubscribeEvent
    public static void onRenderOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
                && Minecraft.getInstance().gui instanceof ForgeGui gui) {
            if (renderCompactHealth(event, gui)) {
                event.setCanceled(true);
                healthLeftHeight = -1;
                return;
            }
            healthLeftHeight = gui.leftHeight;
            // Same formula and timing as ForgeGui#renderHealth, which reads these before updating them.
            guiTickCount = (int) readLong(GUI_TICK_COUNT, gui);
            long blinkTime = readLong(GUI_HEALTH_BLINK_TIME, gui);
            healthBlink = blinkTime > guiTickCount && (blinkTime - guiTickCount) / 3L % 2L == 1L;
        }
    }

    /** Draws pale-purple Weakened Hearts over the top of the vanilla health bar. Hidden while transformed. */
    @SubscribeEvent
    public static void onRenderOverlayPost(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_HEALTH.id()) || healthLeftHeight < 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || minecraft.gameMode == null || !minecraft.gameMode.canHurtPlayer()) {
            return;
        }
        Optional<PlayerMutationData> optional = PlayerMutationProvider.get(player);
        if (optional.isEmpty() || optional.get().areWeakenedHeartsSuppressed() || !optional.get().hasWeakenedHearts()) {
            return;
        }
        renderWeakenedHearts(event.getGuiGraphics(), player, optional.get(),
                event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
    }

    /**
     * Very large health pools (the Goatman, Rotting Abomination, Warden...) would stack a dozen rows of hearts over
     * the screen. Above {@link #COMPACT_HEALTH_THRESHOLD} max health the vanilla bar is replaced by a single row of
     * ten hearts showing the <em>fraction</em> of health left, with the exact "current / max" value above it.
     *
     * @return true if the compact bar was drawn (the vanilla bar must then be skipped)
     */
    private static boolean renderCompactHealth(RenderGuiOverlayEvent.Pre event, ForgeGui gui) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || minecraft.gameMode == null || !minecraft.gameMode.canHurtPlayer()
                || player.getMaxHealth() <= COMPACT_HEALTH_THRESHOLD) {
            return false;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int left = event.getWindow().getGuiScaledWidth() / 2 - 91;
        int top = event.getWindow().getGuiScaledHeight() - gui.leftHeight;
        float max = player.getMaxHealth();
        float health = Mth.clamp(player.getHealth(), 0.0F, max);
        int halves = health <= 0.0F ? 0 : Math.max(1, Mth.ceil(health / max * 20.0F));
        for (int heart = 0; heart < 10; heart++) {
            int x = left + heart * 8;
            graphics.blit(VANILLA_ICONS, x, top, 16, 0, HEART_SIZE, HEART_SIZE);
            int filled = halves - heart * 2;
            if (filled >= 2) {
                graphics.blit(VANILLA_ICONS, x, top, 52, 0, HEART_SIZE, HEART_SIZE);
            } else if (filled == 1) {
                graphics.blit(VANILLA_ICONS, x, top, 61, 0, HEART_SIZE, HEART_SIZE);
            }
        }
        Font font = minecraft.font;
        MutableComponent label = Component.literal(Mth.ceil(health) + " / " + Mth.ceil(max)).withStyle(ChatFormatting.DARK_RED);
        int absorption = Mth.ceil(player.getAbsorptionAmount());
        if (absorption > 0) {
            label.append(Component.literal(" +" + absorption).withStyle(ChatFormatting.GOLD));
        }
        graphics.drawString(font, label, left, top - font.lineHeight - 1, 0xFFFFFF, true);
        gui.leftHeight += 10 + font.lineHeight + 1;
        return true;
    }

    /**
     * Uses exactly the layout and animation of the vanilla/Forge health bar so the overlay moves with it: rows of
     * 10 hearts, the low-health shake (same seeded random, consumed in the same order), the Regeneration bob, and
     * the white damage/heal blink.
     */
    private static void renderWeakenedHearts(GuiGraphics graphics, LocalPlayer player, PlayerMutationData data, int width, int height) {
        Gui gui = Minecraft.getInstance().gui;
        float maxHealth = player.getMaxHealth();
        int health = Mth.ceil(player.getHealth());
        int displayHealth = (int) readLong(GUI_DISPLAY_HEALTH, gui);
        int absorption = Mth.ceil(player.getAbsorptionAmount());
        float healthMax = Math.max(maxHealth, Math.max(displayHealth, health));
        int healthRows = Mth.ceil((healthMax + absorption) / 2.0F / 10.0F);
        int rowHeight = Math.max(10 - (healthRows - 2), 3);
        int left = width / 2 - 91;
        int top = height - healthLeftHeight;
        int regen = player.hasEffect(MobEffects.REGENERATION) ? guiTickCount % Mth.ceil(healthMax + 5.0F) : -1;
        boolean shake = health + absorption <= 4;
        // Same seed expression as ForgeGui, including its int overflow.
        RandomSource random = RandomSource.create((long) (guiTickCount * 312871));

        int maxPoints = Mth.ceil(maxHealth);
        float weakenedStart = Math.max(0.0F, maxHealth - data.getWeakenedHealth());
        int healthHearts = Mth.ceil(healthMax / 2.0F);
        int absorptionHearts = Mth.ceil(absorption / 2.0F);

        // Vanilla draws hearts from the last to the first, drawing one random number per heart while shaking.
        for (int heart = healthHearts + absorptionHearts - 1; heart >= 0; heart--) {
            int x = left + (heart % 10) * 8;
            int y = top - (heart / 10) * rowHeight;
            if (shake) {
                y += random.nextInt(2);
            }
            if (heart < healthHearts && heart == regen) {
                y -= 2;
            }
            if (heart >= healthHearts) {
                continue; // absorption hearts are never weakened
            }
            int leftPoint = heart * 2;
            int rightPoint = leftPoint + 1;
            drawPoint(graphics, x, y, leftPoint, true, maxPoints, weakenedStart, health, displayHealth);
            drawPoint(graphics, x, y, rightPoint, false, maxPoints, weakenedStart, health, displayHealth);
        }
    }

    private static void drawPoint(GuiGraphics graphics, int x, int y, int point, boolean leftHalf, int maxPoints,
                                  float weakenedStart, int health, int displayHealth) {
        if (point >= maxPoints || point < weakenedStart) {
            return;
        }
        drawHalf(graphics, x, y, healthBlink ? CONTAINER_BLINK_U : CONTAINER_U, leftHalf);
        if (healthBlink && point < displayHealth) {
            drawHalf(graphics, x, y, FILLED_BLINK_U, leftHalf);
        }
        if (point < health) {
            drawHalf(graphics, x, y, FILLED_U, leftHalf);
        }
    }

    private static void drawHalf(GuiGraphics graphics, int x, int y, int u, boolean leftHalf) {
        int offset = leftHalf ? 0 : LEFT_HALF_WIDTH;
        int halfWidth = leftHalf ? LEFT_HALF_WIDTH : HEART_SIZE - LEFT_HALF_WIDTH;
        graphics.blit(WEAKENED_HEARTS, x + offset, y, u + offset, 0, halfWidth, HEART_SIZE, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }
}
