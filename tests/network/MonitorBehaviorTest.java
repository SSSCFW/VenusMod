import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class MonitorBehaviorTest {
    private static int checks;
    private static void ok(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    private static void config(NetworkSimulator s,String id,String text){var r=NetworkServiceCommands.run(s,id,text);ok(r.handled()&&r.changed(),text+" "+r.lines());}
    private static void ticks(NetworkSimulator s,int ticks){for(int i=0;i<ticks;i++)s.advance(1);}
    private static MonitorRuntime.Snapshot state(NetworkSimulator s){return s.monitor().snapshots("m").getFirst();}
    public static void main(String[] args){
        var s=new NetworkSimulator();var m=new NetworkProfile(DeviceKind.SERVER);m.address="10.0.1.10";m.gateway="10.0.1.1";
        var r=new NetworkProfile(DeviceKind.ROUTER);r.dhcp=false;r.ports[0].address="10.0.1.1";r.ports[1].address="10.0.2.1";
        var p=new NetworkProfile(DeviceKind.SERVER);p.address="10.0.2.20";s.put("m",m);s.put("r",r);s.put("p",p);
        s.link(new NetworkSimulator.Link("mr","m",0,"r",0,CableGrade.CAT5E,2,false));s.link(new NetworkSimulator.Link("rp","r",1,"p",0,CableGrade.CAT5E,2,false));
        config(s,"m","monitor add host 10.0.2.20");config(s,"m","monitor interval 1");config(s,"m","monitor thresholds 2 2");config(s,"m","monitor on");ticks(s,40);
        ok(state(s).state()==MonitorRuntime.State.DOWN,"往路があっても返信経路なしはDOWN");
        p.gateway="10.0.2.1";s.invalidate("p");ticks(s,40);ok(state(s).state()==MonitorRuntime.State.UP,"返信経路修正後にUP");
        config(s,"r","acl add 0 in 10 deny icmp any any");config(s,"r","acl add 0 in 20 permit ip any any");ticks(s,40);ok(state(s).state()==MonitorRuntime.State.DOWN,"ICMP ACLを迂回しない");
        ok(state(s).detail().contains("ACL"),"診断に失敗理由を保持");
        config(s,"r","acl clear 0 in");ticks(s,40);ok(state(s).state()==MonitorRuntime.State.UP,"ACL修復を検知");
        config(s,"p","syslog server on");config(s,"m","syslog target 10.0.2.20");config(s,"m","syslog on");
        p.failed=true;s.invalidate("p");ticks(s,40);ok(state(s).state()==MonitorRuntime.State.DOWN,"故障対象に失敗");
        p.failed=false;s.invalidate("p");ticks(s,40);ok(s.syslog().entries("p").stream().anyMatch(e->e.message().contains("MONITOR UP")&&e.severity()==5),"復旧通知を実経路でSyslogへ配送");
        ok(s.startTransfer("m",p.address,1_000_000_000_000L).success(),"長い転送を開始");
        config(s,"m","monitor interval 2");ok(s.flows().size()==1,"監視設定だけで転送を切らない");
        ticks(s,80);long probes=state(s).probes();config(s,"m","monitor interval 2");ok(state(s).probes()==probes,"同じ設定の再適用では状態を消さない");
        String encoded=s.nodes().get("m").encode();
        for(String bad:List.of("monitor add","monitor add x localhost","monitor add x 224.0.0.1","monitor add x 0.0.0.0","monitor add host 10.0.2.21","monitor interval 0","monitor interval 3601","monitor thresholds 0 2","monitor thresholds 2 11","monitor delete unknown","monitor on extra")){
            var reply=NetworkServiceCommands.run(s,"m",bad);ok(reply.handled()&&!reply.changed()&&encoded.equals(s.nodes().get("m").encode()),"不正入力を原子的に拒否: "+bad);
        }
        ok(s.flows().size()==1,"不正設定も転送を維持");
        var other=new NetworkProfile(DeviceKind.PC);s.put("separate",other);s.remove("separate");ok(state(s).probes()==probes,"別物理網の追加削除で状態を消さない");
        var saved=NetworkProfile.decode(DeviceKind.SERVER,encoded);s.remove("m");s.put("restored",saved);ok(s.monitor().snapshots("restored").getFirst().state()==MonitorRuntime.State.UNKNOWN,"回収後はUNKNOWN");
        ok(s.monitor().snapshots("m").isEmpty(),"撤去済みの期限と状態を削除");
        System.out.println("MonitorBehaviorTest: "+checks+" checks passed");
    }
}
