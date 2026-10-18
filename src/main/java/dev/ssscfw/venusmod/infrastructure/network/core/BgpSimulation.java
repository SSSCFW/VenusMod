package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** 既存のIPv4/ACLへBGPを接続。高価な往復到達検査は2ピア/tickで再確認する。 */
final class BgpSimulation {
    private record Cached(BgpEngine.Channel channel,long expires){}
    private final NetworkSimulator simulator;
    private final BgpEngine engine;
    private final Map<BgpEngine.Key,Cached> channels=new HashMap<>();
    private final ArrayDeque<BgpEngine.Key> pending=new ArrayDeque<>();
    private boolean dirty=true;
    private long clock;
    BgpSimulation(NetworkSimulator simulator){
        this.simulator=simulator;
        engine=new BgpEngine(new BgpEngine.Transport(){
            public BgpEngine.Channel channel(String id,String peer){var cache=channels.get(new BgpEngine.Key(id,peer));return cache==null||cache.expires<=clock?null:cache.channel;}
            public BgpEngine.Hop resolve(String id,String ip){return simulator.bgpResolve(id,ip);}
            public boolean originates(String id,NetworkPolicy.Prefix prefix){return simulator.bgpOriginates(id,prefix);}
            public boolean usable(String id,BgpEngine.Route route){return simulator.bgpUsable(id,route);}
            public void log(String id,String message){simulator.log(id,message);}
        });
    }
    BgpEngine engine(){return engine;}
    void changed(Set<String> ids){dirty=true;channels.keySet().removeIf(k->ids.contains(k.node()));engine.changed(ids);}
    void advance(long ticks){
        if(ticks<=0)return;clock+=ticks;
        if(dirty){dirty=false;configure();}
        if(!engine.error().isEmpty())return;
        for(int work=0;work<2&&!pending.isEmpty();work++){
            var key=pending.removeFirst();pending.addLast(key);
            var p=simulator.nodes().get(key.node());if(p==null||p.failed)continue;
            var peer=p.bgp.peers.get(key.peer());if(peer==null)continue;
            var channel=simulator.bgpChannel(key.node(),peer);
            // 256接続を巡回しても次の検査前に失効しない。配線/ACL変更時は即座に無効化。
            channels.put(key,new Cached(channel,clock+BgpEngine.MAX_SESSIONS+20));
        }
        engine.advance(ticks);
    }
    private void configure(){
        Map<String,BgpEngine.Configuration> configs=new LinkedHashMap<>();Set<BgpEngine.Key> keys=new LinkedHashSet<>();
        try{
            for(var entry:simulator.nodes().entrySet()){
                var p=entry.getValue();if(!p.bgp.enabled||p.failed)continue;p.bgp.validate(p);
                String rid=p.bgp.routerId;
                if(rid.isBlank()){
                    long max=0;for(var port:p.ports)if(!port.address.isBlank()&&port.enabled){try{long ip=Ipv4.parse(BgpSettings.unicast(port.address));max=Math.max(max,ip);}catch(IllegalArgumentException ignored){}}
                    if(max==0)continue;rid=Ipv4.format(max);
                }
                configs.put(entry.getKey(),new BgpEngine.Configuration(p.bgp.asn,rid,p.bgp.keepaliveTicks,p.bgp.holdTicks,new ArrayList<>(p.bgp.peers.values()),p.bgp.networks));
                for(String peer:p.bgp.peers.keySet())keys.add(new BgpEngine.Key(entry.getKey(),peer));
                if(configs.size()>BgpEngine.MAX_ROUTERS||keys.size()>BgpEngine.MAX_SESSIONS){engine.stop("BGP有効機器64台/接続256件の上限です");pending.clear();channels.clear();return;}
            }
            channels.keySet().retainAll(keys);pending.removeIf(k->!keys.contains(k));
            for(var key:keys)if(!pending.contains(key))pending.addLast(key);
            engine.synchronize(configs);
        }catch(IllegalArgumentException bad){engine.stop("BGP設定不正: "+bad.getMessage());channels.clear();pending.clear();}
    }
}
