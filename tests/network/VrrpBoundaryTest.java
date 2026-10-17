import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** VLAN、片方向広告、既存NAPT、曖昧なポート、速度モジュールとの境界。 */
public final class VrrpBoundaryTest {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        var f=new VrrpIntegrationTest.Fixture();
        f.r("lan").ports[2].vlan=20;f.r("lan").ports[3].vlan=20;
        var pc2=new NetworkProfile(DeviceKind.PC);pc2.address="10.10.1.11";pc2.gateway="10.10.1.254";
        f.sim.put("pc2",pc2);f.wire("pc2","pc2",0,"lan",3);f.sim.invalidate();f.ticks(150);
        check(f.sim.vrrpMaster("r1")&&f.sim.vrrpMaster("r2"),"isolated VLANs have independent masters");
        check(f.vip().destination().equals("r1"),"VLAN1 VIP owner");check(f.sim.ping("pc2","10.10.1.254").destination().equals("r2"),"VLAN20 VIP owner");
        check(f.sim.vrrp().status("r1").received()==0&&f.sim.vrrp().status("r2").received()==0,"advertisement never uses routed transit path");
        f=new VrrpIntegrationTest.Fixture();check(f.cmd("r2","acl add 0 in 10 deny vrrp any any"),"one-way advert drop");check(f.cmd("r2","acl add 0 in 20 permit ip any any"),"data allowed");f.ticks(150);
        check(f.sim.vrrpMaster("r1")&&f.sim.vrrpMaster("r2"),"one-way loss can cause two masters");check(!f.vip().success(),"one-way loss is not hidden");
        check(f.sim.ping("pc","10.10.1.2").success(),"standalone physical IP still works");
        f=new VrrpIntegrationTest.Fixture();check(f.cmd("r1","vrrp interval 2"),"configure slow adverts");f.ticks(250);
        check(f.vip().destination().equals("r1"),"high priority preempts even if advertising slower");f.fault("r1",true);f.ticks(90);
        check(!f.vip().success(),"backup learned Master's 2 second interval");f.ticks(100);check(f.vip().destination().equals("r2"),"timeout based on received interval");
        f=new VrrpIntegrationTest.Fixture();
        for(String id:List.of("r1","r2")){check(f.cmd(id,"nat port 0 inside"),"inside");check(f.cmd(id,"nat port 1 outside"),"outside");check(f.cmd(id,"nat on"),"NAT on");}
        f.r("server").gateway="";f.sim.invalidate();f.ticks(150);
        check(f.sim.startTransfer("pc","10.10.2.10",1_000_000_000).success(),"NAPT through virtual gateway");check(!f.sim.nat().sessions("r1").isEmpty(),"old master has NAT state");
        f.fault("r1",true);f.ticks(100);check(f.sim.flows().isEmpty(),"existing transfers interrupted, not silently migrated");
        check(f.sim.startTransfer("pc","10.10.2.10",1_000_000_000).success(),"new NAPT connection works on standby");check(!f.sim.nat().sessions("r2").isEmpty()&&f.sim.nat().sessions("r1").isEmpty(),"NAT state is recreated, not shared");
        f=new VrrpIntegrationTest.Fixture();f.r("r1").ports[2].address="10.10.1.3";f.sim.invalidate();f.ticks(150);
        check(!f.sim.vrrpMaster("r1")&&f.vip().destination().equals("r2"),"ambiguous automatic port remains inactive");check(f.cmd("r1","vrrp port 0"),"resolve explicit port");f.ticks(150);check(f.vip().destination().equals("r1"),"explicit port makes high priority operational");
        f.r("r1").ports[0].moduleTier=1;f.r("r1").ports[0].speedLimitBps=10_000_000_000L;f.sim.invalidate();f.ticks(100);
        check(!f.sim.vrrpMaster("r1"),"unsupported link speed cannot own VIP");
        f.r("r1").ports[0].speedLimitBps=0;f.sim.invalidate();f.ticks(150);check(f.vip().destination().equals("r1"),"link recovery triggers normal preemption");
        System.out.println("VrrpBoundaryTest: "+checks+" checks passed");
    }
}
