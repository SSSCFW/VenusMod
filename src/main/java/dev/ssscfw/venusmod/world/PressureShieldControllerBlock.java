package dev.ssscfw.venusmod.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Createの隣接回転速度を読み、最終ボスの圧力シールドを解除するソケット。 */
public final class PressureShieldControllerBlock extends BaseEntityBlock {
    public static final MapCodec<PressureShieldControllerBlock> CODEC = simpleCodec(PressureShieldControllerBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public PressureShieldControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override public MapCodec<PressureShieldControllerBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PressureShieldControllerBlockEntity(pos, state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, VenusDimensionContent.SHIELD_CONTROLLER_ENTITY.get(), PressureShieldControllerBlockEntity::tick);
    }
}
