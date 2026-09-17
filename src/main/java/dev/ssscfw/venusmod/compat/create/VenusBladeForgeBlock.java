package dev.ssscfw.venusmod.compat.create;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.foundation.block.IBE;
import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import dev.ssscfw.venusmod.upgrade.BladeUpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/** 金星刀鍛錬機。右クリックでGUIを開き、刀スロット・強化項目・実行操作をGUI内で完結させる。 */
public final class VenusBladeForgeBlock extends VenusMachineBlock implements IBE<VenusBladeForgeBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<BladeUpgradeType> UPGRADE = EnumProperty.create("upgrade", BladeUpgradeType.class);

    public VenusBladeForgeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(UPGRADE, BladeUpgradeType.BLADE_POWER));
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, UPGRADE);
    }

    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(FACING).getAxis(); }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == getRotationAxis(state);
    }
    @Override public IRotate.SpeedLevel getMinimumRequiredSpeedLevel() { return IRotate.SpeedLevel.SLOW; }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        // 抜刀剣を持っている場合は、その刀をGUIのスロットへ入れられるようGUIを開く。
        // それ以外のアイテムはCreateのレンチ等の通常操作を阻害しない。
        if (!SlashBladeEnchantmentCompat.isBlade(held)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            openMenu(level, pos, serverPlayer);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            openMenu(level, pos, serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void openMenu(Level level, BlockPos pos, ServerPlayer player) {
        VenusBladeForgeBlockEntity forge = getBlockEntity(level, pos);
        if (forge != null) player.openMenu(forge);
    }

    @Override public Class<VenusBladeForgeBlockEntity> getBlockEntityClass() { return VenusBladeForgeBlockEntity.class; }
    @Override public BlockEntityType<? extends VenusBladeForgeBlockEntity> getBlockEntityType() {
        return VenusCreateCompat.VENUS_BLADE_FORGE_BE.get();
    }
}
