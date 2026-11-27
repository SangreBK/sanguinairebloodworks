package com.sanguinaire.mutation;

import com.sanguinaire.Sanguinaire;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;

/**
 * The Ender Dragon form's passives (its active ability, the dragon fireball, lives in {@link MutationAbilities}):
 * <ul>
 *     <li>{@link MutationTrait#DRAGON_REPULSION}: like the vanilla dragon's wings, every creature touching the dragon is
 *     thrown back and takes 2 damage. Checked twice a second, which is also vanilla's hurt-immunity window, so a
 *     creature pinned against the dragon takes 2 damage every half second.</li>
 *     <li>{@link MutationTrait#CRYSTAL_HEALING}: every End Crystal within 32 blocks heals the dragon 1 health every half
 *     second, and the healing stacks (three crystals: 3 health every half second). Clients draw a beam from each one
 *     (see {@code TransformationRenderer}), using the same list, {@link #findHealingCrystals}.</li>
 *     <li>Immune to its own lingering breath, and to Blindness and Darkness.</li>
 *     <li>A second, invisible hitbox ({@link DragonHitbox}) around the raised body, so arrows and blows aimed at the
 *     full-size model land.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID)
public final class DragonForm {
    /** Vanilla EnderDragon#checkCrystals searches a box inflated by 32 blocks. */
    public static final double CRYSTAL_RANGE = 32.0D;
    private static final int CRYSTAL_HEAL_INTERVAL = 10;
    private static final int REPULSION_INTERVAL = 10;
    private static final float REPULSION_DAMAGE = 2.0F;
    private static final double REPULSION_PUSH = 1.2D;
    private static final double REPULSION_LIFT = 0.3D;
    private static final int FLAP_INTERVAL = 30;

    private DragonForm() {
    }

    /** Server, every tick while in the Ender Dragon form. */
    static void tick(ServerPlayer player, MutationType type) {
        int age = player.tickCount;
        if (type == MutationType.ENDER_DRAGON) {
            ensureHitbox(player);
        }
        if (type.hasTrait(MutationTrait.DRAGON_REPULSION) && age % REPULSION_INTERVAL == 0) {
            repel(player);
        }
        if (type.hasTrait(MutationTrait.CRYSTAL_HEALING) && age % CRYSTAL_HEAL_INTERVAL == 0
                && player.getHealth() < player.getMaxHealth()) {
            int crystals = findHealingCrystals(player).size();
            if (crystals > 0) {
                player.heal(crystals);
            }
        }
        if (type == MutationType.ENDER_DRAGON) {
            // Effects that were already on the player when they transformed.
            player.removeEffect(MobEffects.BLINDNESS);
            player.removeEffect(MobEffects.DARKNESS);
        }
        if (type == MutationType.ENDER_DRAGON && age % FLAP_INTERVAL == 0 && player.getAbilities().flying && !player.isInvisible()) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_FLAP,
                    SoundSource.PLAYERS, 0.6F, 0.9F + player.getRandom().nextFloat() * 0.2F);
        }
    }

    /** Each dragon-form player's body hitbox (removed entities are replaced on the next tick). */
    private static final java.util.Map<java.util.UUID, DragonHitbox> HITBOXES = new java.util.HashMap<>();

    private static void ensureHitbox(ServerPlayer player) {
        DragonHitbox hitbox = HITBOXES.get(player.getUUID());
        if (hitbox != null && !hitbox.isRemoved() && hitbox.level() == player.level()) {
            return;
        }
        if (hitbox != null) {
            hitbox.discard();
        }
        hitbox = com.sanguinaire.registry.ModEntities.DRAGON_HITBOX.get().create(player.level());
        if (hitbox == null) {
            return;
        }
        hitbox.setOwner(player);
        player.level().addFreshEntity(hitbox);
        HITBOXES.put(player.getUUID(), hitbox);
    }

    /** The dragon's own arrows, fireballs and other projectiles pass through its body hitbox. */
    @SubscribeEvent
    public static void onProjectileImpact(net.minecraftforge.event.entity.ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit && hit.getEntity() instanceof DragonHitbox hitbox
                && hitbox.getOwner() != null && event.getProjectile().getOwner() == hitbox.getOwner()) {
            event.setImpactResult(net.minecraftforge.event.entity.ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        }
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        DragonHitbox hitbox = HITBOXES.remove(event.getEntity().getUUID());
        if (hitbox != null) {
            hitbox.discard();
        }
    }

    private static void repel(ServerPlayer player) {
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.1D),
                entity -> entity != player && entity.isAlive() && !entity.isSpectator() && !(entity instanceof ArmorStand)
                        && !entity.isAlliedTo(player) && !isOwnPet(entity, player)
                        && !(entity instanceof Player other && (other.isCreative() || !player.canHarmPlayer(other))))) {
            Vec3 away = new Vec3(target.getX() - player.getX(), 0.0D, target.getZ() - player.getZ());
            if (away.lengthSqr() < 1.0E-4D) {
                away = new Vec3(player.getRandom().nextDouble() - 0.5D, 0.0D, player.getRandom().nextDouble() - 0.5D);
            }
            away = away.normalize().scale(REPULSION_PUSH);
            target.push(away.x, REPULSION_LIFT, away.z);
            target.hurtMarked = true; // send the push to players at once
            target.hurt(player.damageSources().playerAttack(player), REPULSION_DAMAGE);
        }
    }

    private static boolean isOwnPet(LivingEntity entity, Player player) {
        return entity instanceof TamableAnimal pet && pet.isOwnedBy(player);
    }

    /** Every End Crystal healing this dragon-form player: all within 32 blocks. Safe on both sides. */
    public static List<EndCrystal> findHealingCrystals(Player player) {
        return player.level().getEntitiesOfClass(EndCrystal.class, player.getBoundingBox().inflate(CRYSTAL_RANGE), EndCrystal::isAlive);
    }

    /** The dragon's eyes cannot be clouded: Blindness and Darkness never take hold. */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        MobEffect effect = event.getEffectInstance().getEffect();
        if ((effect == MobEffects.BLINDNESS || effect == MobEffects.DARKNESS) && event.getEntity() instanceof Player player
                && TransformationManager.getActive(player) == MutationType.ENDER_DRAGON) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** The dragon is not harmed by its own lingering breath (or anything else it caused itself). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttacked(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player player && event.getSource().getEntity() == player
                && TransformationManager.getActive(player) == MutationType.ENDER_DRAGON) {
            event.setCanceled(true);
        }
    }
}
