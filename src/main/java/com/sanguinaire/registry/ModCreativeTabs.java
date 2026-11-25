package com.sanguinaire.registry;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.item.SerumItem;
import com.sanguinaire.item.TornPageItem;
import com.sanguinaire.mutation.MutationType;
import com.sanguinaire.mutation.UtilitySerum;
import com.sanguinaire.research.ResearchNote;
import com.sanguinaire.blood.BloodType;
import com.sanguinaire.item.BloodSampleItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Sanguinaire.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + Sanguinaire.MOD_ID))
            .icon(() -> new ItemStack(ModItems.SURGICAL_SAW.get()))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.SURGICAL_SAW.get());
                output.accept(ModItems.VILE_CANE.get());
                output.accept(ModItems.SURGICAL_JOURNAL.get());
                output.accept(ModBlocks.RESEARCH_STATION.get());
                output.accept(ModBlocks.SERUM_SYNTHESIZER.get());
                output.accept(ModBlocks.SANGUINE_ALEMBIC.get());
                output.accept(ModItems.SERUM_BRIEFCASE.get());
                output.accept(ModItems.EMPTY_SYRINGE.get());
                output.accept(ModItems.NULLIFICATION_POTION.get());
                output.accept(ModItems.CONSCRIPT_SPAWN_EGG.get());
                output.accept(ModItems.ZOMBIFIED_CONSCRIPT_SPAWN_EGG.get());
                for (BloodType blood : BloodType.values()) {
                    output.accept(BloodSampleItem.create(blood, null));
                }
                for (UtilitySerum serum : UtilitySerum.values()) {
                    output.accept(SerumItem.create(serum));
                }
                for (ResearchNote note : ResearchNote.values()) {
                    output.accept(TornPageItem.create(note));
                }
                for (MutationType type : MutationType.values()) {
                    output.accept(SerumItem.create(type));
                }
                for (RegistryObject<Item> part : ModItems.BODY_PARTS) {
                    output.accept(part.get());
                }
            })
            .build());

    private ModCreativeTabs() {
    }
}
