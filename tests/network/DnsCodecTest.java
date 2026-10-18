import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import java.nio.ByteBuffer;

public final class DnsCodecTest {
    private static int checks;
    private static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    // 変更前v7クラスを実行して生成した固定fixture。現行encodeを切り詰めていない。
    private static final String V7="AAAABwAKbGVnYWN5LWRucwAJMTAuMS4wLjUzAAAAGAAAAAABAAABAAAAAAAAZAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAAAAAAAAAACAAlPbGQuTG9jYWwACTEwLjEuMC4yMAAMb2xkLi5pbnZhbGlkAAkxMC4xLjAuMjEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABQAAABQAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAAEABE5PTkUABE5PTkUABE5PTkUABE5PTkUABE5PTkUABE5PTkUAAAAAAQH/////AAAAAQAAABQAAACAAAAAACgAAAGQAAABLAAAAAAAAACAAAAAAAAAAAAAgAAAAAAAAAAAAIAAAAAAAAAAAACAAAAAAAAAAAAAgAAAAAAAAAAAAIAAAAAAAAAAAAAAAAAA/egAAAAAABQAAABQAAAAAAAAAAA=";
    public static void main(String[] args){
        var p=NetworkProfile.decode(DeviceKind.SERVER,V7);
        ok(p.failed&&p.name.equals("legacy-dns")&&p.dnsRecords.get("Old.Local").equals("10.1.0.20"),"old v7 values preserved");
        ok(p.dnsRecords.containsKey("old..invalid"),"legacy unusual names aren't silently deleted");
        ok(p.dnsSettings.enabled&&p.dnsSettings.records(p,"OLD.local.").ttl()==60,"old DNS default TTL and canonical lookup");
        p.failed=false;var sim=new NetworkSimulator();sim.put("legacy",p);
        var r=NetworkCli.run(sim,"legacy","dns-record delete old..invalid");ok(r.changed()&&!p.dnsRecords.containsKey("old..invalid"),"old bad name removable without losing other data");
        r=NetworkCli.run(sim,"legacy","dns-record set NEW.local. 42 10.1.0.21 10.1.0.22");ok(r.changed(),"multiple A setup");
        r=NetworkCli.run(sim,"legacy","dns-negative-ttl 7");ok(r.changed(),"negative TTL setup");
        r=NetworkCli.run(sim,"legacy","dns-server off");ok(r.changed(),"disabled state setup");
        String encoded=p.encode();ok(ByteBuffer.wrap(Base64.getDecoder().decode(encoded)).getInt()==9,"new format v9");
        var restored=NetworkProfile.decode(DeviceKind.SERVER,encoded);var records=restored.dnsSettings.records(restored,"new.local");
        ok(records.ttl()==42&&records.addresses().equals(List.of("10.1.0.21","10.1.0.22")),"rrset and ttl round trip");
        ok(!restored.dnsSettings.enabled&&restored.dnsSettings.negativeTtl==7&&restored.dnsRecords.containsKey("Old.Local"),"service state plus legacy record retained");
        ok(restored.encode().equals(encoded),"deterministic round trip");
        var fresh=new NetworkSimulator();fresh.put("legacy",restored);ok(fresh.dnsResolver().cacheSize()==0&&fresh.dnsResolver().cursorCount()==0,"dynamic DNS state not persisted");
        byte[] raw=Base64.getDecoder().decode(encoded);
        for(int trim:List.of(1,4,30)){boolean rejected=false;try{NetworkProfile.decode(DeviceKind.SERVER,Base64.getEncoder().encodeToString(Arrays.copyOf(raw,raw.length-trim)));}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"truncated data rejected");}
        ByteBuffer.wrap(raw).putInt(Integer.MAX_VALUE);boolean rejected=false;try{NetworkProfile.decode(DeviceKind.SERVER,Base64.getEncoder().encodeToString(raw));}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"unknown format rejected");
        // v8拡張の負数TTLや過大回答数を復号時に棄却。
        raw=Base64.getDecoder().decode(encoded);byte[] marker="new.local".getBytes(java.nio.charset.StandardCharsets.UTF_8);int index=-1;
        outer:for(int i=raw.length-marker.length;i>=0;i--){for(int j=0;j<marker.length;j++)if(raw[i+j]!=marker[j])continue outer;index=i;break;}
        ok(index>0,"located extension record marker");
        ByteBuffer.wrap(raw).putInt(index+marker.length,-1);rejected=false;try{NetworkProfile.decode(DeviceKind.SERVER,Base64.getEncoder().encodeToString(raw));}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"negative persisted TTL rejected");
        raw=Base64.getDecoder().decode(encoded);ByteBuffer.wrap(raw).putInt(index+marker.length+4,999999);rejected=false;try{NetworkProfile.decode(DeviceKind.SERVER,Base64.getEncoder().encodeToString(raw));}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"oversized persisted RRset rejected before allocation");
        System.out.println("DnsCodecTest: "+checks+" checks passed");
    }
}
