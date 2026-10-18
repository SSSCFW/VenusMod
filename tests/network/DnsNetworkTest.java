import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class DnsNetworkTest {
    private static int checks;
    private static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    private static void command(NetworkSimulator s,String id,String cmd){var r=NetworkServiceCommands.run(s,id,cmd);ok(r.handled()&&r.changed(),cmd+" / "+r.lines());}
    private static NetworkProfile host(DeviceKind kind,String ip,String gw){var p=new NetworkProfile(kind);p.address=ip;p.gateway=gw;return p;}
    public static void main(String[] args){
        var s=new NetworkSimulator();var a=host(DeviceKind.PC,"192.168.10.10","192.168.10.1");a.dns="203.0.113.20";s.put("a",a);
        var router=new NetworkProfile(DeviceKind.ROUTER);router.dhcp=false;router.ports[0].address="192.168.10.1";router.ports[1].address="203.0.113.1";s.put("r",router);
        var dns=host(DeviceKind.SERVER,"203.0.113.20","");dns.web=true;dns.dnsRecords.put("web.local","203.0.113.20");s.put("d",dns);
        s.link(new NetworkSimulator.Link("ar","a",0,"r",0,CableGrade.CAT5E,1,false));s.link(new NetworkSimulator.Link("rd","r",1,"d",0,CableGrade.CAT5E,1,false));
        var answer=s.dnsResolver().lookup("a","web.local");ok(!answer.success()&&!answer.negative(),"missing reply route is not NXDOMAIN");
        ok(s.dnsResolver().cacheSize()==0&&s.nat().sessions("r").isEmpty(),"failed transport leaves no DNS or NAT cache");
        command(s,"r","nat port 0 inside");command(s,"r","nat port 1 outside");command(s,"r","nat on");
        answer=s.dnsResolver().lookup("a","web.local");ok(answer.success()&&!answer.cached(),"DNS over NAPT");ok(s.nat().sessions("r").size()==1,"one UDP mapping");
        command(s,"r","acl add 0 in 10 deny icmp any any");command(s,"r","acl add 0 in 20 permit ip any any");
        s.dnsResolver().clear("a");ok(s.dnsResolver().lookup("a","web.local").success(),"DNS isn't ICMP probe");
        ok(!s.ping("a","web.local").success(),"resolved name still subject to ICMP ACL");
        var ep=s.resolveEndpoint("a","web.local:80",80);ok(s.requestService("a",ep.address(),"tcp",ep.port(),80).success(),"same name usable over permitted TCP");
        command(s,"r","acl add 0 in 5 deny udp any any 53");s.dnsResolver().clear("a");
        answer=s.dnsResolver().lookup("a","web.local");ok(!answer.success()&&!answer.negative()&&answer.message().contains("ACL"),"outbound DNS ACL failure distinguished");
        ok(s.dnsResolver().cacheSize()==0&&s.nat().sessions("r").isEmpty(),"blocked DNS creates no mapping");
        command(s,"r","acl clear 0 in");command(s,"r","acl add 0 out 10 deny udp any 192.168.10.10/32");command(s,"r","acl add 0 out 20 permit ip any any");
        answer=s.dnsResolver().lookup("a","web.local");ok(!answer.success()&&!answer.negative(),"reply ACL checked");ok(s.nat().sessions("r").isEmpty(),"reply failure rolls back mapping");
        command(s,"r","acl clear 0 out");command(s,"d","dns-server off");
        answer=s.dnsResolver().lookup("a","web.local");ok(!answer.success()&&!answer.negative()&&s.nat().sessions("r").isEmpty(),"stopped service doesn't commit translation");
        command(s,"d","dns-server on");answer=s.dnsResolver().lookup("a","web.local");ok(answer.success(),"restored service succeeds without negative-cache delay");
        s.unlink("rd");ok(s.dnsResolver().lookup("a","web.local").cached(),"link edits do not invalidate an existing name answer");
        ok(!s.ping("a","web.local").success(),"cached DNS does not bypass actual broken link");
        s.link(new NetworkSimulator.Link("rd","r",1,"d",0,CableGrade.CAT5E,1,false));
        var inside=host(DeviceKind.SERVER,"192.168.20.20","192.168.20.1");inside.web=true;inside.dnsRecords.put("public.local","203.0.113.1");s.put("inside",inside);
        s.nodes().get("r").ports[2].address="192.168.20.1";
        s.link(new NetworkSimulator.Link("ri","r",2,"inside",0,CableGrade.CAT5E,1,false));command(s,"r","nat port 2 inside");
        command(s,"r","nat forward add 1 udp 53 192.168.20.20 53");command(s,"r","nat forward add 1 tcp 8080 192.168.20.20 80");
        s.nodes().get("d").dns="203.0.113.1";answer=s.dnsResolver().lookup("d","public.local");ok(answer.success()&&answer.transport().destination().equals("inside"),"DNS forwarding reaches internal authority");
        ep=s.resolveEndpoint("d","PUBLIC.local.:8080",80);var response=s.requestService("d",ep.address(),"tcp",ep.port(),80);
        ok(response.success()&&response.destination().equals("inside")&&response.servicePort()==80,"named web uses translated final service port");
        command(s,"r","nat forward delete 1 udp 53");command(s,"r","nat forward add 1 udp 53 192.168.20.20 54");s.dnsResolver().clear("d");
        answer=s.dnsResolver().lookup("d","public.local");ok(!answer.success()&&!answer.negative()&&answer.message().contains("ポート"),"wrong DNS internal port rejected");
        ok(s.dnsResolver().describe("d").size()==1&&s.nat().sessions("r").isEmpty(),"wrong port didn't create cache/mapping");
        // 同じDNS-IPを使う独立LAN。キャッシュと権威側の回転も他網へ漏らさない。
        var isolated=new NetworkSimulator();
        for(int n=0;n<2;n++){
            var client=host(DeviceKind.PC,"10.0.0.1","");client.dns="10.0.0.2";var authority=host(DeviceKind.SERVER,"10.0.0.2","");
            authority.dnsSettings.set(authority,"same.local",60,List.of("10.0.1."+(n+1),"10.0.2."+(n+1)));
            isolated.put("c"+n,client);isolated.put("d"+n,authority);isolated.link(new NetworkSimulator.Link("l"+n,"c"+n,0,"d"+n,0,CableGrade.CAT5E,1,false));
        }
        ok(isolated.dnsResolver().lookup("c0","same.local").selected().equals("10.0.1.1"),"isolated DNS A");
        ok(isolated.dnsResolver().lookup("c1","same.local").selected().equals("10.0.1.2"),"isolated DNS B same configured IP");
        isolated.unlink("l1");ok(isolated.dnsResolver().lookup("c0","same.local").cached(),"unrelated topology edit preserves cached answer");
        isolated.dnsResolver().clear("c1");ok(!isolated.dnsResolver().lookup("c1","same.local").success(),"isolated broken LAN not answered by other DNS");
        System.out.println("DnsNetworkTest: "+checks+" checks passed");
    }
}
