import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** NATをOSPF・二重NAT・別拠点の重複privateアドレスと組み合わせる。 */
public final class NatRoutingTest {
    private static int checks;
    private static void ok(boolean v,String reason){checks++;if(!v)throw new AssertionError(reason);}
    private static NetworkProfile r(String inside,String outside){var p=new NetworkProfile(DeviceKind.ROUTER);p.dhcp=false;for(var port:p.ports)port.address="";p.ports[0].address=inside;p.ports[1].address=outside;return p;}
    private static NetworkProfile h(String ip,String gateway){var p=new NetworkProfile(DeviceKind.PC);p.address=ip;p.gateway=gateway;return p;}
    private static void wire(NetworkSimulator s,String id,String a,int ap,String b,int bp){s.link(new NetworkSimulator.Link(id,a,ap,b,bp,CableGrade.CAT5E,2,false));}
    private static void cfg(NetworkSimulator s,String id,String command){var reply=NetworkServiceCommands.run(s,id,command);ok(reply.changed(),command+": "+reply.lines());}
    private static void nat(NetworkSimulator s,String id){cfg(s,id,"nat port 0 inside");cfg(s,id,"nat port 1 outside");cfg(s,id,"nat on");}
    private static void tick(NetworkSimulator s,int ticks){for(int n=0;n<ticks;n++)s.advance(1);}
    public static void main(String[] args){
        var s=new NetworkSimulator();NetworkProfile a=r("192.168.1.1","10.0.12.1"),b=r("10.0.12.2","10.0.23.1");
        a.ospf.enabled=b.ospf.enabled=true;a.ospf.routerId="1.1.1.1";b.ospf.routerId="2.2.2.2";
        a.ospf.modes[1]=OspfSettings.Mode.ACTIVE;b.ospf.modes[0]=OspfSettings.Mode.ACTIVE;b.ospf.modes[1]=OspfSettings.Mode.PASSIVE;
        s.put("a",a);s.put("b",b);s.put("client",h("192.168.1.10","192.168.1.1"));s.put("server",h("10.0.23.20","10.0.23.1"));
        wire(s,"ca","client",0,"a",0);wire(s,"ab","a",1,"b",0);wire(s,"bs","b",1,"server",0);nat(s,"a");
        ok(!s.request("client","10.0.23.20","tcp",80).success(),"no learned route yet");tick(s,120);
        ok(s.request("client","10.0.23.20","tcp",80).success(),"NAPT works with OSPF learned egress route");
        ok(s.ospf().routes("b").stream().noneMatch(route->route.network().startsWith("192.168.1.")),"private subnet was not advertised as an implicit workaround");
        cfg(s,"a","acl add 1 in 10 deny ospf any any");cfg(s,"a","acl add 1 in 20 permit ip any any");tick(s,120);
        ok(!s.request("client","10.0.23.20","tcp",80).success()&&s.nat().sessions("a").isEmpty(),"lost OSPF route cannot be replaced by NAT");
        cfg(s,"a","acl clear 1 in");tick(s,120);ok(s.request("client","10.0.23.20","tcp",80).success(),"OSPF recovery permits new translation");
        var doubleNat=new NetworkSimulator();a=r("10.1.0.1","172.16.1.2");b=r("172.16.1.1","203.0.113.1");a.routes.add(new NetworkProfile.Route("0.0.0.0",0,"172.16.1.1",1));
        doubleNat.put("a",a);doubleNat.put("b",b);doubleNat.put("lan",h("10.1.0.10","10.1.0.1"));doubleNat.put("wan",h("203.0.113.20",""));
        wire(doubleNat,"la","lan",0,"a",0);wire(doubleNat,"ab","a",1,"b",0);wire(doubleNat,"bw","b",1,"wan",0);nat(doubleNat,"a");nat(doubleNat,"b");
        for(String protocol:List.of("tcp","udp","icmp"))ok(doubleNat.request("lan","203.0.113.20",protocol,protocol.equals("icmp")?0:80).success(),"double NAT "+protocol);
        ok(doubleNat.nat().sessions("a").size()==3&&doubleNat.nat().sessions("b").size()==3,"each router has its own state");
        cfg(doubleNat,"a","nat forward add 1 tcp 8081 10.1.0.10 80");cfg(doubleNat,"b","nat forward add 1 tcp 8080 172.16.1.2 8081");
        var reply=doubleNat.requestService("wan","203.0.113.1","tcp",8080,80);
        ok(reply.success()&&reply.destination().equals("lan")&&reply.natBindings().size()==2,"two DNAT boundaries and reverse SNAT");
        var overlap=new NetworkSimulator();a=r("10.1.0.1","203.0.113.1");b=r("10.1.0.1","203.0.113.2");
        overlap.put("a",a);overlap.put("b",b);overlap.put("lan-a",h("10.1.0.10","10.1.0.1"));overlap.put("lan-b",h("10.1.0.10","10.1.0.1"));
        wire(overlap,"aa","a",0,"lan-a",0);wire(overlap,"ab","a",1,"b",1);wire(overlap,"bb","b",0,"lan-b",0);nat(overlap,"a");nat(overlap,"b");
        cfg(overlap,"b","nat forward add 1 tcp 8080 10.1.0.10 80");reply=overlap.requestService("lan-a","203.0.113.2","tcp",8080,80);
        ok(reply.success()&&reply.destination().equals("lan-b"),"identical private address in different sites does not misdeliver reply");
        String before=overlap.nodes().get("a").encode();int bindings=overlap.nat().sessions("a").size();
        var invalid=NetworkServiceCommands.run(overlap,"a","nat port 7 outside");ok(!invalid.changed()&&overlap.nodes().get("a").encode().equals(before)&&overlap.nat().sessions("a").size()==bindings,"invalid NAT command keeps settings and active sessions");
        var status=NetworkServiceCommands.run(overlap,"a","nat sessions");ok(status.handled()&&!status.changed()&&overlap.nat().sessions("a").size()==bindings,"read-only inspection does not clear mappings");
        System.out.println("NatRoutingTest: "+checks+" checks passed");
    }
}
