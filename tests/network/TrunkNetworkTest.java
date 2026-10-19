import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class TrunkNetworkTest {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static NetworkProfile host(String ip){var p=new NetworkProfile(DeviceKind.PC);p.address=ip;return p;}
    private static void wire(NetworkSimulator s,String a,int ap,String b,int bp){s.link(new NetworkSimulator.Link(a+ap+"-"+b+bp,a,ap,b,bp,CableGrade.CAT5E,2,false));}
    private static void command(NetworkSimulator s,String node,String text){var r=NetworkCli.run(s,node,text);if(!r.changed()&&!r.lines().equals(List.of("変更はありません")))throw new AssertionError(text+" "+r.lines());}
    private static void ticks(NetworkSimulator s,int count){for(int i=0;i<count;i++)s.advance(1);}
    private static NetworkSimulator twoVlans(){
        var s=new NetworkSimulator();
        for(String id:List.of("left","right")){var p=new NetworkProfile(DeviceKind.L2_SWITCH);p.ports[0].vlan=10;p.ports[1].vlan=20;p.ports[2].trunk=true;s.put(id,p);}
        s.put("a",host("10.10.0.1"));s.put("b",host("10.10.0.2"));s.put("c",host("10.20.0.1"));s.put("d",host("10.20.0.2"));
        wire(s,"a",0,"left",0);wire(s,"c",0,"left",1);wire(s,"left",2,"right",2);wire(s,"right",0,"b",0);wire(s,"right",1,"d",0);return s;
    }
    public static void main(String[] args){
        var s=twoVlans();check(s.ping("a","10.10.0.2").success()&&s.ping("c","10.20.0.2").success(),"既定allは2VLANを通す");
        command(s,"right","port 2 allowed 10");
        check(s.ping("a","10.10.0.2").success(),"受信許可VLAN10");check(!s.ping("c","10.20.0.2").success(),"相手の受信リストでVLAN20拒否");
        check(!s.ping("d","10.20.0.1").success(),"相手の送信リストでもVLAN20拒否");
        check(s.linkCapacity(s.links().stream().filter(l->l.a().equals("left")&&l.b().equals("right")).findFirst().orElseThrow())>0,"VLAN拒否と物理Link Downを混同しない");
        command(s,"right","port 2 allowed all");command(s,"left","port 2 allowed 20");
        check(!s.ping("a","10.10.0.2").success()&&s.ping("c","10.20.0.2").success(),"送信側だけの許可リストでも分離");
        command(s,"left","port 2 allowed none");check(!s.ping("c","10.20.0.2").success(),"明示none全拒否");
        command(s,"left","port 2 allowed 10,20");check(s.ping("a","10.10.0.2").success()&&s.ping("c","10.20.0.2").success(),"再許可で復旧");
        command(s,"left","port 0 allowed none");check(s.ping("a","10.10.0.2").success(),"Accessは保存したnoneを無視");
        command(s,"left","port 0 mode trunk");check(!s.ping("a","10.10.0.2").success(),"Trunk切替でnone有効");
        command(s,"left","port 0 mode access");check(s.ping("a","10.10.0.2").success()&&s.nodes().get("left").ports[0].allowedVlans.toString().equals("none"),"Accessへ戻しても設定を消さない");
        check(s.startTransfer("a","10.10.0.2",10_000_000_000L).success(),"転送開始");String old=s.nodes().get("left").encode();
        for(String bad:List.of("10,","all,20","0-10","20-10","4095")){var r=NetworkCli.run(s,"left","port 2 allowed "+bad);check(!r.changed()&&old.equals(s.nodes().get("left").encode())&&!s.flows().isEmpty(),"不正許可で設定と転送を変更しない");}
        command(s,"left","port 2 allowed 20,10,10");check(!s.flows().isEmpty(),"同じ集合の再適用で転送を切らない");
        s.put("x",host("10.30.0.1"));s.put("y",host("10.30.0.2"));wire(s,"x",0,"y",0);s.startTransfer("x","10.30.0.2",10_000_000_000L);
        command(s,"left","port 2 allowed 20");check(s.flows().stream().anyMatch(f->f.source.equals("x")),"独立網の転送を保持");
        // Native VLANはタグなし分類をしてから、受信側のnative VLANを許可する必要がある。
        var n=new NetworkSimulator();var a=host("192.0.2.1");var b=host("192.0.2.2");a.ports[0].trunk=true;a.ports[0].vlan=10;b.ports[0].trunk=true;b.ports[0].vlan=20;n.put("a",a);n.put("b",b);wire(n,"a",0,"b",0);
        command(n,"a","port 0 allowed 10");command(n,"b","port 0 allowed 20");check(n.ping("a","192.0.2.2").success(),"native不一致は各受信側で分類 (自動補正しない)");
        command(n,"b","port 0 allowed 10");check(!n.ping("a","192.0.2.2").success(),"タグなしでも受信nativeの許可が必要");
        command(n,"a","port 0 allowed none");command(n,"b","port 0 allowed all");check(!n.ping("a","192.0.2.2").success(),"送信nativeも除外可能");
        // Classic STPのVLANインスタンスも禁止されたVLANを通さない。
        var stp=twoVlans();for(String id:List.of("left","right")){NetworkServiceCommands.run(stp,id,"stp mode classic");NetworkServiceCommands.run(stp,id,"stp timers 1 6 4");}
        ticks(stp,300);check(stp.ping("a","10.10.0.2").success()&&stp.ping("c","10.20.0.2").success(),"classic収束後の2VLAN");
        command(stp,"left","port 2 allowed 10");ticks(stp,300);
        check(stp.ping("a","10.10.0.2").success()&&!stp.ping("c","10.20.0.2").success(),"classicの経路/データ共に許可VLANへ限定");
        var key=new StpEngine.Key("left",20);check(stp.stp().status(key).ports().get(2).state()==StpEngine.State.DISABLED,"除外VLANのSTPポートが非転送");
        command(stp,"left","port 2 allowed all");ticks(stp,300);check(stp.ping("c","10.20.0.2").success(),"classic再許可もタイマー後に復旧");
        System.out.println("TrunkNetworkTest: "+checks+" checks passed");
    }
}
