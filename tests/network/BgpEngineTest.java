import dev.ssscfw.venusmod.infrastructure.network.core.*;
import dev.ssscfw.venusmod.infrastructure.network.core.NetworkPolicy.Prefix;
import java.util.*;

/** 単体は下位経路を模擬する。広告の送受信・選択・撤回自体は本物のエンジンを使う。 */
public final class BgpEngineTest {
    static int checks;
    static final Prefix NET=Prefix.parse("10.9.0.0/24");
    static void ok(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    static final class Wire implements BgpEngine.Transport {
        final Map<String,String> addresses=new LinkedHashMap<>();final Set<String> blocked=new HashSet<>(),unreachable=new HashSet<>(),unusable=new HashSet<>();
        final List<String> logs=new ArrayList<>();
        public BgpEngine.Channel channel(String id,String peer){if(blocked.contains(id+">"+peer))return null;String remote=addresses.entrySet().stream().filter(e->e.getValue().equals(peer)).map(Map.Entry::getKey).findFirst().orElse(null);return remote==null?null:new BgpEngine.Channel(remote,addresses.get(id),peer);}
        public BgpEngine.Hop resolve(String id,String ip){return unreachable.contains(id+">"+ip)?null:new BgpEngine.Hop(0,ip,10,24,0);}
        public boolean originates(String id,Prefix prefix){return true;}
        public boolean usable(String id,BgpEngine.Route r){return !unusable.contains(id+">"+r.prefix());}
        public void log(String id,String text){logs.add(id+" "+text);}
    }
    static BgpSettings.Peer peer(String ip,long asn,int prepend,long lp,long med){return new BgpSettings.Peer(ip,asn,-1,false,prepend,lp,med,new BgpSettings.Policy("all"),new BgpSettings.Policy("all"));}
    static BgpEngine.Configuration cfg(long asn,String rid,List<BgpSettings.Peer> peers,boolean net){return new BgpEngine.Configuration(asn,rid,20,80,peers,net?Set.of(NET):Set.of());}
    static void ticks(BgpEngine engine,int n){for(int i=0;i<n;i++)engine.advance(1);}
    static BgpEngine.Route learned(BgpEngine e,String id){return e.routes(id).stream().filter(r->r.prefix().equals(NET)).findFirst().orElse(null);}
    public static void main(String[] args){
        var wire=new Wire();wire.addresses.put("a","192.0.2.1");wire.addresses.put("b","192.0.2.2");wire.addresses.put("c","192.0.2.3");
        var e=new BgpEngine(wire);var configs=new LinkedHashMap<String,BgpEngine.Configuration>();
        configs.put("a",cfg(65001,"1.1.1.1",List.of(peer("192.0.2.2",65002,0,-1,-1),peer("192.0.2.3",65002,0,-1,-1)),false));
        configs.put("b",cfg(65002,"2.2.2.2",List.of(peer("192.0.2.1",65001,0,-1,50)),true));
        configs.put("c",cfg(65002,"3.3.3.3",List.of(peer("192.0.2.1",65001,0,-1,10)),true));
        e.synchronize(configs);ok(learned(e,"a")==null,"設定では経路を合成しない");ticks(e,150);
        ok(e.neighbors("a").stream().allMatch(n->n.state()==BgpEngine.State.ESTABLISHED),"OPEN/KEEPALIVE後にEstablished");
        ok(learned(e,"a").peer().equals("192.0.2.3"),"同一隣接ASなら低いMEDを選択");
        configs.put("a",cfg(65001,"1.1.1.1",List.of(peer("192.0.2.2",65002,0,200,-1),peer("192.0.2.3",65002,0,-1,-1)),false));
        configs.put("b",cfg(65002,"2.2.2.2",List.of(peer("192.0.2.1",65001,2,-1,50)),true));
        e.synchronize(configs);ticks(e,150);
        ok(learned(e,"a").path().size()==3&&learned(e,"a").peer().equals("192.0.2.2"),"LOCAL_PREFはAS_PATH長より優先");
        configs.put("a",cfg(65001,"1.1.1.1",List.of(peer("192.0.2.2",65002,0,-1,-1),peer("192.0.2.3",65002,0,-1,-1)),false));
        e.synchronize(configs);ticks(e,150);ok(learned(e,"a").peer().equals("192.0.2.3"),"AS_PATHが短い側");
        configs.put("b",cfg(65002,"2.2.2.2",List.of(peer("192.0.2.1",65001,0,-1,50)),true));
        configs.put("c",cfg(65003,"3.3.3.3",List.of(peer("192.0.2.1",65001,0,-1,0)),true));
        configs.put("a",cfg(65001,"1.1.1.1",List.of(peer("192.0.2.2",65002,0,-1,-1),peer("192.0.2.3",65003,0,-1,-1)),false));
        e.synchronize(configs);ticks(e,160);ok(learned(e,"a").peer().equals("192.0.2.2"),"異なる隣接ASのMEDは比較せずRIDで決定");
        wire.unreachable.add("a>192.0.2.2");e.changed(Set.of("a"));ticks(e,5);
        ok(learned(e,"a").peer().equals("192.0.2.3"),"NEXT_HOP到達不能な候補を除く");
        wire.unreachable.remove("a>192.0.2.2");e.changed(Set.of("a"));ticks(e,5);ok(learned(e,"a").peer().equals("192.0.2.2"),"下位到達性の回復で再選択");
        long rx=e.neighbors("a").getFirst().received();int timers=e.timers();ticks(e,800);
        ok(e.timers()==timers&&timers==4,"広告受信でタイマーを蓄積しない");ok(e.neighbors("a").getFirst().received()>rx,"KEEPALIVE継続");ok(e.queued()<20,"UPDATEを毎tick複製しない");
        wire.blocked.add("b>192.0.2.1");wire.blocked.add("a>192.0.2.2");ticks(e,10);
        ok(learned(e,"a").peer().equals("192.0.2.2"),"広告断直後はHold未満の経路を保持");ticks(e,100);
        ok(learned(e,"a").peer().equals("192.0.2.3"),"Hold満了で予備経路");
        wire.blocked.clear();ticks(e,160);ok(learned(e,"a").peer().equals("192.0.2.2"),"再接続で復旧");
        // importした経路をiBGP→iBGPで再配布しない。
        var ibgp=new BgpEngine(wire);configs.clear();
        configs.put("a",cfg(65001,"1.1.1.1",List.of(peer("192.0.2.2",65001,0,-1,-1)),true));
        configs.put("b",cfg(65001,"2.2.2.2",List.of(peer("192.0.2.1",65001,0,-1,-1),peer("192.0.2.3",65001,0,-1,-1)),false));
        configs.put("c",cfg(65001,"3.3.3.3",List.of(peer("192.0.2.2",65001,0,-1,-1)),false));ibgp.synchronize(configs);ticks(ibgp,200);
        ok(learned(ibgp,"b")!=null&&learned(ibgp,"b").path().isEmpty(),"自機生成経路のiBGP広告はAS_PATH空");
        ok(learned(ibgp,"c")==null,"iBGP→iBGP非再広告");
        // 使用中FIBから外れた経路を再広告しない。BGP選択そのものが同一でもwithdrawが必要。
        wire.unusable.add("a>"+NET);ibgp.changed(Set.of("a"));ticks(ibgp,80);
        ok(learned(ibgp,"b")==null,"下位経路の優先変更でも広告を再評価してwithdraw");
        System.out.println("BgpEngineTest: "+checks+" checks passed");
    }
}
