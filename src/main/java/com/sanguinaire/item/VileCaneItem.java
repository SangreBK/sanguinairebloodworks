package com.sanguinaire.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.sanguinaire.Sanguinaire;
import com.sanguinaire.client.ClientHooks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import com.sanguinaire.registry.ModEnchantments;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fml.DistExecutor;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The Vile Cane: a walking cane that unfolds into a blade, for surgeons whose bodies are already failing. Its power
 * grows with the bearer's Weakened Hearts. Only works for a human holding it in the main hand; the Mutation
 * Ability key (V by default) switches the mode (see {@code VileCaneEvents} and {@code ToggleCanePacket}).
 * <ul>
 *     <li><b>Cane mode</b> (default): not a weapon (bare-hand damage). Steps up whole blocks and adds
 *     {@value #SPEED_PER_STEP_PERCENT}% movement speed for every {@value #HEARTS_PER_SPEED_STEP} Weakened Hearts.</li>
 *     <li><b>Sword mode</b>: 5 attack damage at a golden sword's speed (1.6), +{@value #SWORD_REACH} attack reach,
 *     +1 damage for every point (half heart) of Weakened Hearts. No sweeping attack.</li>
 * </ul>
 * Durability 2000, repaired with gold ingots; accepts only Unbreaking, Mending, Fire Aspect and its own enchantments:
 * Vilified Edge (sword hits poison enemies under half health) and Unshaken Gait (no knockback in cane mode).
 */
public class VileCaneItem extends Item {
    public static final String TAG_SWORD_MODE = "SwordMode";
    public static final int HEARTS_PER_SPEED_STEP = 2;
    public static final int SPEED_PER_STEP_PERCENT = 5;
    public static final double SWORD_REACH = 1.5D;
    /** Cooldown after switching, so the key can't be spammed. */
    public static final int SWITCH_COOLDOWN_TICKS = 10;
    private static final double SWORD_DAMAGE_BONUS = 4.0D; // player base 1 + 4 = 5
    private static final double SWORD_SPEED_MODIFIER = -2.4D; // player base 4.0 - 2.4 = 1.6, a golden sword
    private static final UUID SWORD_REACH_UUID = UUID.fromString("5b7c2f1e-3a4d-4e8f-9b0a-6c1d2e3f4a5b");
    /** Gold-like enchantability. */
    private static final int ENCHANTMENT_VALUE = 22;

    private final Multimap<Attribute, AttributeModifier> swordModifiers;
    private static final UUID UNSHAKEN_GAIT_UUID = UUID.fromString("2c9e7a41-5d3b-4f60-8a1e-7b4c9d2e6f30");
    private static final Multimap<Attribute, AttributeModifier> UNSHAKEN_GAIT_MODIFIERS = ImmutableMultimap.of(
            Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(UNSHAKEN_GAIT_UUID, "Unshaken Gait", 1.0D,
                    AttributeModifier.Operation.ADDITION));
    /** Vilified Edge: Poison I for 5 seconds on sword hits against enemies under half health. */
    private static final int VILIFIED_POISON_TICKS = 5 * 20;

    public VileCaneItem(Properties properties) {
        super(properties);
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier",
                SWORD_DAMAGE_BONUS, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier",
                SWORD_SPEED_MODIFIER, AttributeModifier.Operation.ADDITION));
        builder.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(SWORD_REACH_UUID, "Vile Cane reach",
                SWORD_REACH, AttributeModifier.Operation.ADDITION));
        this.swordModifiers = builder.build();
    }

    public static boolean isSwordMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_SWORD_MODE);
    }

    /** Number of speed steps (each {@value #SPEED_PER_STEP_PERCENT}%) for this much Weakened Health. */
    public static int speedSteps(float weakenedHealth) {
        return Mth.floor(weakenedHealth / (2.0F * HEARTS_PER_SPEED_STEP));
    }

    /** Extra sword damage for this much Weakened Health: one per point. */
    public static int swordBonus(float weakenedHealth) {
        return Mth.floor(weakenedHealth);
    }

    /** Flips the mode of the cane in the player's main hand, with sound and particles. Server only. */
    public static void switchMode(ServerPlayer player, ItemStack stack) {
        boolean sword = !isSwordMode(stack);
        stack.getOrCreateTag().putBoolean(TAG_SWORD_MODE, sword);
        player.getCooldowns().addCooldown(stack.getItem(), SWITCH_COOLDOWN_TICKS);
        Level level = player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                sword ? SoundEvents.ARMOR_EQUIP_IRON : SoundEvents.ARMOR_EQUIP_GOLD, SoundSource.PLAYERS, 1.0F, sword ? 1.3F : 0.8F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.7F, sword ? 1.4F : 0.9F);
        if (level instanceof ServerLevel serverLevel) {
            Vec3 look = player.getLookAngle();
            Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize().scale(0.35D);
            Vec3 hand = player.getEyePosition().add(look.scale(0.6D)).add(side).subtract(0.0D, 0.45D, 0.0D);
            serverLevel.sendParticles(new DustParticleOptions(new Vector3f(0.45F, 0.04F, 0.08F), 1.0F),
                    hand.x, hand.y, hand.z, 14, 0.15D, 0.25D, 0.15D, 0.0D);
            serverLevel.sendParticles(sword ? ParticleTypes.CRIT : ParticleTypes.SMOKE,
                    hand.x, hand.y, hand.z, 6, 0.1D, 0.2D, 0.1D, 0.02D);
        }
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND) {
            return ImmutableMultimap.of();
        }
        if (isSwordMode(stack)) {
            return swordModifiers;
        }
        return EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.UNSHAKEN_GAIT.get(), stack) > 0
                ? UNSHAKEN_GAIT_MODIFIERS : ImmutableMultimap.of();
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (isSwordMode(stack) && target.isAlive() && target.getHealth() < target.getMaxHealth() * 0.5F
                && EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.VILIFIED_EDGE.get(), stack) > 0) {
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,
                    VILIFIED_POISON_TICKS, 0), attacker);
        }
        stack.hurtAndBreak(1, attacker, entity -> entity.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return ENCHANTMENT_VALUE;
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return repair.is(Items.GOLD_INGOT);
    }

    /** Table and anvil both ask this (Forge routes {@link Enchantment#canEnchant} here). */
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return enchantment == Enchantments.UNBREAKING || enchantment == Enchantments.MENDING
                || enchantment == Enchantments.FIRE_ASPECT
                || enchantment.category == com.sanguinaire.enchantment.SanguinaireEnchantment.VILE_CANE;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String prefix = "tooltip." + Sanguinaire.MOD_ID + ".vile_cane.";
        Component key = DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> ClientHooks::abilityKeyName);
        tooltip.add(Component.translatable(prefix + "switch", key != null ? key : Component.literal("V"))
                .withStyle(ChatFormatting.YELLOW));
        Float weakened = DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> ClientHooks::localWeakenedHealth);
        float health = weakened != null ? weakened : 0.0F;
        if (isSwordMode(stack)) {
            tooltip.add(Component.translatable(prefix + "mode.sword").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable(prefix + "sword.reach", SWORD_REACH).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(prefix + "sword.bonus", swordBonus(health)).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(prefix + "sword.no_sweep").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable(prefix + "mode.cane").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable(prefix + "cane.step").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(prefix + "cane.speed", SPEED_PER_STEP_PERCENT, HEARTS_PER_SPEED_STEP,
                    speedSteps(health) * SPEED_PER_STEP_PERCENT).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(prefix + "cane.no_damage").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
