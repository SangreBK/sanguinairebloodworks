package com.sanguinaire.effect;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * The two timed enhancing serums:
 * <ul>
 *     <li><b>Delayed Accretion</b>: every hit is cancelled before it lands (no damage, knockback or hurt flash) and its
 *     damage is added to a running total, already reduced by armor, Protection and Resistance as the hit would have
 *     been. Repeated hits follow vanilla's half-second immunity (a stronger hit within it adds only the difference),
 *     so standing in fire is not counted twenty times a second. Damage that bypasses invulnerability (the void,
 *     /kill) still goes through. When the effect expires or is removed (milk, /effect clear), the total is dealt at
 *     once (on the next tick) as {@code sanguinaire:delayed_accretion} damage, which nothing reduces again. The total
 *     lives in the entity's persistent data, so it survives logging out. Only players are paid back (the serum is
 *     only for players; a mob given the effect by command is simply invulnerable).</li>
 *     <li><b>Leeching</b>: when the bearer deals damage that is not a projectile, they heal by the damage actually
 *     dealt (after the target's armor), never more than the target had left.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Sanguinaire.MOD_ID)
public final class SerumEffectEvents {
    public static final ResourceKey<DamageType> DELAYED_ACCRETION_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, Sanguinaire.id("delayed_accretion"));

    private static final String TAG_TOTAL = Sanguinaire.MOD_ID + ".accretion_total";
    private static final String TAG_LAST = Sanguinaire.MOD_ID + ".accretion_last";
    private static final String TAG_TIME = Sanguinaire.MOD_ID + ".accretion_time";
    private static final String TAG_PENDING = Sanguinaire.MOD_ID + ".accretion_pending";
    /** Vanilla's invulnerability window after a hit (half of the 20 ticks a living entity gets). */
    private static final int HIT_WINDOW_TICKS = 10;
    private static final DustParticleOptions LEECH_DUST = new DustParticleOptions(new Vector3f(0.55F, 0.02F, 0.08F), 1.0F);

    private SerumEffectEvents() {
    }

    // ---- Delayed Accretion -------------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttacked(LivingAttackEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !entity.hasEffect(ModEffects.DELAYED_ACCRETION.get())) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DELAYED_ACCRETION_DAMAGE)) {
            return;
        }
        event.setCanceled(true);
        float amount = reduced(entity, source, event.getAmount());
        if (amount <= 0.0F) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        long now = entity.level().getGameTime();
        float added;
        if (data.contains(TAG_TIME) && now - data.getLong(TAG_TIME) < HIT_WINDOW_TICKS) {
            float last = data.getFloat(TAG_LAST);
            if (amount <= last) {
                return; // still "invulnerable" from a stronger hit
            }
            added = amount - last; // vanilla: a stronger hit inside the window deals only the difference
            data.putFloat(TAG_LAST, amount);
        } else {
            added = amount;
            data.putFloat(TAG_LAST, amount);
            data.putLong(TAG_TIME, now);
        }
        data.putFloat(TAG_TOTAL, data.getFloat(TAG_TOTAL) + added);
        // A dull thud instead of the hurt sound: the blow was felt, not suffered (yet).
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SHIELD_BLOCK,
                SoundSource.PLAYERS, 0.5F, 0.5F);
    }

    /** What the hit would really have taken after armor, Protection and Resistance (vanilla's own order). */
    private static float reduced(LivingEntity entity, DamageSource source, float amount) {
        if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            amount = CombatRules.getDamageAfterAbsorb(amount, entity.getArmorValue(),
                    (float) entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        }
        MobEffectInstance resistance = entity.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (resistance != null && !source.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
            amount = Math.max(amount * (25 - (resistance.getAmplifier() + 1) * 5) / 25.0F, 0.0F);
        }
        if (!source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
            int protection = EnchantmentHelper.getDamageProtection(entity.getArmorSlots(), source);
            if (protection > 0) {
                amount = CombatRules.getDamageAfterMagicAbsorb(amount, protection);
            }
        }
        return amount;
    }

    @SubscribeEvent
    public static void onExpired(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance != null) {
            onAccretionEnded(event.getEntity(), instance.getEffect());
        }
    }

    @SubscribeEvent
    public static void onRemoved(MobEffectEvent.Remove event) {
        onAccretionEnded(event.getEntity(), event.getEffect());
    }

    private static void onAccretionEnded(LivingEntity entity, MobEffect effect) {
        if (effect != ModEffects.DELAYED_ACCRETION.get() || entity.level().isClientSide()) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        float total = data.getFloat(TAG_TOTAL);
        data.remove(TAG_TOTAL);
        data.remove(TAG_LAST);
        data.remove(TAG_TIME);
        if (total > 0.0F) {
            // Dealt on the next player tick: these events fire while the entity's effect list is being iterated, and a
            // death or a Totem of Undying (which clears all effects) in the middle of that would break the loop.
            data.putFloat(TAG_PENDING, data.getFloat(TAG_PENDING) + total);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (!data.contains(TAG_PENDING)) {
            return;
        }
        float total = data.getFloat(TAG_PENDING);
        data.remove(TAG_PENDING);
        if (total <= 0.0F || !player.isAlive()) {
            return;
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5F, 0.6F);
        player.displayClientMessage(Component.translatable("message." + Sanguinaire.MOD_ID + ".delayed_accretion_ended",
                String.format(java.util.Locale.ROOT, "%.1f", total / 2.0F)).withStyle(ChatFormatting.DARK_PURPLE), true);
        player.invulnerableTime = 0;
        DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DELAYED_ACCRETION_DAMAGE));
        player.hurt(source, total);
    }

    // ---- Leeching ----------------------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        DamageSource source = event.getSource();
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || !(source.getEntity() instanceof LivingEntity attacker) || attacker == target
                || source.is(DamageTypeTags.IS_PROJECTILE) || !attacker.hasEffect(ModEffects.LEECHING.get())) {
            return;
        }
        float healed = Math.min(event.getAmount(), target.getHealth());
        if (healed <= 0.0F || attacker.getHealth() >= attacker.getMaxHealth()) {
            return;
        }
        attacker.heal(healed);
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(LEECH_DUST, target.getX(), target.getY(0.6D), target.getZ(), 6, 0.25D, 0.25D, 0.25D, 0.0D);
            level.sendParticles(LEECH_DUST, attacker.getX(), attacker.getY(0.6D), attacker.getZ(), 4, 0.25D, 0.3D, 0.25D, 0.0D);
        }
    }
}
