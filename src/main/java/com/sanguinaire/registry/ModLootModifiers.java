package com.sanguinaire.registry;

import com.mojang.serialization.Codec;
import com.sanguinaire.Sanguinaire;
import com.sanguinaire.loot.AddTornPageModifier;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModLootModifiers {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Sanguinaire.MOD_ID);

    public static final RegistryObject<Codec<AddTornPageModifier>> ADD_TORN_PAGE =
            LOOT_MODIFIERS.register("add_torn_page", AddTornPageModifier.CODEC);

    private ModLootModifiers() {
    }
}
