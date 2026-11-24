package com.sanguinaire.mutation;

import com.sanguinaire.Sanguinaire;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

/**
 * Movement passives that must run on both logical sides, because player movement is computed by the client and
 * checked by the server:
 * <ul>
 *     <li>{@link MutationTrait#WALL_CLIMBING}: walking into a wall makes the player climb it, like a spider.</li>
 *     <li>{@link MutationTrait#COBWEB_WALKING}: cobwebs no longer slow the player down.</li>
 *     <li>{@link MutationTrait#SINKS}: the player sinks in water and can't swim up (Rotting Abomination).</li>
 * </ul>
 * Cobweb slowdown is stored in {@code Entity#stuckSpeedMultiplier} (SRG {@code f_19865_}), which has no public
 * setter. It is cleared through {@link ObfuscationReflectionHelper}, which resolves the SRG name both in the
 * development environment and in production.
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID)
public final class MovementEvents {
    /** Upward speed while climbing; the vanilla spider/ladder value. */
    private static final double CLIMB_SPEED = 0.2D;
    /** SINKS: never rising (motion capped at this, below jumping's +0.04 per tick), pulled down a little more each tick. */
    private static final double SINK_SPEED = -0.05D;
    private static final double SINK_PULL = 0.01D;
    private static final double MAX_SINK_SPEED = -0.5D;

    private static final Field STUCK_SPEED_MULTIPLIER = findStuckSpeedField();

    private MovementEvents() {
    }

    private static Field findStuckSpeedField() {
        try {
            return ObfuscationReflectionHelper.findField(Entity.class, "f_19865_");
        } catch (ObfuscationReflectionHelper.UnableToFindFieldException exception) {
            Sanguinaire.LOGGER.error("Could not access Entity#stuckSpeedMultiplier; cobweb walking is disabled", exception);
            return null;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        MutationType type = TransformationManager.getActive(player);
        if (type == null) {
            return;
        }

        // Before movement: drop the cobweb slowdown picked up during the previous tick.
        if (event.phase == TickEvent.Phase.START && type.hasTrait(MutationTrait.COBWEB_WALKING) && isInCobweb(player)) {
            clearStuckSpeed(player);
        }

        // After movement, on the controlling client: climb walls the player is pushing against.
        if (event.phase == TickEvent.Phase.END && player.level().isClientSide() && player.isLocalPlayer()
                && type.hasTrait(MutationTrait.WALL_CLIMBING) && player.horizontalCollision
                && !player.getAbilities().flying) {
            Vec3 motion = player.getDeltaMovement();
            double vertical = player.isShiftKeyDown() ? Math.max(motion.y, 0.0D) : Math.max(motion.y, CLIMB_SPEED);
            player.setDeltaMovement(motion.x, vertical, motion.z);
            player.resetFallDistance();
        }

        // After movement, on the controlling client: too heavy to swim. In water it can't sprint-swim or paddle up;
        // it keeps sinking (settling at about 3 blocks per second) until it lands on the bottom.
        if (event.phase == TickEvent.Phase.END && player.level().isClientSide() && player.isLocalPlayer()
                && type.hasTrait(MutationTrait.SINKS) && player.isInWater() && !player.getAbilities().flying) {
            player.setSwimming(false);
            player.setSprinting(false);
            if (!player.onGround()) {
                Vec3 motion = player.getDeltaMovement();
                player.setDeltaMovement(motion.x, Math.max(Math.min(motion.y, SINK_SPEED) - SINK_PULL, MAX_SINK_SPEED), motion.z);
            }
        }
    }

    private static boolean isInCobweb(Player player) {
        return player.level().getBlockStatesIfLoaded(player.getBoundingBox().deflate(1.0E-3D))
                .anyMatch(state -> state.is(Blocks.COBWEB));
    }

    private static void clearStuckSpeed(Player player) {
        if (STUCK_SPEED_MULTIPLIER == null) {
            return;
        }
        try {
            STUCK_SPEED_MULTIPLIER.set(player, Vec3.ZERO);
        } catch (IllegalAccessException exception) {
            Sanguinaire.LOGGER.debug("Could not clear cobweb slowdown", exception);
        }
    }
}
