import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.lang.reflect.*;
import java.util.*;

/** private LANへの経路を知らない仮想WANとの通信。NAT実装前に失敗する振る舞い検査。 */
public final class NatIntegrationTest {
    private static int assertions,failures;
    private static void check(boolean value,String reason){assertions++;if(!value)throw new AssertionError(reason);}
    private static void test(String name,Runnable body){try{body.run();System.out.println("PASS "+name);}catch(Throwable e){failures++;System.out.println("FAIL "+name+": "+e.getMessage());}}
    private static void config(NetworkSimulator sim,String id,String text){var reply=NetworkServiceCommands.run(sim,id,text);check(reply.handled()&&reply.changed(),"設定未実装/失敗: "+text+" / "+reply.lines());}
    private static int sessions(NetworkSimulator sim,String id){try{Object table=sim.getClass().getMethod("nat").invoke(sim);return ((List<?>)table.getClass().getMethod("sessions",String.class).invoke(table,id)).size();}catch(ReflectiveOperationException e){throw new AssertionError("NAT変換表APIが未実装",e);}}
    private static NetworkProfile host(DeviceKind kind,String ip,String gateway){var p=new NetworkProfile(kind);p.address=ip;p.gateway=gateway;return p;}
    private static final class Fixture {
        final NetworkSimulator sim=new NetworkSimulator();
        Fixture(){
            sim.put("a",host(DeviceKind.PC,"192.168.10.10","192.168.10.1"));
            sim.put("b",host(DeviceKind.PC,"192.168.10.20","192.168.10.1"));
            sim.put("sw",new NetworkProfile(DeviceKind.L2_SWITCH));
            var router=new NetworkProfile(DeviceKind.ROUTER);router.dhcp=false;router.ports[0].address="192.168.10.1";router.ports[1].address="203.0.113.1";sim.put("r",router);
            sim.put("s",host(DeviceKind.SERVER,"203.0.113.20",""));
            sim.link(new NetworkSimulator.Link("a-sw","a",0,"sw",0,CableGrade.CAT5E,2,false));
            sim.link(new NetworkSimulator.Link("b-sw","b",0,"sw",1,CableGrade.CAT5E,2,false));
            sim.link(new NetworkSimulator.Link("sw-r","sw",2,"r",0,CableGrade.CAT5E,2,false));
            sim.link(new NetworkSimulator.Link("r-s","r",1,"s",0,CableGrade.CAT5E,2,false));
        }
        void enable(){config(sim,"r","nat port 0 inside");config(sim,"r","nat port 1 outside");config(sim,"r","nat on");}
    }
    public static void main(String[] args){
        test("without NAT reply has no private route",()->{var f=new Fixture();check(!f.sim.request("a","203.0.113.20","tcp",80).success(),"baseline must fail on return path");});
        test("NAPT permits TCP UDP and ICMP without remote private route",()->{var f=new Fixture();f.enable();for(String protocol:List.of("tcp","udp","icmp")){var reply=f.sim.request("a","203.0.113.20",protocol,protocol.equals("icmp")?0:80);check(reply.success(),protocol+": "+reply.message());check(reply.destination().equals("s"),"actual destination");}check(sessions(f.sim,"r")==3,"three protocol sessions");});
        test("different LAN hosts share the same outside address",()->{var f=new Fixture();f.enable();check(f.sim.request("a","203.0.113.20","tcp",80).success(),"first client");check(f.sim.request("b","203.0.113.20","tcp",80).success(),"second client");check(sessions(f.sim,"r")==2,"independent translated tuples");check(f.sim.request("a","203.0.113.20","tcp",80).success()&&sessions(f.sim,"r")==2,"reuse matching live session");});
        test("outside ACL observes translated source",()->{var f=new Fixture();f.enable();config(f.sim,"r","acl add 1 out 10 permit tcp 203.0.113.1/32 any 80");check(f.sim.request("a","203.0.113.20","tcp",80).success(),"source translated before outbound ACL");});
        test("inside ACL sees private source and denied requests are atomic",()->{var f=new Fixture();f.enable();config(f.sim,"r","acl add 0 in 10 deny tcp 192.168.10.10/32 any");config(f.sim,"r","acl add 0 in 20 permit ip any any");check(!f.sim.request("a","203.0.113.20","tcp",80).success(),"private source denied");check(sessions(f.sim,"r")==0,"no session after drop");});
        test("return ACL denial does not leave a session",()->{var f=new Fixture();f.enable();config(f.sim,"r","acl add 0 out 10 deny tcp any 192.168.10.10/32");config(f.sim,"r","acl add 0 out 20 permit ip any any");check(!f.sim.request("a","203.0.113.20","tcp",80).success(),"return denied after destination translation");check(sessions(f.sim,"r")==0,"uncommitted binding rolled back");});
        test("port forwarding resolves to internal server",()->{var f=new Fixture();f.enable();config(f.sim,"r","nat forward add 1 tcp 8080 192.168.10.20 80");var result=f.sim.request("s","203.0.113.1","tcp",8080);check(result.success(),"inbound forward: "+result.message());check(result.destination().equals("b"),"not the NAT device itself");});
        test("unpublished outside port is rejected",()->{var f=new Fixture();f.enable();check(!f.sim.request("s","203.0.113.1","tcp",8081).success(),"no implicit mapping");check(sessions(f.sim,"r")==0,"no unknown inbound binding");});
        test("bad configuration and duplicate forwards preserve data",()->{var f=new Fixture();f.enable();config(f.sim,"r","nat forward add 1 tcp 8080 192.168.10.20 80");String before=f.sim.nodes().get("r").encode();for(String command:List.of("nat port 7 inside","nat port 1 invalid","nat forward add 1 tcp 8080 192.168.10.10 81","nat forward add 1 tcp 65536 192.168.10.20 80","nat forward add 0 tcp 80 192.168.10.20 80")){var reply=NetworkServiceCommands.run(f.sim,"r",command);check(!reply.changed(),"reject "+command);check(f.sim.nodes().get("r").encode().equals(before),"saved config unchanged");}});
        test("NAT configuration survives codec but live sessions do not",()->{var f=new Fixture();f.enable();config(f.sim,"r","nat forward add 1 tcp 8080 192.168.10.20 80");check(f.sim.request("a","203.0.113.20","tcp",80).success(),"create live session");String saved=f.sim.nodes().get("r").encode();var restored=NetworkProfile.decode(DeviceKind.ROUTER,saved);check(restored.encode().equals(saved),"round trip");var g=new Fixture();g.sim.put("r",restored);check(sessions(g.sim,"r")==0,"not persisted");check(g.sim.request("s","203.0.113.1","tcp",8080).success(),"restored forward works");});
        test("idle UDP mapping expires",()->{var f=new Fixture();f.enable();check(f.sim.request("a","203.0.113.20","udp",53).success(),"UDP succeeds");f.sim.advance(601);check(sessions(f.sim,"r")==0,"30 second idle timeout");});
        test("clear invalidates live transfers",()->{var f=new Fixture();f.enable();check(f.sim.startTransfer("a","203.0.113.20",1_000_000_000L).success(),"transfer via NAT");config(f.sim,"r","nat clear");check(f.sim.flows().isEmpty()&&sessions(f.sim,"r")==0,"clear cancels sessions and dependent flows");});
        System.out.println("NatIntegrationTest: "+assertions+" checks / "+failures+" failed cases");if(failures>0)throw new AssertionError("NAT cases failed: "+failures);
    }
}
