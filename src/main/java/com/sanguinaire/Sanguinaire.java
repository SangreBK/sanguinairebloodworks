package com.sanguinaire;

import com.mojang.logging.LogUtils;
import com.sanguinaire.network.ModNetwork;
import com.sanguinaire.player.PlayerMutationData;
import com.sanguinaire.registry.ModBlockEntities;
import com.sanguinaire.registry.ModBlocks;
import com.sanguinaire.registry.ModCreativeTabs;
import com.sanguinaire.registry.ModEffects;
import com.sanguinaire.registry.ModItems;
import com.sanguinaire.registry.ModLootModifiers;
import com.sanguinaire.registry.ModMenuTypes;
import com.sanguinaire.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Entry point for Sanguinaire.
 * <p>
 * Registration is done through {@link net.minecraftforge.registries.DeferredRegister}s that are attached
 * to the mod event bus here. Forge-bus gameplay listeners live in their feature packages and are
 * discovered automatically through {@link Mod.EventBusSubscriber}.
 */
@Mod(Sanguinaire.MOD_ID)
public final class Sanguinaire {
    public static final String MOD_ID = "sanguinaire";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Sanguinaire() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModMenuTypes.MENUS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModLootModifiers.LOOT_MODIFIERS.register(modBus);

        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onRegisterCapabilities);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }

    private void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(PlayerMutationData.class);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
