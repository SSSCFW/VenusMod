import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class NetworkCoreTest {
    private static int assertions;
    private static void check(boolean v,String message){assertions++;if(!v)throw new AssertionError(message);}
    private static NetworkProfile host(String name,String ip){var p=new NetworkProfile(DeviceKind.PC);p.name=name;p.address=ip;p.prefix=24;return p;}
    public static void main(String[] args){
        check(Ipv4.parse("192.168.1.1")==0xc0a80101L,"parse unsigned IPv4");
        for(String bad:List.of("localhost","1.2.3.256","1.2.3","-1.2.3.4","1.2.3.4/24","1.2.3.4 ")){boolean rejected=false;try{Ipv4.parse(bad);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"reject "+bad);}
        check(Ipv4.sameSubnet("192.168.1.3","192.168.1.255",24),"subnet");
        check(!Ipv4.sameSubnet("192.168.1.3","192.168.2.3",24),"subnet separation");
        check(CableGrade.VENUS.bps()==400_000_000_000L,"400 Gbps long not int");
        check(CableGrade.CAT5E.maxLength()==64,"Cat5e length");
        var sim=new NetworkSimulator();var a=host("A","192.168.1.10");var b=host("B","192.168.1.20");var sw=new NetworkProfile(DeviceKind.L2_SWITCH);
        sim.put("a",a);sim.put("b",b);sim.put("s",sw);
        sim.link(new NetworkSimulator.Link("as","a",0,"s",0,CableGrade.CAT5E,4,false));
        sim.link(new NetworkSimulator.Link("bs","b",0,"s",1,CableGrade.CAT6,4,false));
        check(sim.ping("a",b.address).success(),"ping through learned switch");
        check(sim.ping("a",b.address).bps()==1_000_000_000L,"bottleneck");
        check(!sim.macTable("s").isEmpty(),"real MAC learning");check(!sim.arpTable("a").isEmpty(),"ARP learned");
        sw.ports[1].vlan=20;sim.invalidate();check(!sim.ping("a",b.address).success(),"VLAN separation");sw.ports[1].vlan=1;sim.invalidate();
        b.failed=true;check(!sim.ping("a",b.address).success(),"failed host unreachable");b.failed=false;
        b.ports[0].enabled=false;check(!sim.ping("a",b.address).success(),"disabled port");b.ports[0].enabled=true;
        var route=new NetworkProfile(DeviceKind.ROUTER);route.ports[0].address="192.168.1.1";route.ports[1].address="192.168.2.1";
        var c=host("C","192.168.2.20");c.gateway="192.168.2.1";a.gateway="192.168.1.1";
        sim.put("r",route);sim.put("c",c);sim.link(new NetworkSimulator.Link("sr","s",2,"r",0,CableGrade.CAT5E,3,false));sim.link(new NetworkSimulator.Link("rc","r",1,"c",0,CableGrade.CAT5E,4,false));
        check(sim.ping("a",c.address).success(),"connected-subnet routing with return path");c.gateway="";check(!sim.ping("a",c.address).success(),"ping requires reverse path");c.gateway="192.168.2.1";
        var d=new NetworkProfile(DeviceKind.PC);sim.put("d",d);sim.link(new NetworkSimulator.Link("ds","d",0,"s",3,CableGrade.CAT5E,3,false));route.dhcp=true;
        check(sim.dhcp("d").success(),"DHCP allocates address across switch");check(!d.address.isBlank(),"DHCP updates live profile");check(sim.ping("d",a.address).success(),"DHCP client can ping");
        check(Math.abs(NetworkProfile.failureProbability(0.0005,24000,0)-0.0005)<1e-15,"daily probability");check(NetworkProfile.failureProbability(0.0005,24000,3)==0.0,"perfect stabilization always zero");
        a.ports[0].speedLimitBps=100_000_000;sim.invalidate();check(sim.ping("a",b.address).bps()==100_000_000,"configured speed cap");
        var flow=sim.startTransfer("a",b.address,100_000_000);check(flow.success(),"start simulated transfer");sim.advance(20);check(!sim.flows().isEmpty(),"large transfer not instant");sim.advance(400);check(sim.flows().isEmpty(),"transfer completes");
        String serial=a.encode();NetworkProfile restored=NetworkProfile.decode(DeviceKind.PC,serial);check(restored.address.equals(a.address)&&restored.ports[0].speedLimitBps==a.ports[0].speedLimitBps,"configuration round trip");
        boolean rejected=false;try{NetworkProfile.decode(DeviceKind.PC,"x".repeat(70000));}catch(IllegalArgumentException e){rejected=true;}check(rejected,"bounded persistent config");
        sim.unlink("as");check(!sim.ping("a",b.address).success(),"broken cable invalidates graph");
        var wan=new NetworkSimulator();var wa=host("WAN-A","10.0.0.1");var wb=host("WAN-B","10.0.0.2");
        for(var h:List.of(wa,wb)){h.ports[0].optical=true;h.ports[0].moduleTier=4;h.processor=7;}
        wan.put("wa",wa);wan.put("wb",wb);wan.link(new NetworkSimulator.Link("wan","wa",0,"wb",0,CableGrade.VENUS,10,true));
        check(wan.ping("wa",wb.address).bps()==20_000_000,"initial WAN cap");
        wa.wanTier=5;wb.wanTier=5;check(wan.ping("wa",wb.address).bps()==400_000_000_000L,"ultimate WAN bandwidth");
        for(int i=0;i<4;i++)check(wan.startTransfer("wa",wb.address,1_000_000_000).success(),"base transfer buffer slot");
        check(!wan.startTransfer("wa",wb.address,1_000_000_000).success(),"bounded endpoint transfer buffer");
        wa.buffer=1;check(wan.startTransfer("wa",wb.address,1_000_000_000).success(),"buffer module expands pending transfer capacity");
        var cycle=new NetworkSimulator();
        for(String id:List.of("s1","s2","s3"))cycle.put(id,new NetworkProfile(DeviceKind.L2_SWITCH));
        cycle.put("x",host("x","10.1.0.10"));cycle.put("y",host("y","10.1.0.20"));
        cycle.link(new NetworkSimulator.Link("12","s1",0,"s2",0,CableGrade.CAT5E,1,false));
        cycle.link(new NetworkSimulator.Link("13","s1",1,"s3",0,CableGrade.CAT5E,1,false));
        cycle.link(new NetworkSimulator.Link("23","s2",1,"s3",1,CableGrade.CAT5E,1,false));
        cycle.link(new NetworkSimulator.Link("x1","x",0,"s1",2,CableGrade.CAT5E,1,false));
        cycle.link(new NetworkSimulator.Link("y3","y",0,"s3",2,CableGrade.CAT5E,1,false));
        check(cycle.blockedLinks().size()==1,"loop has one blocked link");
        check(cycle.ping("x","10.1.0.20").success(),"redundant topology forwards");
        cycle.unlink("13");check(cycle.blockedLinks().isEmpty()&&cycle.ping("x","10.1.0.20").success(),"alternate path opens after link cut");
        var p1=new NetworkProfile(DeviceKind.ROUTER);var p2=new NetworkProfile(DeviceKind.ROUTER);
        p1.ports[0].address="10.1.0.1";p2.ports[0].address="10.1.0.2";p1.vip=p2.vip="10.1.0.254";p1.vrrpPriority=120;
        cycle.put("r1",p1);cycle.put("r2",p2);
        cycle.link(new NetworkSimulator.Link("r11","r1",0,"s1",3,CableGrade.CAT5E,1,false));
        cycle.link(new NetworkSimulator.Link("r22","r2",0,"s2",2,CableGrade.CAT5E,1,false));
        check(!cycle.ping("x",p1.vip).success(),"VRRP election is not instantaneous");
        for(int tick=0;tick<120;tick++)cycle.advance(1);
        check(cycle.ping("x",p1.vip).destination().equals("r1"),"virtual gateway priority after advertisements");
        p1.failed=true;cycle.invalidate();check(!cycle.ping("x",p2.vip).success(),"VRRP backup waits for missing advertisements");
        for(int tick=0;tick<100;tick++)cycle.advance(1);
        check(cycle.ping("x",p2.vip).destination().equals("r2"),"virtual gateway failover after timeout");
        var invalid=NetworkCli.run(cycle,"x","ip invalid");check(!invalid.changed()&&cycle.nodes().get("x").address.equals("10.1.0.10"),"invalid command preserves configuration");
        check(NetworkCli.run(cycle,"x","ipv6").lines().getFirst().contains("未実装"),"unsupported protocol explicitly rejected");
        System.out.println("NetworkCoreTest: "+assertions+" assertions passed");
    }
}
