package com.sanguinaire.player;

import com.sanguinaire.advancement.ModAdvancements;
import com.sanguinaire.Sanguinaire;
import com.sanguinaire.network.ModNetwork;
import com.sanguinaire.registry.ModTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Weakened Hearts rules (design §3.2). Weakened Hearts are the <em>top</em> {@code weakenedHealth} points of the
 * player's health bar, so damage reaches them first and healing reaches them last.
 * <ul>
 *     <li>Natural regeneration (food) and the Regeneration effect cannot heal into them.</li>
 *     <li>Instant healing (Instant Health potions and similar) can.</li>
 *     <li>While any Weakened Heart is still filled, physical damage deals 1 extra point.</li>
 *     <li>They persist through death (see {@link PlayerMutationEvents}) and are cleared only when a Totem of
 *     Undying actually resurrects the player.</li>
 *     <li>They have no effect while transformed or under a Physical Boost serum (they disappear from the health bar).</li>
 * </ul>
 * <b>Distinguishing gradual from instant healing:</b> Forge's heal event does not carry a cause. Food regeneration
 * and Regeneration-type effects heal at most 1 point at a time from inside the player's own tick, while Instant
 * Health heals 4+ points (drunk) or arrives from outside the tick (splash). A heal is therefore treated as gradual
 * when it happens during the player's tick and is at most 1 point.
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID)
public final class WeakenedHeartEvents {
    private static final float GRADUAL_HEAL_MAX = 1.0F;
    private static final float EXTRA_PHYSICAL_DAMAGE = 1.0F;

    /** The server player whose tick is currently running, if any (server thread only). */
    @Nullable
    private static Player tickingPlayer;

    private WeakenedHeartEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerTickStart(TickEvent.PlayerTickEvent event) {
        if (event.side == LogicalSide.SERVER && event.phase == TickEvent.Phase.START) {
            tickingPlayer = event.player;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTickEnd(TickEvent.PlayerTickEvent event) {
        if (event.side == LogicalSide.SERVER && event.phase == TickEvent.Phase.END && tickingPlayer == event.player) {
            tickingPlayer = null;
        }
    }

    /** Upper bound of health that natural healing may reach: the part of the bar that is not weakened. */
    public static float naturalHealCap(Player player, PlayerMutationData data) {
        return Math.max(0.0F, player.getMaxHealth() - data.getWeakenedHealth());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        boolean gradual = player == tickingPlayer && event.getAmount() <= GRADUAL_HEAL_MAX;
        if (!gradual) {
            return;
        }
        PlayerMutationProvider.get(player).ifPresent(data -> {
            if (data.areWeakenedHeartsSuppressed() || !data.hasWeakenedHearts()) {
                return;
            }
            float room = naturalHealCap(player, data) - player.getHealth();
            if (room <= 0.0F) {
                event.setCanceled(true);
            } else if (event.getAmount() > room) {
                event.setAmount(room);
            }
        });
    }

    /**
     * +1 damage when the hit lands on Weakened Hearts. LivingDamageEvent fires once per damage instance, after armor
     * and absorption, so the extra point is added to health damage exactly once.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide() || event.getAmount() <= 0.0F) {
            return;
        }
        if (!event.getSource().is(ModTags.PHYSICAL_DAMAGE)) {
            return;
        }
        PlayerMutationProvider.get(player).ifPresent(data -> {
            if (data.areWeakenedHeartsSuppressed() || !data.hasWeakenedHearts()) {
                return;
            }
            if (player.getHealth() > naturalHealCap(player, data)) {
                event.setAmount(event.getAmount() + EXTRA_PHYSICAL_DAMAGE);
            }
        });
    }

    /** Only an actual Totem resurrection clears Weakened Hearts. Lowest priority and not cancelled = it activates. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTotemUsed(LivingUseTotemEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerMutationProvider.get(player).ifPresent(data -> {
                if (data.hasWeakenedHearts()) {
                    data.setWeakenedHealth(0.0F);
                    ModNetwork.syncMutationData(player);
                    ModAdvancements.grant(player, ModAdvancements.CLEAN_SLATE);
                }
            });
        }
    }
}
