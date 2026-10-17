import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** Minecraft非依存。Hello/DB同期/LSUはエンジンの配送イベントを通して検査する。 */
public final class OspfEngineTest {
    static int checks;
    static void ok(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static OspfEngine.Port port(int n,String ip,int prefix,int cost,OspfSettings.Mode mode){return new OspfEngine.Port(n,ip,prefix,cost,mode,true);}
    static final class Lab implements OspfEngine.Transport {
        final Map<String,OspfEngine.Configuration> configs=new LinkedHashMap<>();
        final Map<String,List<OspfEngine.Peer>> wires=new HashMap<>();
        final List<String> logs=new ArrayList<>();
        final OspfEngine engine=new OspfEngine(this);
        public List<OspfEngine.Peer> peers(String id,int port){return wires.getOrDefault(id+":"+port,List.of());}
        public void log(String id,String message){logs.add(id+" "+message);}
        void router(String id,long area,OspfEngine.Port... ports){configs.put(id,new OspfEngine.Configuration(id.equals("a")?"1.1.1.1":id.equals("b")?"2.2.2.2":"3.3.3.3",area,20,80,List.of(ports)));}
        void wire(String a,int ap,String b,int bp){wires.computeIfAbsent(a+":"+ap,k->new ArrayList<>()).add(new OspfEngine.Peer(b,bp));wires.computeIfAbsent(b+":"+bp,k->new ArrayList<>()).add(new OspfEngine.Peer(a,ap));}
        void cut(String a,int ap,String b,int bp){wires.get(a+":"+ap).remove(new OspfEngine.Peer(b,bp));wires.get(b+":"+bp).remove(new OspfEngine.Peer(a,ap));}
        void sync(){engine.synchronize(configs);}
        void ticks(int ticks){for(int i=0;i<ticks;i++)engine.advance(1);}
        boolean route(String id,String net){return engine.routes(id).stream().anyMatch(r->r.network().equals(net));}
    }
    static Lab triangle(){
        var l=new Lab();var active=OspfSettings.Mode.ACTIVE;var passive=OspfSettings.Mode.PASSIVE;
        l.router("a",0,port(0,"10.0.12.1",24,10,active),port(1,"10.0.13.1",24,50,active),port(2,"192.168.1.1",24,1,passive));
        l.router("b",0,port(0,"10.0.12.2",24,10,active),port(1,"10.0.23.2",24,10,active));
        l.router("c",0,port(0,"10.0.13.3",24,50,active),port(1,"10.0.23.3",24,10,active),port(2,"192.168.3.1",24,1,passive));
        l.wire("a",0,"b",0);l.wire("a",1,"c",0);l.wire("b",1,"c",1);l.sync();return l;
    }
    public static void main(String[] args)throws Exception{
        var l=triangle();ok(!l.route("a","192.168.3.0"),"no magic routes before protocol exchange");l.ticks(8);
        ok(l.engine.neighbors("a").stream().anyMatch(n->n.state().equals("INIT")),"first Hello is one-way INIT");
        ok(!l.route("a","192.168.3.0"),"INIT cannot forward OSPF learned routes");l.ticks(80);
        var r=l.engine.routes("a").stream().filter(x->x.network().equals("192.168.3.0")).findFirst().orElseThrow();
        ok(r.gateway().equals("10.0.12.2")&&r.cost()==21,"SPF selects sum of directed interface costs, not hop count");
        ok(l.engine.database("a").size()==3,"LSAs flooded hop by hop");
        ok(l.engine.neighbors("a").stream().allMatch(n->n.state().equals("FULL")),"two-way Hello and DB sync before FULL");
        ok(l.engine.neighbors("a").size()==2,"passive LAN does not form neighbor");
        l.cut("a",0,"b",0);l.ticks(100);
        r=l.engine.routes("a").stream().filter(x->x.network().equals("192.168.3.0")).findFirst().orElseThrow();
        ok(r.gateway().equals("10.0.13.3")&&r.cost()==51,"Dead expiry triggers alternate route");
        ok(l.logs.stream().anyMatch(x->x.contains("Dead")),"Dead transition logged");
        l.wire("a",0,"b",0);l.ticks(120);ok(l.engine.routes("a").stream().filter(x->x.network().equals("192.168.3.0")).findFirst().orElseThrow().cost()==21,"cheaper route returns after re-adjacency");
        var separated=triangle();var b=separated.configs.get("b");separated.configs.put("b",new OspfEngine.Configuration(b.routerId(),1,20,80,b.ports()));separated.sync();separated.ticks(120);
        ok(separated.engine.database("b").size()==1,"area mismatch does not leak LSAs");
        ok(separated.engine.routes("a").stream().filter(x->x.network().equals("192.168.3.0")).findFirst().orElseThrow().cost()==51,"other area is not transit");
        var timers=triangle();var c=timers.configs.get("c");timers.configs.put("c",new OspfEngine.Configuration(c.routerId(),0,40,160,c.ports()));timers.sync();timers.ticks(180);ok(!timers.route("a","192.168.3.0"),"Hello and Dead must match");
        var oneWay=triangle();oneWay.wires.get("b:0").clear();oneWay.cut("a",1,"c",0);oneWay.sync();oneWay.ticks(120);
        ok(!oneWay.route("a","192.168.3.0"),"one-way Hellos must not create a route");
        var duplicate=triangle();b=duplicate.configs.get("b");duplicate.configs.put("b",new OspfEngine.Configuration("1.1.1.1",0,20,80,b.ports()));duplicate.sync();duplicate.ticks(120);ok(!duplicate.engine.error().isEmpty()&&!duplicate.route("a","192.168.3.0"),"duplicate router IDs fail closed");
        duplicate.configs.put("b",b);duplicate.sync();duplicate.ticks(120);ok(duplicate.route("a","192.168.3.0"),"duplicate correction recovers");
        var aging=triangle();aging.ticks(100);aging.configs.remove("c");aging.sync();aging.ticks(100);ok(!aging.route("a","192.168.3.0"),"disconnected old advertisement cannot supply a route");aging.ticks(2500);ok(aging.engine.database("a").stream().noneMatch(x->x.routerId().equals("3.3.3.3")),"MaxAge discards stale advertisement");
        var settings=new OspfSettings();settings.enabled=true;settings.routerId="1.2.3.4";settings.area=4294967295L;settings.modes[0]=OspfSettings.Mode.ACTIVE;settings.costs[0]=123;
        var bytes=new java.io.ByteArrayOutputStream();settings.write(new java.io.DataOutputStream(bytes));var copy=new OspfSettings();copy.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
        ok(copy.enabled&&copy.area==settings.area&&copy.modes[0]==settings.modes[0]&&copy.costs[0]==123,"persistent settings round trip");
        settings.deadTicks=20;boolean rejected=false;try{settings.validate();}catch(IllegalArgumentException expected){rejected=true;}ok(rejected,"invalid timers rejected");
        var limit=triangle();for(int i=0;i<OspfEngine.MAX_ROUTERS;i++)limit.configs.put("extra"+i,new OspfEngine.Configuration("10.255.0."+(i+1),0,20,80,List.of()));limit.sync();ok(!limit.engine.error().isEmpty()&&limit.engine.pendingEvents()==0,"router limit drops no unbounded work");
        l.ticks(5000);ok(l.engine.pendingEvents()<500,"steady state timer queue remains bounded");
        System.out.println("OspfEngineTest: "+checks+" checks passed");
    }
}
