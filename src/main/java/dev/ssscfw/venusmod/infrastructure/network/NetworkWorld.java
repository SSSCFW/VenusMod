package dev.ssscfw.venusmod.infrastructure.network;

import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** ロード済み機器と配線の差分を扱う。配線探索はtick当たりの予算で分割する。 */
public final class NetworkWorld {
    private static final Map<ServerLevel,NetworkWorld> WORLDS=new IdentityHashMap<>();
    private final ServerLevel level;
    private final Map<BlockPos,NetworkDeviceEntity> devices=new LinkedHashMap<>();
    private final NetworkSimulator simulator=new NetworkSimulator();
    private final Map<BlockPos,String> registeredIds=new HashMap<>();
    private final CableTopology<BlockPos> topology;
    private final NetworkRequestBudget requestBudget=new NetworkRequestBudget(4,2);
    public boolean admitRequest(UUID player){return requestBudget.tryAcquire(level.getGameTime(),player);}
    private final PriorityQueue<FailureEvent> failures=new PriorityQueue<>(Comparator.comparingLong(FailureEvent::due));
    private record FailureEvent(long due,long generation,NetworkDeviceEntity device){}
    public static NetworkWorld get(ServerLevel level){return WORLDS.computeIfAbsent(level,NetworkWorld::new);}
    private NetworkWorld(ServerLevel level){
        this.level=level;
        topology=new CableTopology<>(new CableTopology.View<BlockPos>(){
            public BlockPos adjacent(BlockPos pos,int port){return pos.relative(NetworkCableBlock.DIRECTIONS[port]).immutable();}
            public long chunk(BlockPos pos){return new ChunkPos(pos).toLong();}
            public boolean loaded(BlockPos pos){return level.hasChunkAt(pos);}
            public CableGrade cable(BlockPos pos){return level.getBlockState(pos).getBlock() instanceof NetworkCableBlock block?block.grade:null;}
            public int color(BlockPos pos){return level.getBlockState(pos).getBlock() instanceof NetworkCableBlock block&&block.color!=null?block.color.getId():-1;}
            public CableTopology.Node device(BlockPos pos){var entity=devices.get(pos);
                return entity!=null&&!entity.isRemoved()&&entity.id().equals(registeredIds.get(pos))
                    ?new CableTopology.Node(entity.id(),entity.profile().kind):level.getBlockState(pos).getBlock() instanceof NetworkDeviceBlock block?new CableTopology.Node("",block.kind):null;}
        },new CableTopology.Listener(){
            public void connect(NetworkSimulator.Link link){simulator.link(link);}
            public void disconnect(String id){simulator.unlink(id);}
        });
    }
    public NetworkSimulator simulator(){return simulator;}
    public boolean ready(){return topology.idle();}
    public String diagnostic(){return topology.diagnostic();}
    public long topologySteps(){return topology.totalSteps();}
    public int topologyLastSteps(){return topology.lastSteps();}
    public static void changed(Level level,BlockPos pos){if(level instanceof ServerLevel server){NetworkWorld world=WORLDS.get(server);if(world!=null)world.topology.changed(pos.immutable());}}
    /** 上限後の復旧用。呼出元のメニューで管理者権限を検査する。 */
    public void rebuildTopology(){topology.rebuild(devices.keySet());}
    public void add(NetworkDeviceEntity entity){
        if(devices.size()>=NetworkSimulator.MAX_NODES&&!devices.containsKey(entity.getBlockPos()))return;
        for(NetworkDeviceEntity other:devices.values())if(other!=entity&&other.id().equals(entity.id())){entity.renewId();break;}
        NetworkDeviceEntity previous=devices.put(entity.getBlockPos().immutable(),entity);
        if(previous!=entity||entity.nextFailureTime==0){entity.scheduleGeneration++;entity.nextFailureTime=level.getGameTime()+Math.clamp(entity.failureRemaining,1,1200);failures.add(new FailureEvent(entity.nextFailureTime,entity.scheduleGeneration,entity));}
        String oldId=registeredIds.put(entity.getBlockPos().immutable(),entity.id());
        if(oldId!=null&&!oldId.equals(entity.id()))simulator.remove(oldId);
        if(simulator.nodes().get(entity.id())!=entity.profile())simulator.put(entity.id(),entity.profile());
        topology.changed(entity.getBlockPos().immutable());
    }
    public void remove(NetworkDeviceEntity entity){
        if(devices.get(entity.getBlockPos())!=entity)return;
        entity.failureRemaining=Math.clamp(entity.nextFailureTime-level.getGameTime(),1,1200);entity.nextFailureTime=0;entity.scheduleGeneration++;
        topology.changed(entity.getBlockPos().immutable());
        devices.remove(entity.getBlockPos());String oldId=registeredIds.remove(entity.getBlockPos());if(oldId!=null)simulator.remove(oldId);
    }
    public void configurationChanged(NetworkDeviceEntity entity){if(devices.get(entity.getBlockPos())!=entity)return;simulator.put(entity.id(),entity.profile());}
    public NetworkDeviceEntity device(String id){for(var device:devices.values())if(device.id().equals(id))return device;return null;}
    public static void tick(LevelTickEvent.Post event){if(event.getLevel() instanceof ServerLevel level){NetworkWorld world=WORLDS.get(level);if(world!=null)world.tick();}}
    private void tick(){
        topology.step(NetworkContent.TOPOLOGY_BUDGET.get());
        // 解析中でも確認済みの別区間と全制御タイマーを進める。未確定リンクは未公開。
        simulator.advance(1);
        long now=level.getGameTime();int work=0;
        while(!failures.isEmpty()&&failures.peek().due<=now&&work++<64){var event=failures.remove();var device=event.device;
            if(device.isRemoved()||devices.get(device.getBlockPos())!=device||device.scheduleGeneration!=event.generation)continue;
            device.failureCheck(level);device.nextFailureTime=now+1200;failures.add(new FailureEvent(device.nextFailureTime,device.scheduleGeneration,device));
        }
        if(failures.size()>Math.max(64,devices.size()*4)){failures.clear();for(var device:devices.values())failures.add(new FailureEvent(device.nextFailureTime,device.scheduleGeneration,device));}
    }
    public static void stopped(ServerStoppedEvent event){WORLDS.clear();}
    public static void breaking(BlockEvent.BreakEvent event){if(event.getLevel() instanceof ServerLevel level&&level.getBlockEntity(event.getPos()) instanceof NetworkDeviceEntity device&&!device.canUse(event.getPlayer()))event.setCanceled(true);}
    public static void chunkLoaded(ChunkEvent.Load event){if(event.getLevel() instanceof ServerLevel level)chunkChanged(level,event.getChunk().getPos().toLong(),true);}
    public static void chunkUnloaded(ChunkEvent.Unload event){if(event.getLevel() instanceof ServerLevel level)chunkChanged(level,event.getChunk().getPos().toLong(),false);}
    private static void chunkChanged(ServerLevel level,long chunk,boolean loaded){
        Runnable action=()->{var world=WORLDS.get(level);if(world!=null)world.topology.chunkChanged(chunk,loaded);};
        if(level.getServer().isSameThread())action.run();else level.getServer().execute(action);
    }
}
