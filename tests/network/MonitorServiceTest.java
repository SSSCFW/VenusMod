import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** 監視の公開CLIから設定し、通常advanceで結果を観測する。 */
public final class MonitorServiceTest {
    private static int checks;
    private static void ok(boolean v,String reason){checks++;if(!v)throw new AssertionError(reason);}
    private static void cmd(NetworkSimulator s,String text){var r=NetworkServiceCommands.run(s,"m",text);ok(r.handled()&&r.changed(),text+" / "+r.lines());}
    private static MonitorRuntime.Snapshot state(NetworkSimulator s,String label){return s.monitor().snapshots("m").stream().filter(x->x.label().equals(label)).findFirst().orElseThrow();}
    private static void ticks(NetworkSimulator s,int count){for(int i=0;i<count;i++)s.advance(1);}
    public static void main(String[] args){
        var s=new NetworkSimulator();var m=new NetworkProfile(DeviceKind.SERVER);m.address="10.80.0.1";
        var target=new NetworkProfile(DeviceKind.SERVER);target.address="10.80.0.2";
        s.put("m",m);s.put("target",target);s.link(new NetworkSimulator.Link("mt","m",0,"target",0,CableGrade.CAT5E,3,false));
        cmd(s,"monitor add site web 10.80.0.2");
        cmd(s,"monitor add host 10.80.0.2");cmd(s,"monitor interval 1");cmd(s,"monitor thresholds 1 1");cmd(s,"monitor on");
        ok(state(s,"site").state()==MonitorRuntime.State.UNKNOWN,"開始だけでUPにしない");ticks(s,3);
        ok(state(s,"host").state()==MonitorRuntime.State.UP,"本体はICMP応答可能");
        ok(state(s,"site").state()==MonitorRuntime.State.DOWN,"web offだけの停止を検出");
        ok(state(s,"site").detail().contains("Web"),"診断にサービス種別を表示");
        target.web=true;ticks(s,21);ok(state(s,"site").state()==MonitorRuntime.State.UP,"サービスだけの復旧を検出");
        long logs=s.logs("m").size();ticks(s,60);ok(s.logs("m").size()==logs,"正常サービス確認は毎回ログを出さない");
        target.web=false;ticks(s,21);ok(state(s,"site").state()==MonitorRuntime.State.DOWN&&state(s,"host").state()==MonitorRuntime.State.UP,"pingとWebは独立した判定");
        cmd(s,"monitor add files transfer 10.80.0.2");cmd(s,"monitor add dns dns 10.80.0.2");cmd(s,"monitor add signal control 10.80.0.2");ticks(s,5);
        ok(state(s,"files").state()==MonitorRuntime.State.UP&&s.flows().isEmpty(),"転送監視は実際の仮想転送を開始しない");
        ok(state(s,"dns").state()==MonitorRuntime.State.UP,"DNSの稼働確認");
        ok(state(s,"signal").state()==MonitorRuntime.State.DOWN,"サーバーを制御端末だと誤判定しない");
        var disable=NetworkServiceCommands.run(s,"target","dns-server off");ok(disable.changed(),"DNSサービスを停止");ticks(s,25);
        ok(state(s,"dns").state()==MonitorRuntime.State.DOWN,"UDP53のサービス停止を検出");
        var before=state(s,"files").probes();cmd(s,"monitor on");ok(state(s,"files").probes()==before,"同じ設定の再適用で時計を消さない");
        cmd(s,"monitor delete site");cmd(s,"monitor add site 10.80.0.2");ticks(s,5);
        ok(state(s,"site").state()==MonitorRuntime.State.UP,"サービス削除後に同名のICMPを登録できる");
        System.out.println("MonitorServiceTest: "+checks+" checks passed");
    }
}
