import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** 仮想時刻の期限・広告の検証。L2/ACLの実経路は別の結合試験で確認。 */
public final class VrrpEngineTest {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static VrrpEngine.Configuration config(String ip,int priority,int interval,boolean preempt){return new VrrpEngine.Configuration(0,ip,24,"10.0.0.254",1,priority,interval,preempt);}
    private static final class Wire implements VrrpEngine.Transport {
        Map<String,List<String>> peers=new HashMap<>();Set<String> changes=new HashSet<>();int logs;
        public List<String> peers(String id){return peers.getOrDefault(id,List.of());}
        public void log(String id,String text){logs++;}
        public void ownershipChanged(String id){changes.add(id);}
    }
    public static void main(String[] args){
        var wire=new Wire();var e=new VrrpEngine(wire);var a=config("10.0.0.1",150,20,true);
        e.synchronize(Map.of("a",a));check(e.status("a").state()==VrrpEngine.State.BACKUP,"starts backup");
        check(VrrpEngine.skewTicks(150,20)==9&&VrrpEngine.downTicks(150,20)==69,"3 advertisements plus rounded skew");
        e.advance(68);check(!e.isMaster("a"),"one tick before deadline");e.advance(1);check(e.isMaster("a"),"deadline reached");
        check(e.status("a").sent()==1&&e.status("a").deadlineInTicks()==20,"initial advertisement");
        e.advance(19);check(e.status("a").sent()==1,"not every tick");e.advance(1);check(e.status("a").sent()==2,"periodic advertisement");
        // より高い優先度でもTTL・VRID・VIP・interval・送信元が不正なら無視。
        for(var bad:List.of(
                new VrrpEngine.Advertisement("b","10.0.0.2","10.0.0.254",1,200,20,254),
                new VrrpEngine.Advertisement("b","10.0.0.2","10.0.0.254",2,200,20,255),
                new VrrpEngine.Advertisement("b","10.0.0.2","10.0.0.253",1,200,20,255),
                new VrrpEngine.Advertisement("b","10.0.0.2","10.0.0.254",1,200,0,255),
                new VrrpEngine.Advertisement("b","10.0.1.2","10.0.0.254",1,200,20,255),
                new VrrpEngine.Advertisement("b","10.0.0.0","10.0.0.254",1,200,20,255),
                new VrrpEngine.Advertisement("b","10.0.0.255","10.0.0.254",1,200,20,255),
                new VrrpEngine.Advertisement("b","224.0.0.1","10.0.0.254",1,200,20,255),
                new VrrpEngine.Advertisement("b","10.0.0.1","10.0.0.254",1,200,20,255))){
            var before=e.status("a");e.receive("a",bad);check(before.equals(e.status("a")),"invalid advertisement has no effect");
        }
        e.receive("a",new VrrpEngine.Advertisement("b","10.0.0.2","10.0.0.254",1,200,40,255));
        check(e.status("a").state()==VrrpEngine.State.BACKUP,"higher priority demotes");
        check(e.status("a").deadlineInTicks()==137,"use received interval not local interval");
        long sent=e.status("a").sent();e.advance(1);
        for(int n=0;n<500;n++)e.receive("a",new VrrpEngine.Advertisement("b","10.0.0.2","10.0.0.254",1,200,40,255));
        check(e.queuedEvents()==1,"repeated advertisements replace one timer, not 500 stale events");
        check(e.status("a").sent()==sent,"backup does not periodically advertise");
        var before=e.status("a");e.synchronize(Map.of("a",a));check(e.status("a").equals(before),"unrelated config refresh preserves deadline and state");
        e.receive("a",new VrrpEngine.Advertisement("b","10.0.0.2","10.0.0.254",1,0,40,255));
        check(e.status("a").deadlineInTicks()==17,"priority zero uses only skew");e.advance(17);check(e.isMaster("a"),"take over after stop");
        e.shutdown("a");check(e.status("a").state()==VrrpEngine.State.INITIALIZE&&e.queuedEvents()==0,"shutdown removes timer");
        Map<String,VrrpEngine.Configuration> many=new LinkedHashMap<>();
        for(int n=1;n<=128;n++)many.put("r"+n,config("10.0.0."+n,100,20,true));
        e.synchronize(many);e.advance(100);check(e.lastProcessed()<=64,"bounded per-tick event work");check(e.queuedEvents()<=128,"one timer per member");
        e.advance(1);check(e.lastProcessed()<=64,"backlog stays budgeted");
        many.put("overflow",config("10.0.0.200",100,20,true));e.synchronize(many);
        check(!e.error().isBlank()&&e.queuedEvents()==0&&!e.isMaster("r1"),"router ceiling fails closed");
        e.synchronize(Map.of("a",a));e.advance(100);check(e.isMaster("a")&&e.error().isBlank(),"corrected config can restart");
        wire.peers.put("a",java.util.stream.IntStream.range(0,65).mapToObj(n->"p"+n).toList());e.advance(20);
        check(!e.error().isBlank()&&e.queuedEvents()==0,"peer ceiling stops without unbounded fanout");
        boolean rejected=false;try{config("224.0.0.1",100,20,true);}catch(IllegalArgumentException bad){rejected=true;}check(rejected,"configuration rejects multicast primary");
        System.out.println("VrrpEngineTest: "+checks+" checks passed (500 receive operations for bounded timer test)");
    }
}
