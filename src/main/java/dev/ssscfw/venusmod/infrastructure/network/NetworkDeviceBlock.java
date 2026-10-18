package dev.ssscfw.venusmod.infrastructure.network;

import com.mojang.serialization.MapCodec;
import dev.ssscfw.venusmod.infrastructure.network.core.DeviceKind;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** 全面に自前のテクスチャを持つ非遮蔽筐体。インベントリを別ドロップしない。 */
public final class NetworkDeviceBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty FAULTED=BooleanProperty.create("faulted");
    private static final VoxelShape SHAPE=Shapes.or(Block.box(1,0,1,15,13,15),Block.box(6,6,0,10,10,16),Block.box(0,6,6,16,10,10),Block.box(6,0,6,10,16,10));
    public final DeviceKind kind;
    public NetworkDeviceBlock(DeviceKind kind,Properties properties){super(properties);this.kind=kind;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(FAULTED,false));}
    @Override public MapCodec<NetworkDeviceBlock> codec(){return simpleCodec(p->new NetworkDeviceBlock(kind,p));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,FAULTED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
    @Override protected VoxelShape getOcclusionShape(BlockState state,BlockGetter level,BlockPos pos){return Shapes.empty();}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NetworkDeviceEntity(pos,state);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        String held=NetworkContent.itemId(player.getMainHandItem());if(held.equals("network_repair_kit")||held.equals("network_configurator"))return InteractionResult.PASS;
        if(level.isClientSide)return InteractionResult.SUCCESS;
        if(player instanceof ServerPlayer server&&level.getBlockEntity(pos) instanceof NetworkDeviceEntity device){if(!device.canUse(server)){server.displayClientMessage(Component.literal("この機器の管理権限がありません"),true);return InteractionResult.FAIL;}server.openMenu(device,buf->buf.writeBlockPos(pos));}
        return InteractionResult.CONSUME;
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);if(!level.isClientSide&&level.getBlockEntity(pos) instanceof NetworkDeviceEntity device){device.restoreItem(stack);if(placer instanceof Player player)device.setOwnerIfAbsent(player);}
    }
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params){
        List<ItemStack> drops=super.getDrops(state,params);BlockEntity be=params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if(be instanceof NetworkDeviceEntity device)for(int i=0;i<drops.size();i++)if(drops.get(i).is(asItem()))drops.set(i,device.savedItem());return drops;
    }
    @Override public ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state){return level.getBlockEntity(pos) instanceof NetworkDeviceEntity device?device.savedItem():new ItemStack(this);}
    @Override protected boolean isSignalSource(BlockState state){return kind==DeviceKind.REDSTONE_TERMINAL;}
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction direction){return kind==DeviceKind.REDSTONE_TERMINAL&&level.getBlockEntity(pos) instanceof NetworkDeviceEntity device?device.redstone():0;}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved){if(!next.is(this))NetworkWorld.changed(level,pos);super.onRemove(state,level,pos,next,moved);}
}
