import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import java.nio.ByteBuffer;
import java.io.*;

/** 固定fixtureは変更前v10クラスから生成。現在のデータを切り詰めた偽旧形式は使わない。 */
public final class MonitorServiceCodecTest {
    private static int checks;
    private static void ok(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    private static final String V10="AAAACgALbW9uaXRvci12MTAACTEwLjgwLjAuMQAAABgAAAAAAQABAQAAAAAAAGQBAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAAAAAAAAAAAAQAKYmFzZS5sb2NhbAAJMTAuODAuMC4zAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAUAAAAUAADT0ZGAAAAAAADT0ZGAAAAAAADT0ZGAAAAAAADT0ZGAAAAAAADT0ZGAAAAAAADT0ZGAAAAAAABAAROT05FAAROT05FAAROT05FAAROT05FAAROT05FAAROT05FAAAAAAEB/////wAAAAEAAAAUAAAAgAAAAAAoAAABkAAAASwAAAAAAAAAgAAAAAAAAAAAAIAAAAAAAAAAAACAAAAAAAAAAAAAgAAAAAAAAAAAAIAAAAAAAAAAAACAAAAAAAAAAAAAAAAAAP3oAAAAAAAUAAAAUAAAAAAAAAAAAQAAAAoAAAAAAAAAAAAJMC4wLjAuMC8wAAACAgAAAAYBAAAABwAAAAQAAAACAAAAAQAGbGVnYWN5AAkxMC44MC4wLjI=";
    private static boolean rejected(byte[] bytes){try{NetworkProfile.decode(DeviceKind.SERVER,Base64.getEncoder().encodeToString(bytes));return false;}catch(IllegalArgumentException expected){return true;}}
    public static void main(String[] args)throws Exception{
        var p=NetworkProfile.decode(DeviceKind.SERVER,V10);
        ok(p.name.equals("monitor-v10")&&p.failed&&p.web&&p.dnsRecords.get("base.local").equals("10.80.0.3"),"既存設定と故障状態を維持");
        ok(p.monitor.enabled&&p.monitor.intervalSeconds==7&&p.monitor.failures==4&&p.monitor.recoveries==2,"旧監視方針を維持");
        ok(p.monitor.services.isEmpty()&&p.monitor.config().target("legacy").kind()==MonitorTarget.Kind.ICMP,"旧対象は自動的にICMPとして移行");
        for(var kind:MonitorTarget.Kind.values())if(kind!=MonitorTarget.Kind.ICMP){String label=kind.label();p.monitor.targets.put(label,"10.80.0.3");p.monitor.services.put(label,new MonitorTarget.Service(kind,kind.servicePort()+100));}
        p.validate();var encoded=p.encode();var saved=NetworkProfile.decode(DeviceKind.SERVER,encoded);
        ok(ByteBuffer.wrap(Base64.getDecoder().decode(encoded)).getInt()==16,"保存v16");
        ok(saved.monitor.config().equals(p.monitor.config())&&saved.failed,"全種類・公開ポートを保存復元");
        var copy=new MonitorSettings();copy.copyFrom(p.monitor);p.monitor.services.clear();
        ok(copy.services.size()==4&&saved.monitor.services.size()==4,"設定コピーの参照を共有しない");
        byte[] bytes=Base64.getDecoder().decode(encoded);ok(rejected(Arrays.copyOf(bytes,bytes.length-1)),"壊れた追加節を拒否");
        ok(rejected(Arrays.copyOf(bytes,bytes.length+1)),"余分な末尾データを拒否");
        // サービス拡張前の旧実fixtureへ、不正なv11追加節を追記する。
        for(int mode=0;mode<5;mode++){
            var outBytes=new ByteArrayOutputStream();byte[] base=Base64.getDecoder().decode(V10);ByteBuffer.wrap(base).putInt(11);outBytes.write(base);var out=new DataOutputStream(outBytes);
            out.writeInt(mode==4?17:mode==3?2:1);
            if(mode!=4){out.writeUTF(mode==0?"orphan":"legacy");out.writeUTF(mode==1?"UNKNOWN_KIND":"WEB");out.writeInt(mode==2?0:80);}
            if(mode==3){out.writeUTF("legacy");out.writeUTF("DNS");out.writeInt(53);}
            ok(rejected(outBytes.toByteArray()),"孤立/種類/ポート/重複/件数を拒否 mode="+mode);
        }
        var sim=new NetworkSimulator();saved.failed=false;sim.put("new",saved);
        ok(sim.monitor().snapshots("new").stream().allMatch(x->x.state()==MonitorRuntime.State.UNKNOWN&&x.probes()==0),"保存した設定から観測履歴を捏造しない");
        var p2=new NetworkProfile(DeviceKind.SERVER);p2.monitor.services.put("orphan",new MonitorTarget.Service(MonitorTarget.Kind.WEB,80));boolean bad=false;
        try{p2.validate();}catch(IllegalArgumentException e){bad=true;}ok(bad,"設定に孤立した種類を入れられない");
        System.out.println("MonitorServiceCodecTest: "+checks+" checks passed");
    }
}
