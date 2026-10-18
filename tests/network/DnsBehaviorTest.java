import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class DnsBehaviorTest {
    private static int checks;
    private static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static DnsContractTest.Fixture fixture(){return new DnsContractTest.Fixture();}
    public static void main(String[] args){
        var f=fixture();f.set("dns-record set base.local 2 10.1.0.20");var dns=f.sim.dnsResolver();
        var first=dns.lookup("c","BASE.local.");ok(first.success()&&!first.cached()&&first.ttlTicks()==40,"cold cache/normalization");
        f.sim.advance(19);var hit=dns.lookup("c","base.local");ok(hit.cached()&&hit.ttlTicks()==21,"TTL decreases");
        f.sim.advance(20);hit=dns.lookup("c","base.local");ok(hit.cached()&&hit.ttlTicks()==1,"boundary before expiry");
        for(int i=0;i<50;i++)dns.describe("c");ok(dns.lookup("c","base.local").ttlTicks()==1&&dns.deadlineCount()==1,"view does not renew or duplicate deadlines");
        f.sim.advance(1);ok(!dns.lookup("c","base.local").cached(),"exact TTL expiry");
        f.set("dns-server off");ok(dns.lookup("c","base.local").success()&&dns.lookup("c","base.local").cached(),"positive cache usable while server is stopped");
        ok(!dns.query("c","base.local",null).success(),"nslookup bypasses cache");
        f.sim.advance(40);ok(!dns.lookup("c","base.local").success()&&dns.cacheSize()==0,"expired cache not served offline");
        f.set("dns-server on");ok(dns.lookup("c","base.local").success(),"transport failure not negatively cached");
        f.set("dns-record set pool.local 0 10.1.0.20 10.1.0.21 10.1.0.22");
        var a=dns.lookup("c","pool.local");var b=dns.lookup("c","pool.local");var c=dns.lookup("c","pool.local");var d=dns.lookup("c","pool.local");
        ok(a.addresses().equals(List.of("10.1.0.20","10.1.0.21","10.1.0.22")),"whole RRset");
        ok(b.selected().equals("10.1.0.21")&&c.selected().equals("10.1.0.22")&&d.selected().equals("10.1.0.20"),"round robin sequence");
        ok(!a.cached()&&!b.cached()&&dns.deadlineCount()==1,"TTL zero never cached");
        f.set("dns-record set sticky.local 30 10.1.0.20 10.1.0.21");a=dns.lookup("c","sticky.local");b=dns.lookup("c","sticky.local");
        ok(a.addresses().equals(b.addresses())&&b.cached(),"cached order stays fixed");
        var bypass=dns.query("c","sticky.local",null);ok(!bypass.cached()&&!bypass.selected().equals(a.selected()),"direct diagnostic rotates independently");
        ok(dns.lookup("c","sticky.local").selected().equals(a.selected()),"diagnostic doesn't overwrite app cache");
        f.set("dns-negative-ttl 1");a=dns.lookup("c","missing.local");ok(a.negative()&&!a.success()&&!a.cached()&&a.ttlTicks()==20,"authoritative NXDOMAIN");
        f.set("dns-record missing.local 10.1.0.20");b=dns.lookup("c","missing.local");ok(b.negative()&&b.cached(),"negative TTL retained after record creation");
        ok(dns.query("c","missing.local",null).success(),"diagnostic sees newly created record");
        f.sim.advance(20);ok(dns.lookup("c","missing.local").success(),"negative expires exactly");
        f.set("dns-negative-ttl 0");a=dns.lookup("c","nocache.local");b=dns.lookup("c","nocache.local");ok(a.negative()&&b.negative()&&!b.cached(),"zero negative TTL");
        var fresh=fixture();fresh.set("dns-record base.local 10.1.0.20");fresh.sim.nodes().get("d").failed=true;
        a=fresh.sim.dnsResolver().lookup("c","base.local");ok(!a.success()&&!a.negative()&&fresh.sim.dnsResolver().cacheSize()==0,"failed DNS is not NXDOMAIN");
        fresh.sim.nodes().get("d").failed=false;ok(fresh.sim.dnsResolver().lookup("c","base.local").success(),"retry after failure");
        fresh.sim.nodes().get("c").dns="10.1.0.20";fresh.sim.nodes().get("w").dnsRecords.put("base.local","10.1.0.21");
        ok(fresh.sim.dnsResolver().lookup("c","base.local").selected().equals("10.1.0.21"),"changing resolver avoids old cached entry");
        fresh.sim.nodes().get("c").ports[0].vlan=5;ok(!fresh.sim.dnsResolver().lookup("c","base.local").cached(),"VLAN change resets client context");
        fresh.sim.remove("c");ok(fresh.sim.dnsResolver().cacheSize()==0,"removed client cache released");
        f.sim.nodes().get("c").failed=true;ok(!dns.lookup("c","sticky.local").success(),"failed client cannot use cached resolution");
        var ep=f.sim.resolveEndpoint("absent","010.001.002.003:5000",80);ok(ep.address().equals("10.1.2.3")&&ep.port()==5000,"literal parser retains numeric normalization, no DNS");
        for(String invalid:List.of("http://base.local","base.local/path","base.local?x","base.local#x","bad..name","base.local..","127.1","256.1.1.1","1234","[::1]","::1","a@host","base.local:0","base.local:65536","base.local:abc","-bad.local","bad-.local","a".repeat(65),"bad\nname","", "例.jp")){
            boolean rejected=false;try{f.sim.resolveEndpoint("c",invalid,80);}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"reject unsafe/invalid name: "+invalid);
        }
        // 既存通信を再確立せずに権威データだけを変更できること。
        var live=fixture();live.set("dns-record base.local 10.1.0.20");ok(live.sim.startTransfer("c","10.1.0.20",100_000_000).success(),"transfer started");
        live.set("dns-record ttl base.local 5");ok(live.sim.flows().size()==1,"DNS-only edit preserves active data flow");
        var toggle=fixture();toggle.set("dns-record base.local 10.1.0.20");toggle.sim.dnsResolver().lookup("c","base.local");
        toggle.cli("c","dns none");toggle.cli("c","dns 10.1.0.53");
        ok(!toggle.sim.dnsResolver().lookup("c","base.local").cached(),"changing DNS away and back clears old cache immediately");
        System.out.println("DnsBehaviorTest: "+checks+" checks passed");
    }
}
