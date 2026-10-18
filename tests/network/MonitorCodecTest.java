import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import java.nio.ByteBuffer;

public final class MonitorCodecTest {
    private static int checks;
    private static void ok(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    private static final String V9="AAAACQARbGVnYWN5LXY5LW1vbml0b3IACTEwLjQyLjAuMQAAABgAAAAAAAAAAQAAAAAAAGQBAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFAAAAFAAA09GRgAAAAAAA09GRgAAAAAAA09GRgAAAAAAA09GRgAAAAAAA09GRgAAAAAAA09GRgAAAAAAAQAETk9ORQAETk9ORQAETk9ORQAETk9ORQAETk9ORQAETk9ORQAAAAABAf////8AAAABAAAAFAAAAIAAAAAAKAAAAZAAAAEsAAAAAAAAAIAAAAAAAAAAAACAAAAAAAAAAAAAgAAAAAAAAAAAAIAAAAAAAAAAAACAAAAAAAAAAAAAgAAAAAAAAAAAAAAAAAD96AAAAAAAFAAAAFAAAAAAAAAAAAEAAAAKAAAAAAABAAAACTAuMC4wLjAvMAAAAgIAAAAG";
    public static void main(String[] args){
        var p=NetworkProfile.decode(DeviceKind.SERVER,V9);
        ok(!p.monitor.enabled&&p.monitor.targets.isEmpty()&&p.syslog.server&&p.address.equals("10.42.0.1"),"実旧v9 fixtureを既存設定維持で移行");
        p.monitor.targets.put("base","10.42.0.2");p.monitor.enabled=true;p.monitor.intervalSeconds=9;p.monitor.failures=4;p.monitor.recoveries=3;p.failed=true;
        var restored=NetworkProfile.decode(DeviceKind.SERVER,p.encode());
        ok(p.monitor.config().equals(restored.monitor.config())&&restored.failed&&restored.syslog.server,"v10往復で監視と既存設定保持");
        ok(ByteBuffer.wrap(Base64.getDecoder().decode(p.encode())).getInt()==10,"保存v10");
        byte[] bytes=Base64.getDecoder().decode(p.encode());byte[] truncated=Arrays.copyOf(bytes,bytes.length-1);boolean rejected=false;
        try{NetworkProfile.decode(DeviceKind.SERVER,Base64.getEncoder().encodeToString(truncated));}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"切れた保存データ拒否");
        var zero=new NetworkProfile(DeviceKind.PC);zero.validate();ok(!zero.monitor.enabled,"既存PCの初期値互換");
        rejected=false;try{NetworkProfile.decode(DeviceKind.PC,p.encode());}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"異なる機種への監視設定注入を拒否");
        System.out.println("MonitorCodecTest: "+checks+" checks passed");
    }
}
