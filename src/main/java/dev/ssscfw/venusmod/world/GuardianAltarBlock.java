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

public final class GuardianAltarBlock extends BaseEntityBlock {
    public static final MapCodec<GuardianAltarBlock> CODEC = simpleCodec(GuardianAltarBlock::new);
    public GuardianAltarBlock(Properties properties) { super(properties); }
    @Override public MapCodec<GuardianAltarBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new GuardianAltarBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, VenusDimensionContent.ALTAR_ENTITY.get(), GuardianAltarBlockEntity::tick);
    }
}
