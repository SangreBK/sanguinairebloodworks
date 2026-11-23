package com.sanguinaire.item;

import com.sanguinaire.registry.ModEnchantments;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * The Surgical Saw. Killing a supported mob with it as the final hit yields an extra body part
 * (see {@link com.sanguinaire.event.BodyPartDropEvents}).
 * <p>
 * Stats from the design document:
 * <ul>
 *     <li>Attack Damage 3 (tooltip value): +2 on top of the player's base attack damage of 1.</li>
 *     <li>Attack Speed same as an Iron Axe: 0.9, i.e. -3.1 from the player's base of 4.0.</li>
 *     <li>Durability 100 (set in {@link com.sanguinaire.registry.ModItems}).</li>
 *     <li>Accepts only Sharpness, Mending and Unbreaking.</li>
 * </ul>
 */
public class SurgicalSawItem extends Item {
    private static final double ATTACK_DAMAGE_BONUS = 2.0D;
    private static final double ATTACK_SPEED_MODIFIER = -3.1D;
    /** Same enchantability as iron tools. */
    private static final int ENCHANTMENT_VALUE = 14;

    private final Multimap<Attribute, AttributeModifier> mainHandModifiers;

    public SurgicalSawItem(Properties properties) {
        super(properties);
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier",
                ATTACK_DAMAGE_BONUS, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier",
                ATTACK_SPEED_MODIFIER, AttributeModifier.Operation.ADDITION));
        this.mainHandModifiers = builder.build();
    }

    public static boolean isAllowedEnchantment(Enchantment enchantment) {
        return enchantment == Enchantments.SHARPNESS
                || enchantment == Enchantments.UNBREAKING
                || enchantment == Enchantments.MENDING
                || enchantment == ModEnchantments.VIVISECTION.get();
    }

    @Override
    @SuppressWarnings("deprecation")
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? mainHandModifiers : super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, entity -> entity.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return ENCHANTMENT_VALUE;
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return repair.is(Items.IRON_INGOT);
    }

    /**
     * Forge routes both the enchanting table and anvil compatibility checks
     * ({@link Enchantment#canEnchant(ItemStack)}) through this method, so it fully restricts the saw.
     */
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return isAllowedEnchantment(enchantment);
    }
}
