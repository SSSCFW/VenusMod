package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** 既存L2シミュレーションとOSPF制御エンジンを接続。探索は2ポート/tickに分割。 */
final class OspfSimulation {
    private record Endpoint(String id,int port){}
    private final NetworkSimulator simulator;
    private final OspfEngine engine;
    private final Map<Endpoint,List<OspfEngine.Peer>> peers=new HashMap<>();
    private final ArrayDeque<Endpoint> pending=new ArrayDeque<>();
    private boolean dirty=true;
    private final Set<String> dirtyNodes=new LinkedHashSet<>();
    OspfSimulation(NetworkSimulator simulator){
        this.simulator=simulator;
        engine=new OspfEngine(new OspfEngine.Transport(){
            public List<OspfEngine.Peer> peers(String id,int port){return peers.getOrDefault(new Endpoint(id,port),List.of());}
            public void log(String id,String message){simulator.log(id,message);}
        });
    }
    OspfEngine engine(){return engine;}
    List<OspfEngine.Route> routes(String id){return dirtyNodes.contains(id)?List.of():engine.routes(id);}
    void changed(Set<String> ids){
        dirty=true;dirtyNodes.addAll(ids);peers.keySet().removeIf(e->ids.contains(e.id));
        pending.removeIf(e->ids.contains(e.id));
    }
    void advance(long ticks){
        if(dirty){dirty=false;configure();dirtyNodes.clear();}
        if(!engine.error().isEmpty())return;
        try{
            for(int work=0;work<2&&!pending.isEmpty();work++){
                Endpoint endpoint=pending.removeFirst();peers.put(endpoint,simulator.findOspfPeers(endpoint.id,endpoint.port));
            }
        }catch(IllegalStateException error){engine.stop("配線/ACL探索: "+error.getMessage());pending.clear();peers.clear();return;}
        engine.advance(ticks);
    }
    private void configure(){
        Map<String,OspfEngine.Configuration> configurations=new LinkedHashMap<>();
        try{
            for(var entry:simulator.nodes().entrySet()){
                var p=entry.getValue();if(!p.kind.routing()||!p.ospf.enabled||p.failed)continue;p.ospf.validate();
                if(configurations.size()>=OspfEngine.MAX_ROUTERS){engine.stop("OSPF有効機器は128台までです");pending.clear();return;}
                String rid=p.ospf.routerId;
                if(rid.isBlank()){
                    long max=0;for(var port:p.ports)if(!port.address.isBlank()){long ip=Ipv4.parse(port.address);if(ip>max&&ip<0xe0000000L)max=ip;}
                    if(max==0)continue;rid=Ipv4.format(max);
                }
                List<OspfEngine.Port> ports=new ArrayList<>();
                for(int n=0;n<6;n++){
                    var port=p.ports[n];var mode=p.ospf.modes[n];if(mode==OspfSettings.Mode.OFF||port.address.isBlank())continue;
                    long speed=simulator.portSpeed(entry.getKey(),n);int cost=p.ospf.costs[n];
                    if(cost==0)cost=(int)Math.clamp((long)Math.ceil(100_000_000_000.0/Math.max(1,speed)),1L,65535L);
                    ports.add(new OspfEngine.Port(n,port.address,port.prefix,cost,mode,port.enabled&&speed>0));
                    if(mode==OspfSettings.Mode.ACTIVE&&port.enabled&&speed>0){var endpoint=new Endpoint(entry.getKey(),n);if(!peers.containsKey(endpoint)&&!pending.contains(endpoint))pending.addLast(endpoint);}
                }
                configurations.put(entry.getKey(),new OspfEngine.Configuration(rid,p.ospf.area,p.ospf.helloTicks,p.ospf.deadTicks,ports));
            }
            engine.synchronize(configurations);
        }catch(IllegalArgumentException error){engine.stop("設定不正: "+error.getMessage());pending.clear();}
    }
}
