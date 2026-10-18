import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** 既存公開CLIだけで新機能の未実装を検出する。 */
public final class DnsContractTest {
    private static int checks,failures;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static void test(String name,Runnable task){try{task.run();System.out.println("PASS "+name);}catch(Throwable e){failures++;System.out.println("FAIL "+name+": "+e);}}
    static NetworkProfile host(DeviceKind k,String ip){var p=new NetworkProfile(k);p.address=ip;return p;}
    static final class Fixture {
        final NetworkSimulator sim=new NetworkSimulator();
        Fixture(){var c=host(DeviceKind.PC,"10.1.0.10");c.dns="10.1.0.53";sim.put("c",c);
            sim.put("d",host(DeviceKind.SERVER,"10.1.0.53"));var web=host(DeviceKind.SERVER,"10.1.0.20");web.web=true;sim.put("w",web);sim.put("s",new NetworkProfile(DeviceKind.L2_SWITCH));
            sim.link(new NetworkSimulator.Link("cs","c",0,"s",0,CableGrade.CAT5E,2,false));sim.link(new NetworkSimulator.Link("ds","d",0,"s",1,CableGrade.CAT5E,2,false));sim.link(new NetworkSimulator.Link("ws","w",0,"s",2,CableGrade.CAT5E,2,false));}
        NetworkCli.Reply cli(String id,String text){return NetworkCli.run(sim,id,text);}
        void set(String text){var r=cli("d",text);check(r.changed(),"command succeeds: "+text+" "+r.lines());}
    }
    public static void main(String[] args){
        test("legacy numeric DNS still works",()->{var f=new Fixture();f.set("dns-record base.local 10.1.0.20");check(f.sim.resolve("c","base.local").success(),"legacy lookup");});
        test("application uses hostname",()->{var f=new Fixture();f.set("dns-record base.local 10.1.0.20");check(f.sim.ping("c","BASE.local.").success(),"hostname ping");check(f.sim.startTransfer("c","base.local:5000",1_000_000).success(),"named transfer");});
        test("round-robin and TTL command",()->{var f=new Fixture();f.set("dns-record set pool.local 0 10.1.0.20 10.1.0.21");var a=f.sim.resolve("c","pool.local");var b=f.sim.resolve("c","pool.local");check(a.success()&&b.success()&&!a.destination().equals(b.destination()),"answer order rotates");});
        test("cached answer survives authority change until TTL",()->{var f=new Fixture();f.set("dns-record set base.local 1 10.1.0.20");check(f.sim.ping("c","base.local").success(),"prime cache");f.set("dns-record set base.local 1 10.1.0.99");check(f.sim.ping("c","base.local").success(),"old cached address");f.sim.advance(20);check(!f.sim.ping("c","base.local").success(),"expiry uses new address");});
        test("authoritative server can stop",()->{var f=new Fixture();f.set("dns-record base.local 10.1.0.20");f.set("dns-server off");check(!f.sim.resolve("c","base.local").success(),"DNS stopped");});
        test("cache diagnostic exists",()->{var f=new Fixture();var r=f.cli("c","dns-cache");check(!r.changed()&&!String.join(" ",r.lines()).contains("不明"),"cache inspection");});
        System.out.println("DnsContractTest: "+checks+" checks / "+failures+" failed cases");if(failures>0)throw new AssertionError("DNS contract has "+failures+" failed cases");
    }
}
