package dev.ssscfw.venusmod.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

public final class VenusPortalBlock extends Block implements Portal {
    public static final MapCodec<VenusPortalBlock> CODEC = simpleCodec(VenusPortalBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    private static final VoxelShape X_SHAPE = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape Z_SHAPE = Block.box(6, 0, 0, 10, 16, 16);
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.76F, 0.12F), 1.0F);

    public VenusPortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override public MapCodec<VenusPortalBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(AXIS); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? X_SHAPE : Z_SHAPE;
    }
    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                               LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        VenusPortalFrame frame = VenusPortalFrame.find(level, pos, state.getValue(AXIS));
        return frame != null && frame.complete(level) ? state : Blocks.AIR.defaultBlockState();
    }
    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (VenusDimensionContent.allowedSource(level) && entity.canUsePortal(false)) entity.setAsInsidePortal(this, pos);
    }
    @Override public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return entity instanceof Player player ? (player.getAbilities().instabuild ? 1 : 80) : 0;
    }
    @Override public DimensionTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        return VenusPortalTravel.destination(level, entity, pos);
    }
    @Override public Transition getLocalTransition() {
        // ネザーポータルと同じ、滞在時間に応じて強くなる画面のゆがみ/揺れを使用する。
        return Transition.CONFUSION;
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(100) == 0) level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.35F, 0.9F + random.nextFloat() * 0.2F, false);
        for (int i = 0; i < 2; i++) level.addParticle(GOLD, pos.getX() + random.nextDouble(),
                pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0, 0.01, 0);
    }
}
