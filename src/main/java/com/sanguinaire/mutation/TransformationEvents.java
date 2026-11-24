package com.sanguinaire.mutation;

import net.minecraft.world.damagesource.DamageTypes;

import com.sanguinaire.network.ModNetwork;

import com.sanguinaire.player.PlayerMutationProvider;

import net.minecraftforge.event.entity.EntityStruckByLightningEvent;

import net.minecraftforge.event.entity.EntityMountEvent;

import net.minecraftforge.event.entity.living.LivingKnockBackEvent;

import net.minecraft.sounds.SoundSource;

import net.minecraft.sounds.SoundEvents;

import net.minecraft.network.chat.Component;

import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;

import com.sanguinaire.Sanguinaire;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.EnderManAngerEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/** Forge-bus hooks for transformation ticking, death handling and event-driven passive traits. */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID)
public final class TransformationEvents {
    /** Half a heart: what is left after a lethal blow forces a transformation to end. */
    private static final float DETRANSFORM_HEALTH = 1.0F;
    private static final double HIGH_JUMP_BONUS = 0.13D;
    private static final int SHIELD_DISABLE_TICKS = 10 * 20;
    private static final int FADE_INVISIBILITY_TICKS = 4 * 20;
    private static final int FADE_BLINDNESS_TICKS = 8 * 20;
    private static final int HUNGER_ON_HIT_TICKS = 3 * 20;
    private static final int POISON_ON_HIT_TICKS = 8 * 20;
    private static final int HUNGER_RESTORED_ON_KILL = 4;

    private TransformationEvents() {
    }

    /** The player who dealt {@code source} with a direct melee hit, if any. */
    @Nullable
    private static Player meleeAttacker(DamageSource source) {
        return source.getEntity() instanceof Player player && source.getDirectEntity() == player ? player : null;
    }

    // ---- Lifecycle -----------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.side == LogicalSide.SERVER && event.player instanceof ServerPlayer player) {
            TransformationManager.tickPhysicalBoost(player);
            TransformationManager.tick(player);
        }
    }

    /**
     * A blow that would kill a transformed player tears the borrowed body away instead: the transformation ends
     * exactly as if its timer had run out (Weakened Hearts, blood aftermath and all) and the player is left on half
     * a heart. Runs on the final damage (after armor and absorption) and before Totems of Undying are considered.
     * Damage that bypasses invulnerability (the void, /kill) still kills.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLethalDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !TransformationManager.isTransformed(player)
                || event.getAmount() < player.getHealth() || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        event.setCanceled(true);
        player.setHealth(DETRANSFORM_HEALTH); // alive while the transformation ends, so its aftermath applies
        TransformationManager.end(player);
        player.setHealth(DETRANSFORM_HEALTH);
    }

    /** A transformation always ends on death (it still costs half a heart). Lowest priority: only real deaths. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            TransformationManager.end(player);
        }
        if (event.getSource().getEntity() instanceof Player killer
                && TransformationManager.hasTrait(killer, MutationTrait.HUNGER_ON_KILL)) {
            killer.getFoodData().eat(HUNGER_RESTORED_ON_KILL, 0.0F);
        }
    }

    /** Zombie-family transformations turn the villagers they kill into Zombie Villagers (on every difficulty). */
    @SubscribeEvent
    public static void onVillagerKilled(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player killer)
                || !TransformationManager.hasTrait(killer, MutationTrait.ZOMBIFIES_VILLAGERS)) {
            return;
        }
        if (!ForgeEventFactory.canLivingConvert(villager, EntityType.ZOMBIE_VILLAGER, timer -> {
        })) {
            return;
        }
        CompoundTag villagerData = new CompoundTag();
        villager.addAdditionalSaveData(villagerData);

        ZombieVillager zombieVillager = villager.convertTo(EntityType.ZOMBIE_VILLAGER, false);
        if (zombieVillager == null) {
            return;
        }
        zombieVillager.finalizeSpawn(level, level.getCurrentDifficultyAt(zombieVillager.blockPosition()),
                MobSpawnType.CONVERSION, new Zombie.ZombieGroupData(false, true), null);
        zombieVillager.setVillagerData(villager.getVillagerData());
        Tag gossips = villagerData.get("Gossips");
        if (gossips != null) {
            zombieVillager.setGossips(gossips);
        }
        zombieVillager.setTradeOffers(villager.getOffers().createTag());
        zombieVillager.setVillagerXp(villager.getVillagerXp());
        ForgeEventFactory.onLivingConvert(villager, zombieVillager);
        level.levelEvent(null, 1026, villager.blockPosition(), 0);
        // The villager has been replaced; skip its death (and loot), exactly like vanilla zombie conversion.
        event.setCanceled(true);
    }

    // ---- Targeting & attacking -----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Mob mob && event.getNewTarget() instanceof Player player
                && TransformationManager.ignoresPlayer(mob, player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (TransformationManager.hasTrait(event.getEntity(), MutationTrait.CANNOT_ATTACK)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof PathfinderMob mob && TransformationManager.fearsWolfForm(mob)) {
            mob.goalSelector.addGoal(1, new AvoidEntityGoal<>(mob, Player.class, 10.0F, 1.0D, 1.3D,
                    target -> target instanceof Player player
                            && TransformationManager.hasTrait(player, MutationTrait.FEARED_BY_SKELETONS_AND_SHEEP)));
        }
    }

    // ---- Damage ----------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        // Cancelling makes the hit fail; vanilla arrows/tridents then bounce off.
        if (source.is(DamageTypeTags.IS_PROJECTILE) && TransformationManager.hasTrait(player, MutationTrait.PROJECTILE_IMMUNE)) {
            event.setCanceled(true);
        } else if (source.is(DamageTypeTags.IS_FIRE) && TransformationManager.hasTrait(player, MutationTrait.FIRE_IMMUNE)) {
            event.setCanceled(true);
        } else if (MutationAbilities.isDashInvulnerable(player) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true); // Fade Hound: untouchable for a moment after its shadow dash
        } else if (MutationAbilities.isFadeSplitInvulnerable(player) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true); // Fade Hound: untouchable while splitting into shadows after a hit
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();

        // Passives of the player being hit.
        if (target instanceof Player victim) {
            Entity attacker = source.getEntity();
            if (attacker instanceof LivingEntity livingAttacker && attacker != victim) {
                if (TransformationManager.hasTrait(victim, MutationTrait.FADES_WHEN_HIT)) {
                    livingAttacker.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, FADE_BLINDNESS_TICKS, 0), victim);
                }
            }
            if (TransformationManager.hasTrait(victim, MutationTrait.FADES_WHEN_HIT) && victim instanceof ServerPlayer serverVictim) {
                victim.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, FADE_INVISIBILITY_TICKS, 0, false, false, true));
                LivingEntity blinded = attacker instanceof LivingEntity living && attacker != victim ? living : null;
                MutationAbilities.startFadeSplit(serverVictim, blinded);
            }
        }

        // Passives of the player dealing a melee hit.
        Player attacker = meleeAttacker(source);
        if (attacker == null || attacker == target) {
            return;
        }
        if (TransformationManager.hasTrait(attacker, MutationTrait.HUNGER_ON_HIT)) {
            target.addEffect(new MobEffectInstance(MobEffects.HUNGER, HUNGER_ON_HIT_TICKS, 1), attacker);
        }
        if (TransformationManager.hasTrait(attacker, MutationTrait.POISON_ON_HIT)) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_ON_HIT_TICKS, 0), attacker);
        }
        if (MutationAbilities.isWolfRaging(attacker)) {
            event.setAmount(event.getAmount() + 1.0F); // Wolf rage: +1 damage per hit
        }
        if (TransformationManager.hasTrait(attacker, MutationTrait.ARMOR_PIERCING) && !source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            event.setAmount(compensateForArmor(target, event.getAmount()));
        }
    }

    /**
     * LivingHurtEvent fires before armor is applied. To make a hit ignore armor, find the pre-armor amount whose
     * post-armor result equals the intended damage (vanilla's armor formula is monotonic, so bisection works).
     */
    private static float compensateForArmor(LivingEntity target, float intended) {
        float armor = target.getArmorValue();
        float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        if (armor <= 0.0F || intended <= 0.0F) {
            return intended;
        }
        float low = intended;
        float high = intended * 10.0F; // armor reduces damage by at most 80%
        for (int i = 0; i < 24; i++) {
            float mid = (low + high) * 0.5F;
            if (CombatRules.getDamageAfterAbsorb(mid, armor, toughness) < intended) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return high;
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (TransformationManager.isNightHunting(player)) {
            event.setDistance(Math.max(0.0F, event.getDistance() - 1.0F)); // Jump Boost I's fall allowance
        }
        if (TransformationManager.hasTrait(player, MutationTrait.FALL_DAMAGE_IMMUNE)) {
            event.setDamageMultiplier(0.0F);
        } else if (TransformationManager.hasTrait(player, MutationTrait.HALF_FALL_DAMAGE)) {
            event.setDamageMultiplier(event.getDamageMultiplier() * 0.5F);
        }
    }

    /** Fraction knocked off every villager trade's price for Villager-form players (at least one item off). */
    public static final double VILLAGER_DISCOUNT = 0.30D;

    /**
     * Villager-form players get a discount on every trade. Applied when the trading screen opens, after the villager
     * has worked out its reputation prices and before the offers are sent; the villager resets its prices when the
     * trade closes, so the discount never sticks or stacks.
     */
    @SubscribeEvent
    public static void onMerchantOpen(PlayerContainerEvent.Open event) {
        if (!(event.getContainer() instanceof MerchantMenu) || !(event.getEntity() instanceof ServerPlayer player)
                || TransformationManager.getActive(player) != MutationType.VILLAGER) {
            return;
        }
        for (Villager villager : player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(16.0D),
                v -> v.getTradingPlayer() == player)) {
            for (MerchantOffer offer : villager.getOffers()) {
                int discount = (int) Math.floor(VILLAGER_DISCOUNT * offer.getBaseCostA().getCount());
                offer.addToSpecialPriceDiff(-Math.max(discount, 1));
            }
        }
    }

    /** Endermen in Enderman form don't get angry when you look at them (no stare, scream or attack). */
    @SubscribeEvent
    public static void onEnderManAnger(EnderManAngerEvent event) {
        if (TransformationManager.getActive(event.getPlayer()) == MutationType.ENDERMAN) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onShieldBlock(ShieldBlockEvent event) {
        Player attacker = meleeAttacker(event.getDamageSource());
        if (attacker == null || !TransformationManager.hasTrait(attacker, MutationTrait.DISABLES_SHIELDS)) {
            return;
        }
        LivingEntity blocker = event.getEntity();
        if (blocker.level().isClientSide()) {
            return;
        }
        if (blocker instanceof Player blockingPlayer) {
            Item shield = blockingPlayer.getUseItem().getItem();
            blockingPlayer.getCooldowns().addCooldown(shield, SHIELD_DISABLE_TICKS);
            blockingPlayer.stopUsingItem();
            blockingPlayer.level().broadcastEntityEvent(blockingPlayer, (byte) 30);
        } else {
            blocker.stopUsingItem();
        }
    }

    // ---- Hitbox -------------------------------------------------------------------------------------------------

    /**
     * Transformed players take the creature's hitbox and eye height (in every pose). Fires on both sides whenever
     * dimensions are refreshed; {@link TransformationManager} and the client sync handler call
     * {@code refreshDimensions()} when a transformation starts or ends so both sides always agree.
     */
    @SubscribeEvent
    public static void onEntitySize(EntityEvent.Size event) {
        if (event.getEntity() instanceof Player player) {
            MutationType type = TransformationManager.getActive(player);
            if (type != null) {
                event.setNewSize(type.getDimensions());
                event.setNewEyeHeight(type.getEyeHeight());
            }
        }
    }

    // ---- Movement, effects, vibrations ---------------------------------------------------------------------------

    /** Fires on the client for the local player too; the synced capability lets both sides agree. */
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof Player player && TransformationManager.hasTrait(player, MutationTrait.CANNOT_JUMP)) {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, Math.min(motion.y, 0.0D), motion.z);
        }
        // The Goatman leaps like a goat. The server's copy of the jump plays it for everyone.
        if (event.getEntity() instanceof Player player && !player.level().isClientSide()
                && TransformationManager.getActive(player) == MutationType.GOATMAN) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GOAT_LONG_JUMP,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof Player player
                && event.getEffectInstance().getEffect() == MobEffects.MOVEMENT_SLOWDOWN
                && TransformationManager.hasTrait(player, MutationTrait.SLOWNESS_IMMUNE)) {
            event.setResult(Event.Result.DENY);
        }
        if (event.getEntity() instanceof Player player
                && event.getEffectInstance().getEffect() == MobEffects.POISON
                && TransformationManager.hasTrait(player, MutationTrait.POISON_IMMUNE)) {
            event.setResult(Event.Result.DENY);
        }
        // Wardens (and their shriekers) don't darken one of their own.
        if (event.getEntity() instanceof Player player
                && event.getEffectInstance().getEffect() == MobEffects.DARKNESS
                && TransformationManager.hasTrait(player, MutationTrait.IGNORED_BY_WARDENS)) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** Night Hunter's Jump Boost I (+0.1 jump power), without a visible effect. Runs where the jump is simulated. */
    /**
     * HIGH_JUMP (Goatman): one block higher. Vanilla's jump (0.42 upward) peaks at 1.25 blocks; 0.55 peaks just over
     * 2. Runs on the client too, which is what actually moves the player.
     */
    @SubscribeEvent
    public static void onHighJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof Player player && TransformationManager.hasTrait(player, MutationTrait.HIGH_JUMP)) {
            player.setDeltaMovement(player.getDeltaMovement().add(0.0D, HIGH_JUMP_BONUS, 0.0D));
        }
    }

    /**
     * Heavy blows: knockback dealt by a form with a {@link MutationType#getKnockbackBonus() knockback bonus} is
     * strengthened. The knockback event carries no attacker, but vanilla records the attacker on the victim just
     * before knocking it back, in the same tick.
     */
    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !(victim.getLastHurtByMob() instanceof Player attacker)
                || victim.getLastHurtByMobTimestamp() != victim.tickCount) {
            return;
        }
        MutationType type = TransformationManager.getActive(attacker);
        if (type != null && type.getKnockbackBonus() > 0.0F) {
            event.setStrength(event.getStrength() + type.getKnockbackBonus());
        }
    }

    /** A Ravager's bite sounds like one: vanilla's Ravager attack sound on every melee hit. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRavagerBite(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!event.isCanceled() && !player.level().isClientSide()
                && TransformationManager.getActive(player) == MutationType.RAVAGER) {
            Entity target = event.getTarget();
            player.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.RAVAGER_ATTACK,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    /**
     * Hurting a creature that treats you as one of its own (a zombie while you are a zombie, a raider while you are a
     * Ravager, a golem while you are a villager...) turns that creature against you for good. Any damage you cause
     * counts: melee, abilities, projectiles.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBetrayal(LivingHurtEvent event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide()
                && event.getSource().getEntity() instanceof ServerPlayer player
                && TransformationManager.ignoresPlayer(mob, player)) {
            TransformationManager.markBetrayed(mob, player);
            mob.setTarget(player);
        }
    }

    /**
     * Creeper form: lightning doesn't hurt, it charges you instead (the charged look, and a blast twice as wide)
     * until the transformation ends.
     */
    @SubscribeEvent
    public static void onLightning(EntityStruckByLightningEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || TransformationManager.getActive(player) != MutationType.CREEPER) {
            return;
        }
        event.setCanceled(true);
        PlayerMutationProvider.get(player).ifPresent(data -> {
            if (!data.isCreeperCharged()) {
                data.setCreeperCharged(true);
                ModNetwork.syncMutationData(player);
            }
        });
    }

    /**
     * The Rotting Abomination's melee blows leave their victim staggering: Slowness V for 1 second whenever one deals
     * damage. Not listed among its passives.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAbominationHit(LivingDamageEvent event) {
        if (event.getAmount() > 0.0F && event.getSource().is(DamageTypes.PLAYER_ATTACK)
                && event.getSource().getDirectEntity() instanceof ServerPlayer player
                && TransformationManager.getActive(player) == MutationType.ROTTING_ABOMINATION) {
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ABOMINATION_SLOW_TICKS,
                    ABOMINATION_SLOW_AMPLIFIER), player);
        }
    }

    /** Slowness V (amplifier 4) for 1 second. */
    private static final int ABOMINATION_SLOW_TICKS = 20;
    private static final int ABOMINATION_SLOW_AMPLIFIER = 4;

    /** Transformed players can't ride anything (boats, horses, minecarts, pigs...). */
    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (event.isMounting() && event.getEntityMounting() instanceof Player player && TransformationManager.isTransformed(player)) {
            event.setCanceled(true);
            if (!player.level().isClientSide()) {
                player.displayClientMessage(Component.translatable("message.sanguinaire.cannot_while_transformed"), true);
            }
        }
    }

    /** No transformed player can sleep. */
    @SubscribeEvent
    public static void onSleep(PlayerSleepInBedEvent event) {
        Player player = event.getEntity();
        if (TransformationManager.isTransformed(player) || (player instanceof ServerPlayer serverPlayer
                && TransformationSequence.isPending(serverPlayer))) {
            event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
            player.displayClientMessage(Component.translatable("message.sanguinaire.cannot_sleep_transformed"), true);
        }
    }

    @SubscribeEvent
    public static void onNightHunterJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof Player player && TransformationManager.isNightHunting(player)) {
            player.setDeltaMovement(player.getDeltaMovement().add(0.0D, 0.1D, 0.0D));
        }
    }

    @SubscribeEvent
    public static void onGameEvent(VanillaGameEvent event) {
        if (event.getCause() instanceof Player player && TransformationManager.hasTrait(player, MutationTrait.NO_VIBRATIONS)) {
            event.setCanceled(true);
        }
    }
}
