package dev.ssscfw.venusmod.compat.create;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;

public final class AtmosphericCondenserBlock extends VenusMachineBlock implements IBE<AtmosphericCondenserBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public AtmosphericCondenserBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { super.createBlockStateDefinition(builder); builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(FACING).getAxis(); }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) { return face.getAxis() == getRotationAxis(state); }
    @Override public Class<AtmosphericCondenserBlockEntity> getBlockEntityClass() { return AtmosphericCondenserBlockEntity.class; }
    @Override public BlockEntityType<? extends AtmosphericCondenserBlockEntity> getBlockEntityType() { return VenusAtmosphereMachines.CONDENSER_ENTITY.get(); }
    @Override protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (FluidUtil.getFluidHandler(held).isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        var machine = getBlockEntity(level, pos);
        if (machine != null && FluidUtil.interactWithFluidHandler(player, hand, machine.output())) return ItemInteractionResult.SUCCESS;
        if (machine != null) player.displayClientMessage(machine.status(), true);
        // 凝縮液の入ったバケツを誤って機械内部に設置することを防止する。
        return ItemInteractionResult.CONSUME;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            var machine = getBlockEntity(level, pos);
            if (machine != null) player.displayClientMessage(machine.status(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
