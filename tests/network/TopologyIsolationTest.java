import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** 本物のリクエスト/転送を作り、別の物理網の操作がそれを失効させないことを検査。 */
public final class TopologyIsolationTest {
    private static int checks, failures;
    private static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void test(String name,Runnable body){try{body.run();System.out.println("PASS "+name);}catch(Throwable e){failures++;System.out.println("FAIL "+name+": "+e);}}
    static NetworkProfile host(String ip,String gateway){var p=new NetworkProfile(DeviceKind.PC);p.address=ip;p.gateway=gateway;return p;}
    static void wire(NetworkSimulator s,String id,String a,int ap,String b,int bp){s.link(new NetworkSimulator.Link(id,a,ap,b,bp,CableGrade.CAT5E,2,false));}
    static void ticks(NetworkSimulator s,int n){for(int i=0;i<n;i++)s.advance(1);}
    static final class Fixture {
        final NetworkSimulator sim=new NetworkSimulator();
        Fixture(){
            sim.put("pc",host("10.40.0.10","10.40.0.1"));
            var r=new NetworkProfile(DeviceKind.ROUTER);r.ports[0].address="10.40.0.1";r.ports[1].address="203.0.113.1";r.dhcp=false;sim.put("r",r);
            sim.put("server",host("203.0.113.10",""));wire(sim,"inside","pc",0,"r",0);wire(sim,"outside","r",1,"server",0);
            for(String cmd:List.of("nat port 0 inside","nat port 1 outside","nat on"))ok(NetworkServiceCommands.run(sim,"r",cmd).changed(),"NAT設定");
            sim.put("x",host("192.0.2.1",""));sim.put("y",host("192.0.2.2",""));wire(sim,"other","x",0,"y",0);
        }
        NetworkSimulator.Flow transfer(){ok(sim.startTransfer("pc","203.0.113.10",100_000_000_000L).success(),"NAPT転送開始");return sim.flows().getFirst();}
    }
    public static void main(String[] args){
        test("disconnected link edit keeps NAT and active transfer",()->{
            var f=new Fixture();var flow=f.transfer();var entries=f.sim.nat().sessions("r");f.sim.unlink("other");
            ok(f.sim.flows().contains(flow),"別網の断線で転送を消さない");ok(f.sim.nat().sessions("r").equals(entries),"別網の断線でNAT表を消さない");
            double before=flow.remaining;ticks(f.sim,10);ok(flow.remaining<before,"別網の変更中も転送を進める");ok(!f.sim.ping("x","192.0.2.2").success(),"切った側は停止");
            wire(f.sim,"other","x",0,"y",0);ok(f.sim.flows().contains(flow),"別網の再接続でも保持");ok(f.sim.ping("x","192.0.2.2").success(),"再接続側も復旧");
        });
        test("adding unrelated device does not clear NAT",()->{var f=new Fixture();var flow=f.transfer();f.sim.put("new",host("198.51.100.1",""));ok(f.sim.flows().contains(flow)&&f.sim.nat().size()==1,"独立機器の追加");ticks(f.sim,1);ok(f.sim.flows().contains(flow),"期限が次tickでも生きる");});
        test("configuration and manual fault are scoped",()->{var f=new Fixture();var flow=f.transfer();ok(NetworkCli.run(f.sim,"x","fault").changed(),"自分の機器を故障");ok(f.sim.flows().contains(flow)&&f.sim.nat().size()==1,"別網の設定保存で保持");});
        test("affected component is stopped synchronously",()->{var f=new Fixture();f.transfer();f.sim.unlink("inside");ok(f.sim.flows().isEmpty()&&f.sim.nat().size()==0,"断線した連結成分の転送/NATは即時消去");ok(f.sim.ping("x","192.0.2.2").success(),"別網は利用可能");});
        test("removing unrelated node preserves existing state",()->{var f=new Fixture();var flow=f.transfer();f.sim.remove("y");ok(f.sim.flows().contains(flow)&&f.sim.nat().size()==1,"別端点削除で保持");ok(f.sim.logs("pc").stream().noneMatch(s->s.contains("中断")),"誤った中断ログなし");});
        test("joining then splitting components invalidates both sides",()->{var f=new Fixture();f.transfer();wire(f.sim,"join","server",1,"x",1);ok(f.sim.flows().isEmpty()&&f.sim.nat().size()==0,"結合時は両方再確立");f.transfer();f.sim.unlink("join");ok(f.sim.flows().isEmpty(),"分断前の成分も失効");});
        test("VRRP owner changes in separate component keep transfers",()->{
            var f=new Fixture();var flow=f.transfer();var vr=new NetworkProfile(DeviceKind.ROUTER);vr.ports[0].address="192.0.2.254";vr.vip="192.0.2.253";vr.vrrp.enabled=true;vr.vrrp.port=0;f.sim.put("vr",vr);wire(f.sim,"vrl","x",1,"vr",0);
            ticks(f.sim,150);ok(f.sim.vrrpMaster("vr"),"VRRPを実広告で起動");ok(f.sim.flows().contains(flow)&&f.sim.nat().size()==1,"別網のMaster選出で転送/NAT保持");
        });
        test("classic STP state changes in separate component keep transfers",()->{
            var f=new Fixture();var flow=f.transfer();var sw=new NetworkProfile(DeviceKind.L2_SWITCH);sw.spanning.classic=true;sw.spanning.edge[0]=true;f.sim.put("sw",sw);wire(f.sim,"swx","sw",0,"x",1);
            ticks(f.sim,10);ok(f.sim.stp().forwarding(new StpEngine.Key("sw",1),0),"STP edge forwarding");ok(f.sim.flows().contains(flow)&&f.sim.nat().size()==1,"別網のSTP遷移で保持");
        });
        test("unrelated edits keep established OSPF route usable before next tick",()->{
            var s=new NetworkSimulator();var a=OspfIntegrationTest.router("11.1.1.1");var b=OspfIntegrationTest.router("11.2.2.2");
            OspfIntegrationTest.ip(a,0,"10.120.12.1",false,10);OspfIntegrationTest.ip(b,0,"10.120.12.2",false,10);
            OspfIntegrationTest.ip(a,1,"10.120.1.1",true,1);OspfIntegrationTest.ip(b,1,"10.120.2.1",true,1);
            s.put("a",a);s.put("b",b);s.put("left",host("10.120.1.10","10.120.1.1"));s.put("right",host("10.120.2.10","10.120.2.1"));
            wire(s,"ab","a",0,"b",0);wire(s,"al","a",1,"left",0);wire(s,"br","b",1,"right",0);ticks(s,160);
            ok(s.ping("left","10.120.2.10").success(),"OSPF学習済み");var neighbors=s.ospf().neighbors("a");var database=s.ospf().database("a");
            s.put("unrelated",host("198.51.100.1",""));ok(s.ping("left","10.120.2.10").success(),"次tickを待たず学習経路維持");
            ok(s.ospf().neighbors("a").equals(neighbors)&&s.ospf().database("a").equals(database),"ネイバー/LSDB/期限不変");
            ticks(s,10);ok(s.ospf().neighbors("a").stream().allMatch(n->n.state().equals("FULL")),"再同期待ちへ戻さない");
        });
        test("invalid CLI leaves in-flight NAT untouched",()->{var f=new Fixture();var flow=f.transfer();var entries=f.sim.nat().sessions("r");
            ok(!NetworkCli.run(f.sim,"pc","ip invalid").changed(),"不正なIPを拒否");
            ok(f.sim.flows().contains(flow)&&f.sim.nat().sessions("r").equals(entries),"拒否した入力で接続を失効しない");
        });
        System.out.println("TopologyIsolationTest: "+checks+" checks / "+failures+" failed cases");if(failures>0)throw new AssertionError("topology isolation failures");
    }
}
