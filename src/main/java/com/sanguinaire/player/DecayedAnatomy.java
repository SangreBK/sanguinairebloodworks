package com.sanguinaire.player;

import com.sanguinaire.advancement.ModAdvancements;
import com.sanguinaire.Sanguinaire;
import com.sanguinaire.item.VileCaneItem;
import com.sanguinaire.mutation.TransformationManager;
import com.sanguinaire.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <b>Decayed Anatomy</b>, the price of going too far. Server-authoritative.
 * <ul>
 *     <li><b>Level I</b> while at least half of the player's maximum health is Weakened Hearts: every hit they deal
 *     does 2 less damage (not with the Vile Cane in sword mode), any hit they block with a shield disables the shield (like an axe), and whenever the hunger
 *     bar drains it drains an extra half drumstick.</li>
 *     <li><b>Level II</b> once all of it is: everything above, plus immunity to Strength and Resistance, villager
 *     trades cost more, and the hunger bar drains yet another half drumstick.</li>
 * </ul>
 * The effect is lifted while transformed (the Weakened Hearts are hidden then) or under Physical Boost (which
 * suppresses them), and returns afterwards. It is shown as a permanent effect and can't be cured with milk.
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID)
public final class DecayedAnatomy {
    private static final int CHECK_INTERVAL = 10;
    private static final float DAMAGE_PENALTY = 2.0F;
    /** Each level drains half a drumstick (1 food point) more whenever the hunger bar drains. */
    private static final int EXTRA_FOOD_PER_LEVEL = 1;
    private static final int SHIELD_DISABLE_TICKS = 100;
    /** Level II: villager prices rise by 30% (at least one item). */
    private static final double PRICE_INCREASE = 0.30D;

    /** Last seen food level per player, to notice when the hunger bar drains. */
    private static final Map<UUID, Integer> LAST_FOOD = new HashMap<>();

    private DecayedAnatomy() {
    }

    /** 0 (healthy), 1 or 2. Server side (effects are only known to their owner's client). */
    public static int getLevel(Player player) {
        MobEffectInstance effect = player.getEffect(ModEffects.DECAYED_ANATOMY.get());
        return effect == null ? 0 : effect.getAmplifier() + 1;
    }

    /** The level the player's body has earned right now. */
    private static int targetLevel(ServerPlayer player) {
        if (TransformationManager.isTransformed(player)) {
            return 0;
        }
        PlayerMutationData data = PlayerMutationProvider.get(player).orElse(null);
        if (data == null || data.areWeakenedHeartsSuppressed() || !data.hasWeakenedHearts()) {
            return 0;
        }
        float max = player.getMaxHealth();
        float weakened = data.getWeakenedHealth();
        if (weakened >= max) {
            return 2;
        }
        return weakened >= max * 0.5F ? 1 : 0;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side != LogicalSide.SERVER || event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % CHECK_INTERVAL == 0) {
            updateEffect(player);
        }
        drainExtraHunger(player);
    }

    private static void updateEffect(ServerPlayer player) {
        int target = targetLevel(player);
        int current = getLevel(player);
        if (target == current) {
            return;
        }
        player.removeEffect(ModEffects.DECAYED_ANATOMY.get());
        if (target > 0) {
            player.addEffect(new MobEffectInstance(ModEffects.DECAYED_ANATOMY.get(), MobEffectInstance.INFINITE_DURATION,
                    target - 1, false, false, true));
        }
        if (target >= 2) {
            ModAdvancements.grant(player, ModAdvancements.BORROWED_TIME);
            player.removeEffect(MobEffects.DAMAGE_BOOST);
            player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        }
    }

    /** Whenever the hunger bar drops, the rotting body takes a little more. */
    private static void drainExtraHunger(ServerPlayer player) {
        int food = player.getFoodData().getFoodLevel();
        Integer last = LAST_FOOD.put(player.getUUID(), food);
        int level = getLevel(player);
        if (last != null && food < last && level > 0 && !player.getAbilities().invulnerable) {
            int drained = Math.max(0, food - EXTRA_FOOD_PER_LEVEL * level);
            player.getFoodData().setFoodLevel(drained);
            LAST_FOOD.put(player.getUUID(), drained);
        }
    }

    /**
     * Level I+: {@value #DAMAGE_PENALTY} less damage on every hit the player deals, except melee hits with the Vile
     * Cane in sword mode. Runs after damage bonuses are added.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingHurtEvent event) {
        Entity source = event.getSource().getEntity();
        if (source instanceof ServerPlayer attacker && attacker != event.getEntity() && getLevel(attacker) > 0
                && !isVileSwordHit(attacker, event)) {
            event.setAmount(Math.max(0.0F, event.getAmount() - DAMAGE_PENALTY));
        }
    }

    private static boolean isVileSwordHit(ServerPlayer attacker, LivingHurtEvent event) {
        var weapon = attacker.getMainHandItem();
        return event.getSource().getDirectEntity() == attacker && weapon.getItem() instanceof VileCaneItem
                && VileCaneItem.isSwordMode(weapon);
    }

    /** Level I+: a shield that blocks anything gives way. */
    @SubscribeEvent
    public static void onShieldBlock(ShieldBlockEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && getLevel(player) > 0 && player.isBlocking()) {
            Item shield = player.getUseItem().getItem();
            player.getCooldowns().addCooldown(shield, SHIELD_DISABLE_TICKS);
            player.stopUsingItem();
            player.level().broadcastEntityEvent(player, (byte) 30);
        }
    }

    /** Level II: Strength and Resistance can no longer take hold. */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof ServerPlayer player && getLevel(player) >= 2
                && (event.getEffectInstance().getEffect() == MobEffects.DAMAGE_BOOST
                || event.getEffectInstance().getEffect() == MobEffects.DAMAGE_RESISTANCE)) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** Level II: villagers charge more to deal with what you've become. */
    @SubscribeEvent
    public static void onMerchantOpen(PlayerContainerEvent.Open event) {
        if (!(event.getContainer() instanceof MerchantMenu) || !(event.getEntity() instanceof ServerPlayer player)
                || getLevel(player) < 2) {
            return;
        }
        for (Villager villager : player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(16.0D),
                v -> v.getTradingPlayer() == player)) {
            for (MerchantOffer offer : villager.getOffers()) {
                int increase = (int) Math.floor(PRICE_INCREASE * offer.getBaseCostA().getCount());
                offer.addToSpecialPriceDiff(Math.max(increase, 1));
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_FOOD.remove(event.getEntity().getUUID());
    }
}
