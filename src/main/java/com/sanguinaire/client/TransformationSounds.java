package com.sanguinaire.client;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.mutation.MutationType;
import com.sanguinaire.mutation.TransformationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Vanilla-form players sound like their mob when hurt and when walking. Player hurt and footstep sounds reach every
 * client (played locally for the walking player, sent by the server to everyone else), so each client swaps a
 * transformed player's sound for the mob's. Idle (ambient) sounds are played by the server in
 * {@link TransformationManager}.
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID, value = Dist.CLIENT)
public final class TransformationSounds {
    private static final Set<ResourceLocation> PLAYER_HURT_SOUNDS = Set.of(
            SoundEvents.PLAYER_HURT.getLocation(),
            SoundEvents.PLAYER_HURT_DROWN.getLocation(),
            SoundEvents.PLAYER_HURT_FREEZE.getLocation(),
            SoundEvents.PLAYER_HURT_ON_FIRE.getLocation(),
            SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH.getLocation());
    /** These sounds are played at the entity's position; allow for interpolation and packet rounding. */
    private static final double MATCH_DISTANCE_SQR = 1.0D;
    /** Game time of the last footstep swapped per player: blocks with a carpet or snow on top play two step sounds. */
    private static final Map<Integer, Long> LAST_STEP = new HashMap<>();

    private TransformationSounds() {
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null) {
            return;
        }
        ResourceLocation location = sound.getLocation();
        if (PLAYER_HURT_SOUNDS.contains(location)) {
            replaceHurtSound(event, sound);
        } else if (isBlockStepSound(location)) {
            replaceStepSound(event, sound);
        }
    }

    private static void replaceHurtSound(PlaySoundEvent event, SoundInstance sound) {
        Player player = findTransformedPlayerAt(sound.getX(), sound.getY(), sound.getZ());
        if (player == null) {
            return;
        }
        MutationType type = TransformationManager.getActive(player);
        SoundEvent replacement = type == null ? null : type.getHurtSound(player.isInWater());
        if (replacement == null) {
            return;
        }
        RandomSource random = SoundInstance.createUnseededRandom();
        float pitch = (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F;
        event.setSound(new SimpleSoundInstance(replacement, SoundSource.PLAYERS, 1.0F, pitch, random,
                sound.getX(), sound.getY(), sound.getZ()));
    }

    private static void replaceStepSound(PlaySoundEvent event, SoundInstance sound) {
        Player player = findTransformedPlayerAt(sound.getX(), sound.getY(), sound.getZ());
        if (player == null) {
            return;
        }
        MutationType type = TransformationManager.getActive(player);
        MutationType.StepSound step = type == null ? null : type.getStepSound(player.isInWater());
        if (step == null) {
            return; // this creature walks with the block's own footsteps
        }
        long gameTime = player.level().getGameTime();
        Long last = LAST_STEP.put(player.getId(), gameTime);
        if (step.sound() == null || (last != null && last == gameTime)) {
            event.setSound(null);
            return;
        }
        event.setSound(new SimpleSoundInstance(step.sound(), SoundSource.PLAYERS, step.volume(), 1.0F,
                SoundInstance.createUnseededRandom(), sound.getX(), sound.getY(), sound.getZ()));
    }

    /** Block footsteps are named like {@code block.stone.step}; mob footsteps ({@code entity.*.step}) are left alone. */
    private static boolean isBlockStepSound(ResourceLocation location) {
        String path = location.getPath();
        return path.endsWith(".step") && !path.startsWith("entity.");
    }

    /** The player standing where the sound was played, if the nearest player there is transformed. */
    @Nullable
    private static Player findTransformedPlayerAt(double x, double y, double z) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        Player nearest = null;
        double nearestDistance = MATCH_DISTANCE_SQR;
        for (Player player : minecraft.level.players()) {
            double distance = player.distanceToSqr(x, y, z);
            if (distance <= nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        return nearest != null && TransformationManager.isTransformed(nearest) ? nearest : null;
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            LAST_STEP.clear();
        }
    }
}
