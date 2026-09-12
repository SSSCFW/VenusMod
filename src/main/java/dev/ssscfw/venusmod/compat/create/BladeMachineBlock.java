package dev.ssscfw.venusmod.compat.create;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;

/** A Create kinetic machine dedicated to SlashBlade items. */
public final class BladeMachineBlock extends KineticBlock implements IBE<AbstractBladeMachineBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public enum Mode {
        REPAIR,
        BREAK
    }

    private final Mode mode;

    public BladeMachineBlock(Mode mode, Properties properties) {
        super(properties);
        this.mode = mode;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    public Mode getMode() {
        return mode;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        // The visible couplers are on both ends of the machine's local horizontal axis.
        return face.getAxis() == state.getValue(FACING).getAxis();
    }

    @Override
    public Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public IRotate.SpeedLevel getMinimumRequiredSpeedLevel() {
        return IRotate.SpeedLevel.SLOW;
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack held,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult) {

        if (!held.isEmpty() && !SlashBladeEnchantmentCompat.isBlade(held)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        AbstractBladeMachineBlockEntity machine = getBlockEntity(level, pos);
        if (machine == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (held.isEmpty()) {
            return machine.takeOutputOrInput(player)
                    ? ItemInteractionResult.SUCCESS
                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        return machine.insertBladeFromPlayer(held, player)
                ? ItemInteractionResult.SUCCESS
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public Class<AbstractBladeMachineBlockEntity> getBlockEntityClass() {
        return AbstractBladeMachineBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AbstractBladeMachineBlockEntity> getBlockEntityType() {
        return mode == Mode.REPAIR
                ? VenusCreateCompat.BLADE_REPAIR_STATION_BE.get()
                : VenusCreateCompat.BLADE_BREAKER_BE.get();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }
}
