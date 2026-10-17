package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** VRRP広告を既存の物理リンク/VLAN/ACLへ載せる。探索は変更時に2機器/tick。 */
final class VrrpSimulation {
    private final NetworkSimulator simulator;
    private final VrrpEngine engine;
    private final Map<String,List<String>> peers=new HashMap<>();
    private final ArrayDeque<String> pending=new ArrayDeque<>();
    private final Set<String> changedOwners=new LinkedHashSet<>();
    private boolean dirty=true;
    VrrpSimulation(NetworkSimulator simulator){
        this.simulator=simulator;
        engine=new VrrpEngine(new VrrpEngine.Transport(){
            public List<String> peers(String id){return peers.getOrDefault(id,List.of());}
            public void log(String id,String message){simulator.log(id,message);}
            public void ownershipChanged(String id){changedOwners.add(id);}
        });
    }
    VrrpEngine engine(){return engine;}
    void changed(){dirty=true;peers.clear();pending.clear();}
    void shutdown(String id){engine.shutdown(id);}
    boolean master(String id){
        var p=simulator.nodes().get(id);if(p==null||!p.vrrp.enabled||p.vip.isBlank()||p.failed||!p.kind.routing())return false;
        int port=p.vrrp.resolvePort(p);var c=engine.configuration(id);
        return c!=null&&port==c.port()&&p.ports[port].enabled&&simulator.portSpeed(id,port)>0&&p.vip.equals(c.vip())&&p.vrrp.group==c.group()
                &&p.ports[port].address.equals(c.address())&&p.ports[port].prefix==c.prefix()&&engine.isMaster(id);
    }
    Set<String> drainOwners(){var ids=Set.copyOf(changedOwners);changedOwners.clear();return ids;}
    void advance(long ticks){
        if(dirty){dirty=false;configure();}
        if(!engine.error().isEmpty())return;
        try{for(int n=0;n<2&&!pending.isEmpty();n++){String id=pending.removeFirst();var c=engine.configuration(id);if(c!=null)peers.put(id,simulator.findVrrpPeers(id,c.port()));}}
        catch(IllegalStateException e){engine.stop("広告経路の探索: "+e.getMessage());pending.clear();peers.clear();return;}
        engine.advance(ticks);
    }
    private void configure(){
        Map<String,VrrpEngine.Configuration> configs=new LinkedHashMap<>();
        for(var entry:simulator.nodes().entrySet()){
            var p=entry.getValue();if(!p.kind.routing()||!p.vrrp.enabled||p.vip.isBlank()||p.failed)continue;
            int port=p.vrrp.resolvePort(p);if(port<0||!p.ports[port].enabled||simulator.portSpeed(entry.getKey(),port)<=0)continue;
            if(configs.size()>=VrrpEngine.MAX_ROUTERS){engine.stop("VRRP有効機器は128台までです");pending.clear();return;}
            try{p.vrrp.validate(p);var physical=p.ports[port];configs.put(entry.getKey(),new VrrpEngine.Configuration(port,physical.address,physical.prefix,p.vip,p.vrrp.group,p.vrrpPriority,p.vrrp.advertisementTicks,p.vrrp.preempt));pending.addLast(entry.getKey());}
            catch(IllegalArgumentException e){simulator.log(entry.getKey(),"VRRP設定不正: "+e.getMessage());}
        }
        engine.synchronize(configs);
    }
}
