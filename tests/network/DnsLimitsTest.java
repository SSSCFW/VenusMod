import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class DnsLimitsTest {
    private static int checks;
    private static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    public static void main(String[] args){
        var s=new NetworkSimulator();int successful=0;
        for(int i=0;i<65;i++){
            var c=new NetworkProfile(DeviceKind.PC);c.address="10.0.0.1";c.dns="10.0.0.2";
            var d=new NetworkProfile(DeviceKind.SERVER);d.address="10.0.0.2";
            for(int n=0;n<64;n++)d.dnsSettings.set(d,"n"+n+".local",1,List.of("10.0.1.1","10.0.1.2"));
            s.put("c"+i,c);s.put("d"+i,d);s.link(new NetworkSimulator.Link("l"+i,"c"+i,0,"d"+i,0,CableGrade.CAT5E,1,false));
            for(int n=0;n<64;n++)if(s.dnsResolver().lookup("c"+i,"n"+n+".local").success())successful++;
        }
        var resolver=s.dnsResolver();ok(successful==65*64,"all bounded fixture lookups succeeded");
        ok(resolver.cacheSize()==4096&&resolver.deadlineCount()==4096,"global cache and single deadline ceiling");
        ok(resolver.cursorCount()==2048,"authoritative rotation cursor ceiling");
        for(int n=0;n<1000;n++)resolver.lookup("c64","n63.local");
        ok(resolver.cacheSize()==4096&&resolver.deadlineCount()==4096,"1000 cache hits do not accumulate deadlines");
        resolver.lookup("c64","extra.local");ok(resolver.describe("c64").size()==64,"per-client positive plus negative count bound");
        resolver.advance(20);ok(resolver.cacheSize()>=4000&&resolver.deadlineCount()==resolver.cacheSize(),"expiry work capped instead of sweeping all entries");
        var after=resolver.lookup("c64","n63.local");ok(after.success()&&!after.cached()&&after.ttlTicks()==20,"expired-but-not-swept entry never served");
        resolver.clear("c64");ok(resolver.describe("c64").getFirst().contains("空"),"clear removes own records");
        ok(resolver.cacheSize()==resolver.deadlineCount(),"clear removes associated deadlines");
        resolver.advance(Long.MAX_VALUE);after=resolver.lookup("c63","n63.local");ok(after.success()&&!after.cached(),"large clock advance does not wrap TTL");
        resolver.advance(Long.MAX_VALUE);ok(resolver.clock()==Long.MAX_VALUE&&resolver.deadlineCount()==resolver.cacheSize(),"saturating time with bounded cleanup");
        // 不正設定の原子的拒否と記録数制限。
        var f=new DnsContractTest.Fixture();f.set("dns-record set good.local 60 10.1.0.20 10.1.0.21");String before=f.sim.nodes().get("d").encode();
        for(String bad:List.of("dns-record set 10.1.0.20","dns-record ttl 10.1.0.20","dns-record list 10.1.0.20","dns-record set good.local -1 10.1.0.20","dns-record set good.local 86401 10.1.0.20","dns-record set good.local 1 10.1.0.20 010.001.000.020","dns-record set good.local 1 bad","dns-record ttl absent.local 10","dns-negative-ttl -1","dns-negative-ttl 301","dns-server true","dns-record set good.local 1 1.1.1.1 1.1.1.2 1.1.1.3 1.1.1.4 1.1.1.5 1.1.1.6 1.1.1.7 1.1.1.8 1.1.1.9")){
            ok(!f.cli("d",bad).changed()&&before.equals(f.sim.nodes().get("d").encode()),"reject without changing valid config: "+bad);
        }
        ok(!f.cli("c","dns-record fake.local 10.1.0.20").changed(),"PC cannot turn into authoritative DNS");
        for(int n=0;n<63;n++)f.set("dns-record n"+n+".local 10.1.0.20");before=f.sim.nodes().get("d").encode();
        ok(!f.cli("d","dns-record overflow.local 10.1.0.20").changed()&&before.equals(f.sim.nodes().get("d").encode()),"64-name limit transactional");
        f.set("dns-record good.local 10.1.0.22");ok(f.sim.nodes().get("d").dnsSettings.records(f.sim.nodes().get("d"),"good.local").addresses().equals(List.of("10.1.0.22")),"legacy replacement removes extra records");
        System.out.println("DnsLimitsTest: "+checks+" checks passed (4160 fill queries / 1000 repeated cache hits are not separate test cases)");
    }
}
