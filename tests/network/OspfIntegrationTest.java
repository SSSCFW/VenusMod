import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import java.io.*;

/** 実際のNetworkSimulatorのVLAN・帯域・ACL・CLI・保存と組み合わせる。 */
public final class OspfIntegrationTest {
    private static int checks;
    private static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    static void command(NetworkSimulator s,String id,String command){var r=NetworkServiceCommands.run(s,id,command);ok(r.handled()&&r.changed(),command+" -> "+r);}
    static NetworkProfile router(String rid){var p=new NetworkProfile(DeviceKind.ROUTER);p.dhcp=false;p.ospf.enabled=true;p.ospf.routerId=rid;for(var port:p.ports)port.address="";return p;}
    static void ip(NetworkProfile p,int n,String address,boolean passive,int cost){p.ports[n].address=address;p.ports[n].prefix=24;p.ospf.modes[n]=passive?OspfSettings.Mode.PASSIVE:OspfSettings.Mode.ACTIVE;p.ospf.costs[n]=cost;}
    static NetworkProfile host(String ip,String gateway){var p=new NetworkProfile(DeviceKind.PC);p.address=ip;p.gateway=gateway;return p;}
    static void link(NetworkSimulator s,String id,String a,int ap,String b,int bp){s.link(new NetworkSimulator.Link(id,a,ap,b,bp,CableGrade.CAT5E,2,false));}
    static void ticks(NetworkSimulator s,int n){for(int i=0;i<n;i++)s.advance(1);}
    public static void main(String[] args)throws Exception{
        var s=new NetworkSimulator();var a=router("1.1.1.1");var b=router("2.2.2.2");var c=router("3.3.3.3");
        ip(a,0,"10.0.12.1",false,10);ip(b,0,"10.0.12.2",false,10);ip(b,1,"10.0.23.2",false,10);ip(c,0,"10.0.23.3",false,10);
        ip(a,1,"10.0.13.1",false,50);ip(c,1,"10.0.13.3",false,50);ip(a,2,"192.168.1.1",true,1);ip(c,2,"192.168.3.1",true,1);
        s.put("a",a);s.put("b",b);s.put("c",c);s.put("x",host("192.168.1.10","192.168.1.1"));s.put("y",host("192.168.3.10","192.168.3.1"));
        s.put("sw",new NetworkProfile(DeviceKind.L2_SWITCH));
        link(s,"as","a",0,"sw",0);link(s,"bs","b",0,"sw",1);link(s,"bc","b",1,"c",0);link(s,"ac","a",1,"c",1);link(s,"ax","a",2,"x",0);link(s,"cy","c",2,"y",0);
        ok(!s.ping("x","192.168.3.10").success(),"no dynamic route before control protocol");ticks(s,120);
        var response=s.ping("x","192.168.3.10");ok(response.success()&&response.nodes().contains("b"),"learned route crosses L2 switch and remote router: "+response);
        ok(a.routes.isEmpty()&&b.routes.isEmpty()&&c.routes.isEmpty(),"OSPF never overwrites saved static routes");
        b.failed=true;s.invalidate();ticks(s,120);response=s.ping("x","192.168.3.10");ok(response.success()&&!response.nodes().contains("b"),"faulted router is bypassed");
        b.failed=false;s.invalidate();ticks(s,120);ok(s.ping("x","192.168.3.10").nodes().contains("b"),"repair restores cheaper route");
        command(s,"a","acl add 0 in 10 deny ospf any any");command(s,"a","acl add 0 in 20 permit ip any any");ticks(s,120);
        ok(s.ospf().neighbors("a").stream().noneMatch(n->n.routerId().equals("2.2.2.2")&&n.state().equals("FULL")),"ACL blocks OSPF alone");
        ok(s.ping("x","192.168.3.10").success()&&!s.ping("x","192.168.3.10").nodes().contains("b"),"other traffic can use alternate path");
        command(s,"a","acl clear 0 in");ticks(s,120);ok(s.ping("x","192.168.3.10").nodes().contains("b"),"control ACL removal reconverges");
        a=s.nodes().get("a");a.routes.add(new NetworkProfile.Route("192.168.3.0",24,"10.0.13.3",1));s.invalidate();ticks(s,100);
        ok(!s.ping("x","192.168.3.10").nodes().contains("b"),"equal prefix static overrides learned route");
        a.routes.clear();a.routes.add(new NetworkProfile.Route("0.0.0.0",0,"10.0.13.3",1));s.invalidate();ticks(s,100);
        ok(s.ping("x","192.168.3.10").nodes().contains("b"),"more specific OSPF beats default static");
        a.routes.clear();s.invalidate();command(s,"a","ospf cost 0 100");ticks(s,100);
        ok(!s.ping("x","192.168.3.10").nodes().contains("b"),"cost CLI changes SPF path");
        command(s,"a","ospf cost 0 10");ticks(s,100);
        command(s,"b","ospf area 1");ticks(s,120);ok(!s.ping("x","192.168.3.10").nodes().contains("b"),"different area is not an ABR shortcut");
        command(s,"b","ospf area 0");ticks(s,120);ok(s.ping("x","192.168.3.10").nodes().contains("b"),"restored area joins");
        command(s,"a","ospf off");ticks(s,10);ok(!s.ping("x","192.168.3.10").success(),"OSPF off withdraws runtime routes");
        command(s,"a","ospf on");ticks(s,120);
        var saved=NetworkProfile.decode(DeviceKind.ROUTER,s.nodes().get("a").encode());ok(saved.ospf.enabled&&saved.ospf.costs[0]==10&&saved.ospf.routerId.equals("1.1.1.1"),"v3 settings survive codec");
        ok(saved.routes.isEmpty(),"learned routes not persisted");
        var snapshot=NetworkServiceCommands.run(s,"a","show ip ospf neighbor");ok(snapshot.handled()&&!snapshot.changed()&&snapshot.lines().stream().anyMatch(l->l.contains("FULL")),"neighbor show alias does not edit settings");
        snapshot=NetworkServiceCommands.run(s,"a","show ip route");ok(snapshot.lines().stream().anyMatch(l->l.startsWith("O ")),"OSPF route code visible");
        String original=s.nodes().get("a").encode();for(String bad:List.of("ospf cost 0 -1","ospf cost 0 0","ospf cost 6 1","ospf router-id 224.0.0.5","ospf area -1","ospf timers 1 1","ospf timers 999999999 999999999")){
            var out=NetworkServiceCommands.run(s,"a",bad);ok(!out.changed()&&s.nodes().get("a").encode().equals(original),"bad command transactional: "+bad);
        }
        var plain=new NetworkProfile(DeviceKind.PC);byte[] v3=Base64.getDecoder().decode("AAAAAwAKbmV0d29ya19wYwAAAAAAGAAAAAAAAAABAAAAAAAAZAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAUAAAAUAADT0ZGAAAAAAADT0ZGAAAAAAADT0ZGAAAAAAADT0ZGAAAAAAADT0ZGAAAAAAADT0ZGAAAAAA==");var suffix=new ByteArrayOutputStream();plain.ospf.write(new DataOutputStream(suffix));
        byte[] v2=Arrays.copyOf(v3,v3.length-suffix.size());java.nio.ByteBuffer.wrap(v2).putInt(2);var legacy=NetworkProfile.decode(DeviceKind.PC,Base64.getEncoder().encodeToString(v2));ok(!legacy.ospf.enabled,"v2 migration remains OSPF disabled");
        // 保存 v1 は v2末尾のservicesを持たない。
        suffix=new ByteArrayOutputStream();plain.services.write(new DataOutputStream(suffix));byte[] v1=Arrays.copyOf(v2,v2.length-suffix.size());java.nio.ByteBuffer.wrap(v1).putInt(1);
        ok(!NetworkProfile.decode(DeviceKind.PC,Base64.getEncoder().encodeToString(v1)).ospf.enabled,"v1 migration remains OSPF disabled");
        System.out.println("OspfIntegrationTest: "+checks+" checks passed");
    }
}
