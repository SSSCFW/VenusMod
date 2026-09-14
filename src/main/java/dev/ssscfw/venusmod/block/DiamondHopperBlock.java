package dev.ssscfw.venusmod.block;

import dev.ssscfw.venusmod.blockentity.DiamondHopperBlockEntity;
import dev.ssscfw.venusmod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hopper-compatible block whose server ticker runs the vanilla hopper transfer
 * cycle at ten times the normal average rate.
 */
public final class DiamondHopperBlock extends HopperBlock {
    public DiamondHopperBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DiamondHopperBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide
                ? null
                : createTickerHelper(
                        type,
                        ModBlockEntities.DIAMOND_HOPPER.get(),
                        DiamondHopperBlockEntity::serverTick);
    }
}
