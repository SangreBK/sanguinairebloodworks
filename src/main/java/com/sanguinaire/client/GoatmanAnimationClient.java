package com.sanguinaire.client;

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
 * Client-side timing for the Goatman's one-shot animations, read by {@code GoatmanModel}:
 * <ul>
 *     <li><b>Attack</b>: starts whenever a Goatman swings (synced to everyone by vanilla's swing packet), unless one
 *     is already playing.</li>
 *     <li><b>Roar</b>: starts when the server announces the roar ability.</li>
 * </ul>
 * Start times are kept in each player's own tick count, the same clock the model's {@code ageInTicks} uses.
 * <p>
 * Also holds the local Goatman's attack cooldown: until the 0.75 s attack animation has finished, the attack key
 * neither swings nor hits (mining blocks is left alone). The server enforces the same wait.
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID, value = Dist.CLIENT)
public final class GoatmanAnimationClient {
    private static final float ATTACK_SECONDS = GoatmanAttacks.ATTACK_ANIMATION_TICKS / 20.0F;
    private static final float ROAR_SECONDS = 1.0F;

    private static final Map<Integer, Integer> ATTACK_STARTS = new HashMap<>();
    private static final Map<Integer, Integer> ROAR_STARTS = new HashMap<>();
    /** Client game time until which the local Goatman cannot attack again. */
    private static long attackReadyAt;

    private GoatmanAnimationClient() {
    }

    /** Seconds into the player's attack animation, or -1 when none is playing. */
    public static float attackSeconds(Player player, float ageInTicks) {
        return elapsed(ATTACK_STARTS, player, ageInTicks, ATTACK_SECONDS);
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
        return seconds >= 0.0F && seconds < length ? seconds : -1.0F;
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

    /** A swing that began this tick (swingTime has just gone from -1 to 0) starts the attack animation. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        for (Player player : minecraft.level.players()) {
            if (!player.swinging || player.swingTime != 0 || TransformationManager.getActive(player) != MutationType.GOATMAN) {
                continue;
            }
            Integer start = ATTACK_STARTS.get(player.getId());
            if (start == null || player.tickCount - start >= GoatmanAttacks.ATTACK_ANIMATION_TICKS) {
                ATTACK_STARTS.put(player.getId(), player.tickCount);
            }
        }
    }

    /** The local Goatman must finish its attack animation before attacking again. */
    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!event.isAttack() || minecraft.player == null || minecraft.level == null
                || TransformationManager.getActive(minecraft.player) != MutationType.GOATMAN) {
            return;
        }
        if (minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.BLOCK) {
            return; // digging is not an attack
        }
        long now = minecraft.level.getGameTime();
        if (now < attackReadyAt) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        attackReadyAt = now + GoatmanAttacks.ATTACK_ANIMATION_TICKS;
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ATTACK_STARTS.clear();
            ROAR_STARTS.clear();
            attackReadyAt = 0L;
        }
    }
}
