import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** 後付けの論理経路注入をせず、公開CLIと通常の仮想通信を使う契約試験。 */
public final class BgpContractTest {
    static int checks;
    static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static void command(NetworkSimulator s,String id,String text){var r=NetworkServiceCommands.run(s,id,text);ok(r.handled()&&r.changed(),text+" => "+r);}
    static NetworkProfile router(){var p=new NetworkProfile(DeviceKind.ROUTER);p.dhcp=false;for(var port:p.ports)port.address="";return p;}
    static NetworkProfile host(String ip,String gw){var p=new NetworkProfile(DeviceKind.PC);p.address=ip;p.gateway=gw;return p;}
    static void link(NetworkSimulator s,String id,String a,int ap,String b,int bp){s.link(new NetworkSimulator.Link(id,a,ap,b,bp,CableGrade.CAT5E,3,false));}
    static void ticks(NetworkSimulator s,int count){for(int i=0;i<count;i++)s.advance(1);}
    public static NetworkSimulator pair(){
        var s=new NetworkSimulator();var a=router();var b=router();
        a.ports[0].address="192.0.2.1";b.ports[0].address="192.0.2.2";
        a.ports[1].address="10.1.0.1";b.ports[1].address="10.2.0.1";
        s.put("a",a);s.put("b",b);s.put("x",host("10.1.0.10","10.1.0.1"));s.put("y",host("10.2.0.10","10.2.0.1"));
        link(s,"ab","a",0,"b",0);link(s,"ax","a",1,"x",0);link(s,"by","b",1,"y",0);return s;
    }
    static void configure(NetworkSimulator s,String id,long asn,String rid,String peer,long remoteAs,String network){
        command(s,id,"bgp as "+asn);command(s,id,"bgp router-id "+rid);
        command(s,id,"bgp neighbor "+peer+" remote-as "+remoteAs);
        command(s,id,"bgp network add "+network);command(s,id,"bgp on");
    }
    static void permit(NetworkSimulator s,String id,String peer){command(s,id,"bgp neighbor "+peer+" import all");command(s,id,"bgp neighbor "+peer+" export all");}
    public static void main(String[] args){
        var s=pair();ok(!s.ping("x","10.2.0.10").success(),"経路の自動注入なし");
        configure(s,"a",65001,"1.1.1.1","192.0.2.2",65002,"10.1.0.0/24");
        configure(s,"b",65002,"2.2.2.2","192.0.2.1",65001,"10.2.0.0/24");
        ticks(s,220);ok(!s.ping("x","10.2.0.10").success(),"eBGPは明示import/exportなしで経路を通さない");
        permit(s,"a","192.0.2.2");permit(s,"b","192.0.2.1");
        ok(!s.ping("x","10.2.0.10").success(),"設定直後にはまだ経路がない");ticks(s,240);
        ok(s.ping("x","10.2.0.10").success(),"経路交換後に端末間の往復通信が成立");
        var status=NetworkServiceCommands.run(s,"a","show bgp");ok(status.handled()&&!status.changed(),"状態表示は読み取り専用");
        String before=s.nodes().get("a").encode();command(s,"a","bgp neighbor 192.0.2.2 next-hop-self on");
        String good=s.nodes().get("a").encode();var invalid=NetworkServiceCommands.run(s,"a","bgp neighbor 192.0.2.2 prepend 999");
        ok(!invalid.changed()&&good.equals(s.nodes().get("a").encode()),"誤設定は保存データを変えない");
        command(s,"b","bgp network delete 10.2.0.0/24");ticks(s,160);
        ok(!s.ping("x","10.2.0.10").success(),"広告削除が撤回として反映される");
        command(s,"b","bgp network add 10.2.0.0/24");ticks(s,160);ok(s.ping("x","10.2.0.10").success(),"再広告で復旧");
        command(s,"b","bgp off");ticks(s,160);ok(!s.ping("x","10.2.0.10").success(),"停止で経路を破棄");
        System.out.println("BgpContractTest: "+checks+" checks passed");
    }
}
