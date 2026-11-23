package com.sanguinaire.enchantment;

import com.sanguinaire.item.SurgicalSawItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Vivisection (Surgical Saw only): +{@link #BONUS_DAMAGE} damage against creatures whose every specimen the wielder
 * has already researched (see {@code VivisectionEvents}). Knowing exactly where to cut.
 */
public class VivisectionEnchantment extends Enchantment {
    public static final float BONUS_DAMAGE = 9.0F;
    public static final EnchantmentCategory SURGICAL_SAW = EnchantmentCategory.create("SANGUINAIRE_SURGICAL_SAW",
            item -> item instanceof SurgicalSawItem);

    public VivisectionEnchantment() {
        super(Rarity.RARE, SURGICAL_SAW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinCost(int level) {
        return 15;
    }

    @Override
    public int getMaxCost(int level) {
        return 50;
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof SurgicalSawItem;
    }
}
