package com.sanguinaire.client;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.client.render.TransformationRenderer;
import com.sanguinaire.mutation.MutationAbilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Client-side presentation of the original transformations' abilities. Purely visual; the server has already
 * applied every gameplay effect.
 * <ul>
 *     <li><b>Goatman roar</b>: a shockwave ring of dust from the Goatman's feet; anyone within reach feels it: the
 *     camera shakes, the view lurches inward and the screen edges throb blood red after a pale flash.</li>
 *     <li><b>Abomination vomit</b>: a gushing stream of bile and rotten chunks thrown forward for half a second.</li>
 *     <li><b>Fade Hound dash</b>: for the dasher, the view stretches outward as if pulled through the dark, with the
 *     screen edges blacked out for a moment (the smoke trail itself is spawned by the server for everyone).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID, value = Dist.CLIENT)
public final class AbilityEffectsClient {
    private static final double ROAR_FELT_RANGE = 14.0D;
    private static final int ROAR_SHAKE_TICKS = 16;
    private static final int ROAR_PULSE_TICKS = 18;
    private static final int DASH_TICKS = 10;
    private static final int SPLIT_TICKS = MutationAbilities.FADE_SPLIT_TICKS;

    private static final RandomSource RANDOM = RandomSource.create();
    private static final ItemParticleOption BILE = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SLIME_BALL));
    private static final ItemParticleOption CHUNKS = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.ROTTEN_FLESH));

    /** Entity id → ticks of vomit left. */
    private static final Map<Integer, Integer> VOMIT_STREAMS = new HashMap<>();
    /** Entity id → ticks of Fade Hound split left (shadow trails). */
    private static final Map<Integer, Integer> SPLIT_TRAILS = new HashMap<>();

    private static int shakeTicks;
    private static float shakeStrength;
    private static int roarPulseTicks;
    private static int dashTicks;
    private static int splitTicks;

    private AbilityEffectsClient() {
    }

    public static void onAbility(int entityId, int kind, int durationTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(entityId);
        if (entity == null) {
            return;
        }
        boolean self = entity == minecraft.player;
        switch (kind) {
            case MutationAbilities.ANIMATION_GOATMAN_ROAR -> {
                GoatmanAnimationClient.onRoar(entityId);
                spawnShockwave(level, entity);
                if (minecraft.player != null && minecraft.player.distanceTo(entity) <= ROAR_FELT_RANGE) {
                    float closeness = self ? 1.0F : 1.0F - minecraft.player.distanceTo(entity) / (float) ROAR_FELT_RANGE;
                    shakeTicks = ROAR_SHAKE_TICKS;
                    shakeStrength = Math.max(shakeStrength * (shakeTicks / (float) ROAR_SHAKE_TICKS), 1.5F + 2.5F * closeness);
                    roarPulseTicks = ROAR_PULSE_TICKS;
                }
            }
            case MutationAbilities.ANIMATION_ABOMINATION_VOMIT -> VOMIT_STREAMS.put(entityId, Math.max(1, durationTicks));
            case MutationAbilities.ANIMATION_FADEHOUND_DASH -> {
                if (self) {
                    dashTicks = DASH_TICKS;
                }
            }
            case MutationAbilities.ANIMATION_SERUM_INJECT -> {
                if (self) {
                    ClientTransformationEffects.onSerumInjected();
                }
            }
            case MutationAbilities.ANIMATION_FADEHOUND_SPLIT -> {
                SPLIT_TRAILS.put(entityId, SPLIT_TICKS);
                if (self) {
                    splitTicks = SPLIT_TICKS;
                }
            }
            default -> {
            }
        }
    }

    /** A ring of dust and cloud bursting outward along the ground. */
    private static void spawnShockwave(ClientLevel level, Entity source) {
        double y = source.getY() + 0.15D;
        int points = 64;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0D) * i / points;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            level.addParticle(ParticleTypes.CLOUD, source.getX() + dx * 0.6D, y, source.getZ() + dz * 0.6D,
                    dx * 0.55D, 0.02D, dz * 0.55D);
            if (i % 2 == 0) {
                level.addParticle(ParticleTypes.POOF, source.getX() + dx * 0.4D, y + 0.3D, source.getZ() + dz * 0.4D,
                        dx * 0.35D, 0.05D, dz * 0.35D);
            }
        }
        level.addParticle(ParticleTypes.EXPLOSION, source.getX(), source.getY() + source.getBbHeight() * 0.6D, source.getZ(), 0.0D, 0.0D, 0.0D);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        if (shakeTicks > 0) {
            shakeTicks--;
        }
        if (roarPulseTicks > 0) {
            roarPulseTicks--;
        }
        if (dashTicks > 0) {
            dashTicks--;
        }
        if (splitTicks > 0) {
            splitTicks--;
        }
        Iterator<Map.Entry<Integer, Integer>> trails = SPLIT_TRAILS.entrySet().iterator();
        while (trails.hasNext()) {
            Map.Entry<Integer, Integer> trail = trails.next();
            Entity entity = minecraft.level.getEntity(trail.getKey());
            if (!(entity instanceof LivingEntity living) || trail.getValue() <= 0) {
                trails.remove();
                continue;
            }
            spawnSplitTrails(minecraft.level, living, 1.0F - trail.getValue() / (float) SPLIT_TICKS);
            trail.setValue(trail.getValue() - 1);
        }
        Iterator<Map.Entry<Integer, Integer>> iterator = VOMIT_STREAMS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Integer> stream = iterator.next();
            Entity entity = minecraft.level.getEntity(stream.getKey());
            if (entity == null || stream.getValue() <= 0) {
                iterator.remove();
                continue;
            }
            spawnVomit(minecraft.level, entity);
            stream.setValue(stream.getValue() - 1);
        }
    }

    /** Smoke and violet sparks shed by each of the four scattering shadows. */
    private static void spawnSplitTrails(ClientLevel level, LivingEntity hound, float progress) {
        double y = hound.getY() + hound.getBbHeight() * 0.45D;
        for (int copy = 0; copy < 4; copy++) {
            Vec3 offset = TransformationRenderer.fadeSplitOffset(progress, hound.yBodyRot, copy);
            double x = hound.getX() + offset.x;
            double z = hound.getZ() + offset.z;
            level.addParticle(ParticleTypes.SMOKE, x + RANDOM.nextGaussian() * 0.15D, y + RANDOM.nextGaussian() * 0.2D,
                    z + RANDOM.nextGaussian() * 0.15D, 0.0D, 0.02D, 0.0D);
            level.addParticle(ParticleTypes.REVERSE_PORTAL, x + RANDOM.nextGaussian() * 0.2D, y + RANDOM.nextGaussian() * 0.25D,
                    z + RANDOM.nextGaussian() * 0.2D, 0.0D, 0.0D, 0.0D);
            if (RANDOM.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.SQUID_INK, x, y, z, offset.x * 0.04D, 0.01D, offset.z * 0.04D);
            }
        }
    }

    /** One tick of the bile stream, gushing from the mouth along the look direction with a little spread. */
    private static void spawnVomit(ClientLevel level, Entity entity) {
        Vec3 look = entity.getViewVector(1.0F);
        Vec3 mouth = entity.getEyePosition().add(look.scale(0.5D)).subtract(0.0D, 0.25D, 0.0D);
        for (int i = 0; i < 9; i++) {
            double speed = 0.55D + RANDOM.nextDouble() * 0.45D;
            double vx = (look.x + RANDOM.nextGaussian() * 0.09D) * speed;
            double vy = (look.y + RANDOM.nextGaussian() * 0.09D) * speed + 0.06D;
            double vz = (look.z + RANDOM.nextGaussian() * 0.09D) * speed;
            ItemParticleOption particle = i % 3 == 0 ? CHUNKS : BILE;
            level.addParticle(particle, mouth.x, mouth.y, mouth.z, vx, vy, vz);
            if (i % 3 == 1) {
                level.addParticle(ParticleTypes.SNEEZE, mouth.x, mouth.y, mouth.z, vx * 0.8D, vy * 0.8D, vz * 0.8D);
            }
        }
        level.addParticle(ParticleTypes.FALLING_DRIPSTONE_WATER, mouth.x, mouth.y - 0.1D, mouth.z, 0.0D, 0.0D, 0.0D);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (splitTicks > 0) {
            // The view stutters between the shadows for the first half of the split.
            float progress = Mth.clamp((splitTicks - (float) event.getPartialTick()) / SPLIT_TICKS, 0.0F, 1.0F);
            float amount = 2.2F * Math.max(0.0F, progress - 0.5F) * 2.0F;
            event.setYaw(event.getYaw() + (RANDOM.nextFloat() - 0.5F) * amount);
            event.setRoll(event.getRoll() + (RANDOM.nextFloat() - 0.5F) * amount);
        }
        if (shakeTicks <= 0) {
            return;
        }
        float fade = Mth.clamp((shakeTicks - (float) event.getPartialTick()) / ROAR_SHAKE_TICKS, 0.0F, 1.0F);
        float amount = shakeStrength * fade * fade;
        event.setPitch(event.getPitch() + (RANDOM.nextFloat() - 0.5F) * amount);
        event.setYaw(event.getYaw() + (RANDOM.nextFloat() - 0.5F) * amount);
        event.setRoll(event.getRoll() + (RANDOM.nextFloat() - 0.5F) * amount * 0.8F);
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        float modifier = event.getNewFovModifier();
        if (roarPulseTicks > 0) {
            // The world lurches toward you, then settles.
            float progress = roarPulseTicks / (float) ROAR_PULSE_TICKS;
            modifier *= 1.0F - 0.12F * progress * progress;
        }
        if (dashTicks > 0) {
            // Pulled through the dark: the view stretches outward and snaps back.
            float progress = dashTicks / (float) DASH_TICKS;
            modifier *= 1.0F + 0.25F * progress;
        }
        if (splitTicks > 0) {
            // Scattered: the view snaps wide and eases back in as the shadows fade.
            float progress = splitTicks / (float) SPLIT_TICKS;
            modifier *= 1.0F + 0.15F * progress * progress;
        }
        event.setNewFovModifier(modifier);
    }

    /** Over the world, under the HUD (drawn just before the vanilla vignette). */
    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.VIGNETTE.id()) || (roarPulseTicks <= 0 && dashTicks <= 0 && splitTicks <= 0)) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        float partial = event.getPartialTick();
        if (roarPulseTicks > 0) {
            float progress = Mth.clamp((roarPulseTicks - partial) / ROAR_PULSE_TICKS, 0.0F, 1.0F);
            if (progress > 0.8F) {
                // A pale flash at the instant of the roar.
                graphics.fill(0, 0, width, height, argb((progress - 0.8F) / 0.2F * 0.35F, 0xFFF1D6));
            }
            float throb = progress * (0.75F + 0.25F * Mth.sin(progress * 18.0F));
            drawBorder(graphics, width, height, throb * 0.7F, 0x7A0000, 4);
        }
        if (dashTicks > 0) {
            float progress = Mth.clamp((dashTicks - partial) / DASH_TICKS, 0.0F, 1.0F);
            drawBorder(graphics, width, height, progress * 0.85F, 0x050008, 3);
        }
        if (splitTicks > 0) {
            float progress = Mth.clamp((splitTicks - partial) / SPLIT_TICKS, 0.0F, 1.0F);
            if (progress > 0.85F) {
                // A cold violet flash the instant the hound shatters.
                graphics.fill(0, 0, width, height, argb((progress - 0.85F) / 0.15F * 0.4F, 0xB59CFF));
            }
            float flicker = 0.7F + 0.3F * RANDOM.nextFloat();
            drawBorder(graphics, width, height, progress * 0.75F * flicker, 0x1A0533, 3);
        }
    }

    /** Edges fading inward; {@code depthDivisor} sets how far in they reach. */
    private static void drawBorder(GuiGraphics graphics, int width, int height, float strength, int rgb, int depthDivisor) {
        int depth = Math.max(8, Math.min(width, height) / depthDivisor);
        int edge = argb(strength, rgb);
        int clear = argb(0.0F, rgb);
        graphics.fillGradient(0, 0, width, depth, edge, clear);
        graphics.fillGradient(0, height - depth, width, height, clear, edge);
        int strips = 24;
        int stripWidth = Math.max(1, depth / strips);
        for (int i = 0; i < strips; i++) {
            float fade = strength * (1.0F - i / (float) strips);
            int x = i * stripWidth;
            graphics.fill(x, 0, x + stripWidth, height, argb(fade, rgb));
            graphics.fill(width - x - stripWidth, 0, width - x, height, argb(fade, rgb));
        }
    }

    private static int argb(float alpha, int rgb) {
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            VOMIT_STREAMS.clear();
            shakeTicks = 0;
            roarPulseTicks = 0;
            dashTicks = 0;
            splitTicks = 0;
            SPLIT_TRAILS.clear();
        }
    }
}
