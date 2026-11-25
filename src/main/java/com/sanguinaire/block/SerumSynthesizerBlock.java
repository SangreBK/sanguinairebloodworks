package com.sanguinaire.block;

import com.sanguinaire.blockentity.SerumSynthesizerBlockEntity;
import com.sanguinaire.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.Nullable;

public class SerumSynthesizerBlock extends MachineBlock {

    public SerumSynthesizerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(HAS_RESULT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HAS_RESULT); // shows the syringe once there's a finished product
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SerumSynthesizerBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.SERUM_SYNTHESIZER.get(), SerumSynthesizerBlockEntity::serverTick);
    }
}
