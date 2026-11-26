package com.sanguinaire.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Delayed Accretion (from the Serum of Delayed Accretion): the bearer cannot be hurt, but every blow is counted and
 * the whole sum lands at once when the effect ends. Handled by {@link SerumEffectEvents}.
 */
public class DelayedAccretionEffect extends MobEffect {
    public DelayedAccretionEffect() {
        super(MobEffectCategory.NEUTRAL, 0x5B4A8A);
    }
}
