import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** 稼働判定もNAT取引に含む。監視結果・変換表へのテスト側注入は行わない。 */
public final class MonitorServiceNetworkTest {
    private static int checks;
    private static void ok(boolean v,String reason){checks++;if(!v)throw new AssertionError(reason);}
    private static void config(NetworkSimulator s,String id,String text){var r=NetworkServiceCommands.run(s,id,text);ok(r.handled()&&r.changed(),text+" / "+r.lines());}
    private static void ticks(NetworkSimulator s,int n){for(int i=0;i<n;i++)s.advance(1);}
    private static MonitorRuntime.Snapshot state(NetworkSimulator s,String id,String label){return s.monitor().snapshots(id).stream().filter(x->x.label().equals(label)).findFirst().orElseThrow();}
    private static boolean up(NetworkSimulator s,String id,String label){return state(s,id,label).state()==MonitorRuntime.State.UP;}
    private static final class Fixture {
        final NetworkSimulator s=new NetworkSimulator();
        final NetworkProfile inside=new NetworkProfile(DeviceKind.SERVER),outside=new NetworkProfile(DeviceKind.SERVER);
        Fixture(){
            inside.address="10.81.0.10";inside.gateway="10.81.0.1";outside.address="198.51.100.20";
            var r=new NetworkProfile(DeviceKind.ROUTER);r.dhcp=false;r.ports[0].address="10.81.0.1";r.ports[1].address="198.51.100.1";
            s.put("in",inside);s.put("out",outside);s.put("r",r);
            s.link(new NetworkSimulator.Link("ir","in",0,"r",0,CableGrade.CAT5E,2,false));s.link(new NetworkSimulator.Link("ro","r",1,"out",0,CableGrade.CAT5E,2,false));
            config(s,"r","nat port 0 inside");config(s,"r","nat port 1 outside");config(s,"r","nat on");
        }
        void start(String id){config(s,id,"monitor interval 1");config(s,id,"monitor thresholds 1 1");config(s,id,"monitor on");ticks(s,4);}
    }
    public static void main(String[] args){
        var f=new Fixture();var s=f.s;
        config(s,"in","monitor add site web 198.51.100.20");config(s,"in","monitor add host 198.51.100.20");
        config(s,"r","acl add 0 in 10 deny icmp any any");config(s,"r","acl add 0 in 20 permit ip any any");f.outside.web=true;f.start("in");
        ok(up(s,"in","site")&&!up(s,"in","host"),"ICMP拒否でもTCP80のサービスはUP");
        ok(s.nat().sessions("r").size()==1,"成功したサービスだけ変換表に確定");
        long expiry=s.nat().sessions("r").getFirst().expiresAt();f.outside.web=false;ticks(s,60);
        ok(!up(s,"in","site")&&state(s,"in","site").detail().contains("Web"),"経路があっても停止したWebはDOWN");
        ok(s.nat().sessions("r").getFirst().expiresAt()==expiry,"停止確認で既存変換表を延命しない");
        config(s,"r","nat clear");ticks(s,21);ok(s.nat().sessions("r").isEmpty(),"停止確認で新しい変換表を作らない");
        f.outside.web=true;ticks(s,21);ok(up(s,"in","site"),"Web復帰で再確立");
        config(s,"r","acl add 0 out 10 deny tcp any any");config(s,"r","acl add 0 out 20 permit ip any any");ticks(s,21);
        ok(!up(s,"in","site")&&state(s,"in","site").detail().contains("ACL"),"返信ACLを検査");ok(s.nat().sessions("r").isEmpty(),"返信失敗は未確定");
        var g=new Fixture();s=g.s;
        config(s,"r","nat forward add 1 tcp 8080 10.81.0.10 80");config(s,"r","nat forward add 1 tcp 8081 10.81.0.10 81");
        config(s,"out","monitor add site web 198.51.100.1:8080");config(s,"out","monitor add wrong web 198.51.100.1:8081");g.inside.web=true;g.start("out");
        ok(up(s,"out","site")&&!up(s,"out","wrong"),"公開ポートではなく変換後の実サービス80番を検査");
        ok(s.nat().sessions("r").size()==1,"誤内部ポートのマッピングは作成しない");
        config(s,"r","nat forward add 1 udp 5353 10.81.0.10 53");config(s,"out","monitor add resolver dns 198.51.100.1:5353");ticks(s,5);
        ok(up(s,"out","resolver"),"公開UDPポートから内部DNS53番を確認");
        config(s,"in","dns-server off");ticks(s,21);ok(!up(s,"out","resolver"),"NAT先のDNS停止を確認");
        config(s,"out","monitor off");config(s,"r","nat clear");
        var flow=s.startTransfer("in","198.51.100.20",1_000_000_000_000L);ok(flow.success(),"従来の転送開始");
        var existing=s.flows().getFirst();config(s,"in","monitor add other web 198.51.100.20");g.start("in");
        ok(s.flows().size()==1&&s.flows().getFirst()==existing,"監視設定と停止サービス確認で進行中の転送を壊さない");
        var direct=new NetworkSimulator();var a=new NetworkProfile(DeviceKind.SERVER);a.address="10.82.0.1";var b=new NetworkProfile(DeviceKind.SERVER);b.address="10.83.0.1";b.web=true;
        var r=new NetworkProfile(DeviceKind.ROUTER);r.dhcp=false;r.ports[0].address="10.82.0.254";r.ports[1].address="10.83.0.254";a.gateway="10.82.0.254";
        direct.put("a",a);direct.put("b",b);direct.put("r",r);direct.link(new NetworkSimulator.Link("a-r","a",0,"r",0,CableGrade.CAT5E,2,false));direct.link(new NetworkSimulator.Link("b-r","b",0,"r",1,CableGrade.CAT5E,2,false));
        config(direct,"a","monitor add site web 10.83.0.1");config(direct,"a","monitor thresholds 1 1");config(direct,"a","monitor on");ticks(direct,4);
        ok(!up(direct,"a","site")&&state(direct,"a","site").detail().contains("応答経路"),"サービス稼働でも戻りの経路が必要");
        System.out.println("MonitorServiceNetworkTest: "+checks+" checks passed");
    }
}
