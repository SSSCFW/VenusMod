import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

public final class TrunkCodecTest {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static final String V13="AAAADQAS5penVjEz44K544Kk44OD44OBAAAAAAAYAAAAAAAAAAEAAAAAAABkAQAAAAAAAQAAAAAAGAAAAAAAAAAAAQAAAAAAAQAAAAAAGAAAAAAAAAAAAQEAAAAAFwAAAAAAGAAAAAAAAAAAAQAAAAAAAQAAAAAAGAAAAAAAAAAAAQAAAAAAAQAAAAAAGAAAAAAAAAAAAQAAAAAAAQAAAAAAGAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABQAAABQAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAAEABE5PTkUABE5PTkUABE5PTkUABE5PTkUABE5PTkUABE5PTkUAAAAAAQH/////AAAAAQAAABQAAACAAAAAACgAAAGQAAABLAAAAAAAAACAAAAAAAAAAAAAgAAAAAAAAAAAAIAAAAAAAAAAAACAAAAAAAAAAAAAgAAAAAAAAAAAAIAAAAAAAAAAAAAAAAAA/egAAAAAABQAAABQAAAAAAAAAAABAAAACgAAAAAAAAAAAAkwLjAuMC4wLzAAAAICAAAABgAAAAAFAAAAAwAAAAIAAAAAAAAAAAAESE9MRAAAAAUAAAAAAAAAAAAAAHsAAAAeAAAAAAAAAAAAAAAA";
    public static void main(String[] args)throws Exception{
        var legacy=NetworkProfile.decode(DeviceKind.L2_SWITCH,V13);
        check(legacy.name.equals("旧V13スイッチ")&&legacy.ports[2].vlan==23&&legacy.ports[2].trunk,"実旧v13の固定fixtureを移行");
        check(Arrays.stream(legacy.ports).allMatch(p->p.allowedVlans.equals(VlanAllowance.ALL)),"旧ポートはすべてall");
        legacy.ports[0].allowedVlans=VlanAllowance.NONE;legacy.ports[2].allowedVlans=VlanAllowance.parse("10,20-30");
        String encoded=legacy.encode();check(ByteBuffer.wrap(Base64.getDecoder().decode(encoded)).getInt()==14,"新形式v14");
        var copy=NetworkProfile.decode(DeviceKind.L2_SWITCH,encoded);
        check(copy.ports[0].allowedVlans.equals(VlanAllowance.NONE)&&copy.ports[2].allowedVlans.equals(legacy.ports[2].allowedVlans),"Accessの保存リストも往復");
        check(copy.encode().equals(encoded),"保存が安定");
        byte[] raw=Base64.getDecoder().decode(encoded);
        for(int size:new int[]{raw.length-1,raw.length+1}){boolean refused=false;try{NetworkProfile.decode(DeviceKind.L2_SWITCH,Base64.getEncoder().encodeToString(Arrays.copyOf(raw,size)));}catch(IllegalArgumentException ex){refused=true;}check(refused,"切断/余分なデータを拒否");}
        // 最後のポートのUTF文字列を、構造的には正常な範囲外VLANへ置換する。
        var bytes=new ByteArrayOutputStream();bytes.write(raw,0,raw.length-5);var out=new DataOutputStream(bytes);out.writeUTF("4095");
        boolean refused=false;try{NetworkProfile.decode(DeviceKind.L2_SWITCH,Base64.getEncoder().encodeToString(bytes.toByteArray()));}catch(IllegalArgumentException ex){refused=true;}check(refused,"保存内の不正VLANも拒否");
        for(DeviceKind kind:DeviceKind.values()){var p=new NetworkProfile(kind);p.ports[1].allowedVlans=VlanAllowance.parse("1,100-200,4094");p.validate();check(NetworkProfile.decode(kind,p.encode()).ports[1].allowedVlans.equals(p.ports[1].allowedVlans),"全機種のポートへ保存 "+kind);}
        System.out.println("TrunkCodecTest: "+checks+" checks passed");
    }
}
