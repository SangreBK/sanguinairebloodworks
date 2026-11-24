package com.sanguinaire.client;

import com.sanguinaire.mutation.VileCaneEvents;
import com.sanguinaire.network.packet.ToggleCanePacket;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.mutation.MutationAbilities;
import com.sanguinaire.mutation.MutationType;
import com.sanguinaire.mutation.TransformationManager;
import com.sanguinaire.mutation.TransformationSequence;
import com.sanguinaire.network.ModNetwork;
import com.sanguinaire.network.packet.UseAbilityPacket;
import com.sanguinaire.player.PlayerMutationData;
import com.sanguinaire.player.PlayerMutationProvider;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * Client-side presentation of a transformation (design §3.1): the red flash with a slight zoom when it begins,
 * and, as it nears its end, a heartbeat with screen borders pulsing red in time with it.
 * Also polls the Mutation Active Ability Key and asks the server to use the ability.
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID, value = Dist.CLIENT)
public final class ClientTransformationEffects {
    private static final int FLASH_TICKS = 40;
    private static final int HEARTBEAT_WARNING_TICKS = 10 * 20;
    private static final int HEARTBEAT_FAST_TICKS = 3 * 20;
    private static final int PULSE_FADE_TICKS = 10;
    private static final float FLASH_ZOOM = 0.88F;
    /** Was 0.25 (the UI default); raised by 2. The sound engine caps loudness at 1.0, i.e. four times louder. */
    private static final float HEARTBEAT_VOLUME = 2.25F;

    private static int flashTicks;
    /** Detransformation effect: zoom, shake and red edges. */
    private static final int END_TICKS = 24;
    private static final float END_ZOOM = 0.82F;
    private static final float END_SHAKE = 3.0F;
    private static final RandomSource END_RANDOM = RandomSource.create();
    private static int endTicks;
    private static int ticksSinceHeartbeat = Integer.MAX_VALUE / 2;
    /** Serum build-up: ticks since injection, or -1 when none is running. */
    private static int buildupTicks = -1;
    private static final int BUILDUP_TICKS = TransformationSequence.DURATION_TICKS;
    private static final float BUILDUP_ZOOM = 0.78F;
    private static final float BUILDUP_SHAKE = 2.6F;

    private ClientTransformationEffects() {
    }

    /**
     * The serum has gone in (server's {@code TransformationSequence}): the camera snaps to first person, then over
     * two seconds the view tunnels in and trembles, the edges throb red with every heartbeat and blood runs across
     * the view, until the change itself.
     */
    public static void onSerumInjected() {
        buildupTicks = 0;
        Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
    }

    /** 0..1 through the build-up, or -1 when none is running. */
    private static float buildupProgress(float partialTick) {
        return buildupTicks < 0 ? -1.0F : Mth.clamp((buildupTicks + partialTick) / BUILDUP_TICKS, 0.0F, 1.0F);
    }

    /** 0..1 strength of the latest heartbeat pulse, fading over a few ticks. */
    private static float heartbeatPulse(float partialTick) {
        float best = 0.0F;
        int[] beats = TransformationSequence.HEARTBEAT_TICKS;
        for (int i = 0; i < beats.length; i++) {
            float since = buildupTicks + partialTick - beats[i];
            if (since >= 0.0F && since < 5.0F) {
                float urgency = 0.45F + 0.55F * i / (beats.length - 1);
                best = Math.max(best, (1.0F - since / 5.0F) * urgency);
            }
        }
        return best;
    }

    /**
     * The flesh tears back into human shape: the camera snaps back to first person, the view lurches inward with a
     * short shake and the screen edges flood red. (The blood burst itself is spawned by the server for everyone.)
     */
    public static void onLocalTransformationEnded() {
        endTicks = END_TICKS;
        Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float buildup = buildupProgress((float) event.getPartialTick());
        if (buildup >= 0.0F) {
            float amount = BUILDUP_SHAKE * (buildup * buildup + heartbeatPulse((float) event.getPartialTick()) * 0.8F);
            event.setPitch(event.getPitch() + (END_RANDOM.nextFloat() - 0.5F) * amount);
            event.setYaw(event.getYaw() + (END_RANDOM.nextFloat() - 0.5F) * amount);
            event.setRoll(event.getRoll() + (END_RANDOM.nextFloat() - 0.5F) * amount * 1.4F);
        }
        if (endTicks <= 0) {
            return;
        }
        float fade = Mth.clamp((endTicks - (float) event.getPartialTick()) / END_TICKS, 0.0F, 1.0F);
        float amount = END_SHAKE * fade * fade;
        event.setPitch(event.getPitch() + (END_RANDOM.nextFloat() - 0.5F) * amount);
        event.setYaw(event.getYaw() + (END_RANDOM.nextFloat() - 0.5F) * amount);
        event.setRoll(event.getRoll() + (END_RANDOM.nextFloat() - 0.5F) * amount);
    }

    public static void onLocalTransformationStarted() {
        buildupTicks = -1;
        // The change is complete: step out and see the new body.
        Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
        flashTicks = FLASH_TICKS;
        ticksSinceHeartbeat = Integer.MAX_VALUE / 2;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.isPaused()) {
            return;
        }
        if (endTicks > 0) {
            endTicks--;
        }
        if (buildupTicks >= 0) {
            spawnBuildupBlood(minecraft, player);
            if (++buildupTicks > BUILDUP_TICKS + 10) {
                buildupTicks = -1; // the server never transformed us (e.g. death): let the effect go
            }
        }

        while (ModKeyMappings.MUTATION_ABILITY.consumeClick()) {
            MutationType type = TransformationManager.getActive(player);
            if (type != null && MutationAbilities.hasActiveAbility(type)) {
                ModNetwork.sendToServer(new UseAbilityPacket());
            } else if (type == null && !VileCaneEvents.heldCane(player).isEmpty()) {
                // Humans use the same key to switch a Vile Cane held in the main hand.
                ModNetwork.sendToServer(new ToggleCanePacket());
            }
        }

        Optional<PlayerMutationData> optional = PlayerMutationProvider.get(player);
        if (optional.isEmpty() || !optional.get().isTransformed()) {
            flashTicks = 0;
            return;
        }
        PlayerMutationData data = optional.get();
        // Local countdown between server syncs (display only).
        data.setRemainingTicks(data.getRemainingTicks() - 1);
        if (flashTicks > 0) {
            flashTicks--;
        }

        ticksSinceHeartbeat++;
        int remaining = data.getRemainingTicks();
        if (remaining > 0 && remaining <= HEARTBEAT_WARNING_TICKS) {
            int interval = remaining <= HEARTBEAT_FAST_TICKS ? 10 : 20;
            if (ticksSinceHeartbeat >= interval) {
                ticksSinceHeartbeat = 0;
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 1.0F, HEARTBEAT_VOLUME));
            }
        }
    }

    /** Blood running down across the view: drops spawned just in front of the camera, mostly towards the edges. */
    private static void spawnBuildupBlood(Minecraft minecraft, LocalPlayer player) {
        if (minecraft.level == null || !minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        float progress = buildupTicks / (float) BUILDUP_TICKS;
        int drops = 1 + (int) (progress * 4);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-4D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        }
        right = right.normalize();
        Vec3 up = right.cross(look).normalize();
        Vec3 eye = player.getEyePosition();
        for (int i = 0; i < drops; i++) {
            double side = (END_RANDOM.nextBoolean() ? 1.0D : -1.0D) * (0.25D + END_RANDOM.nextDouble() * 0.35D);
            double height = (END_RANDOM.nextDouble() - 0.3D) * 0.45D;
            Vec3 at = eye.add(look.scale(0.55D)).add(right.scale(side)).add(up.scale(height));
            minecraft.level.addParticle(new DustParticleOptions(new Vector3f(0.5F, 0.02F, 0.02F), 0.6F + END_RANDOM.nextFloat() * 0.5F),
                    at.x, at.y, at.z, 0.0D, -0.02D, 0.0D);
        }
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        float buildup = buildupProgress(0.0F);
        if (buildup >= 0.0F) {
            // The world tunnels in, lurching a little further with each heartbeat.
            float squeeze = buildup * buildup + heartbeatPulse(0.0F) * 0.25F;
            event.setNewFovModifier(event.getNewFovModifier() * Mth.lerp(Mth.clamp(squeeze, 0.0F, 1.0F), 1.0F, BUILDUP_ZOOM));
        }
        if (flashTicks > 0) {
            float progress = flashTicks / (float) FLASH_TICKS;
            event.setNewFovModifier(event.getNewFovModifier() * Mth.lerp(progress, 1.0F, FLASH_ZOOM));
        }
        if (endTicks > 0) {
            float progress = endTicks / (float) END_TICKS;
            event.setNewFovModifier(event.getNewFovModifier() * Mth.lerp(progress * progress, 1.0F, END_ZOOM));
        }
    }

    /** Drawn just before the vignette, i.e. over the world but under the rest of the HUD. */
    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.VIGNETTE.id())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        float partial = event.getPartialTick();
        if (endTicks > 0) {
            float strength = Mth.clamp((endTicks - partial) / END_TICKS, 0.0F, 1.0F);
            graphics.fill(0, 0, width, height, argb(strength * strength * 0.35F, 0x6A0000));
            drawPulsingBorder(graphics, width, height, strength * 0.9F);
        }
        float buildup = buildupProgress(partial);
        if (buildup >= 0.0F) {
            float pulse = heartbeatPulse(partial);
            graphics.fill(0, 0, width, height, argb(buildup * buildup * 0.3F + pulse * 0.12F, 0x5A0000));
            drawPulsingBorder(graphics, width, height, Mth.clamp(0.25F + buildup * 0.45F + pulse * 0.5F, 0.0F, 1.0F));
        }
        if (minecraft.player == null || !TransformationManager.isTransformed(minecraft.player)) {
            return;
        }

        if (flashTicks > 0) {
            float alpha = Mth.clamp((flashTicks - partial) / FLASH_TICKS, 0.0F, 1.0F) * 0.75F;
            graphics.fill(0, 0, width, height, argb(alpha, 0x8A0A0A));
        }

        if (ticksSinceHeartbeat < PULSE_FADE_TICKS) {
            float pulse = 1.0F - Mth.clamp((ticksSinceHeartbeat + partial) / PULSE_FADE_TICKS, 0.0F, 1.0F);
            drawPulsingBorder(graphics, width, height, pulse * 0.6F);
        }
    }

    private static void drawPulsingBorder(GuiGraphics graphics, int width, int height, float strength) {
        int depth = Math.max(8, Math.min(width, height) / 6);
        int edge = argb(strength, 0xA00000);
        int clear = argb(0.0F, 0xA00000);
        graphics.fillGradient(0, 0, width, depth, edge, clear);
        graphics.fillGradient(0, height - depth, width, height, clear, edge);
        int strips = 16;
        for (int i = 0; i < strips; i++) {
            float fade = strength * (1.0F - i / (float) strips);
            int stripWidth = Math.max(1, depth / strips);
            int x = i * stripWidth;
            graphics.fill(x, 0, x + stripWidth, height, argb(fade, 0xA00000));
            graphics.fill(width - x - stripWidth, 0, width - x, height, argb(fade, 0xA00000));
        }
    }

    private static int argb(float alpha, int rgb) {
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
