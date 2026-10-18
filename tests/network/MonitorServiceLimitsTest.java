import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class MonitorServiceLimitsTest {
    private static int checks;
    private static void ok(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    public static void main(String[] args){
        var s=new NetworkSimulator();var p=new NetworkProfile(DeviceKind.SERVER);p.address="10.84.0.1";s.put("s",p);
        for(String text:List.of("monitor add a web http://10.84.0.2","monitor add a web base.local","monitor add a tcp 10.84.0.2:80","monitor add a web 224.0.0.1","monitor add a web 0.0.0.0","monitor add a web 10.84.0.2:65536","monitor add a web 10.84.0.2:0","monitor add a icmp 10.84.0.2:80","monitor add a web","monitor add a dns 10.84.0.2 trailing")){
            String before=p.encode();var r=NetworkServiceCommands.run(s,"s",text);ok(r.handled()&&!r.changed()&&p.encode().equals(before),"不正入力は原子的に拒否: "+text);
        }
        for(int i=0;i<16;i++)ok(NetworkServiceCommands.run(s,"s","monitor add t"+i+" web 10.84.0.2").changed(),"サービス対象を登録");
        ok(!NetworkServiceCommands.run(s,"s","monitor add too_many 10.84.0.2").changed(),"ICMPとサービスで16件上限を共有");
        ok(!NetworkServiceCommands.run(s,"s","monitor add t0 dns 10.84.0.3").changed(),"種類を変えて同名上書き不可");
        NetworkServiceCommands.run(s,"s","monitor interval 1");NetworkServiceCommands.run(s,"s","monitor thresholds 1 1");NetworkServiceCommands.run(s,"s","monitor on");
        for(int n=0;n<23;n++){var device=new NetworkProfile(DeviceKind.L2_SWITCH);for(int port=0;port<6;port++)device.ports[port].vlan=n*6+port+1;s.put("v"+n,device);}
        s.advance(1);ok(s.monitor().lastProbes()==2&&s.monitor().scheduled()==16,"サービスでも1tick2件・1対象1期限");
        s.advance(1_000_000);ok(s.monitor().totalProbes()==4,"時間飛びでも過去分を一括実行しない");
        for(int n=0;n<20;n++)s.advance(1);
        ok(s.monitor().snapshots("s").stream().allMatch(x->x.state()==MonitorRuntime.State.UNKNOWN&&x.detail().contains("上限")),"サービス監視でもVLAN処理上限はDOWNでなく判定保留");
        ok(s.logs("s").stream().noneMatch(x->x.contains("MONITOR DOWN")),"処理上限の誤障害通知なし");
        NetworkServiceCommands.run(s,"s","monitor clear");ok(p.monitor.targets.isEmpty()&&p.monitor.services.isEmpty()&&s.monitor().scheduled()==0,"一括削除で種類も期限も除去");
        System.out.println("MonitorServiceLimitsTest: "+checks+" checks passed");
    }
}
