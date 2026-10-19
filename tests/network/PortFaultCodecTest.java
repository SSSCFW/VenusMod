import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import java.nio.ByteBuffer;

public final class PortFaultCodecTest {
    private static int checks;
    private static void ok(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
    private static final String V14="AAAADgANbGVnYWN5LXJvdXRlcgAAAAAAGAAAAAABAQABAAAAAAAAZAEAAAAAAAEACzE5Mi4xNjguMS4xAAAAGAAAAAAAAAAAAQEAAAAAFwALMTkyLjE2OC4yLjEAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFAAAAFAAA09GRgAAAAAAA09GRgAAAAAAA09GRgAAAAAAA09GRgAAAAAAA09GRgAAAAAAA09GRgAAAAAAAQAETk9ORQAETk9ORQAETk9ORQAETk9ORQAETk9ORQAETk9ORQAAAAABAf////8AAAABAAAAFAAAAIAAAAAAKAAAAZAAAAEsAAAAAAAAAIAAAAAAAAAAAACAAAAAAAAAAAAAgAAAAAAAAAAAAIAAAAAAAAAAAACAAAAAAAAAAAAAgAAAAAAAAAAAAAAAAAD96AAAAAAAFAAAAFAAAAAAAAAAAAEAAAAKAAAAAAAAAAAACTAuMC4wLjAvMAAAAgIAAAAGAAAAAAUAAAADAAAAAgAAAAAAAAAAAARIT0xEAAAABQAAAAAAAAAAAAAAewAAAB4AAAAAAAAAAAAAAAAAA2FsbAAKMjMsMTAwLTExMAADYWxsAANhbGwAA2FsbAADYWxs";
    public static void main(String[] args){
        var old=NetworkProfile.decode(DeviceKind.ROUTER,V14);
        ok(old.name.equals("legacy-router")&&old.failed&&old.ports[1].vlan==23,"fixed legacy v14 values");
        ok(Arrays.stream(old.ports).noneMatch(p->p.failed),"old port faults default to none");
        for(DeviceKind kind:DeviceKind.values()){
            var p=new NetworkProfile(kind);p.ports[0].failed=true;p.ports[5].failed=true;p.ports[5].enabled=false;p.ports[5].allowedVlans=VlanAllowance.NONE;
            var encoded=p.encode();ok(ByteBuffer.wrap(Base64.getDecoder().decode(encoded)).getInt()==15,"format v15");
            var copy=NetworkProfile.decode(kind,encoded);ok(copy.ports[0].failed&&copy.ports[5].failed&&!copy.ports[1].failed&&!copy.ports[5].enabled,"fault roundtrip "+kind);
            ok(copy.encode().equals(encoded),"canonical encoding "+kind);
            var saved=ConfigBackup.capture("d",p,10);ok(!NetworkProfile.decode(kind,saved.configuration()).ports[0].failed,"hardware not in config backup");
            p.ports[0].failed=false;p.ports[2].failed=true;
            var restored=saved.restored(p);ok(!restored.ports[0].failed&&restored.ports[2].failed&&restored.ports[5].failed,"restore current faults not old faults");
        }
        byte[] raw=Base64.getDecoder().decode(old.encode());
        for(int value:new int[]{64,128,255}){byte[] bad=raw.clone();bad[bad.length-1]=(byte)value;boolean refused=false;try{NetworkProfile.decode(DeviceKind.ROUTER,Base64.getEncoder().encodeToString(bad));}catch(IllegalArgumentException e){refused=true;}ok(refused,"unknown physical port bits rejected");}
        for(int length:new int[]{raw.length-1,raw.length+1}){boolean refused=false;try{NetworkProfile.decode(DeviceKind.ROUTER,Base64.getEncoder().encodeToString(Arrays.copyOf(raw,length)));}catch(IllegalArgumentException e){refused=true;}ok(refused,"truncated/extra state rejected");}
        System.out.println("PortFaultCodecTest: "+checks+" checks passed");
    }
}
