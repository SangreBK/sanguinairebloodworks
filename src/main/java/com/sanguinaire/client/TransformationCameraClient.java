package com.sanguinaire.client;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.mutation.MutationType;
import com.sanguinaire.mutation.TransformationManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Pulls the third-person camera further back (and higher) for the big transformations, whose bodies otherwise fill
 * the screen. Vanilla places the camera 4 blocks behind the eyes; here it is placed again from the eyes, raised by a
 * per-creature amount and moved back by a per-creature distance, with the same wall clipping vanilla uses.
 * Works for both the rear and the front view (the camera's look vector is already reversed for the front view).
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID, value = Dist.CLIENT)
public final class TransformationCameraClient {
    private static final double VANILLA_DISTANCE = 4.0D;
    /** Camera.setPosition(Vec3) is protected; it is the class's only void method taking a single Vec3. */
    private static final Method SET_POSITION = findSetPosition();

    private TransformationCameraClient() {
    }

    /** Distance behind the eyes, in blocks (vanilla: 4). */
    private static double distanceFor(MutationType type) {
        return switch (type) {
            case GHAST -> 7.0D;
            case GOATMAN -> 5.0D;
            case ELDER_GUARDIAN -> 6.5D;
            default -> VANILLA_DISTANCE;
        };
    }

    /** How far above the eyes the camera orbits, in blocks. */
    private static double heightFor(MutationType type) {
        return switch (type) {
            case GOATMAN -> 0.8D;
            case ELDER_GUARDIAN -> 1.0D;
            default -> 0.0D;
        };
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (SET_POSITION == null) {
            return;
        }
        Camera camera = event.getCamera();
        Entity entity = camera.getEntity();
        Minecraft minecraft = Minecraft.getInstance();
        if (!camera.isDetached() || entity != minecraft.player || !(entity instanceof Player player) || minecraft.level == null) {
            return;
        }
        MutationType type = TransformationManager.getActive(player);
        if (type == null) {
            return;
        }
        double distance = distanceFor(type);
        double height = heightFor(type);
        if (distance == VANILLA_DISTANCE && height == 0.0D) {
            return;
        }
        float partial = (float) event.getPartialTick();
        Vec3 eyes = entity.getEyePosition(partial);
        Vec3 pivot = eyes;
        if (height > 0.0D) {
            // Don't raise the pivot through a ceiling.
            HitResult up = minecraft.level.clip(new ClipContext(eyes, eyes.add(0.0D, height + 0.2D, 0.0D),
                    ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
            double room = up.getType() == HitResult.Type.MISS ? height : Math.max(0.0D, up.getLocation().y - eyes.y - 0.2D);
            pivot = eyes.add(0.0D, Math.min(height, room), 0.0D);
        }
        Vec3 back = new Vec3(-camera.getLookVector().x(), -camera.getLookVector().y(), -camera.getLookVector().z());
        double free = maxZoom(minecraft, entity, pivot, back, distance);
        try {
            SET_POSITION.invoke(camera, pivot.add(back.scale(free)));
        } catch (ReflectiveOperationException ignored) {
            // leave vanilla's placement
        }
    }

    /** Vanilla's camera collision: eight slightly offset rays, the camera stops just short of the nearest wall. */
    private static double maxZoom(Minecraft minecraft, Entity entity, Vec3 pivot, Vec3 back, double distance) {
        double free = distance;
        for (int i = 0; i < 8; i++) {
            float ox = ((i & 1) * 2 - 1) * 0.1F;
            float oy = ((i >> 1 & 1) * 2 - 1) * 0.1F;
            float oz = ((i >> 2 & 1) * 2 - 1) * 0.1F;
            Vec3 from = pivot.add(ox, oy, oz);
            Vec3 to = from.add(back.scale(distance));
            HitResult hit = minecraft.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
            if (hit.getType() != HitResult.Type.MISS) {
                double d = hit.getLocation().distanceTo(pivot);
                if (d < free) {
                    free = d;
                }
            }
        }
        return free;
    }

    private static Method findSetPosition() {
        for (Method method : Camera.class.getDeclaredMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 1 && params[0] == Vec3.class && method.getReturnType() == void.class
                    && !Modifier.isStatic(method.getModifiers())) {
                method.setAccessible(true);
                return method;
            }
        }
        Sanguinaire.LOGGER.warn("Camera.setPosition(Vec3) not found; transformation camera distances disabled");
        return null;
    }
}
