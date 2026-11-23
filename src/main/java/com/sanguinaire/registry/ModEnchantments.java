package com.sanguinaire.registry;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.enchantment.VivisectionEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, Sanguinaire.MOD_ID);

    public static final RegistryObject<Enchantment> VIVISECTION = ENCHANTMENTS.register("vivisection", VivisectionEnchantment::new);

    private ModEnchantments() {
    }
}
