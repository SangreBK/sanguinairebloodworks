package com.sanguinaire.registry;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.block.ResearchStationBlock;
import com.sanguinaire.block.SanguineAlembicBlock;
import com.sanguinaire.block.SerumSynthesizerBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Sanguinaire.MOD_ID);

    public static final RegistryObject<ResearchStationBlock> RESEARCH_STATION = registerWithItem("research_station",
            () -> new ResearchStationBlock(machineProperties()));

    public static final RegistryObject<SerumSynthesizerBlock> SERUM_SYNTHESIZER = registerWithItem("serum_synthesizer",
            () -> new SerumSynthesizerBlock(machineProperties().lightLevel(state -> 3)));

    public static final RegistryObject<SanguineAlembicBlock> SANGUINE_ALEMBIC = registerWithItem("sanguine_alembic",
            () -> new SanguineAlembicBlock(machineProperties().lightLevel(state -> 2)));

    private ModBlocks() {
    }

    /** Wood-and-iron workbench feel: mined fastest with an axe, never requires a specific tool to drop. */
    private static BlockBehaviour.Properties machineProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.5F)
                .sound(SoundType.WOOD);
    }

    private static <T extends Block> RegistryObject<T> registerWithItem(String name, Supplier<T> block) {
        RegistryObject<T> registered = BLOCKS.register(name, block);
        ModItems.ITEMS.register(name, () -> new BlockItem(registered.get(), new Item.Properties()));
        return registered;
    }
}
