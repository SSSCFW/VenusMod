import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** VRRPはpingした瞬間の全体選出ではなく、通常tickと広告で遷移する。 */
public final class VrrpIntegrationTest {
    private static int checks,failed;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void test(String name,Runnable body){try{body.run();System.out.println("PASS "+name);}catch(Throwable e){failed++;System.out.println("FAIL "+name+": "+e.getMessage());}}
    static class Fixture {
        final NetworkSimulator sim=new NetworkSimulator();
        Fixture(){
            var pc=new NetworkProfile(DeviceKind.PC);pc.address="10.10.1.10";pc.gateway="10.10.1.254";sim.put("pc",pc);
            sim.put("lan",new NetworkProfile(DeviceKind.L2_SWITCH));sim.put("transit",new NetworkProfile(DeviceKind.L2_SWITCH));
            for(int n=1;n<=2;n++){var p=new NetworkProfile(DeviceKind.ROUTER);p.dhcp=false;p.ports[0].address="10.10.1."+n;p.ports[1].address="10.10.2."+n;p.vip="10.10.1.254";p.vrrpPriority=n==1?150:100;sim.put("r"+n,p);}
            var server=new NetworkProfile(DeviceKind.SERVER);server.address="10.10.2.10";server.gateway="10.10.2.2";sim.put("server",server);
            wire("pc","pc",0,"lan",0);wire("a","r1",0,"lan",1);wire("b","r2",0,"lan",2);
            wire("at","r1",1,"transit",0);wire("bt","r2",1,"transit",1);wire("st","server",0,"transit",2);
        }
        void wire(String name,String a,int ap,String b,int bp){sim.link(new NetworkSimulator.Link(name,a,ap,b,bp,CableGrade.CAT5E,2,false));}
        void ticks(int n){for(int i=0;i<n;i++)sim.advance(1);}
        NetworkProfile r(String id){return sim.nodes().get(id);}
        void fault(String id,boolean value){r(id).failed=value;sim.invalidate();}
        boolean cmd(String id,String text){return NetworkServiceCommands.run(sim,id,text).changed();}
        String status(String id){return String.join(" ",NetworkServiceCommands.run(sim,id,"vrrp status").lines());}
        NetworkSimulator.Result vip(){return sim.ping("pc","10.10.1.254");}
    }
    public static void main(String[] args){
        test("no VIP owner before election deadline",()->{var f=new Fixture();check(!f.vip().success(),"cold-start VIP must not respond immediately");f.ticks(30);check(!f.vip().success(),"wait for 3 adverts plus skew");f.ticks(90);check(f.vip().destination().equals("r1"),"highest priority becomes Master");});
        test("hardware fault has a detection interval",()->{var f=new Fixture();f.ticks(120);f.fault("r1",true);check(!f.vip().success(),"backup cannot know fault before missing adverts");f.ticks(10);check(!f.vip().success(),"no instantaneous failover");f.ticks(90);check(f.vip().destination().equals("r2"),"backup takes over");check(f.sim.ping("pc","10.10.2.10").success(),"data routes through standby with return route");});
        test("advertisements drive real state and stable virtual MAC",()->{var f=new Fixture();f.ticks(120);check(f.status("r1").contains("MASTER"),"read master state");check(f.status("r2").contains("BACKUP"),"read backup state");check(f.vip().success(),"ARP resolved");String mac=f.sim.arpTable("pc").get("10.10.1.254");check(mac.equals("00:00:5e:00:01:01"),"virtual MAC not device MAC");f.fault("r1",true);f.ticks(100);check(f.vip().success(),"VIP after takeover");check(f.sim.arpTable("pc").get("10.10.1.254").equals(mac),"same MAC after takeover");});
        test("preempt off retains current master",()->{var f=new Fixture();check(f.cmd("r1","vrrp preempt off"),"configure no preempt");f.ticks(120);f.fault("r1",true);f.ticks(100);f.fault("r1",false);f.ticks(150);check(f.vip().destination().equals("r2"),"recovered high priority does not preempt");check(f.cmd("r1","vrrp preempt on"),"enable preempt");f.ticks(150);check(f.vip().destination().equals("r1"),"preemption after down timer");});
        test("graceful shutdown sends priority zero",()->{var f=new Fixture();f.ticks(120);check(f.cmd("r1","vrrp off"),"off command");check(!f.vip().success(),"not instantaneous");f.ticks(20);check(f.vip().destination().equals("r2"),"priority0 bypasses three advertisements");check(f.cmd("r1","vrrp on"),"on retains configured VIP");f.ticks(150);check(f.vip().destination().equals("r1"),"restored config works");});
        test("blocked adverts create visible split-brain",()->{var f=new Fixture();for(String id:List.of("r1","r2")){check(f.cmd(id,"acl add 0 in 10 deny vrrp any any"),"VRRP ACL accepted");check(f.cmd(id,"acl add 0 in 20 permit ip any any"),"other traffic allowed");}f.ticks(150);check(f.status("r1").contains("MASTER")&&f.status("r2").contains("MASTER"),"both timers expire on isolated adverts");check(!f.vip().success()&&f.vip().message().contains("競合"),"do not hide duplicate masters with global sorting");for(String id:List.of("r1","r2"))check(f.cmd(id,"acl clear 0 in"),"clear filter");f.ticks(100);check(f.vip().destination().equals("r1"),"adverts resolve split brain");});
        test("equal priority uses higher primary IPv4",()->{var f=new Fixture();f.r("r2").vrrpPriority=150;f.sim.invalidate();f.ticks(150);check(f.vip().destination().equals("r2"),"tie uses IPv4 not UUID order");});
        test("different VRID is not a redundancy group",()->{var f=new Fixture();check(f.cmd("r1","vrrp group 12"),"group12");check(f.cmd("r2","vrrp group 13"),"group13");f.ticks(150);check(!f.vip().success(),"same VIP in different groups is duplicate not shared");});
        test("reading status cannot delay failover",()->{var f=new Fixture();f.ticks(120);f.fault("r1",true);for(int i=0;i<100;i++){f.status("r2");f.ticks(1);}check(f.vip().destination().equals("r2"),"queries preserve timer");});
        test("validation keeps existing config",()->{var f=new Fixture();String before=f.r("r1").encode();for(String command:List.of("vrrp group 0","vrrp group 256","vrrp port 6","vrrp interval 0","vrrp interval 11","vrrp preempt maybe","vrrp 224.0.0.1 100","vrrp 10.10.1.1 255")){check(!f.cmd("r1",command),"invalid: "+command);check(before.equals(f.r("r1").encode()),"invalid update atomic");}check(!f.cmd("pc","vrrp 10.10.1.254 100"),"PC cannot run VRRP");});
        System.out.println("VrrpIntegrationTest: "+checks+" checks / "+failed+" failed cases");if(failed>0)throw new AssertionError(failed+" failed cases");
    }
}
