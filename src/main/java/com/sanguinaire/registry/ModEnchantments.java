package com.sanguinaire.registry;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.enchantment.SanguinaireEnchantment;
import com.sanguinaire.enchantment.VivisectionEnchantment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, Sanguinaire.MOD_ID);

    public static final RegistryObject<Enchantment> VIVISECTION = ENCHANTMENTS.register("vivisection", VivisectionEnchantment::new);

    private static final EquipmentSlot[] HANDS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private static final EquipmentSlot[] MAIN_HAND = {EquipmentSlot.MAINHAND};

    // Serum Briefcase
    /** +10 armor while the briefcase is in the offhand. */
    public static final RegistryObject<Enchantment> TOUGH_CASE = ENCHANTMENTS.register("tough_case",
            () -> new SanguinaireEnchantment(Enchantment.Rarity.UNCOMMON, SanguinaireEnchantment.SERUM_BRIEFCASE, HANDS, 10, null));
    /** A hit of 8+ damage makes the case inject a random serum (50% free). */
    public static final RegistryObject<Enchantment> SERUM_GAMBLE = ENCHANTMENTS.register("serum_gamble",
            () -> new SanguinaireEnchantment(Enchantment.Rarity.RARE, SanguinaireEnchantment.SERUM_BRIEFCASE, HANDS, 15,
                    () -> ModEnchantments.BRIEF_MANAGEMENT.get()));
    /** Serums of the same kind stack to 2 inside the case. */
    public static final RegistryObject<Enchantment> BRIEF_MANAGEMENT = ENCHANTMENTS.register("brief_management",
            () -> new SanguinaireEnchantment(Enchantment.Rarity.UNCOMMON, SanguinaireEnchantment.SERUM_BRIEFCASE, HANDS, 10,
                    () -> ModEnchantments.SERUM_GAMBLE.get()));

    // Vile Cane
    /** Sword-mode hits poison enemies under half health. */
    public static final RegistryObject<Enchantment> VILIFIED_EDGE = ENCHANTMENTS.register("vilified_edge",
            () -> new SanguinaireEnchantment(Enchantment.Rarity.RARE, SanguinaireEnchantment.VILE_CANE, MAIN_HAND, 15, null));
    /** No knockback while holding the cane in cane mode. */
    public static final RegistryObject<Enchantment> UNSHAKEN_GAIT = ENCHANTMENTS.register("unshaken_gait",
            () -> new SanguinaireEnchantment(Enchantment.Rarity.UNCOMMON, SanguinaireEnchantment.VILE_CANE, MAIN_HAND, 10, null));

    private ModEnchantments() {
    }
}
