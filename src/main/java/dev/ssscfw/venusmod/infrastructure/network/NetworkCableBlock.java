package dev.ssscfw.venusmod.infrastructure.network;

import dev.ssscfw.venusmod.infrastructure.network.core.CableGrade;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.*;

/** 中央と接続腕は4/16幅。BlockEntityもTickerも持たない。 */
public final class NetworkCableBlock extends Block {
    public static final Direction[] DIRECTIONS={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST,Direction.UP,Direction.DOWN};
    public static final BooleanProperty[] CONNECTIONS=Arrays.stream(DIRECTIONS).map(d->BooleanProperty.create(d.getName())).toArray(BooleanProperty[]::new);
    private static final VoxelShape[] SHAPES=new VoxelShape[64];
    static {
        VoxelShape center=Block.box(6,6,6,10,10,10);
        VoxelShape[] arms={Block.box(6,6,0,10,10,6),Block.box(10,6,6,16,10,10),Block.box(6,6,10,10,10,16),Block.box(0,6,6,6,10,10),Block.box(6,10,6,10,16,10),Block.box(6,0,6,10,6,10)};
        for(int mask=0;mask<64;mask++){VoxelShape shape=center;for(int i=0;i<6;i++)if((mask&(1<<i))!=0)shape=Shapes.or(shape,arms[i]);SHAPES[mask]=shape.optimize();}
    }
    public final CableGrade grade;
    public NetworkCableBlock(CableGrade grade,Properties properties){super(properties);this.grade=grade;BlockState state=stateDefinition.any();for(var prop:CONNECTIONS)state=state.setValue(prop,false);registerDefaultState(state);}
    public static int port(Direction direction){for(int i=0;i<6;i++)if(DIRECTIONS[i]==direction)return i;throw new IllegalArgumentException("direction");}
    private boolean connects(BlockState adjacent){return adjacent.getBlock() instanceof NetworkDeviceBlock || adjacent.getBlock() instanceof NetworkCableBlock cable&&cable.grade==grade;}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){BlockState state=defaultBlockState();for(int i=0;i<6;i++){BlockPos p=context.getClickedPos().relative(DIRECTIONS[i]);if(context.getLevel().hasChunkAt(p))state=state.setValue(CONNECTIONS[i],connects(context.getLevel().getBlockState(p)));}return state;}
    @Override protected BlockState updateShape(BlockState state,Direction direction,BlockState adjacent,LevelAccessor level,BlockPos pos,BlockPos neighbor){return state.setValue(CONNECTIONS[port(direction)],connects(adjacent));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(CONNECTIONS);}
    private static int mask(BlockState state){int m=0;for(int i=0;i<6;i++)if(state.getValue(CONNECTIONS[i]))m|=1<<i;return m;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPES[mask(state)];}
    @Override protected VoxelShape getOcclusionShape(BlockState state,BlockGetter level,BlockPos pos){return Shapes.empty();}
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved){super.onPlace(state,level,pos,old,moved);if(!old.is(this))NetworkWorld.changed(level,pos);}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState replacement,boolean moved){if(!replacement.is(this))NetworkWorld.changed(level,pos);super.onRemove(state,level,pos,replacement,moved);}
}
