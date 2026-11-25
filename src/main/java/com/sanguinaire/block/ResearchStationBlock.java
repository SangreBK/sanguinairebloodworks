package com.sanguinaire.block;

import com.sanguinaire.blockentity.ResearchStationBlockEntity;
import com.sanguinaire.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

public class ResearchStationBlock extends MachineBlock {
    /** Whether a Research Journal sits in the journal slot; the model only shows the journal when it does. */
    public static final BooleanProperty HAS_JOURNAL = BooleanProperty.create("has_journal");

    public ResearchStationBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(HAS_JOURNAL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HAS_JOURNAL);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResearchStationBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
                ? createTickerHelper(type, ModBlockEntities.RESEARCH_STATION.get(), ResearchStationBlockEntity::clientTick)
                : createTickerHelper(type, ModBlockEntities.RESEARCH_STATION.get(), ResearchStationBlockEntity::serverTick);
    }
}
