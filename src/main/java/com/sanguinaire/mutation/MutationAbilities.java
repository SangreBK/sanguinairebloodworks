package com.sanguinaire.mutation;

import java.util.Set;

import java.util.HashSet;

import net.minecraft.sounds.SoundEvent;

import net.minecraft.world.entity.EntityType;

import net.minecraft.world.effect.MobEffect;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;

import net.minecraft.world.entity.decoration.ArmorStand;

import net.minecraft.world.entity.TamableAnimal;

import net.minecraft.world.entity.raid.Raider;

import com.sanguinaire.config.SanguinaireConfig;

import com.sanguinaire.advancement.ModAdvancements;
import com.sanguinaire.Sanguinaire;
import com.sanguinaire.network.ModNetwork;
import com.sanguinaire.player.PlayerMutationData;
import com.sanguinaire.player.PlayerMutationProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side execution of the Mutation Active Ability Key (design §4). The client only <em>requests</em> the
 * ability; everything (current transformation, target, range, cooldown) is validated and executed here.
 *
 * <h2>Cooldowns</h2>
 * Stored on the player (they persist through relogs, deaths and across transformations), starting when the ability
 * actually takes effect:
 * <ul>
 *     <li>Warden sonic shriek: 20 s</li>
 *     <li>Guardian / Elder Guardian laser: 5 s (from the moment the beam fires; an interrupted charge costs nothing)</li>
 *     <li>Ghast fireball: 4 s</li>
 *     <li>Enderman teleport: 2 s (only after a successful teleport)</li>
 *     <li>Goatman roar: 20 s</li>
 *     <li>Rotting Abomination bile: 10 s</li>
 *     <li>Fade Hound shadow dash: 4 s (only after a successful dash)</li>
 *     <li>Spider / Cave Spider stealth: 8 s</li>
 *     <li>Zombie / Husk / Drowned call of the horde: 30 s</li>
 *     <li>Phantom swoop: 3 s (only while flying)</li>
 *     <li>Goat ram: 8 s</li>
 *     <li>Wolf rage: 8 s</li>
 * </ul>
 * Creeper and Villager abilities have no cooldown; all requests also pass a short anti-spam guard.
 *
 * <h2>Wind-ups</h2>
 * Like the mobs, the Ghast and Warden telegraph their attacks: the Ghast shows its angry shooting face and wails
 * before the fireball leaves (10 ticks), and the Warden plays its sonic-boom charge animation and sound before the
 * shriek fires (34 ticks, vanilla timing). Clients are told to play the animation through
 * {@link com.sanguinaire.network.packet.AbilityAnimationPacket}. The cooldown starts when the wind-up begins.
 */
public final class MutationAbilities {
    /** Anti-spam guard for ability packets; not a gameplay cooldown. */
    public static final int MIN_TICKS_BETWEEN_REQUESTS = 5;

    private static final String COOLDOWN_SONIC_BOOM = "sonic_boom";
    private static final String COOLDOWN_LASER = "laser";
    private static final String COOLDOWN_FIREBALL = "fireball";
    private static final String COOLDOWN_TELEPORT = "teleport";
    private static final String COOLDOWN_ROAR = "roar";
    private static final String COOLDOWN_VOMIT = "vomit";
    private static final String COOLDOWN_DASH = "dash";
    private static final String COOLDOWN_RAVAGER_ROAR = "ravager_roar";
    private static final String COOLDOWN_SPIDER_STEALTH = "spider_stealth";
    private static final String COOLDOWN_HORDE = "horde";
    private static final String COOLDOWN_SWOOP = "phantom_swoop";
    private static final String COOLDOWN_RAM = "goat_ram";
    private static final String COOLDOWN_WOLF_RAGE = "wolf_rage";
    private static final String COOLDOWN_DRAGON_FIREBALL = "dragon_fireball";
    /** The fireball's lingering cloud lasts 30 seconds (vanilla), so the breath recovers more slowly than a Ghast's. */
    private static final int DRAGON_FIREBALL_COOLDOWN_TICKS = 8 * 20;
    private static final int SPIDER_STEALTH_TICKS = 8 * 20;
    private static final int SPIDER_STEALTH_COOLDOWN_TICKS = 8 * 20;
    private static final int HORDE_COOLDOWN_TICKS = 30 * 20;
    private static final double HORDE_RADIUS = 20.0D;
    /** Followers further away than this lose track of the player. */
    private static final double HORDE_LEASH = 48.0D;
    private static final int SWOOP_COOLDOWN_TICKS = 3 * 20;
    private static final int RAM_COOLDOWN_TICKS = 8 * 20;
    private static final int WOLF_RAGE_TICKS = 8 * 20;
    private static final int WOLF_RAGE_COOLDOWN_TICKS = 8 * 20;
    /** Dashes push the player every tick for a few ticks: speed x ticks = distance. */
    private static final int DASH_PUSH_TICKS = 4;
    private static final double SWOOP_SPEED = 8.0D / DASH_PUSH_TICKS;
    private static final double RAM_SPEED = 5.0D / DASH_PUSH_TICKS;
    private static final float DASH_HIT_DAMAGE = 2.0F;
    /** Charged creeper blast: vanilla doubles the radius of a charged creeper. */
    private static final float CHARGED_CREEPER_EXPLOSION_RADIUS = 6.0F;
    private static final int RAVAGER_ROAR_COOLDOWN_TICKS = 15 * 20;
    private static final double RAVAGER_ROAR_RADIUS = 5.0D;
    /** Vanilla's Ravager roar pushes with 4.0, divided by the squared distance (nearer is thrown harder). */
    private static final double RAVAGER_ROAR_STRENGTH = 4.0D;
    private static final double RAVAGER_ROAR_MAX_PUSH = 2.5D;
    /** Same as the vanilla Ravager's roar. */
    private static final float RAVAGER_ROAR_DAMAGE = 6.0F;

    private static final double ROAR_RADIUS = 8.0D;
    private static final int ROAR_STUN_TICKS = 20;             // Slowness VI for 1 s
    private static final int ROAR_WEAKNESS_TICKS = 5 * 20;     // Weakness II for 5 s
    private static final double VOMIT_RANGE = 8.0D;
    /** The spray's half-width at the mouth and how much it widens per block (sized for the 2-block-wide model). */
    private static final double VOMIT_BASE_WIDTH = 1.1D;
    private static final double VOMIT_SPREAD = 0.25D;
    /** Push along the spray (vanilla knockback strength; a Knockback II sword is about 1.4). */
    private static final float VOMIT_PUSH = 1.3F;
    private static final float VOMIT_DAMAGE = 10.0F;
    private static final int VOMIT_POISON_TICKS = 10 * 20;     // Poison I for 10 s
    private static final double DASH_RANGE = 16.0D;
    private static final int DASH_INVULNERABLE_TICKS = 2 * 20;
    private static final int SONIC_BOOM_COOLDOWN_TICKS = 20 * 20;
    private static final int LASER_COOLDOWN_TICKS = 5 * 20;
    private static final int FIREBALL_COOLDOWN_TICKS = 4 * 20;
    private static final int TELEPORT_COOLDOWN_TICKS = 2 * 20;

    private static final float CREEPER_EXPLOSION_RADIUS = 3.0F; // vanilla creeper
    private static final double SONIC_BOOM_RANGE = 15.0D;       // vanilla warden
    private static final float SONIC_BOOM_DAMAGE = 10.0F;       // vanilla warden
    private static final double TELEPORT_RANGE = 64.0D;
    private static final int GHAST_EXPLOSION_POWER = 1;         // vanilla ghast
    private static final double LASER_RANGE = 16.0D;            // vanilla guardian follow distance
    private static final int GUARDIAN_CHARGE_TICKS = 80;        // vanilla Guardian#getAttackDuration
    private static final int ELDER_GUARDIAN_CHARGE_TICKS = 60;  // vanilla ElderGuardian#getAttackDuration
    private static final double TRADE_RANGE = 10.0D;

    private static final int GHAST_WINDUP_TICKS = 10;           // vanilla: warning at 10, shot at 20 of charge
    private static final int WARDEN_WINDUP_TICKS = 34;          // vanilla SonicBoom: damage 34 ticks after start
    public static final int ANIMATION_GHAST_CHARGE = 0;
    public static final int ANIMATION_WARDEN_SONIC_BOOM = 1;
    public static final int ANIMATION_GOATMAN_ROAR = 2;
    public static final int ANIMATION_ABOMINATION_VOMIT = 3;
    public static final int ANIMATION_FADEHOUND_DASH = 4;
    /** Fade Hound splitting into four flickering shadows after being hit. */
    public static final int ANIMATION_FADEHOUND_SPLIT = 5;
    /** Serum injected: the build-up before a transformation (see {@link TransformationSequence}). */
    public static final int ANIMATION_SERUM_INJECT = 6;
    /** Ravager roar: the puppet's mouth opens wide (Ravager#getRoarTick). */
    public static final int ANIMATION_RAVAGER_ROAR = 7;
    public static final int RAVAGER_ROAR_ANIMATION_TICKS = 20;
    /** Wolf rage: the puppet shows the angry wolf texture for the duration. */
    public static final int ANIMATION_WOLF_RAGE = 8;
    /** How long the split lasts; the hound cannot be hurt for this long. */
    public static final int FADE_SPLIT_TICKS = 20;
    /** Dash trail: violet dust every quarter block along three streaks. */
    private static final DustParticleOptions DASH_TRAIL_DUST = new DustParticleOptions(new Vector3f(0.55F, 0.12F, 0.9F), 1.3F);
    private static final double DASH_TRAIL_SPACING = 0.25D;
    private static final double DASH_TRAIL_FLANK = 0.45D;
    private static final double DASH_TRAIL_FLANK_HEIGHT = 0.8D;
    /** The top of Nico's Fade Hound head: about a block in front of the centre, just under 2 blocks up. */
    private static final double DASH_TRAIL_HEAD_FORWARD = 1.0D;
    private static final double DASH_TRAIL_HEAD_HEIGHT = 1.9D;
    /** Hostile mobs one roar must stun for "Silence of the Lambs". */
    private static final int ROAR_ADVANCEMENT_ENEMIES = 8;
    public static final int ROAR_EFFECT_TICKS = 20;
    public static final int VOMIT_EFFECT_TICKS = 16;
    public static final int DASH_EFFECT_TICKS = 10;

    /** Overworld game time until which a Fade Hound is untouchable after its dash. */
    private static final Map<UUID, Long> DASH_INVULNERABLE_UNTIL = new HashMap<>();
    /** Overworld game time until which a Fade Hound is untouchable while splitting after a hit. */
    private static final Map<UUID, Long> FADE_SPLIT_UNTIL = new HashMap<>();
    /** Last time a Ravager's miss played its bite sound (anti-spam). */
    private static final Map<UUID, Long> LAST_EMPTY_SWING = new HashMap<>();
    /** Overworld game time until which a Wolf deals +1 damage. */
    private static final Map<UUID, Long> WOLF_RAGE_UNTIL = new HashMap<>();
    /** Mobs following a Zombie/Husk/Drowned player after its call. */
    private static final Map<UUID, Set<UUID>> HORDES = new HashMap<>();

    /** A short forced dash (Phantom swoop, Goat ram); everything it passes through is hit once. */
    private static final class Dash {
        final MutationType type;
        final Vec3 velocity;
        int ticksLeft;
        final Set<Integer> hit = new HashSet<>();

        Dash(MutationType type, Vec3 velocity, int ticks) {
            this.type = type;
            this.velocity = velocity;
            this.ticksLeft = ticks;
        }
    }

    private static final Map<UUID, Dash> DASHES = new HashMap<>();
    private static final int EMPTY_SWING_MIN_TICKS = 8;

    private record LaserCharge(int targetId, int totalTicks, int ticksLeft) {
    }

    private record Windup(MutationType type, int ticksLeft) {
    }

    private static final Map<UUID, Windup> WINDUPS = new HashMap<>();

    private static final Map<UUID, LaserCharge> LASER_CHARGES = new HashMap<>();
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();

    private MutationAbilities() {
    }

    /** Handles an ability request from the client. Server thread only. */
    public static void requestAbility(ServerPlayer player) {
        MutationType type = TransformationManager.getActive(player);
        if (type == null || !player.isAlive()) {
            return;
        }
        long now = player.level().getGameTime();
        Long last = LAST_REQUEST.get(player.getUUID());
        if (last != null && now - last < MIN_TICKS_BETWEEN_REQUESTS) {
            return;
        }
        LAST_REQUEST.put(player.getUUID(), now);

        switch (type) {
            case CREEPER -> explode(player);
            case WARDEN -> sonicBoom(player);
            case ENDERMAN -> teleportToLook(player);
            case GHAST -> shootFireball(player);
            case GUARDIAN -> startLaser(player, GUARDIAN_CHARGE_TICKS);
            case ELDER_GUARDIAN -> startLaser(player, ELDER_GUARDIAN_CHARGE_TICKS);
            case VILLAGER -> tradeWithNearbyVillager(player);
            case GOATMAN -> roar(player);
            case ROTTING_ABOMINATION -> vomit(player);
            case FADEHOUND -> shadowDash(player);
            case RAVAGER -> ravagerRoar(player);
            case SPIDER, VENOMOUS_SPIDER -> spiderStealth(player);
            case ZOMBIE, HUSK, DROWNED -> callHorde(player, type);
            case PHANTOM -> swoop(player);
            case GOAT -> ram(player);
            case WOLF -> wolfRage(player);
            case ENDER_DRAGON -> shootDragonFireball(player);
            default -> {
                // This transformation has no active ability.
            }
        }
    }

    public static boolean hasActiveAbility(MutationType type) {
        return switch (type) {
            case CREEPER, WARDEN, ENDERMAN, GHAST, GUARDIAN, ELDER_GUARDIAN, VILLAGER,
                 GOATMAN, ROTTING_ABOMINATION, FADEHOUND, RAVAGER, SPIDER, VENOMOUS_SPIDER, ZOMBIE, HUSK, DROWNED,
                 PHANTOM, GOAT, WOLF, ENDER_DRAGON -> true;
            default -> false;
        };
    }

    /** Per-tick processing of charged abilities. Called from {@link TransformationManager#tick}. */
    public static void tick(ServerPlayer player) {
        tickWindup(player);
        tickDashInvulnerability(player);
        tickDash(player);
        if (player.tickCount % 10 == 0) {
            tickHorde(player);
        }
        LaserCharge charge = LASER_CHARGES.get(player.getUUID());
        if (charge == null) {
            return;
        }
        Entity entity = player.level().getEntity(charge.targetId());
        if (!(entity instanceof LivingEntity target) || !target.isAlive() || !player.hasLineOfSight(target)
                || player.distanceToSqr(target) > LASER_RANGE * LASER_RANGE) {
            stopLaser(player);
            return;
        }
        int left = charge.ticksLeft() - 1;
        if (left > 0) {
            LASER_CHARGES.put(player.getUUID(), new LaserCharge(charge.targetId(), charge.totalTicks(), left));
            return;
        }
        stopLaser(player);
        fireLaser(player, target);
        startCooldown(player, COOLDOWN_LASER, LASER_COOLDOWN_TICKS);
    }

    private static void tickWindup(ServerPlayer player) {
        Windup windup = WINDUPS.get(player.getUUID());
        if (windup == null) {
            return;
        }
        if (TransformationManager.getActive(player) != windup.type()) {
            WINDUPS.remove(player.getUUID());
            return;
        }
        int left = windup.ticksLeft() - 1;
        if (left > 0) {
            WINDUPS.put(player.getUUID(), new Windup(windup.type(), left));
            return;
        }
        WINDUPS.remove(player.getUUID());
        if (windup.type() == MutationType.GHAST) {
            fireFireball(player);
        } else if (windup.type() == MutationType.WARDEN) {
            fireSonicBoom(player);
        }
    }

    /** Clears transient ability state (transformation ended, logout). */
    public static void clear(ServerPlayer player) {
        WINDUPS.remove(player.getUUID());
        stopLaser(player);
        LAST_REQUEST.remove(player.getUUID());
        DASH_INVULNERABLE_UNTIL.remove(player.getUUID());
        FADE_SPLIT_UNTIL.remove(player.getUUID());
        LAST_EMPTY_SWING.remove(player.getUUID());
        WOLF_RAGE_UNTIL.remove(player.getUUID());
        HORDES.remove(player.getUUID());
        DASHES.remove(player.getUUID());
    }

    // ---- Cooldowns -------------------------------------------------------------------------------------------

    /** Overworld game time: shared by every dimension, so cooldowns survive dimension changes. */
    private static long now(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    /** @return true if the ability is ready; otherwise tells the player how long is left. */
    private static boolean checkCooldown(ServerPlayer player, String ability) {
        PlayerMutationData data = PlayerMutationProvider.get(player).orElse(null);
        if (data == null) {
            return false;
        }
        long readyAt = data.getAbilityReadyAt(ability);
        long now = now(player);
        if (now >= readyAt) {
            return true;
        }
        long secondsLeft = (readyAt - now + 19) / 20;
        player.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".ability_cooldown", secondsLeft), true);
        return false;
    }

    private static void startCooldown(ServerPlayer player, String ability, int ticks) {
        PlayerMutationProvider.get(player).ifPresent(data -> data.setAbilityReadyAt(ability, now(player) + ticks));
    }

    // ---- Creeper ---------------------------------------------------------------------------------------------

    private static void explode(ServerPlayer player) {
        Level level = player.level();
        // The exploding entity is excluded from its own explosion; mobGriefing is respected (MOB interaction).
        // Struck by lightning earlier in this transformation: a charged creeper's blast is twice as wide.
        boolean charged = PlayerMutationProvider.get(player).map(PlayerMutationData::isCreeperCharged).orElse(false);
        float radius = charged ? CHARGED_CREEPER_EXPLOSION_RADIUS : CREEPER_EXPLOSION_RADIUS;
        level.explode(player, player.getX(), player.getY(), player.getZ(), radius, Level.ExplosionInteraction.MOB);
        ModAdvancements.grant(player, ModAdvancements.AW_MAN);
        TransformationManager.end(player);
    }

    // ---- Warden ----------------------------------------------------------------------------------------------

    private static void sonicBoom(ServerPlayer player) {
        if (WINDUPS.containsKey(player.getUUID()) || !checkCooldown(player, COOLDOWN_SONIC_BOOM)) {
            return;
        }
        startCooldown(player, COOLDOWN_SONIC_BOOM, SONIC_BOOM_COOLDOWN_TICKS);
        WINDUPS.put(player.getUUID(), new Windup(MutationType.WARDEN, WARDEN_WINDUP_TICKS));
        ModNetwork.playAbilityAnimation(player, ANIMATION_WARDEN_SONIC_BOOM, WARDEN_WINDUP_TICKS + 20);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE,
                SoundSource.PLAYERS, 3.0F, 1.0F);
    }

    private static void fireSonicBoom(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getLookAngle();
        Vec3 end = start.add(direction.scale(SONIC_BOOM_RANGE));
        // Like the Warden's, the shriek passes through blocks.
        for (int i = 1; i <= Mth.floor(SONIC_BOOM_RANGE); i++) {
            Vec3 point = start.add(direction.scale(i));
            level.sendParticles(ParticleTypes.SONIC_BOOM, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0F, 1.0F);

        LivingEntity target = findLookTarget(player, start, end);
        if (target != null) {
            target.hurt(level.damageSources().sonicBoom(player), SONIC_BOOM_DAMAGE);
            double resistance = 1.0D - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            Vec3 push = target.position().subtract(player.position()).normalize();
            target.push(push.x * 2.5D * resistance, push.y * 0.5D * resistance, push.z * 2.5D * resistance);
            target.hurtMarked = true;
        }
    }

    // ---- Enderman --------------------------------------------------------------------------------------------

    private static void teleportToLook(ServerPlayer player) {
        if (!checkCooldown(player, COOLDOWN_TELEPORT)) {
            return;
        }
        Level level = player.level();
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(TELEPORT_RANGE));
        BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos destination = hit.getBlockPos().relative(hit.getDirection());
        double x = destination.getX() + 0.5D;
        double y = destination.getY();
        double z = destination.getZ() + 0.5D;
        EntityTeleportEvent.EnderEntity event = ForgeEventFactory.onEnderTeleport(player, x, y, z);
        if (event.isCanceled()) {
            return;
        }
        double fromX = player.getX();
        double fromY = player.getY();
        double fromZ = player.getZ();
        // randomTeleport finds solid ground below the point and refuses positions that would suffocate the player.
        if (player.randomTeleport(event.getTargetX(), event.getTargetY(), event.getTargetZ(), true)) {
            level.playSound(null, fromX, fromY, fromZ, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
            player.resetFallDistance();
            startCooldown(player, COOLDOWN_TELEPORT, TELEPORT_COOLDOWN_TICKS);
        }
    }

    // ---- Ghast -----------------------------------------------------------------------------------------------

    private static void shootFireball(ServerPlayer player) {
        if (WINDUPS.containsKey(player.getUUID()) || !checkCooldown(player, COOLDOWN_FIREBALL)) {
            return;
        }
        startCooldown(player, COOLDOWN_FIREBALL, FIREBALL_COOLDOWN_TICKS);
        WINDUPS.put(player.getUUID(), new Windup(MutationType.GHAST, GHAST_WINDUP_TICKS));
        ModNetwork.playAbilityAnimation(player, ANIMATION_GHAST_CHARGE, GHAST_WINDUP_TICKS + 6);
        player.level().levelEvent(null, 1015, player.blockPosition(), 0); // ghast warning wail
    }

    private static void fireFireball(ServerPlayer player) {
        Level level = player.level();
        Vec3 look = player.getLookAngle();
        LargeFireball fireball = new LargeFireball(level, player, look.x, look.y, look.z, GHAST_EXPLOSION_POWER);
        Vec3 spawn = player.getEyePosition().add(look.scale(1.5D));
        fireball.setPos(spawn.x, spawn.y, spawn.z);
        level.addFreshEntity(fireball);
        level.levelEvent(null, 1016, player.blockPosition(), 0); // ghast shoot sound
    }

    // ---- Ender Dragon ------------------------------------------------------------------------------------------

    /**
     * Ender Dragon: spits a vanilla dragon fireball along the look direction. Where it bursts it leaves the dragon's
     * lingering breath, a cloud of Instant Damage II that spreads for 30 seconds. The dragon is immune to its own
     * breath ({@link DragonForm#onAttacked}).
     */
    private static void shootDragonFireball(ServerPlayer player) {
        if (!checkCooldown(player, COOLDOWN_DRAGON_FIREBALL)) {
            return;
        }
        startCooldown(player, COOLDOWN_DRAGON_FIREBALL, DRAGON_FIREBALL_COOLDOWN_TICKS);
        Level level = player.level();
        Vec3 look = player.getLookAngle();
        net.minecraft.world.entity.projectile.DragonFireball fireball =
                new net.minecraft.world.entity.projectile.DragonFireball(level, player, look.x, look.y, look.z);
        // From the head, clear of the dragon's own hitbox.
        Vec3 spawn = player.getEyePosition().add(look.scale(2.5D));
        fireball.setPos(spawn.x, spawn.y, spawn.z);
        level.addFreshEntity(fireball);
        level.levelEvent(null, 1017, player.blockPosition(), 0); // ender dragon shoot sound
    }

    // ---- Guardian / Elder Guardian -----------------------------------------------------------------------------

    private static void startLaser(ServerPlayer player, int chargeTicks) {
        if (LASER_CHARGES.containsKey(player.getUUID()) || !checkCooldown(player, COOLDOWN_LASER)) {
            return;
        }
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(LASER_RANGE));
        LivingEntity target = findLookTarget(player, start, end);
        if (target == null || !player.hasLineOfSight(target)) {
            return;
        }
        LASER_CHARGES.put(player.getUUID(), new LaserCharge(target.getId(), chargeTicks, chargeTicks));
        ModNetwork.syncLaser(player, target.getId(), chargeTicks);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void stopLaser(ServerPlayer player) {
        if (LASER_CHARGES.remove(player.getUUID()) != null) {
            ModNetwork.syncLaser(player, -1, 0);
        }
    }

    /** Mirrors the vanilla Guardian beam: a magic hit followed by a melee-strength hit. */
    private static void fireLaser(ServerPlayer player, LivingEntity target) {
        Level level = player.level();
        float magic = level.getDifficulty() == Difficulty.HARD ? 3.0F : 1.0F;
        target.hurt(level.damageSources().indirectMagic(player, player), magic);
        target.hurt(level.damageSources().playerAttack(player), (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY(0.5D), target.getZ(),
                    12, 0.3D, 0.4D, 0.3D, 0.05D);
        }
    }

    // ---- Villager ----------------------------------------------------------------------------------------------

    /** Opens the trading screen of the villager you look at, or the nearest one within range. */
    /**
     * Opens trading with the nearest villager that will trade, within {@link #TRADE_RANGE} blocks. It goes through
     * a normal right-click on the villager, so vanilla reputation prices, Hero of the Village and the Villager-form
     * discount ({@link TransformationEvents#onMerchantOpen}) all apply, and other mods see a normal interaction.
     */
    private static void tradeWithNearbyVillager(ServerPlayer player) {
        Villager villager = player.level()
                .getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(TRADE_RANGE),
                        v -> v.isAlive() && !v.isBaby() && !v.isTrading() && !v.isSleeping() && !v.getOffers().isEmpty()
                                && player.distanceToSqr(v) <= TRADE_RANGE * TRADE_RANGE)
                .stream()
                .min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);
        if (villager == null) {
            player.displayClientMessage(Component.translatable("message.sanguinaire.no_villager_to_trade"), true);
            return;
        }
        player.interactOn(villager, InteractionHand.MAIN_HAND);
        if (villager.getTradingPlayer() == player) {
            villager.playSound(SoundEvents.VILLAGER_YES, 1.0F, villager.getVoicePitch());
            player.level().broadcastEntityEvent(villager, (byte) 14); // happy villager particles
        }
    }

    // ---- Ravager ----------------------------------------------------------------------------------------------

    /**
     * The Ravager's roar: everything around the player is thrown back, like the vanilla Ravager's roar after its
     * stun (nearer creatures fly further), taking 6 damage. Raiders, allies and the player's own pets are spared.
     */
    private static void ravagerRoar(ServerPlayer player) {
        if (!checkCooldown(player, COOLDOWN_RAVAGER_ROAR)) {
            return;
        }
        startCooldown(player, COOLDOWN_RAVAGER_ROAR, RAVAGER_ROAR_COOLDOWN_TICKS);
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RAVAGER_ROAR_RADIUS),
                entity -> entity != player && entity.isAlive() && entity.distanceToSqr(player) <= RAVAGER_ROAR_RADIUS * RAVAGER_ROAR_RADIUS)) {
            if (target instanceof Raider || player.isAlliedTo(target)
                    || (target instanceof TamableAnimal pet && pet.isOwnedBy(player))
                    || (target instanceof ArmorStand stand && stand.isMarker())) {
                continue;
            }
            double dx = target.getX() - player.getX();
            double dz = target.getZ() - player.getZ();
            double distanceSqr = Math.max(dx * dx + dz * dz, 0.001D);
            double push = Math.min(RAVAGER_ROAR_STRENGTH / distanceSqr, RAVAGER_ROAR_MAX_PUSH);
            double length = Math.sqrt(distanceSqr);
            target.hurt(level.damageSources().playerAttack(player), RAVAGER_ROAR_DAMAGE);
            target.push(dx / length * push, 0.2D, dz / length * push);
            target.hurtMarked = true; // send the new velocity (players move themselves)
        }
        Vec3 center = player.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.POOF, center.x, center.y, center.z, 40, 1.2D, 0.6D, 1.2D, 0.1D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.0F, 1.0F);
        ModNetwork.playAbilityAnimation(player, ANIMATION_RAVAGER_ROAR, RAVAGER_ROAR_ANIMATION_TICKS);
    }

    // ---- Goatman ----------------------------------------------------------------------------------------------

    /** A bellowing roar: every mob nearby is stunned (Slowness VI, 1 s) and enfeebled (Weakness II, 5 s). */
    private static void roar(ServerPlayer player) {
        if (!checkCooldown(player, COOLDOWN_ROAR)) {
            return;
        }
        startCooldown(player, COOLDOWN_ROAR, SanguinaireConfig.GOATMAN.abilityCooldownTicks());
        Level level = player.level();
        int stunnedEnemies = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(ROAR_RADIUS),
                mob -> mob.isAlive() && mob.distanceToSqr(player) <= ROAR_RADIUS * ROAR_RADIUS)) {
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ROAR_STUN_TICKS, 5), player);
            mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ROAR_WEAKNESS_TICKS, 1), player);
            mob.getNavigation().stop();
            if (mob instanceof Enemy) {
                stunnedEnemies++;
            }
        }
        if (stunnedEnemies >= ROAR_ADVANCEMENT_ENEMIES) {
            ModAdvancements.grant(player, ModAdvancements.SILENCE_OF_THE_LAMBS);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 2.0F, 0.7F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GOAT_SCREAMING_AMBIENT, SoundSource.PLAYERS, 1.5F, 0.5F);
        ModNetwork.playAbilityAnimation(player, ANIMATION_GOATMAN_ROAR, ROAR_EFFECT_TICKS);
    }

    // ---- Rotting Abomination ------------------------------------------------------------------------------------

    /** A gout of acidic bile: everything caught in the spray takes 10 magic damage and Poison I for 10 s. */
    private static void vomit(ServerPlayer player) {
        if (!checkCooldown(player, COOLDOWN_VOMIT)) {
            return;
        }
        startCooldown(player, COOLDOWN_VOMIT, SanguinaireConfig.ROTTING_ABOMINATION.abilityCooldownTicks());
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        AABB area = player.getBoundingBox().expandTowards(look.scale(VOMIT_RANGE)).inflate(VOMIT_BASE_WIDTH + VOMIT_RANGE * VOMIT_SPREAD);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != player && entity.isAlive() && !entity.isSpectator())) {
            Vec3 toTarget = target.getBoundingBox().getCenter().subtract(eye);
            double along = toTarget.dot(look);
            if (along <= 0.0D || along > VOMIT_RANGE + target.getBbWidth() * 0.5D) {
                continue;
            }
            // A spray that widens with distance.
            double offAxis = toTarget.subtract(look.scale(along)).length();
            if (offAxis > VOMIT_BASE_WIDTH + target.getBbWidth() * 0.5D + along * VOMIT_SPREAD || !player.hasLineOfSight(target)) {
                continue;
            }
            target.hurt(level.damageSources().indirectMagic(player, player), VOMIT_DAMAGE);
            target.addEffect(new MobEffectInstance(MobEffects.POISON, VOMIT_POISON_TICKS, 0), player);
            // The force of the gush shoves it away along the spray.
            target.knockback(VOMIT_PUSH, -look.x, -look.z);
            target.hurtMarked = true;
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SLIME_BALL)),
                    target.getX(), target.getY(0.6D), target.getZ(), 14, 0.3D, 0.3D, 0.3D, 0.08D);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1.2F, 0.4F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LLAMA_SPIT, SoundSource.PLAYERS, 1.5F, 0.5F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2F, 0.5F);
        ModNetwork.playAbilityAnimation(player, ANIMATION_ABOMINATION_VOMIT, VOMIT_EFFECT_TICKS);
    }

    // ---- Fade Hound ---------------------------------------------------------------------------------------------

    /**
     * Dashes through the shadows to the furthest open spot along the look direction (up to 16 blocks, never through
     * walls), heals half its health and is untouchable for 2 seconds, leaving a trail of smoke and ink.
     */
    private static void shadowDash(ServerPlayer player) {
        if (!checkCooldown(player, COOLDOWN_DASH) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(DASH_RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double maxDistance = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().distanceTo(eye) : DASH_RANGE;
        double halfHeight = player.getBbHeight() * 0.5D;
        Vec3 destination = null;
        for (double distance = maxDistance; distance >= 1.0D; distance -= 0.25D) {
            Vec3 feet = eye.add(look.scale(distance)).subtract(0.0D, halfHeight, 0.0D);
            if (level.noCollision(player, player.getDimensions(player.getPose()).makeBoundingBox(feet))) {
                destination = feet;
                break;
            }
        }
        if (destination == null) {
            return; // nowhere to go
        }
        Vec3 from = player.position();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y + halfHeight, from.z, 20, 0.3D, 0.4D, 0.3D, 0.02D);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, 0.8F);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.CAMEL_DASH, SoundSource.PLAYERS, 1.0F, 1.5F);
        player.teleportTo(destination.x, destination.y, destination.z);
        player.resetFallDistance();

        // The shadow trail: three violet streaks, from both flanks and from the top of the head.
        Vec3 path = destination.subtract(from);
        Vec3 flat = new Vec3(path.x, 0.0D, path.z);
        Vec3 forward = flat.lengthSqr() < 1.0E-4D ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3[] streaks = {
                side.scale(DASH_TRAIL_FLANK).add(0.0D, DASH_TRAIL_FLANK_HEIGHT, 0.0D),
                side.scale(-DASH_TRAIL_FLANK).add(0.0D, DASH_TRAIL_FLANK_HEIGHT, 0.0D),
                forward.scale(DASH_TRAIL_HEAD_FORWARD).add(0.0D, DASH_TRAIL_HEAD_HEIGHT, 0.0D)};
        int steps = Math.max(1, Mth.ceil(path.length() / DASH_TRAIL_SPACING));
        for (int i = 0; i <= steps; i++) {
            Vec3 point = from.add(path.scale(i / (double) steps));
            for (Vec3 offset : streaks) {
                Vec3 at = point.add(offset);
                level.sendParticles(DASH_TRAIL_DUST, at.x, at.y, at.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            }
        }
        level.sendParticles(ParticleTypes.SQUID_INK, destination.x, destination.y + halfHeight, destination.z, 12, 0.3D, 0.3D, 0.3D, 0.05D);
        level.playSound(null, destination.x, destination.y, destination.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, 1.2F);

        // Half the bar, rounded up to whole half hearts, so a hound at half health is mended all the way.
        player.heal(Mth.ceil(player.getMaxHealth() * 0.5F));
        DASH_INVULNERABLE_UNTIL.put(player.getUUID(), now(player) + DASH_INVULNERABLE_TICKS);
        startCooldown(player, COOLDOWN_DASH, SanguinaireConfig.FADEHOUND.abilityCooldownTicks());
        ModNetwork.playAbilityAnimation(player, ANIMATION_FADEHOUND_DASH, DASH_EFFECT_TICKS);
    }

    /**
     * A swing at nothing (reported by the client). The Ravager snaps its jaws: same sound as a landed bite, at most
     * once every few ticks.
     */
    public static void onEmptySwing(ServerPlayer player) {
        if (TransformationManager.getActive(player) == MutationType.ROTTING_ABOMINATION) {
            GoatmanAttacks.queueSlam(player); // the slam needs no target
            return;
        }
        if (TransformationManager.getActive(player) != MutationType.RAVAGER) {
            return;
        }
        long now = now(player);
        Long last = LAST_EMPTY_SWING.get(player.getUUID());
        if (last != null && now - last < EMPTY_SWING_MIN_TICKS) {
            return;
        }
        LAST_EMPTY_SWING.put(player.getUUID(), now);
        Vec3 mouth = player.getEyePosition().add(player.getLookAngle().scale(1.5D));
        player.level().playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.RAVAGER_ATTACK, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    // ---- Spider / Cave Spider ----------------------------------------------------------------------------------

    /** Vanishes for 8 seconds and gains one of Strength I, Speed I or Regeneration I for as long. */
    private static void spiderStealth(ServerPlayer player) {
        if (!checkCooldown(player, COOLDOWN_SPIDER_STEALTH) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, SPIDER_STEALTH_TICKS, 0, false, false, true));
        MobEffect[] boons = {MobEffects.DAMAGE_BOOST, MobEffects.MOVEMENT_SPEED, MobEffects.REGENERATION};
        player.addEffect(new MobEffectInstance(boons[player.getRandom().nextInt(boons.length)], SPIDER_STEALTH_TICKS, 0));
        level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY(0.4D), player.getZ(), 16, 0.4D, 0.25D, 0.4D, 0.02D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPIDER_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.7F);
        startCooldown(player, COOLDOWN_SPIDER_STEALTH, SPIDER_STEALTH_COOLDOWN_TICKS);
    }

    // ---- Zombie / Husk / Drowned ------------------------------------------------------------------------------

    /**
     * Groans the creature's idle call: every creature of exactly the same kind within 20 blocks (that hasn't been
     * betrayed) follows the player until the transformation ends, still fighting anything it would fight.
     */
    private static void callHorde(ServerPlayer player, MutationType type) {
        if (!checkCooldown(player, COOLDOWN_HORDE) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        EntityType<?> kind = switch (type) {
            case HUSK -> EntityType.HUSK;
            case DROWNED -> EntityType.DROWNED;
            default -> EntityType.ZOMBIE;
        };
        SoundEvent call = switch (type) {
            case HUSK -> SoundEvents.HUSK_AMBIENT;
            case DROWNED -> player.isInWater() ? SoundEvents.DROWNED_AMBIENT_WATER : SoundEvents.DROWNED_AMBIENT;
            default -> SoundEvents.ZOMBIE_AMBIENT;
        };
        level.playSound(null, player.getX(), player.getY(), player.getZ(), call, SoundSource.PLAYERS, 2.5F, 0.8F);
        Set<UUID> followers = HORDES.computeIfAbsent(player.getUUID(), id -> new HashSet<>());
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(HORDE_RADIUS),
                mob -> mob.getType() == kind && mob.isAlive() && mob.distanceToSqr(player) <= HORDE_RADIUS * HORDE_RADIUS
                        && !TransformationManager.hasBetrayed(mob, player))) {
            followers.add(mob.getUUID());
            level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getEyeY() + 0.3D, mob.getZ(), 5, 0.2D, 0.1D, 0.2D, 0.01D);
        }
        startCooldown(player, COOLDOWN_HORDE, HORDE_COOLDOWN_TICKS);
    }

    /** Followers walk back to the player whenever they have nothing to fight. Every 10 ticks. */
    private static void tickHorde(ServerPlayer player) {
        Set<UUID> followers = HORDES.get(player.getUUID());
        if (followers == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        followers.removeIf(id -> {
            Entity entity = level.getEntity(id);
            if (!(entity instanceof Mob mob) || !mob.isAlive() || TransformationManager.hasBetrayed(mob, player)
                    || mob.distanceToSqr(player) > HORDE_LEASH * HORDE_LEASH) {
                return true;
            }
            if (mob.getTarget() == null && mob.distanceToSqr(player) > 9.0D) {
                mob.getNavigation().moveTo(player, 1.1D);
            }
            return false;
        });
        if (followers.isEmpty()) {
            HORDES.remove(player.getUUID());
        }
    }

    /**
     * Whatever the horde's caller attacks, the horde attacks too (not each other, and not the caller's own followers).
     * Called from {@link TransformationEvents} when a Zombie/Husk/Drowned-form player hurts something.
     */
    public static void directHorde(ServerPlayer player, LivingEntity victim) {
        Set<UUID> followers = HORDES.get(player.getUUID());
        if (followers == null || followers.contains(victim.getUUID()) || victim == player || !victim.isAlive()
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        for (UUID id : followers) {
            if (level.getEntity(id) instanceof Mob mob && mob.isAlive() && mob != victim && mob.getTarget() != victim) {
                mob.setTarget(victim);
            }
        }
    }

    // ---- Phantom / Goat dashes ---------------------------------------------------------------------------------

    /** Phantom: shrieks and swoops 8 blocks along the look direction; only while flying. */
    private static void swoop(ServerPlayer player) {
        if (!player.getAbilities().flying) {
            player.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".swoop_needs_flight"), true);
            return;
        }
        if (DASHES.containsKey(player.getUUID()) || !checkCooldown(player, COOLDOWN_SWOOP)) {
            return;
        }
        DASHES.put(player.getUUID(), new Dash(MutationType.PHANTOM, player.getLookAngle().scale(SWOOP_SPEED), DASH_PUSH_TICKS));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS, 1.5F, 1.0F);
        startCooldown(player, COOLDOWN_SWOOP, SWOOP_COOLDOWN_TICKS);
    }

    /** Goat: lowers its horns and charges 5 blocks straight ahead. */
    private static void ram(ServerPlayer player) {
        if (DASHES.containsKey(player.getUUID()) || !checkCooldown(player, COOLDOWN_RAM)) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        Vec3 forward = flat.lengthSqr() < 1.0E-4D ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
        DASHES.put(player.getUUID(), new Dash(MutationType.GOAT, forward.scale(RAM_SPEED), DASH_PUSH_TICKS));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GOAT_PREPARE_RAM, SoundSource.PLAYERS, 1.2F, 1.0F);
        startCooldown(player, COOLDOWN_RAM, RAM_COOLDOWN_TICKS);
    }

    /**
     * Pushes the dashing player for a few ticks (the motion is sent to the client, which does the actual moving and
     * the wall collisions) and hits everything in the swept path once for {@link #DASH_HIT_DAMAGE}.
     */
    private static void tickDash(ServerPlayer player) {
        Dash dash = DASHES.get(player.getUUID());
        if (dash == null) {
            return;
        }
        if (TransformationManager.getActive(player) != dash.type || !player.isAlive()) {
            DASHES.remove(player.getUUID());
            return;
        }
        Level level = player.level();
        AABB path = player.getBoundingBox().inflate(0.3D).expandTowards(dash.velocity);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, path, entity -> canDashHit(player, entity))) {
            if (dash.hit.add(target.getId())) {
                target.hurt(player.damageSources().playerAttack(player), DASH_HIT_DAMAGE);
                if (dash.type == MutationType.GOAT) {
                    level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GOAT_RAM_IMPACT, SoundSource.PLAYERS, 1.0F, 1.0F);
                }
            }
        }
        if (dash.ticksLeft > 0) {
            dash.ticksLeft--;
            player.setDeltaMovement(dash.velocity);
        } else {
            DASHES.remove(player.getUUID());
            player.setDeltaMovement(dash.velocity.scale(0.2D));
        }
        player.hurtMarked = true; // sends the new motion to the client
        player.resetFallDistance();
    }

    private static boolean canDashHit(ServerPlayer player, LivingEntity entity) {
        if (entity == player || !entity.isAlive() || entity.isSpectator() || player.isAlliedTo(entity)) {
            return false;
        }
        if (entity instanceof ArmorStand stand && stand.isMarker()) {
            return false;
        }
        return !(entity instanceof TamableAnimal pet && player.getUUID().equals(pet.getOwnerUUID()));
    }

    // ---- Wolf --------------------------------------------------------------------------------------------------

    /** Bares its teeth: angry-wolf look and +1 damage on every hit for 8 seconds. */
    private static void wolfRage(ServerPlayer player) {
        if (!checkCooldown(player, COOLDOWN_WOLF_RAGE)) {
            return;
        }
        WOLF_RAGE_UNTIL.put(player.getUUID(), now(player) + WOLF_RAGE_TICKS);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOLF_GROWL, SoundSource.PLAYERS, 1.3F, 0.9F);
        ModNetwork.playAbilityAnimation(player, ANIMATION_WOLF_RAGE, WOLF_RAGE_TICKS);
        startCooldown(player, COOLDOWN_WOLF_RAGE, WOLF_RAGE_COOLDOWN_TICKS);
    }

    /** True while a Wolf's rage is active. Server only. */
    public static boolean isWolfRaging(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || TransformationManager.getActive(player) != MutationType.WOLF) {
            return false;
        }
        Long until = WOLF_RAGE_UNTIL.get(player.getUUID());
        return until != null && now(serverPlayer) < until;
    }

    /** True while a Fade Hound is still untouchable after its dash. Server only. */
    public static boolean isDashInvulnerable(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        Long until = DASH_INVULNERABLE_UNTIL.get(player.getUUID());
        return until != null && now(serverPlayer) < until;
    }

    /**
     * Fade Hound hit reaction: the hound shatters into four flickering shadows that scatter and fade, leaving it
     * invisible. For {@link #FADE_SPLIT_TICKS} ticks nothing can hurt it. The attacker is blinded by the caller.
     */
    public static void startFadeSplit(ServerPlayer player, @Nullable LivingEntity attacker) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        FADE_SPLIT_UNTIL.put(player.getUUID(), now(player) + FADE_SPLIT_TICKS);
        double x = player.getX();
        double y = player.getY(0.5D);
        double z = player.getZ();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 50, 0.35D, 0.6D, 0.35D, 0.08D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 16, 0.3D, 0.4D, 0.3D, 0.03D);
        level.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 10, 0.25D, 0.35D, 0.25D, 0.06D);
        level.playSound(null, x, y, z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.2F, 0.7F);
        level.playSound(null, x, y, z, SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 0.8F, 1.3F);
        if (attacker != null) {
            level.playSound(null, attacker.getX(), attacker.getEyeY(), attacker.getZ(), SoundEvents.ILLUSIONER_PREPARE_BLINDNESS,
                    SoundSource.PLAYERS, 1.0F, 0.9F);
            level.sendParticles(ParticleTypes.SQUID_INK, attacker.getX(), attacker.getEyeY(), attacker.getZ(), 12,
                    0.2D, 0.15D, 0.2D, 0.02D);
        }
        ModNetwork.playAbilityAnimation(player, ANIMATION_FADEHOUND_SPLIT, FADE_SPLIT_TICKS);
    }

    /** True while a Fade Hound is splitting after a hit. Server only. */
    public static boolean isFadeSplitInvulnerable(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        Long until = FADE_SPLIT_UNTIL.get(player.getUUID());
        if (until == null) {
            return false;
        }
        if (now(serverPlayer) >= until) {
            FADE_SPLIT_UNTIL.remove(player.getUUID());
            return false;
        }
        return true;
    }

    /** Wisps of shadow cling to the Fade Hound while it is untouchable. */
    private static void tickDashInvulnerability(ServerPlayer player) {
        Long until = DASH_INVULNERABLE_UNTIL.get(player.getUUID());
        if (until == null) {
            return;
        }
        if (now(player) >= until) {
            DASH_INVULNERABLE_UNTIL.remove(player.getUUID());
            return;
        }
        if (player.tickCount % 2 == 0 && player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY(0.5D), player.getZ(), 3,
                    player.getBbWidth() * 0.4D, player.getBbHeight() * 0.3D, player.getBbWidth() * 0.4D, 0.01D);
        }
    }

    // ---- Targeting -------------------------------------------------------------------------------------------

    @Nullable
    private static LivingEntity findLookTarget(ServerPlayer player, Vec3 start, Vec3 end) {
        AABB searchBox = player.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, start, end, searchBox,
                entity -> entity instanceof LivingEntity living && living.isAlive() && !entity.isSpectator() && entity != player,
                start.distanceToSqr(end));
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }
}
