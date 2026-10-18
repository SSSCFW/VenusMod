import dev.ssscfw.venusmod.infrastructure.network.core.*;
import dev.ssscfw.venusmod.infrastructure.network.core.NetworkPolicy.Prefix;
import java.util.*;

public final class BgpLimitsTest {
    static int checks;
    static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        var wire=new BgpEngineTest.Wire();wire.addresses.put("a","192.0.2.1");wire.addresses.put("b","192.0.2.2");wire.addresses.put("c","192.0.2.3");
        var engine=new BgpEngine(wire);var configs=new LinkedHashMap<String,BgpEngine.Configuration>();
        configs.put("a",BgpEngineTest.cfg(65001,"1.1.1.1",List.of(BgpEngineTest.peer("192.0.2.2",65002,0,-1,-1)),true));
        configs.put("b",BgpEngineTest.cfg(65002,"2.2.2.2",List.of(BgpEngineTest.peer("192.0.2.1",65001,0,-1,-1),BgpEngineTest.peer("192.0.2.3",65001,0,-1,-1)),false));
        configs.put("c",BgpEngineTest.cfg(65001,"3.3.3.3",List.of(BgpEngineTest.peer("192.0.2.2",65002,0,-1,-1)),false));
        engine.synchronize(configs);BgpEngineTest.ticks(engine,180);
        ok(BgpEngineTest.learned(engine,"b")!=null,"AS65002にAS65001の経路が届く");
        ok(BgpEngineTest.learned(engine,"c")==null,"AS_PATH中にある同じASへ広告しない");
        boolean refused=false;try{new BgpEngine.Advertisement(Prefix.parse("10.0.0.0/24"),"192.0.2.1",Collections.nCopies(33,65001L),100,0);}catch(IllegalArgumentException e){refused=true;}ok(refused,"AS_PATH長32上限");
        refused=false;try{new BgpEngine.Advertisement(Prefix.parse("10.0.0.0/24"),"224.0.0.1",List.of(65001L),100,0);}catch(IllegalArgumentException e){refused=true;}ok(refused,"マルチキャストNEXT_HOP拒否");
        var tooMany=new LinkedHashMap<String,BgpEngine.Configuration>();for(int i=0;i<65;i++)tooMany.put("n"+i,BgpEngineTest.cfg(65001,"1.1.1.1",List.of(),false));
        engine.synchronize(tooMany);ok(!engine.error().isEmpty()&&engine.queued()==0&&engine.timers()==0,"機器上限で停止・待ち行列消去");
        engine.synchronize(configs);BgpEngineTest.ticks(engine,180);ok(engine.error().isEmpty()&&BgpEngineTest.learned(engine,"b")!=null,"構成を減らして復旧");
        // 受信128+自機生成1は機器の選択prefix上限を超える。
        Set<Prefix> networksB=new LinkedHashSet<>(),networksC=new LinkedHashSet<>();for(int i=0;i<64;i++){networksB.add(Prefix.parse("10.20."+i+".0/24"));networksC.add(Prefix.parse("10.21."+i+".0/24"));}
        configs.clear();configs.put("a",BgpEngineTest.cfg(65001,"1.1.1.1",List.of(BgpEngineTest.peer("192.0.2.2",65002,0,-1,-1),BgpEngineTest.peer("192.0.2.3",65003,0,-1,-1)),true));
        configs.put("b",new BgpEngine.Configuration(65002,"2.2.2.2",20,80,List.of(BgpEngineTest.peer("192.0.2.1",65001,0,-1,-1)),networksB));
        configs.put("c",new BgpEngine.Configuration(65003,"3.3.3.3",20,80,List.of(BgpEngineTest.peer("192.0.2.1",65001,0,-1,-1)),networksC));
        engine.synchronize(configs);BgpEngineTest.ticks(engine,180);
        ok(engine.error().contains("prefix上限"),"prefix上限超過を明示");ok(engine.routes("a").isEmpty()&&engine.queued()==0,"一部経路だけを成功扱いせず停止");
        System.out.println("BgpLimitsTest: "+checks+" checks passed");
    }
}
