import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class NtpNetworkTest {
    static int checks;
    static void ok(boolean v,String reason){checks++;if(!v)throw new AssertionError(reason);}
    static void set(NetworkSimulator s,String id,String command){var r=NetworkServiceCommands.run(s,id,command);ok(r.handled()&&r.changed(),command+" / "+r.lines());}
    static final class Fixture {
        final NetworkSimulator s=new NetworkSimulator();
        Fixture(){var inside=new NetworkProfile(DeviceKind.SERVER);inside.address="10.88.0.10";inside.gateway="10.88.0.1";
            var outside=new NetworkProfile(DeviceKind.SERVER);outside.address="198.51.100.20";
            var r=new NetworkProfile(DeviceKind.ROUTER);r.dhcp=false;r.ports[0].address="10.88.0.1";r.ports[1].address="198.51.100.1";
            s.put("in",inside);s.put("out",outside);s.put("r",r);s.link(new NetworkSimulator.Link("ir","in",0,"r",0,CableGrade.CAT5E,2,false));s.link(new NetworkSimulator.Link("ro","r",1,"out",0,CableGrade.CAT5E,2,false));}
        void nat(){set(s,"r","nat port 0 inside");set(s,"r","nat port 1 outside");set(s,"r","nat on");}
        void client(String id,String target){set(s,id,"ntp offset 5000");set(s,id,"ntp target "+target);set(s,id,"ntp interval 1");set(s,id,"ntp on");}
    }
    public static void main(String[] args){
        var f=new Fixture();var s=f.s;set(s,"out","ntp server on");f.client("in","198.51.100.20");s.advance(1);
        ok(s.ntp().snapshot("in").samples()==0&&s.ntp().snapshot("in").detail().contains("応答経路"),"outgoing route alone not sufficient for synchronization");
        ok(s.ntp().snapshot("in").correctionMillis()==0,"unsuccessful exchange cannot move local clock");f.nat();
        set(s,"r","acl add 0 in 10 deny icmp any any");set(s,"r","acl add 0 in 20 permit ip any any");s.advance(20);
        ok(s.ntp().snapshot("in").state().equals("SYNCED")&&!s.ping("in","198.51.100.20").success(),"NTP works even when ping is refused");
        ok(s.nat().sessions("r").size()==1,"one successful UDP exchange commits mapping");
        long expiry=s.nat().sessions("r").getFirst().expiresAt();double correction=s.ntp().snapshot("in").correctionMillis();set(s,"out","ntp server off");s.advance(20);
        ok(s.nat().sessions("r").getFirst().expiresAt()==expiry,"inactive time service does not renew NAT mapping");ok(s.ntp().snapshot("in").correctionMillis()==correction,"service shutdown leaves last clock correction intact");
        set(s,"r","nat clear");s.advance(20);ok(s.nat().sessions("r").isEmpty(),"failed probe cannot create fresh NAT");
        set(s,"out","ntp server on");set(s,"r","acl add 0 out 10 deny udp any any");set(s,"r","acl add 0 out 20 permit ip any any");s.advance(20);
        ok(s.ntp().snapshot("in").detail().contains("ACL")&&s.nat().sessions("r").isEmpty(),"reverse ACL refusal rolls back transaction");
        set(s,"r","acl clear 0 out");s.advance(20);ok(s.ntp().snapshot("in").state().equals("SYNCED"),"restore reply path and resynchronize");
        s.unlink("ro");s.advance(20);ok(!s.ntp().snapshot("in").state().equals("SYNCED"),"real broken logical link cannot be bypassed");
        var g=new Fixture();s=g.s;g.nat();set(s,"in","ntp server on");set(s,"r","nat forward add 1 udp 8123 10.88.0.10 123");set(s,"r","nat forward add 1 udp 8124 10.88.0.10 124");
        g.client("out","198.51.100.1 8124");s.advance(1);ok(s.ntp().snapshot("out").samples()==0&&s.nat().sessions("r").isEmpty(),"wrong translated internal port does not synchronize or create mapping");
        set(s,"out","ntp target 198.51.100.1 8123");s.advance(1);ok(s.ntp().snapshot("out").state().equals("SYNCED"),"public UDP8123 translated to internal UDP123");
        ok(Math.abs(s.ntp().clockMillis("out")-s.ntp().clockMillis("in"))<0.01,"clock sampled from actual translated recipient");
        var before=s.nat().sessions("r").getFirst();set(s,"out","ntp interval 3");ok(s.nat().sessions("r").getFirst().equals(before),"NTP settings do not clear existing NAT sessions");
        System.out.println("NtpNetworkTest: "+checks+" checks passed");
    }
}
