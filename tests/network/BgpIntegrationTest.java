import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class BgpIntegrationTest {
    static int checks;
    static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static void tick(NetworkSimulator s,int n){BgpContractTest.ticks(s,n);}
    static void cmd(NetworkSimulator s,String id,String text){var r=NetworkServiceCommands.run(s,id,text);ok(r.handled()&&r.changed(),text+" => "+r);}
    static void peer(NetworkSimulator s,String id,long asn,String rid,String ip,long remoteAs){
        cmd(s,id,"bgp as "+asn);cmd(s,id,"bgp router-id "+rid);cmd(s,id,"bgp neighbor "+ip+" remote-as "+remoteAs);
        cmd(s,id,"bgp neighbor "+ip+" import all");cmd(s,id,"bgp neighbor "+ip+" export all");cmd(s,id,"bgp on");
    }
    static BgpEngine.Route route(NetworkSimulator s,String id,String prefix){return s.bgp().routes(id).stream().filter(r->r.prefix().toString().equals(prefix)).findFirst().orElse(null);}
    static boolean established(NetworkSimulator s,String id){return s.bgp().neighbors(id).stream().anyMatch(n->n.state()==BgpEngine.State.ESTABLISHED);}
    static NetworkSimulator ibgp(){
        var s=new NetworkSimulator();var x=BgpContractTest.router();var a=BgpContractTest.router();var b=BgpContractTest.router();
        x.ports[0].address="192.0.2.2";a.ports[0].address="192.0.2.1";a.ports[1].address="10.0.12.1";b.ports[0].address="10.0.12.2";
        x.ports[1].address="10.2.0.1";b.ports[1].address="10.3.0.1";
        s.put("x",x);s.put("a",a);s.put("b",b);s.put("hx",BgpContractTest.host("10.2.0.10","10.2.0.1"));s.put("hb",BgpContractTest.host("10.3.0.10","10.3.0.1"));
        BgpContractTest.link(s,"xa","x",0,"a",0);BgpContractTest.link(s,"ab","a",1,"b",0);BgpContractTest.link(s,"xh","x",1,"hx",0);BgpContractTest.link(s,"bh","b",1,"hb",0);
        peer(s,"x",65002,"2.2.2.2","192.0.2.1",65001);peer(s,"a",65001,"1.1.1.1","192.0.2.2",65002);
        peer(s,"a",65001,"1.1.1.1","10.0.12.2",65001);peer(s,"b",65001,"3.3.3.3","10.0.12.1",65001);
        cmd(s,"x","bgp network add 10.2.0.0/24");cmd(s,"b","bgp network add 10.3.0.0/24");return s;
    }
    public static void main(String[] args){
        var s=ibgp();tick(s,280);
        ok(established(s,"b"),"iBGPセッション形成");
        ok(route(s,"b","10.2.0.0/24")==null,"iBGPが保持したNEXT_HOPは到達性なしなら選択しない");
        ok(s.bgp().candidates("b").stream().anyMatch(t->t.contains("192.0.2.2")&&t.contains("到達不能")),"受信経路は診断用に保持");
        ok(!s.ping("hb","10.2.0.10").success(),"unreachable NEXT_HOPでは転送しない");
        cmd(s,"a","bgp neighbor 10.0.12.2 next-hop-self on");tick(s,280);
        var selected=route(s,"b","10.2.0.0/24");ok(selected!=null&&selected.nextHop().equals("10.0.12.1"),"next-hop-selfは指定ピアへだけ適用");
        ok(selected.path().equals(List.of(65002L)),"iBGPでAS_PATHを追加しない");
        ok(s.ping("hb","10.2.0.10").success(),"eBGPとiBGPを経由した往復通信");
        cmd(s,"a","bgp neighbor 192.0.2.2 prepend 2");tick(s,280);
        ok(route(s,"x","10.3.0.0/24").path().equals(List.of(65001L,65001L,65001L)),"通常1回+追加2回のASプリペンド");
        cmd(s,"a","bgp neighbor 192.0.2.2 import none");tick(s,240);
        ok(route(s,"b","10.2.0.0/24")==null,"import拒否でiBGPへもwithdraw");
        cmd(s,"a","bgp neighbor 192.0.2.2 import 10.2.0.0/16");tick(s,240);ok(s.ping("hb","10.2.0.10").success(),"CIDRフィルターでmore-specificを許可");
        // NAT設定を持つ境界ルーターもBGPを使える。変換が必要な中継ピアは対象外。
        cmd(s,"a","nat port 0 outside");cmd(s,"a","nat port 1 inside");cmd(s,"a","nat on");tick(s,280);
        ok(established(s,"a"),"NATが有効でもルーター自身のBGPは成立する");
        ok(route(s,"a","10.2.0.0/24")!=null,"NAT併用時にも経路を学習");
        // ACLでTCP179だけ止め、経路広告の消失と復旧を確認する。
        cmd(s,"a","acl add 0 in 10 deny tcp any any 179");cmd(s,"a","acl add 0 in 20 permit ip any any");tick(s,260);
        ok(route(s,"b","10.2.0.0/24")==null,"BGP輸送のACL拒否はHold満了/withdrawを起こす");
        cmd(s,"a","acl clear 0 in");tick(s,280);ok(route(s,"b","10.2.0.0/24")!=null,"ACL解除で再形成・再広告");
        System.out.println("BgpIntegrationTest: "+checks+" checks passed");
    }
}
