package com.sanguinaire.block;

import com.sanguinaire.blockentity.SanguineAlembicBlockEntity;
import com.sanguinaire.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Sanguine Alembic: binds a potion's effect to a Blood Sample. */
public class SanguineAlembicBlock extends MachineBlock {
    public SanguineAlembicBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SanguineAlembicBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.SANGUINE_ALEMBIC.get(), SanguineAlembicBlockEntity::serverTick);
    }
}
