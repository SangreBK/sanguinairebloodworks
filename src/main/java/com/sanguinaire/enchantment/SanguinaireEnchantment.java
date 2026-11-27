package com.sanguinaire.enchantment;

import com.sanguinaire.item.SerumBriefcaseItem;
import com.sanguinaire.item.VileCaneItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

import java.util.function.Supplier;

/**
 * The single-level enchantments for the Serum Briefcase and the Vile Cane (see {@code ModEnchantments}). Each has a
 * {@code enchantment.sanguinaire.<id>.desc} lang key, which Enchantment Descriptions (and similar mods) show.
 * <ul>
 *     <li>Serum Briefcase: <b>Tough Case</b> (+10 armor in the offhand), <b>Serum Gamble</b> (a hard hit makes the
 *     case inject a random serum, 50% free), <b>Brief Management</b> (same serums stack to 2 in the case; incompatible
 *     with Serum Gamble).</li>
 *     <li>Vile Cane: <b>Vilified Edge</b> (sword hits poison enemies under half health), <b>Unshaken Gait</b> (no
 *     knockback in cane mode).</li>
 * </ul>
 * Effects live in the items ({@link SerumBriefcaseItem}, {@link VileCaneItem}) and {@link EquipmentEnchantmentEvents}.
 */
public class SanguinaireEnchantment extends Enchantment {
    public static final EnchantmentCategory SERUM_BRIEFCASE = EnchantmentCategory.create("SANGUINAIRE_SERUM_BRIEFCASE",
            item -> item instanceof SerumBriefcaseItem);
    public static final EnchantmentCategory VILE_CANE = EnchantmentCategory.create("SANGUINAIRE_VILE_CANE",
            item -> item instanceof VileCaneItem);

    private final int minCost;
    /** An enchantment this one can't be combined with, or null. */
    private final Supplier<Enchantment> incompatible;

    public SanguinaireEnchantment(Rarity rarity, EnchantmentCategory category, EquipmentSlot[] slots, int minCost,
                                  Supplier<Enchantment> incompatible) {
        super(rarity, category, slots);
        this.minCost = minCost;
        this.incompatible = incompatible;
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinCost(int level) {
        return minCost;
    }

    @Override
    public int getMaxCost(int level) {
        return minCost + 35;
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return category.canEnchant(stack.getItem());
    }

    @Override
    protected boolean checkCompatibility(Enchantment other) {
        return super.checkCompatibility(other) && (incompatible == null || other != incompatible.get());
    }
}
