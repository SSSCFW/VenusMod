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

/**
 * 金星刀鍛錬機。
 * スニーク右クリックで項目を選び、通常右クリックで手に持った刀を1レベルだけ強化する。
 */
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

    @Override protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
                                                        Player player, InteractionHand hand, BlockHitResult hit) {
        if (!SlashBladeEnchantmentCompat.isBlade(held)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) cycleSelection(level, pos, state, player, held);
            return ItemInteractionResult.SUCCESS;
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        VenusBladeForgeBlockEntity forge = getBlockEntity(level, pos);
        if (forge == null || !(player instanceof ServerPlayer serverPlayer)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return forge.tryUpgrade(serverPlayer, held, state.getValue(UPGRADE))
                ? ItemInteractionResult.SUCCESS : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                cycleSelection(level, pos, state, player, player.getMainHandItem());
            } else {
                VenusBladeForgeBlockEntity forge = getBlockEntity(level, pos);
                if (forge != null) forge.showStatus(serverPlayer, player.getMainHandItem(), state.getValue(UPGRADE));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void cycleSelection(Level level, BlockPos pos, BlockState state, Player player, ItemStack blade) {
        BladeUpgradeType next = state.getValue(UPGRADE).next();
        level.setBlock(pos, state.setValue(UPGRADE, next), 3);
        if (player instanceof ServerPlayer serverPlayer) {
            VenusBladeForgeBlockEntity forge = getBlockEntity(level, pos);
            if (forge != null) forge.showStatus(serverPlayer, blade, next);
        }
    }

    @Override public Class<VenusBladeForgeBlockEntity> getBlockEntityClass() { return VenusBladeForgeBlockEntity.class; }
    @Override public BlockEntityType<? extends VenusBladeForgeBlockEntity> getBlockEntityType() { return VenusCreateCompat.VENUS_BLADE_FORGE_BE.get(); }
}
