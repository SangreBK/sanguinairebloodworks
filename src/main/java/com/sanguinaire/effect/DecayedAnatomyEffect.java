package com.sanguinaire.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Decayed Anatomy: the body of someone who has gone too far. Applied permanently (infinite duration, no particles)
 * while half (level I) or all (level II) of the player's health is Weakened Hearts, and lifted while transformed.
 * All of its consequences live in {@link com.sanguinaire.player.DecayedAnatomy}; the effect itself is the
 * on-screen marker and the source of truth for the level.
 */
public class DecayedAnatomyEffect extends MobEffect {
    public DecayedAnatomyEffect() {
        super(MobEffectCategory.HARMFUL, 0x5E6B2C);
    }

    /** Milk can't cure rot. (Forge hook; if it is ever missing, the effect is simply re-applied within a second.) */
    public List<ItemStack> getCurativeItems() {
        return new ArrayList<>();
    }
}
