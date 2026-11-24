package com.sanguinaire.client;

import com.sanguinaire.network.packet.EmptySwingPacket;

import com.sanguinaire.network.ModNetwork;

import net.minecraft.client.player.Input;

import net.minecraftforge.client.event.MovementInputUpdateEvent;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.mutation.GoatmanAttacks;
import com.sanguinaire.mutation.MutationType;
import com.sanguinaire.mutation.TransformationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side timing for the one-shot animations of Nico's animated creatures, read by {@code GoatmanModel},
 * {@code FadeHoundModel} and {@code RottingAbominationModel} (attacks last 0.75 s, the Abomination's 1.125 s):
 * <ul>
 *     <li><b>Attack</b>: starts whenever a Goatman swings (synced to everyone by vanilla's swing packet), unless one
 *     is already playing.</li>
 *     <li><b>Roar</b>: starts when the server announces the roar ability.</li>
 * </ul>
 * Start times are kept in each player's own tick count, the same clock the model's {@code ageInTicks} uses.
 * <p>
 * Also holds the local player's attack cooldown: until the attack animation has finished, the attack key neither
 * swings nor hits (mining blocks is left alone). The server enforces the same wait. A Rotting Abomination is also
 * rooted for the whole attack: its movement input is zeroed while the animation plays.
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID, value = Dist.CLIENT)
public final class GoatmanAnimationClient {
    private static final float ROAR_SECONDS = 1.0F;

    private static final Map<Integer, Integer> ATTACK_STARTS = new HashMap<>();
    private static final Map<Integer, Integer> ROAR_STARTS = new HashMap<>();
    /** Each player's swing timer last tick (-1 when not swinging), to spot new swings. */
    private static final Map<Integer, Integer> LAST_SWING_TIME = new HashMap<>();
    /** Client game time until which the local Goatman cannot attack again. */
    private static long attackReadyAt;

    private GoatmanAnimationClient() {
    }

    /** Seconds into the player's attack animation, or -1 when none is playing. */
    public static float attackSeconds(Player player, float ageInTicks) {
        return elapsed(ATTACK_STARTS, player, ageInTicks, attackTicks(player) / 20.0F);
    }

    private static int attackTicks(Player player) {
        return GoatmanAttacks.attackAnimationTicks(TransformationManager.getActive(player));
    }

    /** True while the player's attack animation is playing. */
    public static boolean isAttacking(Player player) {
        return attackSeconds(player, player.tickCount) >= 0.0F;
    }

    /** Seconds into the player's roar animation, or -1 when none is playing. */
    public static float roarSeconds(Player player, float ageInTicks) {
        return elapsed(ROAR_STARTS, player, ageInTicks, ROAR_SECONDS);
    }

    private static float elapsed(Map<Integer, Integer> starts, Player player, float ageInTicks, float length) {
        Integer start = starts.get(player.getId());
        if (start == null) {
            return -1.0F;
        }
        float seconds = (ageInTicks - start) / 20.0F;
        if (seconds < -0.05F) {
            // The player's tick count restarted (respawn reuses the entity): this start belongs to an older life.
            starts.remove(player.getId());
            return -1.0F;
        }
        return seconds < length ? Math.max(0.0F, seconds) : -1.0F;
    }

    /** Starts the attack animation unless one is already playing (an expired or stale start does not count). */
    private static void startAttack(Player player) {
        Integer start = ATTACK_STARTS.get(player.getId());
        int age = start == null ? Integer.MAX_VALUE : player.tickCount - start;
        if (age < 0 || age >= attackTicks(player)) {
            ATTACK_STARTS.put(player.getId(), player.tickCount);
        }
    }

    /** Called when the server announces a Goatman roar. */
    public static void onRoar(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            Entity entity = minecraft.level.getEntity(entityId);
            if (entity != null) {
                ROAR_STARTS.put(entityId, entity.tickCount);
            }
        }
    }

    /**
     * A new swing starts the attack animation. A swing is new when the arm was not swinging last tick, or when its
     * swing timer went backwards (vanilla restarts a swing that is past its halfway point, so a fast follow-up never
     * shows {@code swingTime == 0} on a tick we look at). Sneaking, sprinting and jumping change none of this.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        for (Player player : minecraft.level.players()) {
            int id = player.getId();
            int previous = LAST_SWING_TIME.getOrDefault(id, -1);
            int current = player.swinging ? player.swingTime : -1;
            LAST_SWING_TIME.put(id, current);
            boolean newSwing = current >= 0 && (previous < 0 || current < previous);
            if (newSwing && GoatmanAttacks.hasDelayedAttack(TransformationManager.getActive(player))) {
                startAttack(player);
            }
        }
    }

    /** The local Goatman must finish its attack animation before attacking again. */
    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!event.isAttack() || minecraft.player == null || minecraft.level == null
                || !GoatmanAttacks.hasDelayedAttack(TransformationManager.getActive(minecraft.player))) {
            return;
        }
        boolean abomination = TransformationManager.getActive(minecraft.player) == MutationType.ROTTING_ABOMINATION;
        boolean block = minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.BLOCK;
        long now = minecraft.level.getGameTime();
        if (block && (!abomination || now < attackReadyAt)) {
            return; // digging is not an attack (and is never blocked)
        }
        if (now < attackReadyAt) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        attackReadyAt = now + attackTicks(minecraft.player);
        startAttack(minecraft.player); // don't wait for the swing to be noticed next tick
        if (abomination && (minecraft.hitResult == null || minecraft.hitResult.getType() != HitResult.Type.ENTITY)) {
            // A slam at the air or the ground still crashes down: the server places the blast where the player aims.
            ModNetwork.sendToServer(new EmptySwingPacket());
        }
    }

    /** A Rotting Abomination stands its ground while it attacks: no walking, strafing or jumping. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        if (TransformationManager.getActive(player) == MutationType.ROTTING_ABOMINATION && isAttacking(player)) {
            Input input = event.getInput();
            input.forwardImpulse = 0.0F;
            input.leftImpulse = 0.0F;
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            input.jumping = false;
            input.shiftKeyDown = false;
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ATTACK_STARTS.clear();
            ROAR_STARTS.clear();
            LAST_SWING_TIME.clear();
            attackReadyAt = 0L;
        }
    }
}
