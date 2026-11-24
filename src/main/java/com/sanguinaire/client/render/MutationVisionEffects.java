package com.sanguinaire.client.render;

import java.lang.reflect.Field;

import java.util.Map;

import java.util.IdentityHashMap;

import java.util.ArrayList;

import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import net.minecraftforge.client.event.RenderLevelStageEvent;

import net.minecraft.client.renderer.entity.EntityRenderer;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.mutation.MutationType;
import com.sanguinaire.mutation.TransformationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * How the world looks to the local player in certain forms.
 * <ul>
 *     <li><b>Warden</b> (design §4.11): "permanently blind, can only see creatures that move". The world sinks into
 *     a thick, dark fog; living creatures that are standing still are not drawn at all; creatures that move are
 *     drawn normally with a glowing blue silhouette, only while they move.</li>
 *     <li><b>Enderman</b> (design §4.12): all colours are inverted, using the same post-processing shader as
 *     spectating an Enderman.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID, value = Dist.CLIENT)
public final class MutationVisionEffects {
    private static final ResourceLocation WHITE = Sanguinaire.id("textures/misc/white.png");
    private static final String INVERT_SHADER = "minecraft:shaders/post/invert.json";
    private static final float WARDEN_FOG_END = 14.0F;
    private static final double MOVING_THRESHOLD_SQR = 1.0E-4D;
    private static final int HIGHLIGHT_RED = 40;
    private static final int HIGHLIGHT_GREEN = 140;
    private static final int HIGHLIGHT_BLUE = 255;
    private static final int HIGHLIGHT_ALPHA = 150;

    /** EntityRenderer#shadowRadius (SRG f_114477_). */
    private static final Field SHADOW_RADIUS = ObfuscationReflectionHelper.findField(EntityRenderer.class, "f_114477_");
    /** Renderers whose shadow is switched off right now, with their real radius. */
    private static final Map<EntityRenderer<?>, Float> HIDDEN_SHADOWS = new IdentityHashMap<>();

    private static boolean renderingHighlight;
    private static boolean invertActive;
    @Nullable
    private static EnderMan invertSource;

    private MutationVisionEffects() {
    }

    private static boolean localIs(MutationType type) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && TransformationManager.getActive(player) == type;
    }

    private static boolean isMoving(LivingEntity entity) {
        double dx = entity.getX() - entity.xo;
        double dy = entity.getY() - entity.yo;
        double dz = entity.getZ() - entity.zo;
        return dx * dx + dy * dy + dz * dz > MOVING_THRESHOLD_SQR;
    }

    // ---- Warden vision ---------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (renderingHighlight || !localIs(MutationType.WARDEN)) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity == Minecraft.getInstance().player || TransformationRenderer.isPuppet(entity)) {
            return;
        }
        if (!isMoving(entity)) {
            event.setCanceled(true);
            hideShadow(event.getRenderer());
        } else {
            restoreShadow(event.getRenderer());
        }
    }

    /**
     * The entity dispatcher draws the shadow after the renderer, even when the render event was cancelled, so a
     * still creature's shadow would give it away. Its renderer's shadow radius is zeroed while it is hidden and put
     * back as soon as that renderer draws a visible creature, and in any case once all entities are drawn.
     */
    private static void hideShadow(EntityRenderer<?> renderer) {
        try {
            if (!HIDDEN_SHADOWS.containsKey(renderer)) {
                HIDDEN_SHADOWS.put(renderer, SHADOW_RADIUS.getFloat(renderer));
            }
            SHADOW_RADIUS.setFloat(renderer, 0.0F);
        } catch (IllegalAccessException ignored) {
            // leave the shadow
        }
    }

    private static void restoreShadow(EntityRenderer<?> renderer) {
        Float radius = HIDDEN_SHADOWS.remove(renderer);
        if (radius != null) {
            try {
                SHADOW_RADIUS.setFloat(renderer, radius);
            } catch (IllegalAccessException ignored) {
                // nothing more to do
            }
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES && !HIDDEN_SHADOWS.isEmpty()) {
            for (EntityRenderer<?> renderer : new ArrayList<>(HIDDEN_SHADOWS.keySet())) {
                restoreShadow(renderer);
            }
        }
    }

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (renderingHighlight || !localIs(MutationType.WARDEN)) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity == Minecraft.getInstance().player || TransformationRenderer.isPuppet(entity) || !isMoving(entity)) {
            return;
        }
        LivingEntityRenderer renderer = event.getRenderer();
        float partialTick = event.getPartialTick();
        float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        renderingHighlight = true;
        try {
            renderer.render(entity, yaw, partialTick, event.getPoseStack(),
                    new TintedBufferSource(event.getMultiBufferSource(), WHITE, HIGHLIGHT_RED, HIGHLIGHT_GREEN, HIGHLIGHT_BLUE, HIGHLIGHT_ALPHA),
                    event.getPackedLight());
        } finally {
            renderingHighlight = false;
        }
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (localIs(MutationType.WARDEN)) {
            event.setNearPlaneDistance(0.0F);
            event.setFarPlaneDistance(WARDEN_FOG_END);
            event.setCanceled(true); // required for Forge to apply the new distances
        }
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (localIs(MutationType.WARDEN)) {
            event.setRed(0.01F);
            event.setGreen(0.02F);
            event.setBlue(0.05F);
        }
    }

    // ---- Enderman vision -------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        GameRenderer gameRenderer = minecraft.gameRenderer;
        boolean wantInvert = minecraft.level != null && localIs(MutationType.ENDERMAN);
        PostChain current = gameRenderer.currentEffect();
        boolean hasInvert = current != null && INVERT_SHADER.equals(current.getName());

        if (wantInvert && !hasInvert) {
            if (invertSource == null || invertSource.level() != minecraft.level) {
                invertSource = EntityType.ENDERMAN.create(minecraft.level);
            }
            if (invertSource != null) {
                // The vanilla spectator hook loads the invert shader for Endermen.
                gameRenderer.checkEntityPostEffect(invertSource);
                invertActive = true;
            }
        } else if (!wantInvert && invertActive) {
            if (hasInvert) {
                gameRenderer.shutdownEffect();
            }
            invertActive = false;
            invertSource = null;
        }
    }
}
