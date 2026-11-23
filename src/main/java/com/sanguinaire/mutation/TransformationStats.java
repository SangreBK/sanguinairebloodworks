package com.sanguinaire.mutation;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

/**
 * The statistics that replace the player's own while transformed. All values are final attribute values
 * (e.g. {@code attackDamage = 40} means the player hits for 40 with an empty hand).
 * <p>
 * Note: vanilla caps some attributes (armor at 30, armor toughness at 20), so a design value above a cap
 * (Goatman toughness 35) is applied at the cap.
 */
public record TransformationStats(double maxHealth, double armor, double armorToughness, double knockbackResistance,
                                  double attackDamage, double attackSpeed) {
    /** "Slow, similar to an axe": an iron axe's 0.9 attacks per second. */
    public static final double AXE_ATTACK_SPEED = 0.9D;
    /** "Fast, similar to a sword": 1.6 attacks per second. */
    public static final double SWORD_ATTACK_SPEED = 1.6D;
    /**
     * Mobs have no attack-speed attribute; vanilla melee mobs attack once every 20 ticks, i.e. 1 attack per second.
     */
    public static final double MOB_ATTACK_SPEED = 1.0D;
    /** A player's empty-hand damage, used for mobs without a melee attack-damage attribute (e.g. Ghast). */
    private static final double FIST_DAMAGE = 1.0D;

    public static TransformationStats of(double maxHealth, double armor, double armorToughness, double knockbackResistance,
                                         double attackDamage, double attackSpeed) {
        return new TransformationStats(maxHealth, armor, armorToughness, knockbackResistance, attackDamage, attackSpeed);
    }

    /**
     * Reads the vanilla mob's real default attributes for this game version ("Same as a Zombie", etc.).
     *
     * @param canAttack false for transformations that cannot attack (attack damage becomes 0)
     */
    public static TransformationStats ofMob(EntityType<? extends LivingEntity> type, boolean canAttack) {
        AttributeSupplier supplier = DefaultAttributes.getSupplier(type);
        double damage = canAttack ? value(supplier, Attributes.ATTACK_DAMAGE, FIST_DAMAGE) : 0.0D;
        return new TransformationStats(
                value(supplier, Attributes.MAX_HEALTH, 20.0D),
                value(supplier, Attributes.ARMOR, 0.0D),
                value(supplier, Attributes.ARMOR_TOUGHNESS, 0.0D),
                value(supplier, Attributes.KNOCKBACK_RESISTANCE, 0.0D),
                damage,
                value(supplier, Attributes.ATTACK_SPEED, MOB_ATTACK_SPEED));
    }

    private static double value(AttributeSupplier supplier, Attribute attribute, double fallback) {
        return supplier.hasAttribute(attribute) ? supplier.getValue(attribute) : fallback;
    }
}
