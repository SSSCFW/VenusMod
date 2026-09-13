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

/** 金星将軍/アフロディーテ・コア専用の一度限り召喚祭壇。 */
public final class BossAltarBlock extends BaseEntityBlock {
    public enum Kind { GENERAL, APHRODITE }

    public static final MapCodec<BossAltarBlock> CODEC = simpleCodec(properties -> new BossAltarBlock(Kind.GENERAL, properties));
    private final Kind kind;

    public BossAltarBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() { return kind; }

    @Override public MapCodec<BossAltarBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new BossAltarBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, VenusDimensionContent.BOSS_ALTAR_ENTITY.get(), BossAltarBlockEntity::tick);
    }
}
