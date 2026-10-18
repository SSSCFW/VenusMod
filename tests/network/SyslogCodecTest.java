import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import java.nio.ByteBuffer;

public final class SyslogCodecTest {
    private static int checks;
    private static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static final String V8="AAAACAANbGVnYWN5LXN5c2xvZwAJMTAuMC4wLjUzAAAAGAAAAAABAAEBAAAAAAAAZAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAAAAAAAAAABAApiYXNlLmxvY2FsAAkxMC4wLjAuMjAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABQAAABQAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAAEABE5PTkUABE5PTkUABE5PTkUABE5PTkUABE5PTkUABE5PTkUAAAAAAQH/////AAAAAQAAABQAAACAAAAAACgAAAGQAAABLAAAAAAAAACAAAAAAAAAAAAAgAAAAAAAAAAAAIAAAAAAAAAAAACAAAAAAAAAAAAAgAAAAAAAAAAAAIAAAAAAAAAAAAAAAAAA/egAAAAAABQAAABQAAAAAAAAAAABAAAACgAAAAA=";
    public static void main(String[] args)throws Exception{
        var old=NetworkProfile.decode(DeviceKind.SERVER,V8);ok(!old.syslog.enabled&&!old.syslog.server&&old.syslog.target.isEmpty(),"旧v8はSyslog無効で移行");
        ok(old.name.equals("legacy-syslog")&&old.failed&&old.web&&old.dnsRecords.get("base.local").equals("10.0.0.20"),"旧設定を維持");
        old.syslog.enabled=true;old.syslog.server=true;old.syslog.target="10.0.0.2";old.syslog.port=5514;old.syslog.level=4;old.syslog.allow="10.0.0.0/24";
        String encoded=old.encode();ok(ByteBuffer.wrap(Base64.getDecoder().decode(encoded)).getInt()==10,"保存v10");
        var decoded=NetworkProfile.decode(DeviceKind.SERVER,encoded);ok(decoded.encode().equals(encoded),"設定全体の往復");
        ok(decoded.syslog.enabled&&decoded.syslog.server&&decoded.syslog.port==5514&&decoded.syslog.level==4&&decoded.syslog.allow.equals("10.0.0.0/24"),"Syslogフィールド往復");
        // v10の監視拡張より前にあるSyslog severityを壊す。新しい末尾の対象件数とは区別する。
        var tail=new java.io.ByteArrayOutputStream();old.monitor.write(new java.io.DataOutputStream(tail));
        byte[] bytes=Base64.getDecoder().decode(encoded);int severityOffset=bytes.length-tail.size()-4;
        ok(ByteBuffer.wrap(bytes).getInt(severityOffset)==4,"破損対象はSyslog severityフィールド");ByteBuffer.wrap(bytes).putInt(severityOffset,8);boolean rejected=false;try{NetworkProfile.decode(DeviceKind.SERVER,Base64.getEncoder().encodeToString(bytes));}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"不正保存severityを拒否");
        var sim=new NetworkSimulator();var a=new NetworkProfile(DeviceKind.PC);a.address="10.0.0.1";var b=new NetworkProfile(DeviceKind.PC);b.address="10.0.0.2";sim.put("a",a);sim.put("b",b);sim.link(new NetworkSimulator.Link("ab","a",0,"b",0,CableGrade.CAT5E,1,false));
        ok(sim.startTransfer("a","10.0.0.2",1_000_000_000).success(),"転送開始");
        for(String command:List.of("syslog target 10.0.0.2","syslog on","syslog level 3","syslog allow 10.0.0.0/24","syslog off")){
            var r=NetworkServiceCommands.run(sim,"a",command);ok(r.changed()&&sim.flows().size()==1,"Syslog設定で通信を切らない: "+command);}
        String unchanged=sim.nodes().get("a").encode();
        for(String command:List.of("syslog on extra","syslog target","syslog target http://10.0.0.2","syslog target 224.0.0.1","syslog target 0.0.0.0","syslog target 10.0.0.2 65536","syslog target none extra","syslog level -1","syslog level 8","syslog server on","syslog allow invalid","syslog test 8 fail","syslog logs 1000","syslog clear other")){
            var r=NetworkServiceCommands.run(sim,"a",command);ok(r.handled()&&!r.changed()&&sim.nodes().get("a").encode().equals(unchanged)&&sim.flows().size()==1,"不正入力で設定・転送維持: "+command);}
        var restored=new NetworkSimulator();restored.put("d",decoded);ok(restored.syslog().pending()==0&&restored.syslog().retained()==0,"復元で配送待ち・受信ログを再生成しない");
        System.out.println("SyslogCodecTest: "+checks+" checks passed");
    }
}
