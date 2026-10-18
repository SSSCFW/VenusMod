import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public final class NetworkFormSafetyTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static NetworkForm.Values fields(NetworkForm.View page,Map<String,String> changes){var v=page.values();return new NetworkForm.Values(v.scope(),changes.getOrDefault("name",v.name()),changes.getOrDefault("ip",v.address()),changes.getOrDefault("prefix",v.prefix()),changes.getOrDefault("gw",v.gateway()),changes.getOrDefault("dns",v.dns()),Boolean.parseBoolean(changes.getOrDefault("enabled",""+v.enabled())),Boolean.parseBoolean(changes.getOrDefault("trunk",""+v.trunk())),Boolean.parseBoolean(changes.getOrDefault("optical",""+v.optical())),changes.getOrDefault("vlan",v.vlan()),changes.getOrDefault("speed",v.speed()));}
    private static NetworkForm.Outcome edit(NetworkSimulator sim,String id,int scope,Map<String,String> changes){var page=NetworkForm.capture(sim.nodes().get(id),scope);return NetworkForm.apply(sim,id,NetworkForm.encode(page),NetworkForm.encode(fields(page,changes)));}
    public static void main(String[] args)throws Exception{
        var sim=new NetworkSimulator();var a=new NetworkProfile(DeviceKind.PC);a.address="10.0.0.10";var b=new NetworkProfile(DeviceKind.SERVER);b.address="10.0.0.20";b.web=true;sim.put("a",a);sim.put("b",b);sim.link(new NetworkSimulator.Link("ab","a",0,"b",0,CableGrade.CAT5E,2,false));
        check(sim.startTransfer("a",b.address,1_000_000_000L).success(),"baseline transfer");long flow=sim.flows().getFirst().id;
        var r=edit(sim,"a",-1,Map.of("name","識別名"));check(r.accepted()&&sim.flows().getFirst().id==flow,"name-only retains flow");
        r=edit(sim,"a",-1,Map.of("dns",b.address));check(r.accepted()&&sim.flows().getFirst().id==flow,"DNS client setting retains data flow");
        b.dnsSettings.set(b,"plant.local",60,List.of(b.address));check(sim.dnsResolver().lookup("a","plant.local").success(),"populate DNS cache");
        edit(sim,"a",-1,Map.of("dns","10.0.0.21"));edit(sim,"a",-1,Map.of("dns",b.address));
        check(!sim.dnsResolver().lookup("a","plant.local").cached(),"DNS server edit clears only local resolver cache");
        for(var pair:List.of(Map.of("name","x\n"),Map.of("name","x".repeat(49)),Map.of("prefix","33"),Map.of("prefix","-1"),Map.of("prefix","+1"),Map.of("dns","外部.local"),Map.of("gw","300.0.0.1"))){
            String old=sim.nodes().get("a").encode();r=edit(sim,"a",-1,pair);check(!r.accepted()&&sim.nodes().get("a").encode().equals(old),"invalid basic is atomic: "+pair);check(sim.flows().getFirst().id==flow,"invalid form preserves active flow");
        }
        for(var pair:List.of(Map.of("vlan","0"),Map.of("vlan","4095"),Map.of("vlan","1.1"),Map.of("speed","-1"),Map.of("speed","9223372036854775807"),Map.of("speed","123"),Map.of("ip","10.0.1.2"),Map.of("optical","true"),Map.of("dns","10.0.0.1"))){
            String old=sim.nodes().get("a").encode();r=edit(sim,"a",0,pair);check(!r.accepted()&&sim.nodes().get("a").encode().equals(old),"invalid port is atomic: "+pair);
        }
        var page=NetworkForm.capture(sim.nodes().get("a"),0);var forged=new NetworkForm.View(page.kind(),page.values(),400_000_000_000L,true);
        r=NetworkForm.apply(sim,"a",NetworkForm.encode(forged),NetworkForm.encode(fields(page,Map.of("speed","400000000000"))));check(!r.accepted(),"client cannot forge capability snapshot");
        var basic=NetworkForm.capture(sim.nodes().get("a"),-1);r=NetworkForm.apply(sim,"a",NetworkForm.encode(basic),NetworkForm.encode(page.values()));check(!r.accepted(),"cross-scope edit rejected");
        r=NetworkForm.apply(sim,"a",NetworkForm.encode(new NetworkForm.View(DeviceKind.ROUTER,basic.values(),0,false)),NetworkForm.encode(basic.values()));check(!r.accepted(),"forged kind rejected");
        byte[] raw=Base64.getDecoder().decode(NetworkForm.encode(page));var extra=Arrays.copyOf(raw,raw.length+1);
        r=NetworkForm.apply(sim,"a",Base64.getEncoder().encodeToString(extra),NetworkForm.encode(page.values()));check(!r.accepted(),"trailing view bytes rejected");
        r=NetworkForm.apply(sim,"a",NetworkForm.encode(page),Base64.getEncoder().encodeToString(Arrays.copyOf(Base64.getDecoder().decode(NetworkForm.encode(page.values())),4)));check(!r.accepted(),"truncated edit rejected");
        // 別ポートだけでなく別サービスへの同時編集も最新オブジェクトからコピーして保持する。
        var serverPage=NetworkForm.capture(b,-1);b.dnsSettings.set(b,"new.local",19,List.of("10.0.0.30"));b.failed=true;b.publicService=true;b.processor=4;b.ports[0].moduleTier=2;
        r=NetworkForm.apply(sim,"b",NetworkForm.encode(serverPage),NetworkForm.encode(fields(serverPage,Map.of("name","new-name"))));
        check(r.accepted()&&b.dnsRecords.containsKey("new.local")&&b.failed&&b.publicService&&b.processor==4&&b.ports[0].moduleTier==2,"form does not roll back services/faults/upgrades");
        check(!new String(Base64.getDecoder().decode(NetworkForm.encode(NetworkForm.capture(b,-1))),StandardCharsets.ISO_8859_1).contains("new.local"),"snapshot excludes DNS records");
        // 抜去で実現不可になった既存速度を維持しながら管理停止でき、Autoに戻せる。
        var p=sim.nodes().get("a");p.ports[0].moduleTier=2;p.ports[0].speedLimitBps=40_000_000_000L;p.ports[0].optical=true;p.ports[0].moduleTier=0;
        r=edit(sim,"a",0,Map.of("enabled","false"));check(r.accepted()&&sim.nodes().get("a").ports[0].speedLimitBps==40_000_000_000L,"unavailable existing settings not silently lowered");
        r=edit(sim,"a",0,Map.of("speed","0","optical","false","enabled","true"));check(r.accepted()&&sim.ping("a","10.0.0.20").success()==false,"reset allowed; failed peer still not reachable");
        for(DeviceKind kind:DeviceKind.values()){
            var profile=new NetworkProfile(kind);var pages=NetworkForm.captureAll(profile);check(pages.size()==7,"one basic and six ports "+kind);
            for(String encoded:pages){check(encoded.length()<=NetworkForm.MAX_ENCODED,"bounded snapshot "+kind);var v=NetworkForm.decodeView(encoded);check(v.equals(NetworkForm.decodeView(NetworkForm.encode(v))),"roundtrip "+kind);}
        }
        var old=NetworkProfile.decode(DeviceKind.PC,sim.nodes().get("a").encode());check(old.address.equals(sim.nodes().get("a").address),"persist via existing codec");
        check(java.nio.ByteBuffer.wrap(Base64.getDecoder().decode(old.encode())).getInt()==9,"Syslogを含む保存v9、フォームは既存フィールドを維持");
        System.out.println("NetworkFormSafetyTest: "+checks+" checks passed");
    }
}
