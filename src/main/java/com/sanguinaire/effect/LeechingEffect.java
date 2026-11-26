package com.sanguinaire.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Leeching (from the Serum of Leeching): all non-projectile damage the bearer deals heals them by the same amount.
 * Handled by {@link SerumEffectEvents}.
 */
public class LeechingEffect extends MobEffect {
    public LeechingEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x8A1020);
    }
}
