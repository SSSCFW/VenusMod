package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** BPDUは隣接bridgeだけへ配送する。IP/ARP探索、ルーティング、ACL、NATを通さない。 */
final class StpSimulation implements StpEngine.Transport {
    private final NetworkSimulator sim;
    private final StpEngine engine=new StpEngine(this);
    private Map<StpEngine.Key,StpEngine.Configuration> configurations=Map.of();
    private final Map<String,String> issues=new HashMap<>();
    private boolean dirty=true;
    private final Set<String> dirtyNodes=new LinkedHashSet<>(),forwardingChanged=new LinkedHashSet<>();
    StpSimulation(NetworkSimulator sim){this.sim=sim;}
    StpEngine engine(){return engine;}
    void changed(Set<String> ids){dirty=true;dirtyNodes.addAll(ids);}
    Set<String> drainChanges(){var result=Set.copyOf(forwardingChanged);forwardingChanged.clear();return result;}
    private static boolean classic(NetworkProfile p){return p!=null&&p.kind.bridge()&&p.stp&&p.spanning.classic;}
    boolean forwarding(String id,int port,int vlan){var p=sim.nodes().get(id);return !classic(p)||!dirtyNodes.contains(id)&&engine.forwarding(new StpEngine.Key(id,vlan),port);}
    boolean learning(String id,int port,int vlan){var p=sim.nodes().get(id);return classic(p)&&!dirtyNodes.contains(id)&&engine.learning(new StpEngine.Key(id,vlan),port);}
    List<StpEngine.Key> keys(String id){return configurations.keySet().stream().filter(k->k.device().equals(id)).sorted().toList();}
    String issue(String id,int port){return issues.getOrDefault(id+"/"+port,"");}
    void clearGuard(String id,int port){for(var key:keys(id))engine.clearGuard(key,port);}
    public void changed(StpEngine.Key key){forwardingChanged.add(key.device());}
    public void log(StpEngine.Key key,String message){sim.log(key.device(),"VLAN "+key.vlan()+" "+message);}
    public StpEngine.Peer peer(StpEngine.Key key,int port){
        var link=sim.physicalLink(key.device(),port);if(link==null||sim.linkCapacity(link)<=0)return null;
        var local=sim.nodes().get(key.device());String otherId=link.peer(key.device());var other=sim.nodes().get(otherId);
        if(!classic(local)||!classic(other)||local.failed||other.failed)return null;int otherPort=link.peerPort(key.device());
        int vlan=NetworkSimulator.receivedVlan(local.ports[port],key.vlan(),other.ports[otherPort]);
        if(vlan<1||NetworkSimulator.receivedVlan(other.ports[otherPort],vlan,local.ports[port])!=key.vlan())return null;
        var target=new StpEngine.Key(otherId,vlan);return configurations.containsKey(target)?new StpEngine.Peer(target,otherPort):null;
    }
    void advance(long ticks){if(dirty){dirty=false;configure();dirtyNodes.clear();}engine.advance(ticks);}
    private void configure(){
        var vlans=new TreeSet<Integer>();vlans.add(1);for(var p:sim.nodes().values())for(var port:p.ports)if(port.enabled)vlans.add(port.vlan);
        issues.clear();var result=new TreeMap<StpEngine.Key,StpEngine.Configuration>();
        if(vlans.size()>NetworkSimulator.MAX_VLANS){configurations=Map.of();engine.stop("VLAN種類数の上限です (128)");return;}
        for(var e:sim.nodes().entrySet()){
            var p=e.getValue();if(!classic(p)||p.failed)continue;
            try{p.spanning.validate(p);}catch(IllegalArgumentException invalid){issues.put(e.getKey()+"/0",invalid.getMessage());continue;}
            for(int vlan:vlans){
                if(Arrays.stream(p.ports).noneMatch(port->port.enabled&&(port.trunk||port.vlan==vlan)))continue;
                if(result.size()>=StpEngine.MAX_INSTANCES){configurations=Map.of();engine.stop("STP instance上限 (512)。VLAN数/機器数を減らしてください");return;}
                List<StpEngine.PortConfig> ports=new ArrayList<>();
                for(int n=0;n<6;n++){
                    var port=p.ports[n];var link=sim.physicalLink(e.getKey(),n);long speed=link==null?0:sim.linkCapacity(link);
                    boolean live=port.enabled&&(port.trunk||port.vlan==vlan)&&speed>0;String connection="";
                    if(link!=null){String otherId=link.peer(e.getKey());int otherPort=link.peerPort(e.getKey());var other=sim.nodes().get(otherId);
                        connection=link.id()+"/"+otherId+":"+otherPort+"/"+other.ports[otherPort].vlan+"/"+other.ports[otherPort].trunk;
                        if(other.kind.bridge()&&!classic(other)){live=false;issues.put(e.getKey()+"/"+n,"相手のL2機器もstp mode classicが必要 (simple/off混在は未対応)");}
                        if(live){int mapped=NetworkSimulator.receivedVlan(port,vlan,other.ports[otherPort]);if(mapped<1||NetworkSimulator.receivedVlan(other.ports[otherPort],mapped,port)!=vlan)live=false;}
                    }
                    ports.add(new StpEngine.PortConfig(live,p.spanning.cost(n,speed),p.spanning.portPriorities[n],p.spanning.edge[n],p.spanning.guard[n],connection));
                }
                result.put(new StpEngine.Key(e.getKey(),vlan),new StpEngine.Configuration(new StpEngine.BridgeId(p.spanning.priorityFor(vlan),NetworkSimulator.macAddress(e.getKey())),p.spanning.helloTicks,p.spanning.maxAgeTicks,p.spanning.forwardDelayTicks,ports));
            }
        }
        configurations=Collections.unmodifiableMap(result);engine.synchronize(configurations);
    }
}
