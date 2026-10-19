import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.nio.ByteBuffer;
import java.util.*;
public final class PortDegradationCodecTest {
    private static int checks;
    private static void ok(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
    private static void refused(byte[] bytes){boolean rejected=false;try{NetworkProfile.decode(DeviceKind.L2_SWITCH,Base64.getEncoder().encodeToString(bytes));}catch(IllegalArgumentException expected){rejected=true;}ok(rejected,"malformed degradation mask refused");}
    private static final String V15="AAAADwAKbGVnYWN5LXYxNQAAAAAAGAAAAAAAAAABAAAAAAAAZAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAABfXhAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAUAAAAUAADT0ZGAAAAAAADT0ZGAAAAAAADT0ZGAAAAAAADT0ZGAAAAAAADT0ZGAAAAAAADT0ZGAAAAAAABAAROT05FAAROT05FAAROT05FAAROT05FAAROT05FAAROT05FAAAAAAEB/////wAAAAEAAAAUAAAAgAAAAAAoAAABkAAAASwAAAAAAAAAgAAAAAAAAAAAAIAAAAAAAAAAAACAAAAAAAAAAAAAgAAAAAAAAAAAAIAAAAAAAAAAAACAAAAAAAAAAAAAAAAAAP3oAAAAAAAUAAAAUAAAAAAAAAAAAQAAAAoAAAAAAAAAAAAJMC4wLjAuMC8wAAACAgAAAAYAAAAABQAAAAMAAAACAAAAAAAAAAAABEhPTEQAAAAFAAAAAAAAAAAAAAB7AAAAHgAAAAAAAAAAAAAAAAADYWxsAANhbGwAA2FsbAADYWxsAANhbGwAA2FsbAQ=";
    public static void main(String[] args){
        var old=NetworkProfile.decode(DeviceKind.L2_SWITCH,V15);
        ok(old.name.equals("legacy-v15")&&old.ports[2].failed&&old.ports[1].speedLimitBps==100000000L,"actual old v15 fixture retained");
        ok(Arrays.stream(old.ports).noneMatch(p->p.degraded),"old world not degraded by migration");
        for(var kind:DeviceKind.values()){
            var p=new NetworkProfile(kind);p.ports[0].degraded=true;p.ports[1].failed=true;p.ports[5].degraded=true;p.ports[5].enabled=false;
            var saved=p.encode();ok(ByteBuffer.wrap(Base64.getDecoder().decode(saved)).getInt()==16,"writes v16");
            var copy=NetworkProfile.decode(kind,saved);ok(copy.ports[0].degraded&&copy.ports[1].failed&&!copy.ports[1].degraded&&!copy.ports[5].enabled&&copy.ports[5].degraded,"roundtrip "+kind);
            var backup=ConfigBackup.capture("device",p,0);var backupProfile=NetworkProfile.decode(kind,backup.configuration());
            ok(!backupProfile.ports[0].degraded&&!backupProfile.ports[1].failed,"backup excludes all hardware faults");
            p.ports[0].degraded=false;p.ports[3].degraded=true;
            var restored=backup.restored(p);ok(!restored.ports[0].degraded&&restored.ports[3].degraded&&restored.ports[1].failed,"restore retains current hardware not saved hardware");
        }
        byte[] bytes=Base64.getDecoder().decode(old.encode());
        for(int mask:new int[]{64,128,255}){var bad=bytes.clone();bad[bad.length-1]=(byte)mask;refused(bad);}
        var both=bytes.clone();both[both.length-1]=4;refused(both);
        refused(Arrays.copyOf(bytes,bytes.length-1));refused(Arrays.copyOf(bytes,bytes.length+1));
        System.out.println("PortDegradationCodecTest: "+checks+" checks passed");
    }
}
