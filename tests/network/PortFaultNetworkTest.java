import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class PortFaultNetworkTest {
    private static int checks;
    private static void ok(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
    private static NetworkProfile host(String ip){var p=new NetworkProfile(DeviceKind.PC);p.address=ip;return p;}
    private static void link(NetworkSimulator sim,String name,String a,int ap,String b,int bp){sim.link(new NetworkSimulator.Link(name,a,ap,b,bp,CableGrade.CAT5E,2,false));}
    private static void command(NetworkSimulator sim,String id,String text){var r=NetworkCli.run(sim,id,text);ok(r.changed(),text+" / "+r.lines());}
    public static void main(String[] args){
        var sim=new NetworkSimulator();sim.put("a",host("10.1.0.10"));sim.put("b",host("10.1.0.20"));sim.put("c",host("10.1.0.30"));sim.put("sw",new NetworkProfile(DeviceKind.L2_SWITCH));
        link(sim,"a-sw","a",0,"sw",0);link(sim,"b-sw","b",0,"sw",1);link(sim,"c-sw","c",0,"sw",2);
        sim.put("x",host("10.2.0.10"));sim.put("y",host("10.2.0.20"));link(sim,"xy","x",0,"y",0);
        ok(sim.ping("a","10.1.0.20").success(),"initial communication");ok(sim.startTransfer("x","10.2.0.20",1_000_000_000L).success(),"independent flow started");
        command(sim,"sw","port 0 fault");
        ok(!sim.ping("a","10.1.0.20").success(),"faulted ingress cannot forward");
        ok(sim.ping("b","10.1.0.30").success(),"healthy pair remains usable");
        ok(!sim.nodes().get("sw").failed,"switch itself is alive");ok(sim.flows().size()==1,"independent network flow preserved");
        ok(PortFaults.portStatus(sim,"sw",0).contains("ポート故障")&&PortFaults.portStatus(sim,"a",0).contains("対向"),"both endpoint diagnostics show fault");
        command(sim,"sw","port 0 enable");ok(!sim.ping("a","10.1.0.20").success(),"enable cannot repair");
        var p=sim.nodes().get("sw");p.ports[0].moduleTier=4;sim.put("sw",p);
        ok(!sim.ping("a","10.1.0.20").success(),"module upgrade cannot repair");
        var base=NetworkForm.encode(NetworkForm.capture(p,0));var v=NetworkForm.capture(p,0).values();
        var edit=new NetworkForm.Values(0,"",v.address(),v.prefix(),"","",false,v.trunk(),v.optical(),v.vlan(),v.speed(),v.allowedVlans());
        ok(NetworkForm.apply(sim,"sw",base,NetworkForm.encode(edit)).accepted(),"port form accepts administrative edit while failed");
        ok(sim.nodes().get("sw").ports[0].failed,"form cannot clear hardware fault");
        p=sim.nodes().get("sw");PortFaults.repairPort(p,0);sim.put("sw",p);
        ok(!sim.ping("a","10.1.0.20").success(),"repair preserves admin OFF");command(sim,"sw","port 0 enable");
        ok(sim.ping("a","10.1.0.20").success(),"repaired enabled port recovers");
        int count=sim.logs("sw").size();command(sim,"sw","port 1 fault");
        ok(sim.logs("sw").stream().anyMatch(s->s.contains("PORT FAULT")&&s.contains("P1")),"manual port fault is logged");
        ok(!NetworkCli.run(sim,"sw","port 1 fault").changed(),"duplicate fault is no-op");
        ok(!NetworkCli.run(sim,"sw","port 1 repair").changed(),"CLI cannot repair for free");
        ok(!NetworkCli.run(sim,"sw","port 6 fault").changed(),"invalid port rejected");
        // 三角形のsimple STP: 管理状態は変えず1ポート故障で代替経路へ。
        var ring=new NetworkSimulator();for(String s:List.of("s1","s2","s3"))ring.put(s,new NetworkProfile(DeviceKind.L2_SWITCH));
        ring.put("u",host("10.3.0.10"));ring.put("v",host("10.3.0.20"));
        link(ring,"12","s1",0,"s2",0);link(ring,"13","s1",1,"s3",0);link(ring,"23","s2",1,"s3",1);link(ring,"u1","u",0,"s1",2);link(ring,"v3","v",0,"s3",2);
        ok(ring.ping("u","10.3.0.20").success(),"initial redundant path");command(ring,"s1","port 1 fault");
        var via=ring.ping("u","10.3.0.20");ok(via.success()&&via.links().contains("23")&&!via.links().contains("13"),"STP uses alternate route for port fault");
        PortFaults.repairPort(ring.nodes().get("s1"),1);ring.invalidate("s1");
        ok(ring.ping("u","10.3.0.20").success(),"ring recovers after repair");
        System.out.println("PortFaultNetworkTest: "+checks+" checks passed");
    }
}
