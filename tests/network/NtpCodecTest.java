import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import java.nio.ByteBuffer;
public final class NtpCodecTest {
    static int checks;
    static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    static final String V12="AAAADAALdjEyIGZpeHR1cmUACTE5Mi4wLjIuOQAAABgAAAAAAAAAAQAAAAAAAGQBAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFAAAAFAAA09GRgAAAAAAA09GRgAAAAAAA09GRgAAAAAAA09GRgAAAAAAA09GRgAAAAAAA09GRgAAAAAAAQAETk9ORQAETk9ORQAETk9ORQAETk9ORQAETk9ORQAETk9ORQAAAAABAf////8AAAABAAAAFAAAAIAAAAAAKAAAAZAAAAEsAAAAAAAAAIAAAAAAAAAAAACAAAAAAAAAAAAAgAAAAAAAAAAAAIAAAAAAAAAAAACAAAAAAAAAAAAAgAAAAAAAAAAAAAAAAAD96AAAAAAAFAAAAFAAAAAAAAAAAAEAAAAKAAAAAAEAAAkxOTIuMC4yLjMACTAuMC4wLjAvMAAAAgIAAAAGAAAAAAUAAAADAAAAAgAAAAAAAAAAAARIT0xEAAAABQAAAAA=";
    public static void main(String[] args){
        var old=NetworkProfile.decode(DeviceKind.SERVER,V12);
        ok(!old.ntp.enabled&&!old.ntp.server&&old.ntp.offsetMillis==0,"old v12 device keeps NTP off");
        ok(old.name.equals("v12 fixture")&&old.address.equals("192.0.2.9")&&old.syslog.target.equals("192.0.2.3"),"old settings preserved");
        old.ntp.enabled=true;old.ntp.target="192.0.2.123";old.ntp.port=8123;old.ntp.intervalSeconds=7;old.ntp.offsetMillis=-3456;old.ntp.driftPpm=25;
        String encoded=old.encode();ok(ByteBuffer.wrap(Base64.getDecoder().decode(encoded)).getInt()==13,"new format v13");
        var copy=NetworkProfile.decode(DeviceKind.SERVER,encoded);ok(copy.ntp.config().equals(old.ntp.config()),"NTP configuration round trip");ok(encoded.equals(copy.encode()),"full encoding stable");
        var s=new NetworkSimulator();s.put("old",old);s.put("copy",copy);ok(s.ntp().snapshot("copy").samples()==0&&s.ntp().snapshot("copy").state().equals("WAITING"),"restoring doesn't fabricate a successful sample");
        byte[] bytes=Base64.getDecoder().decode(encoded);
        for(int length:new int[]{bytes.length-1,bytes.length+1}){boolean refused=false;try{NetworkProfile.decode(DeviceKind.SERVER,Base64.getEncoder().encodeToString(Arrays.copyOf(bytes,length)));}catch(IllegalArgumentException e){refused=true;}ok(refused,"truncated or trailing data refused");}
        byte[] bad=bytes.clone();ByteBuffer.wrap(bad).putInt(bad.length-4,1001);boolean refused=false;try{NetworkProfile.decode(DeviceKind.SERVER,Base64.getEncoder().encodeToString(bad));}catch(IllegalArgumentException e){refused=true;}ok(refused,"out-of-range persisted drift rejected");
        String unchanged=s.nodes().get("old").encode();
        for(String command:List.of("ntp server on","ntp on extra","ntp target 0.0.0.0","ntp target 224.0.0.1","ntp target time.example","ntp target http://192.0.2.1","ntp target 192.0.2.1 65536","ntp target none extra","ntp interval 0","ntp interval 3601","ntp offset -86400001","ntp offset 9223372036854775808","ntp drift -1001","ntp drift nan","ntp sync extra")){
            var r=NetworkServiceCommands.run(s,"old",command);ok(r.handled()&&!r.changed()&&unchanged.equals(s.nodes().get("old").encode()),"bad input atomic: "+command);
        }
        for(DeviceKind kind:DeviceKind.values()){var p=new NetworkProfile(kind);p.ntp.enabled=true;p.ntp.target="192.0.2.123";p.validate();ok(NetworkProfile.decode(kind,p.encode()).ntp.enabled,"all device kinds may be clients");}
        System.out.println("NtpCodecTest: "+checks+" checks passed");
    }
}
