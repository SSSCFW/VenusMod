import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class StaticRouteCommandTest {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static NetworkProfile router(String west,String east){
        var p=new NetworkProfile(DeviceKind.ROUTER);p.dhcp=false;
        for(var port:p.ports)port.address="";
        p.ports[3].address=west;p.ports[1].address=east;return p;
    }
    public static void main(String[] args){
        var sim=new NetworkSimulator();var r=router("10.1.0.1","10.0.0.1");
        r.routes.add(new NetworkProfile.Route("10.2.0.0",24,"10.0.0.2",1));
        r.routes.add(new NetworkProfile.Route("10.2.0.0",24,"10.0.0.3",1));
        r.routes.add(new NetworkProfile.Route("10.2.0.0",24,"10.0.0.2",2));
        sim.put("r",r);
        var removed=NetworkCli.run(sim,"r","route delete 10.2.0.0/24 via 10.0.0.2 port 1");
        check(removed.changed(),"exact route deletion must be supported");
        check(sim.nodes().get("r").routes.size()==2,"only requested gateway/port deleted");
        String before=sim.nodes().get("r").encode();
        check(!NetworkCli.run(sim,"r","route delete 10.2.0.0/24 via 10.0.0.2 port 1").changed(),"missing deletion is no change");
        check(before.equals(sim.nodes().get("r").encode()),"missing route preserves config");
        // 旧ワールドのホスト部付き・重複経路も同じ意味の経路として扱う。
        var p=sim.nodes().get("r");
        p.routes.add(new NetworkProfile.Route("192.168.9.42",24,"010.000.000.002",1));
        p.routes.add(new NetworkProfile.Route("192.168.9.0",24,"10.0.0.2",1));
        check(!NetworkCli.run(sim,"r","route add 192.168.9.99/24 via 10.0.0.2 port 1").changed(),"semantic duplicate is idempotent");
        check(NetworkCli.run(sim,"r","route delete 192.168.9.7/24 via 10.0.0.2 port 1").changed(),"legacy route deletion");
        check(sim.nodes().get("r").routes.size()==2,"only equivalent legacy duplicates removed");
        check(NetworkCli.run(sim,"r","route add 10.99.4.42/24 via 10.0.0.2 port 1").changed(),"new route added");
        check(sim.nodes().get("r").routes.getLast().network().equals("10.99.4.0"),"network canonicalized");
        for(String command:List.of("route delete 10.2.0.0/33 via 10.0.0.2 port 1","route delete 10.2.0.0/24 via invalid port 1","route delete 10.2.0.0/24 via 10.0.0.2 port 6","route delete 10.2.0.0/24 via 10.0.0.2 port 1 fault","route delete all","route clear extra")){
            before=sim.nodes().get("r").encode();check(!NetworkCli.run(sim,"r",command).changed(),"reject "+command);
            check(before.equals(sim.nodes().get("r").encode()),"rejection atomic");
        }
        var pc=new NetworkProfile(DeviceKind.PC);sim.put("pc",pc);
        check(!NetworkCli.run(sim,"pc","route add 0.0.0.0/0 via 10.0.0.2 port 1").changed(),"PC cannot change static routes");
        var full=router("10.1.0.1","10.0.0.1");
        for(int i=0;i<64;i++)full.routes.add(new NetworkProfile.Route("172.16."+i+".0",24,"10.0.0.2",1));sim.put("full",full);
        check(!NetworkCli.run(sim,"full","route add 172.16.0.4/24 via 10.0.0.2 port 1").changed(),"full-table duplicate does not grow table");
        check(!NetworkCli.run(sim,"full","route add 172.16.99.0/24 via 10.0.0.2 port 1").changed(),"64 route limit retained");
        check(sim.nodes().get("full").routes.size()==64,"limit rejection atomic");
        check(NetworkCli.run(sim,"full","route clear").changed(),"legacy clear remains available");
        check(!NetworkCli.run(sim,"full","route clear").changed(),"empty clear no-op");
        // 2ルーター間で往復経路が必要。個別削除で別経路を壊さない。
        var net=new NetworkSimulator();var a=new NetworkProfile(DeviceKind.PC);a.address="10.1.0.10";a.gateway="10.1.0.1";
        var b=new NetworkProfile(DeviceKind.SERVER);b.address="10.2.0.10";b.gateway="10.2.0.1";
        net.put("a",a);net.put("b",b);net.put("r1",router("10.1.0.1","10.0.0.1"));net.put("r2",router("10.0.0.2","10.2.0.1"));
        net.link(new NetworkSimulator.Link("a1","a",1,"r1",3,CableGrade.CAT5E,1,false));
        net.link(new NetworkSimulator.Link("12","r1",1,"r2",3,CableGrade.CAT5E,1,false));
        net.link(new NetworkSimulator.Link("2b","r2",1,"b",3,CableGrade.CAT5E,1,false));
        check(!net.ping("a",b.address).success(),"no magic routing");
        check(NetworkCli.run(net,"r1","route add 10.2.0.0/24 via 10.0.0.2 port 1").changed(),"outbound route");
        check(!net.ping("a",b.address).success(),"return path required");
        check(NetworkCli.run(net,"r2","route add 10.1.0.0/24 via 10.0.0.1 port 3").changed(),"return route");
        check(net.ping("a",b.address).success(),"routing succeeds");
        check(net.startTransfer("a",b.address,1_000_000_000).success(),"active transfer started");
        var flow=net.flows().getFirst();
        for(String noop:List.of("route add 10.2.0.99/24 via 10.0.0.2 port 1","route delete 10.99.0.0/24 via 10.0.0.2 port 1","route delete bad via 10.0.0.2 port 1")){
            check(!NetworkCli.run(net,"r1",noop).changed(),"no-op refused");check(net.flows().contains(flow),"no-op keeps transfer");
        }
        check(NetworkCli.run(net,"r1","route delete 10.2.0.0/24 via 10.0.0.2 port 1").changed(),"delete active route");
        check(!net.ping("a",b.address).success(),"deleted route no longer forwards");
        check(net.nodes().get("r2").routes.size()==1,"other router untouched");
        var round=NetworkProfile.decode(DeviceKind.ROUTER,net.nodes().get("r2").encode());
        check(round.routes.equals(net.nodes().get("r2").routes),"v16 persistence unchanged");
        System.out.println("StaticRouteCommandTest: "+checks+" checks passed");
    }
}
