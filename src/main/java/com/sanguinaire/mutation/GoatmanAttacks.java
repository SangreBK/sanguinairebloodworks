package com.sanguinaire.mutation;

import net.minecraft.sounds.SoundEvents;

import net.minecraft.world.entity.TamableAnimal;

import net.minecraft.world.entity.ai.attributes.Attributes;

import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.world.phys.Vec3;

import net.minecraft.world.phys.HitResult;

import net.minecraft.world.phys.BlockHitResult;

import net.minecraft.world.level.ClipContext;

import net.minecraft.core.particles.BlockParticleOption;

import net.minecraft.core.BlockPos;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
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
 * The Goatman's, Fade Hound's and Rotting Abomination's melee attacks follow their animations (Goatman and Fade
 * Hound 0.75 s, claws landing at 0.20 s and the bite at 0.13 s; Abomination 1.125 s, its blow landing at 0.17 s).
 * Server only. The sweep and swipe sound are the Goatman's alone; the Abomination's hits slow their victim (see
 * {@code TransformationEvents.onAbominationHit}) and it is rooted while it attacks (client side, see
 * {@code GoatmanAnimationClient}).
 * <p>
 * Timings per creature: {@link #attackAnimationTicks}, {@link #hitDelayTicks}.
 * <p>
 * A Goatman's attack on an entity is held back: the vanilla attack is cancelled, and {@link #HIT_DELAY_TICKS}
 * later the hit is delivered for real (if the target is still alive and in reach). Until the animation is over
 * further attacks are ignored. The client enforces the same wait on the attack key, so normally nothing is
 * cancelled here except the original, early hit.
 * <p>
 * The delivered hit is a normal vanilla attack on the first target (full damage, crits, enchantments), then a wide
 * sweep deals {@link #SWEEP_DAMAGE} to everything else around it, with a heavy swipe sound. The Goatman's attack
 * speed is set high enough (see {@code MutationType}) that every hit allowed by the animation lands at full strength.
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID)
public final class GoatmanAttacks {
    /** Goatman and Fade Hound: 0.75 s attack animation. */
    public static final int ATTACK_ANIMATION_TICKS = 15;
    /** Rotting Abomination: 1.125 s attack animation (22.5 ticks, rounded up). */
    public static final int ABOMINATION_ATTACK_ANIMATION_TICKS = 23;
    /** The Goatman's claws connect 0.20 s in. */
    public static final int HIT_DELAY_TICKS = 4;
    /** The Fade Hound's bite lands 0.13 s in (2.6 ticks, rounded up). */
    public static final int FADEHOUND_HIT_DELAY_TICKS = 3;
    /** The Abomination's blow lands 0.17 s in (3.4 ticks). */
    public static final int ABOMINATION_HIT_DELAY_TICKS = 3;
    /** The server cooldown is a little shorter than the animation, so network jitter never swallows a click the client allowed. */
    private static final int SERVER_COOLDOWN_SLACK_TICKS = 2;
    /** Extra distance the target may move away during the wind-up and still be hit. */
    private static final double REACH_TOLERANCE = 1.5D;
    /** Damage dealt to everything else caught in the swipe. */
    private static final float SWEEP_DAMAGE = 5.0F;
    private static final double SWEEP_RADIUS = 1.5D;
    private static final double SWEEP_MAX_DISTANCE = 4.5D;
    private static final float SWEEP_KNOCKBACK = 0.5F;

    /** Radius of the Abomination's slam around the point of impact. */
    private static final double SLAM_RADIUS = 2.5D;
    /** No target: the slam lands where the player aims, up to its reach. */
    private static final int NO_TARGET = -1;

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
                || !hasDelayedAttack(TransformationManager.getActive(player))) {
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
        MutationType type = TransformationManager.getActive(player);
        READY_AT.put(player.getUUID(), now + attackAnimationTicks(type) - SERVER_COOLDOWN_SLACK_TICKS);
        PENDING.put(player.getUUID(), new PendingHit(event.getTarget().getId(), now + hitDelayTicks(type)));
    }

    /**
     * The Abomination swung at the air or the ground (reported by the client): its slam still lands, at the point it
     * aims at, after the same wind-up and with the same cooldown as an attack on an entity.
     */
    public static void queueSlam(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || TransformationManager.getActive(player) != MutationType.ROTTING_ABOMINATION) {
            return;
        }
        int now = server.getTickCount();
        if (now < READY_AT.getOrDefault(player.getUUID(), 0) || PENDING.containsKey(player.getUUID())) {
            return;
        }
        READY_AT.put(player.getUUID(), now + ABOMINATION_ATTACK_ANIMATION_TICKS - SERVER_COOLDOWN_SLACK_TICKS);
        PENDING.put(player.getUUID(), new PendingHit(NO_TARGET, now + ABOMINATION_HIT_DELAY_TICKS));
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
            MutationType type = player == null ? null : TransformationManager.getActive(player);
            if (player == null || !player.isAlive() || !hasDelayedAttack(type)) {
                continue;
            }
            if (type == MutationType.ROTTING_ABOMINATION) {
                slam(player, entry.getValue().targetId());
                continue;
            }
            Entity target = player.level().getEntity(entry.getValue().targetId());
            if (target == null || !target.isAlive()) {
                continue;
            }
            // Measured from the eyes to the target's hitbox, like vanilla's own reach check (feet-to-centre wrongly
            // dropped hits on wide creatures such as Ghasts and Ravagers).
            double reach = player.getEntityReach() + REACH_TOLERANCE;
            if (target.getBoundingBox().distanceToSqr(player.getEyePosition()) > reach * reach) {
                continue; // it got away during the wind-up
            }
            DELIVERING.add(player.getUUID());
            try {
                player.attack(target);
                if (type == MutationType.GOATMAN) {
                    sweep(player, target);
                }
            } finally {
                DELIVERING.remove(player.getUUID());
            }
            if (type != MutationType.GOATMAN) {
                continue; // the Fade Hound's bite and the Abomination's blow have no sweep or swipe sound
            }
            player.level().playSound(null, target.getX(), target.getY(0.5D), target.getZ(), ModSounds.GOATMAN_SWIPE.get(),
                    SoundSource.PLAYERS, 1.3F, 0.55F + player.getRandom().nextFloat() * 0.1F);
        }
    }

    /**
     * The claws carry through: every other creature around the target takes {@link #SWEEP_DAMAGE} and is knocked
     * back, like a sword's sweeping edge but wider, to suit the Goatman's reach.
     */
    private static void sweep(ServerPlayer player, Entity target) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        double sinYaw = Mth.sin(player.getYRot() * Mth.DEG_TO_RAD);
        double cosYaw = Mth.cos(player.getYRot() * Mth.DEG_TO_RAD);
        AABB area = target.getBoundingBox().inflate(SWEEP_RADIUS, 0.5D, SWEEP_RADIUS);
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (other == player || other == target || !other.isAlive() || player.isAlliedTo(other)
                    || (other instanceof ArmorStand stand && stand.isMarker())
                    || player.distanceToSqr(other) > SWEEP_MAX_DISTANCE * SWEEP_MAX_DISTANCE) {
                continue;
            }
            other.knockback(SWEEP_KNOCKBACK, sinYaw, -cosYaw);
            other.hurt(player.damageSources().playerAttack(player), SWEEP_DAMAGE);
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() - sinYaw, player.getY(0.6D), player.getZ() + cosYaw,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    /**
     * The Abomination's blow crashes down as an area attack: everything within {@link #SLAM_RADIUS} of the impact
     * takes the full attack damage (the struck target through a normal vanilla attack, so crits and enchantments
     * apply; the others through the same damage type). The impact is the target, if there is one still in reach,
     * otherwise the block or point the player aims at. A heavy hit's dust, crack and boom mark the spot either way.
     */
    private static void slam(ServerPlayer player, int targetId) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        double reach = player.getEntityReach() + REACH_TOLERANCE;
        Entity target = targetId == NO_TARGET ? null : level.getEntity(targetId);
        if (target != null && (!target.isAlive() || target.getBoundingBox().distanceToSqr(player.getEyePosition()) > reach * reach)) {
            target = null;
        }
        Vec3 impact;
        if (target != null) {
            impact = new Vec3(target.getX(), target.getY(), target.getZ());
        } else {
            Vec3 eye = player.getEyePosition();
            Vec3 end = eye.add(player.getLookAngle().scale(player.getEntityReach()));
            BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            impact = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
        }
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        AABB area = new AABB(impact, impact).inflate(SLAM_RADIUS);
        DELIVERING.add(player.getUUID());
        try {
            if (target != null) {
                player.attack(target);
            }
            for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, area)) {
                if (other == player || other == target || !other.isAlive() || other.isSpectator() || player.isAlliedTo(other)
                        || (other instanceof ArmorStand stand && stand.isMarker())
                        || (other instanceof TamableAnimal pet && player.getUUID().equals(pet.getOwnerUUID()))
                        || other.getBoundingBox().distanceToSqr(impact) > SLAM_RADIUS * SLAM_RADIUS) {
                    continue;
                }
                other.hurt(player.damageSources().playerAttack(player), damage);
            }
        } finally {
            DELIVERING.remove(player.getUUID());
        }
        // A strong hit: a burst at the impact, a ring of crits and the dust of whatever it landed on.
        level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y + 0.3D, impact.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.CRIT, impact.x, impact.y + 0.4D, impact.z, 30, 1.0D, 0.3D, 1.0D, 0.4D);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, impact.x, impact.y + 0.1D, impact.z, 6, 0.8D, 0.05D, 0.8D, 0.01D);
        BlockPos ground = BlockPos.containing(impact.x, impact.y - 0.2D, impact.z);
        BlockState floor = level.getBlockState(ground);
        if (!floor.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, floor), impact.x, impact.y + 0.1D, impact.z,
                    40, 1.2D, 0.1D, 1.2D, 0.15D);
        }
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.5F, 0.6F);
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.PLAYERS, 1.0F, 0.5F);
    }

    /** Creatures whose melee hits wait for their attack animation: the Goatman, the Fade Hound, the Abomination. */
    public static boolean hasDelayedAttack(@Nullable MutationType type) {
        return type == MutationType.GOATMAN || type == MutationType.FADEHOUND || type == MutationType.ROTTING_ABOMINATION;
    }

    /** Length of the creature's attack animation, which is also the time between attacks. */
    public static int attackAnimationTicks(@Nullable MutationType type) {
        return type == MutationType.ROTTING_ABOMINATION ? ABOMINATION_ATTACK_ANIMATION_TICKS : ATTACK_ANIMATION_TICKS;
    }

    /** Ticks from the swing to the moment the hit lands. */
    public static int hitDelayTicks(@Nullable MutationType type) {
        if (type == MutationType.FADEHOUND) {
            return FADEHOUND_HIT_DELAY_TICKS;
        }
        return type == MutationType.ROTTING_ABOMINATION ? ABOMINATION_HIT_DELAY_TICKS : HIT_DELAY_TICKS;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        READY_AT.remove(event.getEntity().getUUID());
    }
}
