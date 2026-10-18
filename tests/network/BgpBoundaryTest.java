import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class BgpBoundaryTest {
    static int checks;
    static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    static void tick(NetworkSimulator s,int n){BgpContractTest.ticks(s,n);}
    static void cmd(NetworkSimulator s,String id,String text){var r=NetworkServiceCommands.run(s,id,text);ok(r.handled()&&r.changed(),text+" => "+r);}
    static NetworkSimulator configured(){var s=BgpContractTest.pair();BgpIntegrationTest.peer(s,"a",65001,"1.1.1.1","192.0.2.2",65002);BgpIntegrationTest.peer(s,"b",65002,"2.2.2.2","192.0.2.1",65001);cmd(s,"a","bgp network add 10.1.0.0/24");cmd(s,"b","bgp network add 10.2.0.0/24");return s;}
    public static void main(String[] args){
        var s=configured();cmd(s,"b","bgp neighbor 192.0.2.1 remote-as 65099");tick(s,200);
        ok(!BgpIntegrationTest.established(s,"a")&&!BgpIntegrationTest.established(s,"b"),"OPENのAS不一致を拒否");
        cmd(s,"b","bgp neighbor 192.0.2.1 remote-as 65001");cmd(s,"b","bgp router-id 1.1.1.1");tick(s,180);ok(!BgpIntegrationTest.established(s,"a"),"Router ID衝突を拒否");
        cmd(s,"b","bgp router-id 2.2.2.2");tick(s,180);ok(s.ping("x","10.2.0.10").success(),"設定修正後に復旧");
        cmd(s,"b","bgp network add 10.88.0.0/24");cmd(s,"b","bgp network add 10.2.0.0/16");tick(s,100);
        ok(BgpIntegrationTest.route(s,"a","10.88.0.0/24")==null,"経路がないprefixを生成しない");
        ok(BgpIntegrationTest.route(s,"a","10.2.0.0/16")==null,"more-specificの存在だけで集約経路を作らない");
        var a=s.nodes().get("a");a.routes.add(new NetworkProfile.Route("10.2.0.0",24,"192.0.2.254",0));s.invalidate("a");tick(s,30);
        ok(!s.ping("x","10.2.0.10").success(),"同一prefixはstaticがeBGPより優先。ARP失敗をBGPで隠さない");
        a.routes.clear();s.invalidate("a");tick(s,100);ok(s.ping("x","10.2.0.10").success(),"static解除でBGPを使用");
        var result=s.startTransfer("x","10.2.0.10",1_000_000_000_000L);ok(result.success(),"独立網保護の転送開始");
        long received=s.bgp().neighbors("a").getFirst().received();s.put("isolated",BgpContractTest.host("172.16.99.1",""));
        tick(s,20);ok(!s.flows().isEmpty()&&s.flows().getFirst().progress()>0,"別物理網の機器追加で転送を保持");
        ok(s.bgp().neighbors("a").getFirst().received()>=received&&BgpIntegrationTest.established(s,"a"),"BGPセッション時計を保持");
        String before=s.nodes().get("a").encode();var bad=NetworkServiceCommands.run(s,"a","bgp neighbor 192.0.2.2 source 7");
        ok(!bad.changed()&&before.equals(s.nodes().get("a").encode())&&!s.flows().isEmpty(),"誤入力で転送/設定を破壊しない");
        cmd(s,"a","bgp off");ok(!s.ping("x","10.2.0.10").success(),"ローカルBGP停止は次tick前にも転送に反映");
        // iBGPのマルチホップ輸送にOSPFの下位経路を利用する。
        s=new NetworkSimulator();var left=BgpContractTest.router();var middle=BgpContractTest.router();var right=BgpContractTest.router();
        left.ports[0].address="10.12.0.1";middle.ports[0].address="10.12.0.2";middle.ports[1].address="10.23.0.2";right.ports[0].address="10.23.0.3";
        left.ports[1].address="10.1.0.1";right.ports[1].address="10.2.0.1";
        int n=1;for(var p:List.of(left,middle,right)){p.ospf.enabled=true;p.ospf.routerId=n+"."+n+"."+n+"."+n++;p.ospf.modes[0]=OspfSettings.Mode.ACTIVE;}
        middle.ospf.modes[1]=OspfSettings.Mode.ACTIVE;
        middle.routes.add(new NetworkProfile.Route("10.1.0.0",24,"10.12.0.1",0));middle.routes.add(new NetworkProfile.Route("10.2.0.0",24,"10.23.0.3",1));
        s.put("left",left);s.put("middle",middle);s.put("right",right);s.put("x",BgpContractTest.host("10.1.0.10","10.1.0.1"));s.put("y",BgpContractTest.host("10.2.0.10","10.2.0.1"));
        BgpContractTest.link(s,"lm","left",0,"middle",0);BgpContractTest.link(s,"mr","middle",1,"right",0);BgpContractTest.link(s,"lx","left",1,"x",0);BgpContractTest.link(s,"ry","right",1,"y",0);
        BgpIntegrationTest.peer(s,"left",65001,"1.1.1.1","10.23.0.3",65001);BgpIntegrationTest.peer(s,"right",65001,"3.3.3.3","10.12.0.1",65001);
        cmd(s,"left","bgp network add 10.1.0.0/24");cmd(s,"right","bgp network add 10.2.0.0/24");
        ok(!s.ping("x","10.2.0.10").success(),"OSPF/BGP同期前は経路なし");tick(s,320);
        ok(BgpIntegrationTest.established(s,"left"),"OSPF経路を通るiBGP輸送");ok(s.ping("x","10.2.0.10").success(),"中継にも経路を設定したマルチホップiBGPで実転送");
        cmd(s,"right","bgp as 65002");cmd(s,"left","bgp neighbor 10.23.0.3 remote-as 65002");tick(s,200);
        ok(!BgpIntegrationTest.established(s,"left"),"eBGP multihopを未対応のまま成功させない");
        System.out.println("BgpBoundaryTest: "+checks+" checks passed");
    }
}
