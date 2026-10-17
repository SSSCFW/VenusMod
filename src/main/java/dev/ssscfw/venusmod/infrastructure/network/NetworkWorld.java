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
    private static final int MAX_CABLE_BLOCKS=131072;
    private final ServerLevel level;
    private final Map<BlockPos,NetworkDeviceEntity> devices=new LinkedHashMap<>();
    private NetworkSimulator simulator=new NetworkSimulator();
    private boolean dirty=true;
    private Scan scan;
    private String diagnostic="配線情報を準備しています";
    private Set<Long> cableChunks=Set.of(),waitingChunks=Set.of();
    private final PriorityQueue<FailureEvent> failures=new PriorityQueue<>(Comparator.comparingLong(FailureEvent::due));
    private record FailureEvent(long due,long generation,NetworkDeviceEntity device){}
    private record Endpoint(String id,int port){}
    public static NetworkWorld get(ServerLevel level){return WORLDS.computeIfAbsent(level,NetworkWorld::new);}
    private NetworkWorld(ServerLevel level){this.level=level;}
    public NetworkSimulator simulator(){return simulator;}
    public boolean ready(){return !dirty&&scan==null;}
    public String diagnostic(){return diagnostic;}
    public static void changed(Level level){if(level instanceof ServerLevel server){NetworkWorld world=WORLDS.get(server);if(world!=null)world.markDirty();}}
    private void markDirty(){dirty=true;scan=null;simulator.invalidate();}
    public void add(NetworkDeviceEntity entity){
        if(devices.size()>=NetworkSimulator.MAX_NODES&&!devices.containsKey(entity.getBlockPos()))return;
        for(NetworkDeviceEntity other:devices.values())if(other!=entity&&other.id().equals(entity.id())){entity.renewId();break;}
        NetworkDeviceEntity previous=devices.put(entity.getBlockPos().immutable(),entity);
        if(previous!=entity||entity.nextFailureTime==0){entity.scheduleGeneration++;entity.nextFailureTime=level.getGameTime()+Math.clamp(entity.failureRemaining,1,1200);failures.add(new FailureEvent(entity.nextFailureTime,entity.scheduleGeneration,entity));}
        markDirty();
    }
    public void remove(NetworkDeviceEntity entity){
        if(devices.get(entity.getBlockPos())!=entity)return;
        entity.failureRemaining=Math.clamp(entity.nextFailureTime-level.getGameTime(),1,1200);entity.nextFailureTime=0;entity.scheduleGeneration++;
        devices.remove(entity.getBlockPos());simulator.remove(entity.id());markDirty();
    }
    public void configurationChanged(NetworkDeviceEntity entity){if(devices.get(entity.getBlockPos())!=entity)return;simulator.put(entity.id(),entity.profile());simulator.invalidate();}
    public NetworkDeviceEntity device(String id){for(var device:devices.values())if(device.id().equals(id))return device;return null;}
    public static void tick(LevelTickEvent.Post event){if(event.getLevel() instanceof ServerLevel level){NetworkWorld world=WORLDS.get(level);if(world!=null)world.tick();}}
    private void tick(){
        if(dirty){scan=new Scan();dirty=false;diagnostic="配線を解析しています (チャンクの強制ロードなし)";}
        if(scan!=null){if(scan.step(NetworkContent.TOPOLOGY_BUDGET.get())){simulator=scan.result;cableChunks=Set.copyOf(scan.usedChunks);waitingChunks=Set.copyOf(scan.unloadedChunks);diagnostic=scan.warning;scan=null;}}
        if(ready())simulator.advance(1);
        long now=level.getGameTime();int work=0;
        while(!failures.isEmpty()&&failures.peek().due<=now&&work++<64){var event=failures.remove();var device=event.device;
            if(device.isRemoved()||devices.get(device.getBlockPos())!=device||device.scheduleGeneration!=event.generation)continue;
            device.failureCheck(level);device.nextFailureTime=now+1200;failures.add(new FailureEvent(device.nextFailureTime,device.scheduleGeneration,device));
        }
        if(failures.size()>Math.max(64,devices.size()*4)){failures.clear();for(var device:devices.values())failures.add(new FailureEvent(device.nextFailureTime,device.scheduleGeneration,device));}
    }
    public static void stopped(ServerStoppedEvent event){WORLDS.clear();}
    public static void breaking(BlockEvent.BreakEvent event){if(event.getLevel() instanceof ServerLevel level&&level.getBlockEntity(event.getPos()) instanceof NetworkDeviceEntity device&&!device.canUse(event.getPlayer()))event.setCanceled(true);}
    public static void chunkLoaded(ChunkEvent.Load event){if(event.getLevel() instanceof ServerLevel level){var w=WORLDS.get(level);if(w!=null&&w.waitingChunks.contains(event.getChunk().getPos().toLong()))w.markDirty();}}
    public static void chunkUnloaded(ChunkEvent.Unload event){if(event.getLevel() instanceof ServerLevel level){var w=WORLDS.get(level);if(w!=null&&w.cableChunks.contains(event.getChunk().getPos().toLong()))w.markDirty();}}
    private final class Scan {
        final NetworkSimulator result=new NetworkSimulator();
        final ArrayDeque<BlockPos> seeds=new ArrayDeque<>(),queue=new ArrayDeque<>();
        final Set<BlockPos> visited=new HashSet<>();
        final Set<Long> usedChunks=new HashSet<>(),unloadedChunks=new HashSet<>();
        final LinkedHashSet<Endpoint> ends=new LinkedHashSet<>();
        CableGrade grade;
        int length,rejected;
        boolean branched,incomplete;
        String warning="";
        Scan(){for(var entry:devices.entrySet()){var device=entry.getValue();if(device.isRemoved()||!level.hasChunkAt(entry.getKey()))continue;result.put(device.id(),device.profile());for(Direction d:NetworkCableBlock.DIRECTIONS)seeds.addLast(entry.getKey().relative(d));}}
        boolean step(int budget){
            int work=0;
            while(work++<budget){
                if(queue.isEmpty()){
                    if(grade!=null)finishSegment();
                    if(seeds.isEmpty()){warning="接続 "+result.links().size()+"本 / 未接続・不正配線 "+rejected+"区間";return true;}
                    BlockPos seed=seeds.removeFirst();if(visited.contains(seed))continue;
                    if(!level.hasChunkAt(seed)){unloadedChunks.add(new ChunkPos(seed).toLong());continue;}
                    if(!(level.getBlockState(seed).getBlock() instanceof NetworkCableBlock cable))continue;
                    grade=cable.grade;length=0;branched=false;incomplete=false;ends.clear();queue.add(seed);
                }
                BlockPos pos=queue.removeFirst();if(!visited.add(pos))continue;
                if(visited.size()>MAX_CABLE_BLOCKS){warning="配線上限を超えました ("+MAX_CABLE_BLOCKS+"ブロック)。この解析は中止しました";result.invalidate();for(var link:new ArrayList<>(result.links()))result.unlink(link.id());queue.clear();seeds.clear();grade=null;return true;}
                if(!level.hasChunkAt(pos)){incomplete=true;unloadedChunks.add(new ChunkPos(pos).toLong());continue;}
                usedChunks.add(new ChunkPos(pos).toLong());length++;int degree=0;
                for(Direction d:NetworkCableBlock.DIRECTIONS){BlockPos adjacent=pos.relative(d);
                    if(!level.hasChunkAt(adjacent)){incomplete=true;unloadedChunks.add(new ChunkPos(adjacent).toLong());continue;}
                    var state=level.getBlockState(adjacent);
                    if(state.getBlock() instanceof NetworkCableBlock cable&&cable.grade==grade){degree++;if(!visited.contains(adjacent))queue.addLast(adjacent);}
                    else if(state.getBlock() instanceof NetworkDeviceBlock){degree++;var device=devices.get(adjacent);if(device==null||device.isRemoved())incomplete=true;else ends.add(new Endpoint(device.id(),NetworkCableBlock.port(d.getOpposite())));}
                }
                if(degree>2)branched=true;
            }
            return false;
        }
        private void finishSegment(){
            if(ends.size()==2&&!branched&&!incomplete&&length<=grade.maxLength()){
                var a=ends.iterator();Endpoint first=a.next(),second=a.next();
                if(!first.id.equals(second.id)&&result.nodes().containsKey(first.id)&&result.nodes().containsKey(second.id)){
                    String x=first.id+":"+first.port,y=second.id+":"+second.port;String id=x.compareTo(y)<0?x+"-"+y:y+"-"+x;
                    var ak=result.nodes().get(first.id).kind;var bk=result.nodes().get(second.id).kind;
                    boolean wan=ak==DeviceKind.ONU&&bk==DeviceKind.OLT||ak==DeviceKind.OLT&&bk==DeviceKind.ONU;
                    try{result.link(new NetworkSimulator.Link(id,first.id,first.port,second.id,second.port,grade,length,wan));}catch(IllegalArgumentException|IllegalStateException duplicate){rejected++;}
                }else rejected++;
            }else rejected++;
            grade=null;
        }
    }
}
