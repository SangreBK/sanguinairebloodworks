package com.sanguinaire.mutation;

import com.sanguinaire.Sanguinaire;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Goatman's melee attack follows its animation (0.75 s; the claws land at 0.20 s). Server only.
 * <p>
 * A Goatman's attack on an entity is held back: the vanilla attack is cancelled, and {@link #HIT_DELAY_TICKS}
 * later the hit is delivered for real (if the target is still alive and in reach). Until the animation is over
 * further attacks are ignored. The client enforces the same wait on the attack key, so normally nothing is
 * cancelled here except the original, early hit.
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID)
public final class GoatmanAttacks {
    /** 0.75 s attack animation. */
    public static final int ATTACK_ANIMATION_TICKS = 15;
    /** The claws connect 0.20 s in. */
    public static final int HIT_DELAY_TICKS = 4;
    /** A little shorter than the animation, so network jitter never swallows a click the client allowed. */
    private static final int SERVER_COOLDOWN_TICKS = ATTACK_ANIMATION_TICKS - 2;
    /** Extra distance the target may move away during the wind-up and still be hit. */
    private static final double REACH_TOLERANCE = 1.5D;

    private record PendingHit(int targetId, int hitAt) {
    }

    private static final Map<UUID, PendingHit> PENDING = new HashMap<>();
    private static final Map<UUID, Integer> READY_AT = new HashMap<>();
    /** Players whose delayed hit is being delivered right now (their attack must not be held back again). */
    private static final Set<UUID> DELIVERING = new HashSet<>();

    private GoatmanAttacks() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || DELIVERING.contains(player.getUUID())
                || TransformationManager.getActive(player) != MutationType.GOATMAN) {
            return;
        }
        event.setCanceled(true);
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        int now = server.getTickCount();
        if (now < READY_AT.getOrDefault(player.getUUID(), 0)) {
            return; // still mid-swing
        }
        READY_AT.put(player.getUUID(), now + SERVER_COOLDOWN_TICKS);
        PENDING.put(player.getUUID(), new PendingHit(event.getTarget().getId(), now + HIT_DELAY_TICKS));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        int now = event.getServer().getTickCount();
        Iterator<Map.Entry<UUID, PendingHit>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingHit> entry = iterator.next();
            if (now < entry.getValue().hitAt()) {
                continue;
            }
            iterator.remove();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive() || TransformationManager.getActive(player) != MutationType.GOATMAN) {
                continue;
            }
            Entity target = player.level().getEntity(entry.getValue().targetId());
            if (target == null || !target.isAlive()) {
                continue;
            }
            double reach = player.getEntityReach() + REACH_TOLERANCE;
            if (player.distanceToSqr(target) > reach * reach) {
                continue; // it got away during the wind-up
            }
            DELIVERING.add(player.getUUID());
            try {
                player.attack(target);
            } finally {
                DELIVERING.remove(player.getUUID());
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        READY_AT.remove(event.getEntity().getUUID());
    }
}
