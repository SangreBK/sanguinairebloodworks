package com.sanguinaire.registry;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.blockentity.ResearchStationBlockEntity;
import com.sanguinaire.blockentity.SanguineAlembicBlockEntity;
import com.sanguinaire.blockentity.SerumSynthesizerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Sanguinaire.MOD_ID);

    @SuppressWarnings("DataFlowIssue") // the data fixer type is unused for mod block entities
    public static final RegistryObject<BlockEntityType<ResearchStationBlockEntity>> RESEARCH_STATION =
            BLOCK_ENTITIES.register("research_station", () -> BlockEntityType.Builder
                    .of(ResearchStationBlockEntity::new, ModBlocks.RESEARCH_STATION.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SerumSynthesizerBlockEntity>> SERUM_SYNTHESIZER =
            BLOCK_ENTITIES.register("serum_synthesizer", () -> BlockEntityType.Builder
                    .of(SerumSynthesizerBlockEntity::new, ModBlocks.SERUM_SYNTHESIZER.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SanguineAlembicBlockEntity>> SANGUINE_ALEMBIC =
            BLOCK_ENTITIES.register("sanguine_alembic", () -> BlockEntityType.Builder
                    .of(SanguineAlembicBlockEntity::new, ModBlocks.SANGUINE_ALEMBIC.get()).build(null));

    private ModBlockEntities() {
    }
}
