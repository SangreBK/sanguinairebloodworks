package com.sanguinaire.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Physical Boost (from the Physical Boost Serum): the same melee bonus as Strength II (+6 attack damage at level I)
 * and, while it lasts, Weakened Hearts are suppressed. Its icon and timer show how long the serum has left.
 * The suppression is handled by {@link com.sanguinaire.mutation.TransformationManager#tickPhysicalBoost}.
 */
public class PhysicalBoostEffect extends MobEffect {
    /** Strength gives 3 per level; Strength II = 6. Multiplied by (amplifier + 1). */
    private static final double ATTACK_DAMAGE_BONUS = 6.0D;

    public PhysicalBoostEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xC2452D);
        addAttributeModifier(Attributes.ATTACK_DAMAGE, "6f1c2a8e-3b5d-4c7e-9f10-2a3b4c5d6e7f", ATTACK_DAMAGE_BONUS,
                AttributeModifier.Operation.ADDITION);
    }
}
