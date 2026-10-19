import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class PortDegradationNetworkTest {
    private static int checks;
    private static void ok(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
    private static NetworkProfile host(String ip){var p=new NetworkProfile(DeviceKind.PC);p.address=ip;return p;}
    private static void cmd(NetworkSimulator sim,String id,String c){var r=NetworkCli.run(sim,id,c);ok(r.changed(),c+": "+r.lines());}
    public static void main(String[] args){
        var sim=new NetworkSimulator();sim.put("a",host("10.1.0.10"));sim.put("b",host("10.1.0.20"));
        var wire=new NetworkSimulator.Link("ab","a",0,"b",0,CableGrade.CAT5E,2,false);sim.link(wire);
        sim.put("x",host("10.2.0.10"));sim.put("y",host("10.2.0.20"));sim.link(new NetworkSimulator.Link("xy","x",0,"y",0,CableGrade.CAT5E,2,false));
        ok(sim.startTransfer("x","10.2.0.20",10_000_000_000L).success(),"independent transfer");
        ok(sim.ping("a","10.1.0.20").bps()==1_000_000_000L,"normal bandwidth");
        cmd(sim,"a","port 0 degrade");
        var r=sim.ping("a","10.1.0.20");ok(r.success()&&r.bps()==100_000_000L,"still reachable at reduced bandwidth");
        ok(sim.nodes().get("a").ports[0].enabled&&sim.nodes().get("a").ports[0].speedLimitBps==0,"admin/auto preserved");
        ok(sim.flows().size()==1,"unrelated physical network unaffected");
        ok(PortFaults.portStatus(sim,"a",0).contains("劣化")&&PortFaults.portStatus(sim,"b",0).contains("対向"),"local and remote diagnostic");
        cmd(sim,"b","port 0 degrade");ok(sim.linkCapacity(wire)==100_000_000L,"both sides derate once");
        cmd(sim,"a","port 0 speed 100m");cmd(sim,"b","port 0 speed 100m");
        ok(sim.ping("a","10.1.0.20").bps()==10_000_000L,"fixed100M remains linked with10M effective");
        cmd(sim,"a","port 0 speed 1g");ok(!sim.ping("a","10.1.0.20").success(),"fixed mismatch still down");
        cmd(sim,"a","port 0 speed auto");cmd(sim,"b","port 0 speed auto");
        sim.nodes().get("a").ports[0].moduleTier=4;
        ok(sim.linkCapacity(wire)==100_000_000L&&sim.nodes().get("a").ports[0].degraded,"module exchange no repair");
        ok(sim.startTransfer("a","10.1.0.20",100_000_000L).success(),"degraded transfer admitted");
        sim.advance(20);
        var f=sim.flows().stream().filter(v->v.source.equals("a")).findFirst().orElseThrow();
        ok(f.remaining>87_000_000&&f.remaining<89_000_000,"virtual bytes advance at100M rather than1G");
        int active=sim.flows().size(),logs=sim.logs("a").size();
        ok(!NetworkCli.run(sim,"a","port 0 degrade").changed()&&sim.flows().size()==active&&sim.logs("a").size()==logs,"same degradation does not reset transfer/log");
        ok(!NetworkCli.run(sim,"a","port 6 degrade").changed()&&sim.flows().size()==active,"invalid command preserves flows");
        var snapshot=NetworkForm.encode(NetworkForm.capture(sim.nodes().get("a"),0));var values=NetworkForm.capture(sim.nodes().get("a"),0).values();
        ok(NetworkForm.apply(sim,"a",snapshot,NetworkForm.encode(values)).accepted()&&sim.nodes().get("a").ports[0].degraded,"form cannot clear hardware state");
        ok(sim.logs("a").stream().anyMatch(s->s.contains("PORT DEGRADED P0")),"event logged once");
        PortFaults.repairAll(sim.nodes().get("a"));PortFaults.repairAll(sim.nodes().get("b"));sim.invalidate("a");
        ok(sim.ping("a","10.1.0.20").bps()==1_000_000_000L,"repair restores throughput");
        var wan=new NetworkSimulator();var left=host("10.3.0.1");var right=host("10.3.0.2");
        left.ports[0].optical=right.ports[0].optical=true;left.ports[0].moduleTier=right.ports[0].moduleTier=1;
        wan.put("left",left);wan.put("right",right);var fiber=new NetworkSimulator.Link("wan","left",0,"right",0,CableGrade.OM3,2,true);wan.link(fiber);
        cmd(wan,"left","port 0 degrade");ok(wan.linkCapacity(fiber)==20_000_000L,"already-slower WAN not derated again");
        wan.nodes().get("left").wanTier=wan.nodes().get("right").wanTier=3;
        ok(wan.linkCapacity(fiber)==1_000_000_000L,"10G fiber degraded to1G once WAN bottleneck upgraded");
        System.out.println("PortDegradationNetworkTest: "+checks+" checks passed");
    }
}
