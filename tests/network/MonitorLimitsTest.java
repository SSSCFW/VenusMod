import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class MonitorLimitsTest {
    private static int checks;
    private static void ok(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    private static NetworkProfile server(int n,int targets){var p=new NetworkProfile(DeviceKind.SERVER);p.address="10.0.0."+(n+1);p.monitor.enabled=true;p.monitor.intervalSeconds=1;for(int i=0;i<targets;i++)p.monitor.targets.put("t"+i,"10.1.0."+(i+1));return p;}
    public static void main(String[] args){
        var s=new NetworkSimulator();for(int i=0;i<16;i++)s.put("s"+i,server(i,16));ok(s.monitor().scheduled()==256,"期限は対象ごとに1つ、全体256件");
        s.advance(1);ok(s.monitor().lastProbes()==2&&s.monitor().totalProbes()==2,"1tick最大2件");
        s.advance(1_000_000);ok(s.monitor().totalProbes()==4,"時間飛びでも過去分を一括処理しない");
        for(int i=0;i<126;i++)s.advance(1);
        ok(s.monitor().snapshots("s15").stream().allMatch(e->e.probes()>0),"後から登録したサーバーも公平に実行");
        ok(s.monitor().scheduled()==256,"繰り返しで期限が増殖しない");
        var extra=server(17,1);extra.monitor.enabled=false;s.put("extra",extra);
        var reply=NetworkServiceCommands.run(s,"extra","monitor on");ok(reply.handled()&&!reply.changed()&&!extra.monitor.enabled,"上限超過の新規開始を拒否");
        ok(s.monitor().scheduled()==256,"拒否で既存期限を破壊しない");
        s.remove("s0");reply=NetworkServiceCommands.run(s,"extra","monitor on");ok(reply.changed()&&s.monitor().scheduled()==241,"空き容量を再利用");
        var local=server(30,16);local.monitor.enabled=false;s.put("local",local);String before=local.encode();
        reply=NetworkServiceCommands.run(s,"local","monitor add over 10.1.0.100");ok(!reply.changed()&&before.equals(local.encode()),"1台16件上限");
        var many=new NetworkSimulator();for(int i=0;i<64;i++)many.put("m"+i,server(i,1));
        many.put("limit",server(65,1));ok(many.monitor().scheduled()==64&&many.monitor().describe("limit").getFirst().contains("LIMIT"),"保存機器のロードも台数上限を適用");
        many.remove("limit");ok(many.monitor().scheduled()==64,"上限停止した機器を回収できる");
        var bounded=new NetworkSimulator();var stopped=server(0,16);stopped.failed=true;bounded.put("stopped",stopped);
        bounded.advance(10000);ok(bounded.monitor().totalProbes()==0&&bounded.monitor().scheduled()==16,"停止機器の確認でプローブしない");
        var overloaded=new NetworkSimulator();var watched=server(0,1);overloaded.put("watch",watched);
        for(int n=0;n<23;n++){var v=new NetworkProfile(DeviceKind.L2_SWITCH);for(int port=0;port<6;port++)v.ports[port].vlan=n*6+port+1;overloaded.put("v"+n,v);}
        for(int n=0;n<100;n++)overloaded.advance(1);
        ok(overloaded.monitor().snapshots("watch").getFirst().state()==MonitorRuntime.State.UNKNOWN,"シミュレーター処理上限を相手のDOWNと誤通知しない");
        ok(overloaded.monitor().snapshots("watch").getFirst().detail().contains("上限"),"処理上限の判定保留を診断表示");
        System.out.println("MonitorLimitsTest: "+checks+" checks passed");
    }
}
