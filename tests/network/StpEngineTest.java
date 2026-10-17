import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class StpEngineTest {
    static int count;
    static void check(boolean yes,String why){count++;if(!yes)throw new AssertionError(why);}
    static final class Fixture implements StpEngine.Transport {
        final Map<String,StpEngine.Peer> peers=new HashMap<>();
        final Map<StpEngine.Key,StpEngine.Configuration> configs=new LinkedHashMap<>();
        final StpEngine e=new StpEngine(this);
        int changes;
        String k(StpEngine.Key key,int port){return key+"/"+port;}
        void connect(StpEngine.Key a,int ap,StpEngine.Key b,int bp){peers.put(k(a,ap),new StpEngine.Peer(b,bp));peers.put(k(b,bp),new StpEngine.Peer(a,ap));}
        public StpEngine.Peer peer(StpEngine.Key key,int port){return peers.get(k(key,port));}
        public void changed(StpEngine.Key key){changes++;}
        public void log(StpEngine.Key key,String message){}
        void sync(){e.synchronize(configs);}
        void ticks(int n){for(int i=0;i<n;i++)e.advance(1);}
    }
    static StpEngine.Key key(String id){return new StpEngine.Key(id,1);}
    static StpEngine.Configuration config(int id,int priority,int... costs){
        var ports=new ArrayList<StpEngine.PortConfig>();
        for(int n=0;n<6;n++)ports.add(new StpEngine.PortConfig(n<costs.length,n<costs.length?costs[n]:20,128,false,false,"c"+n));
        return new StpEngine.Configuration(new StpEngine.BridgeId(priority,String.format("02:00:00:00:00:%02x",id)),20,120,80,ports);
    }
    static void edge(Fixture f,StpEngine.Key key,int port,boolean edge,boolean guard){
        var c=f.configs.get(key);var ps=new ArrayList<>(c.ports());var p=ps.get(port);
        ps.set(port,new StpEngine.PortConfig(p.up(),p.cost(),p.priority(),edge,guard,p.connection()));
        f.configs.put(key,new StpEngine.Configuration(c.bridge(),c.hello(),c.maxAge(),c.forwardDelay(),ps));
    }
    public static void main(String[] args){
        var a=key("a");var b=key("b");var c=key("c");var f=new Fixture();
        f.configs.put(a,config(1,4096,10,100));f.configs.put(b,config(2,32768,10,10));f.configs.put(c,config(3,32768,100,10));
        f.connect(a,0,b,0);f.connect(a,1,c,0);f.connect(b,1,c,1);f.sync();
        check(!f.e.forwarding(a,0),"startup must wait");f.ticks(50);
        check(f.e.status(c).root().equals(f.configs.get(a).bridge()),"BPDUs elect lowest BID");
        check(f.e.status(c).rootPort()==1&&f.e.status(c).rootCost()==20,"cost beats shorter hop count");
        check(f.e.status(c).ports().get(0).role()==StpEngine.Role.ALTERNATE,"redundant link is alternate");
        check(!f.e.forwarding(c,0),"alternate blocks data but has received BPDUs");
        check(f.e.status(c).ports().get(0).received()>0,"blocking receives BPDUs");
        f.ticks(55);check(f.e.status(b).ports().get(0).state()==StpEngine.State.LEARNING,"listening -> learning");
        check(!f.e.forwarding(b,0)&&f.e.learning(b,0),"learning is not forwarding");f.ticks(120);
        check(f.e.forwarding(a,0)&&f.e.forwarding(b,0)&&f.e.forwarding(c,1),"tree converges after both delays");
        check(f.e.scheduledEvents()<=f.configs.size()*13,"one hello, expiry, transition per port bounded");
        f.e.advance(0);var unchanged=f.e.status(b);f.e.status(b);check(f.e.status(b).equals(unchanged),"reads don't advance timers");
        // 故障で低コスト経路を切り、代替ポートは待機を経て利用する。
        f.configs.remove(b);f.sync();f.ticks(140);
        check(f.e.status(c).rootPort()==0,"expired lower path chooses surviving direct path");
        check(!f.e.forwarding(c,0),"alternate cannot skip forward delay");f.ticks(200);check(f.e.forwarding(c,0),"alternate eventually forwards");
        // 全ルート広告を止めた後、転送された古い情報が永続化しない。
        f.configs.remove(a);f.sync();f.ticks(400);check(f.e.status(c).root().equals(f.configs.get(c).bridge()),"stale root expires");
        var edge=new Fixture();edge.configs.put(a,config(1,32768,10));edge(edge,a,0,true,true);edge.sync();
        check(edge.e.forwarding(a,0),"edge forwards immediately");
        var superior=new StpEngine.Bpdu(new StpEngine.BridgeId(0,"02:00:00:00:00:09"),0,new StpEngine.BridgeId(0,"02:00:00:00:00:09"),128*256+1,20,120,80,0);
        edge.e.receive(a,0,superior);check(edge.e.status(a).ports().get(0).state()==StpEngine.State.ERR_DISABLED,"BPDU guard latches shutdown");
        edge.ticks(400);check(!edge.e.forwarding(a,0),"guard doesn't clear by timeout");edge.e.clearGuard(a,0);check(edge.e.forwarding(a,0),"explicit guard reset");
        edge(edge,a,0,true,false);edge.sync();edge.e.receive(a,0,superior);check(!edge.e.forwarding(a,0),"edge receiving BPDU loses fast-forward privilege");
        check(edge.e.status(a).ports().get(0).state()==StpEngine.State.LISTENING,"edge without guard waits on BPDU");
        var ec=edge.configs.get(a);var eps=new ArrayList<>(ec.ports());var ep=eps.get(0);
        eps.set(0,new StpEngine.PortConfig(true,11,ep.priority(),true,false,ep.connection()));
        edge.configs.put(a,new StpEngine.Configuration(ec.bridge(),ec.hello(),ec.maxAge(),ec.forwardDelay(),eps));edge.sync();
        check(!edge.e.forwarding(a,0),"cost edit must not restore edge after seeing a bridge BPDU");

        long received=edge.e.status(a).ports().get(0).received();
        var expired=new StpEngine.Bpdu(superior.root(),0,superior.sender(),superior.portId(),20,120,80,120);
        edge.e.receive(a,0,expired);check(edge.e.status(a).ports().get(0).received()==received,"aged BPDU rejected");
        // 同じBIDではsender port ID、その次に自ポートIDを比較。
        var pair=new Fixture();pair.configs.put(a,config(1,0,10,10));pair.configs.put(b,config(2,32768,10,10));pair.connect(a,0,b,1);pair.connect(a,1,b,0);pair.sync();pair.ticks(300);
        check(pair.e.status(b).rootPort()==1,"remote port id tie-break before local port number");
        // 異なるVLANのinstanceは独立。
        var second=new StpEngine.Key("b",20);pair.configs.put(second,config(2,32768,10));pair.sync();pair.ticks(180);
        check(pair.e.status(second).root().equals(pair.configs.get(second).bridge()),"VLAN instance isolation");
        // 有限キューを超えた際に未保護の転送に戻らない。
        var huge=new LinkedHashMap<StpEngine.Key,StpEngine.Configuration>();for(int n=0;n<=StpEngine.MAX_INSTANCES;n++)huge.put(new StpEngine.Key("z"+n,1),config(3,32768,1));
        pair.e.synchronize(huge);check(!pair.e.error().isBlank()&&!pair.e.forwarding(a,0),"over-limit fail closed");
        var costly=new Fixture();costly.configs.put(a,config(1,0,200000000));costly.configs.put(b,config(2,32768,200000000));costly.connect(a,0,b,0);costly.sync();costly.ticks(250);
        check(costly.e.status(b).rootPort()==0&&costly.e.status(b).rootCost()==200000000L,"maximum port cost remains a valid path");
        System.out.println("StpEngineTest: "+count+" checks passed");
    }
}
