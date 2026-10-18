import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import java.nio.ByteBuffer;

public final class BgpCodecTest {
    static int checks;
    static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    // 追加前(v6)の実コードから生成。現在のencodeを切り詰めた疑似fixtureではない。
    static final String V6="AAAABgASQkdQ5Lul5YmN44Gu5qmf5ZmoAAAAAAAYAAAAAAEBAAEAAA0xOTIuMTY4LjEuMjU0AAAAZAEAAAAAAAEACzE5Mi4xNjguMS4xAAAAGAAAAAAAAAAAAQAAAAAAAQALMTkyLjE2OC4yLjEAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFAAAAFAAA09GRgAAAAAAA09GRgAAAAAAA09GRgAAAAAAA09GRgAAAAAAA09GRgAAAAAAA09GRgAAAAAAAQAETk9ORQAETk9ORQAETk9ORQAETk9ORQAETk9ORQAETk9ORQAAAAABAf////8AAAABAAAAFAAAAIAAAAAAKAAAAZAAAAEsAAAAAAAAAIAAAAAAAAAAAACAAAAAAAAAAAAAgAAAAAAAAAAAAIAAAAAAAAAAAACAAAAAAAAAAAAAgAAAAAAAAA==";
    public static void main(String[] args){
        var legacy=NetworkProfile.decode(DeviceKind.ROUTER,V6);
        ok(legacy.name.equals("BGP以前の機器")&&legacy.failed&&legacy.vip.equals("192.168.1.254"),"既存データ保持");
        ok(!legacy.bgp.enabled&&legacy.bgp.peers.isEmpty(),"旧機器はBGP無効で移行");
        var s=BgpBoundaryTest.configured();BgpContractTest.ticks(s,160);
        var p=s.nodes().get("a");String encoded=p.encode();var decoded=NetworkProfile.decode(DeviceKind.ROUTER,encoded);
        ok(ByteBuffer.wrap(Base64.getDecoder().decode(encoded)).getInt()==12,"DNS拡張を含む保存v12 (BGP設定を保持)");
        ok(decoded.bgp.asn==65001&&decoded.bgp.enabled&&decoded.bgp.routerId.equals("1.1.1.1"),"AS・ID・有効状態保存");
        ok(decoded.bgp.peers.equals(p.bgp.peers)&&decoded.bgp.networks.equals(p.bgp.networks),"ピア/政策/生成経路保存");
        var fresh=new NetworkSimulator();fresh.put("a",decoded);ok(fresh.bgp().routes("a").isEmpty()&&fresh.bgp().neighbors("a").isEmpty(),"動的経路/タイマーを持ち運ばない");
        for(String bad:List.of("bgp as 0","bgp as 4294967295","bgp router-id 224.0.0.1","bgp neighbor 192.0.2.2 prepend 9","bgp timers 1 2","bgp neighbor 192.0.2.2 med 4294967296","bgp neighbor 192.0.2.2 local-pref -2","bgp neighbor 192.0.2.2 import bad","bgp network add 224.0.0.0/24","bgp neighbor 192.0.2.2 delete extra")){
            var reply=NetworkServiceCommands.run(s,"a",bad);ok(!reply.changed()&&encoded.equals(s.nodes().get("a").encode()),"非破壊拒否: "+bad);
        }
        for(int i=3;i<=9;i++)BgpBoundaryTest.cmd(s,"a","bgp neighbor 192.0.2."+i+" remote-as 65002");
        String full=s.nodes().get("a").encode();var overflow=NetworkServiceCommands.run(s,"a","bgp neighbor 192.0.2.10 remote-as 65002");
        ok(!overflow.changed()&&full.equals(s.nodes().get("a").encode()),"8ピア上限");
        byte[] raw=Base64.getDecoder().decode(full);boolean rejected=false;
        try{NetworkProfile.decode(DeviceKind.ROUTER,Base64.getEncoder().encodeToString(Arrays.copyOf(raw,raw.length-1)));}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"破損保存拒否");
        ByteBuffer.wrap(raw).putInt(Integer.MAX_VALUE);rejected=false;try{NetworkProfile.decode(DeviceKind.ROUTER,Base64.getEncoder().encodeToString(raw));}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"未知保存形式拒否");
        System.out.println("BgpCodecTest: "+checks+" checks passed");
    }
}
