import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.lang.reflect.*;
import java.util.*;

/** 同じ仮想経路へ設定を適用し、観測される通信の可否・保存結果を検査する。 */
public final class NetworkServicesTest {
    private static int assertions,failed;
    private static void check(boolean ok,String why){assertions++;if(!ok)throw new AssertionError(why);}
    private static void test(String name,Runnable body){try{body.run();System.out.println("PASS "+name);}catch(Throwable e){failed++;System.out.println("FAIL "+name+": "+e.getMessage());}}
    private static boolean config(NetworkSimulator sim,String id,String command){
        try {
            var cls=Class.forName("dev.ssscfw.venusmod.infrastructure.network.core.NetworkServiceCommands");
            Object reply=cls.getMethod("run",NetworkSimulator.class,String.class,String.class).invoke(null,sim,id,command);
            return (boolean)reply.getClass().getMethod("changed").invoke(reply);
        }catch(InvocationTargetException e){throw new AssertionError(e.getCause());}
        catch(ReflectiveOperationException e){throw new AssertionError("ACL/DHCP設定APIが未実装");}
    }
    private static NetworkSimulator.Result request(NetworkSimulator sim,String id,String dst,String protocol,int port){
        try{return (NetworkSimulator.Result)NetworkSimulator.class.getMethod("request",String.class,String.class,String.class,int.class).invoke(sim,id,dst,protocol,port);}
        catch(InvocationTargetException e){throw new AssertionError(e.getCause());}
        catch(ReflectiveOperationException e){throw new AssertionError("プロトコル別通信が未実装");}
    }
    private static NetworkProfile host(String ip,String gateway){var p=new NetworkProfile(DeviceKind.PC);p.address=ip;p.gateway=gateway;return p;}
    private static final class Fixture {
        final NetworkSimulator sim=new NetworkSimulator();
        Fixture(){
            sim.put("a",host("10.0.1.10","10.0.1.1"));
            var r=new NetworkProfile(DeviceKind.ROUTER);r.dhcp=false;r.ports[0].address="10.0.1.1";r.ports[1].address="10.0.2.1";sim.put("r",r);
            var s=new NetworkProfile(DeviceKind.SERVER);s.address="10.0.2.10";s.gateway="10.0.2.1";s.dhcp=true;s.web=true;sim.put("s",s);
            sim.link(new NetworkSimulator.Link("ar","a",0,"r",0,CableGrade.CAT5E,2,false));
            sim.link(new NetworkSimulator.Link("rs","r",1,"s",0,CableGrade.CAT5E,2,false));
        }
        NetworkProfile a(){return sim.nodes().get("a");} NetworkProfile r(){return sim.nodes().get("r");} NetworkProfile s(){return sim.nodes().get("s");}
        void relay(){a().address="";a().gateway="";check(config(sim,"r","dhcp-relay 0 10.0.2.10"),"configure relay");}
        void pool(String start,String end){check(config(sim,"s","dhcp-pool add 10.0.1.0/24 "+start+" "+end+" 10.0.1.1 10.0.2.10"),"configure pool");}
    }
    public static void main(String[] args){
        test("baseline routes",()->{var f=new Fixture();check(f.sim.ping("a","10.0.2.10").success(),"baseline ping");});
        test("ACL first match, protocol and original source",()->{
            var f=new Fixture();check(config(f.sim,"r","acl add 0 in 20 permit ip any any"),"permit rest");
            check(config(f.sim,"r","acl add 0 in 10 deny icmp 10.0.1.10/32 10.0.2.0/24"),"deny rule");
            var denied=f.sim.ping("a","10.0.2.10");check(!denied.success()&&denied.message().contains("ACL"),"ping must be denied with reason");
            check(request(f.sim,"a","10.0.2.10","tcp",80).success(),"TCP80 allowed independently of ICMP");
            check(!f.sim.arpTable("a").isEmpty(),"IP ACL does not block ARP");
            check(config(f.sim,"r","acl delete 0 in 10"),"remove one rule");check(f.sim.ping("a","10.0.2.10").success(),"removed rule no longer blocks");
        });
        test("ACL implicit deny and destination port",()->{
            var f=new Fixture();check(config(f.sim,"r","acl add 0 in 10 permit tcp any any 80"),"web permit");
            check(request(f.sim,"a","10.0.2.10","tcp",80).success(),"web permit result");
            check(!request(f.sim,"a","10.0.2.10","tcp",5000).success(),"unmatched TCP5000 implicit deny");
            check(!request(f.sim,"a","10.0.2.10","udp",53).success(),"UDP unmatched");
            check(config(f.sim,"r","acl clear 0 in"),"detach ACL");check(f.sim.ping("a","10.0.2.10").success(),"no ACL allows previous traffic");
        });
        test("return ACL direction is enforced",()->{
            var f=new Fixture();check(config(f.sim,"r","acl add 0 out 10 deny tcp 10.0.2.10/32 any"),"response deny");
            check(config(f.sim,"r","acl add 0 out 20 permit ip any any"),"rest allowed");
            check(!request(f.sim,"a","10.0.2.10","tcp",80).success(),"response drop makes request fail");
            check(f.sim.ping("a","10.0.2.10").success(),"ICMP still permitted");
        });
        test("DNS and transfer use their own protocols",()->{
            var f=new Fixture();f.a().dns="10.0.2.10";f.s().dnsRecords.put("base.local","10.0.1.10");
            check(config(f.sim,"r","acl add 0 in 10 deny icmp any any"),"block ICMP");
            check(config(f.sim,"r","acl add 0 in 20 permit ip any any"),"permit data");
            check(f.sim.resolve("a","base.local").success(),"DNS not based on ping");
            check(f.sim.startTransfer("a","10.0.2.10",100_000_000).success(),"transfer not based on ping");
            check(config(f.sim,"r","acl add 0 in 5 deny udp any any 53"),"DNS deny");
            check(!f.sim.resolve("a","base.local").success(),"UDP53 filtered");
            check(f.sim.flows().isEmpty(),"config changes cancel existing flows");
        });
        test("configuration validation is transactional",()->{
            var f=new Fixture();String before=f.r().encode();
            for(String bad:List.of("acl add 8 in 10 permit ip any any","acl add 0 in 0 deny ip any any","acl add 0 in 10 permit icmp any any 80","acl add 0 in 10 deny tcp bad any","acl add 0 in 10 deny tcp any any 65536","dhcp-relay 0 external.example")){
                check(!config(f.sim,"r",bad),"reject "+bad);check(f.r().encode().equals(before),"unchanged after invalid command");
            }
            check(!config(f.sim,"a","dhcp-relay 0 10.0.2.10"),"no relay on PC");
            check(!config(f.sim,"a","acl add 0 in 10 deny ip any any"),"ACL only L3 in this version");
        });
        test("relay obtains correct subnet and options",()->{
            var f=new Fixture();f.relay();f.pool("10.0.1.100","10.0.1.101");var result=f.sim.dhcp("a");
            check(result.success(),"relay DHCP succeeds: "+result.message());check(f.a().address.equals("10.0.1.100"),"client subnet selected using giaddr, not server subnet");
            check(f.a().gateway.equals("10.0.1.1")&&f.a().dns.equals("10.0.2.10"),"pool options");
            check(f.sim.ping("a","10.0.2.10").success(),"leased client can route");
            check(result.message().contains("relay"),"relay visible in diagnostic");
        });
        test("relay failure does not mutate address",()->{
            var f=new Fixture();f.relay();f.pool("10.0.1.100","10.0.1.101");f.s().gateway="";String before=f.a().encode();
            check(!f.sim.dhcp("a").success(),"missing return path");check(f.a().encode().equals(before),"failure leaves client intact");
            f.s().gateway="10.0.2.1";check(config(f.sim,"r","acl add 1 out 10 deny udp any any 67"),"block relay UDP");
            check(config(f.sim,"r","acl add 1 out 20 permit ip any any"),"rest allowed");
            check(!f.sim.dhcp("a").success(),"UDP67 deny blocks relay");check(f.a().encode().equals(before),"filtered request no lease");
            check(config(f.sim,"r","acl clear 1 out"),"remove relay filter");check(f.sim.dhcp("a").success(),"recovered lease");
            check(f.a().address.equals("10.0.1.100"),"failed requests did not consume pool");
        });
        test("remote pool is required",()->{
            var f=new Fixture();f.relay();check(!f.sim.dhcp("a").success(),"no implicit remote pool");check(f.a().address.isBlank(),"not assigned server subnet");
        });
        test("persistent reservations prevent offline address reuse",()->{
            var f=new Fixture();f.relay();f.pool("10.0.1.100","10.0.1.101");check(f.sim.dhcp("a").success(),"first binding");
            String serverConfig=f.s().encode();var saved=f.a();f.sim.remove("a");
            f.sim.put("s",NetworkProfile.decode(DeviceKind.SERVER,serverConfig));
            f.sim.put("b",new NetworkProfile(DeviceKind.PC));f.sim.link(new NetworkSimulator.Link("br","b",0,"r",0,CableGrade.CAT5E,1,false));
            check(f.sim.dhcp("b").success(),"second binding");check(f.sim.nodes().get("b").address.equals("10.0.1.101"),"offline binding reserved");
            f.sim.remove("b");f.sim.put("c",new NetworkProfile(DeviceKind.PC));f.sim.link(new NetworkSimulator.Link("cr","c",0,"r",0,CableGrade.CAT5E,1,false));
            check(!f.sim.dhcp("c").success(),"pool exhausted, not reallocated");
            f.sim.remove("c");f.sim.put("a",saved);f.sim.link(new NetworkSimulator.Link("ar2","a",0,"r",0,CableGrade.CAT5E,1,false));
            check(f.sim.dhcp("a").success()&&f.a().address.equals("10.0.1.100"),"same client retains reservation");
        });
        test("pool validates subnet, range and overlap",()->{
            var f=new Fixture();
            for(String bad:List.of("dhcp-pool add 10.0.1.0/24 10.0.1.0 10.0.1.100 10.0.1.1 10.0.2.10","dhcp-pool add 10.0.1.0/24 10.0.1.100 10.0.1.255 10.0.1.1 10.0.2.10","dhcp-pool add 10.0.1.0/24 10.0.1.110 10.0.1.100 10.0.1.1 10.0.2.10","dhcp-pool add 10.0.1.0/24 10.0.1.100 10.0.1.110 10.0.3.1 10.0.2.10"))check(!config(f.sim,"s",bad),"invalid scope "+bad);
            f.pool("10.0.1.100","10.0.1.110");check(!config(f.sim,"s","dhcp-pool add 10.0.1.0/25 10.0.1.100 10.0.1.120 10.0.1.1 10.0.2.10"),"overlapping scope rejected");
        });
        test("VIP excluded from allocation",()->{
            var f=new Fixture();f.r().vip="10.0.1.100";f.relay();f.pool("10.0.1.100","10.0.1.102");
            check(f.sim.dhcp("a").success()&&f.a().address.equals("10.0.1.101"),"VIP never offered");
        });
        test("new service settings survive profile codec",()->{
            var f=new Fixture();check(config(f.sim,"r","acl add 0 in 10 deny icmp any any"),"ACL before save");check(config(f.sim,"r","dhcp-relay 0 10.0.2.10"),"relay before save");
            String saved=f.r().encode();var restored=NetworkProfile.decode(DeviceKind.ROUTER,saved);check(restored.encode().equals(saved),"new codec round-trip");
            f.sim.put("r",restored);check(!f.sim.ping("a","10.0.2.10").success(),"saved ACL enforced");
        });
        test("v1 profile migration and malformed version",()->{
            var p=NetworkProfile.decode(DeviceKind.ROUTER,"AAAAAQAJ5pen6Kit5a6aAAAAAAAYAAAAAAEBAAEAAAAAAABkAQAAAAAACgALMTkyLjE2OC4xLjEAAAAYAAAAAAAAAAABAAAAAAABAAsxOTIuMTY4LjIuMQAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAAAAAAAAAAA");
            check(p.name.equals("旧設定")&&p.failed&&p.ports[0].vlan==10,"v1 state retained");
            check(p.services.policy.rules().isEmpty()&&p.services.pools().isEmpty(),"legacy has no accidental ACL");
            // v3はOSPF追加で正式対応済み。未知形式は将来の隣接番号でなく不正な値で検査する。
            String migrated=p.encode();
            check(NetworkProfile.decode(DeviceKind.ROUTER,migrated).encode().equals(migrated),"migrated current version remains readable");
            check(!p.ospf.enabled,"migration does not enable OSPF on legacy devices");
            byte[] raw=Base64.getDecoder().decode(migrated);
            java.nio.ByteBuffer.wrap(raw).putInt(Integer.MAX_VALUE);
            boolean rejected=false;try{NetworkProfile.decode(DeviceKind.ROUTER,Base64.getEncoder().encodeToString(raw));}catch(IllegalArgumentException e){rejected=true;}
            check(rejected,"unknown config version rejected");
            raw=Base64.getDecoder().decode(migrated);
            byte[] truncated=Arrays.copyOf(raw,raw.length-1);
            rejected=false;try{NetworkProfile.decode(DeviceKind.ROUTER,Base64.getEncoder().encodeToString(truncated));}catch(IllegalArgumentException e){rejected=true;}
            check(rejected,"truncated current config must not silently lose OSPF settings");
        });
        test("duplicate sequence and ACL ceiling",()->{
            var f=new Fixture();check(config(f.sim,"r","acl add 0 in 1 permit ip any any"),"first rule");
            String before=f.r().encode();check(!config(f.sim,"r","acl add 0 in 1 deny ip any any"),"duplicate sequence rejected");check(f.r().encode().equals(before),"duplicate preserves first rule");
            for(int i=2;i<=64;i++)check(config(f.sim,"r","acl add 0 in "+i+" permit ip any any"),"bounded ACL rule "+i);
            check(!config(f.sim,"r","acl add 0 in 65 permit ip any any"),"rule limit 64");
        });
        test("DHCP ACK filtering is atomic",()->{
            var f=new Fixture();f.relay();f.pool("10.0.1.100","10.0.1.100");
            check(config(f.sim,"r","acl add 0 out 10 deny udp any any 68"),"drop client ACK");check(config(f.sim,"r","acl add 0 out 20 permit ip any any"),"permit rest");
            check(!f.sim.dhcp("a").success(),"ACK drop rejected");check(f.a().address.isBlank()&&f.s().services.bindings().isEmpty(),"failed ACK creates neither address nor binding");
            check(config(f.sim,"r","acl clear 0 out"),"clear ACK filter");check(f.sim.dhcp("a").success(),"ACK recovery");
            check(f.sim.drainChangedProfiles().containsAll(List.of("a","s")),"client AND server need persistence");
        });
        System.out.println("NetworkServicesTest: "+assertions+" checks / "+failed+" failed cases");if(failed>0)throw new AssertionError("failed cases: "+failed);
    }
}
