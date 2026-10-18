import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** 既存公開APIで定期監視を試す。平常時は操作しなくても通常tickで進む。 */
public final class MonitorContractTest {
    private static int checks;
    private static void ok(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    private static NetworkServiceCommands.Reply cmd(NetworkSimulator s,String id,String command){var r=NetworkServiceCommands.run(s,id,command);ok(r.handled(),"未対応: "+command);return r;}
    private static String status(NetworkSimulator s){return String.join("\n",cmd(s,"m","monitor status").lines());}
    private static void ticks(NetworkSimulator s,int n){for(int i=0;i<n;i++)s.advance(1);}
    public static void main(String[] args){
        var s=new NetworkSimulator();var m=new NetworkProfile(DeviceKind.SERVER);m.address="10.42.0.1";
        var p=new NetworkProfile(DeviceKind.PC);p.address="10.42.0.2";s.put("m",m);s.put("p",p);
        s.link(new NetworkSimulator.Link("mp","m",0,"p",0,CableGrade.CAT5E,3,false));
        ok(cmd(s,"m","monitor add base 10.42.0.2").changed(),"監視対象登録");
        ok(cmd(s,"m","monitor interval 1").changed(),"周期設定");
        ok(cmd(s,"m","monitor thresholds 3 2").changed(),"閾値設定");
        ok(cmd(s,"m","monitor on").changed(),"監視開始");
        ok(status(s).contains("UNKNOWN"),"有効化だけでUPにしない");ticks(s,40);
        ok(status(s).contains("base 10.42.0.2 UP"),"2回の成功でUP");
        long before=s.logs("m").size();ticks(s,100);ok(s.logs("m").size()==before,"平常プローブでログを埋めない");
        p.failed=true;s.invalidate("p");ticks(s,20);ok(status(s).contains("base 10.42.0.2 UP"),"1回の失敗ではDOWNにしない");
        ticks(s,40);ok(status(s).contains("base 10.42.0.2 DOWN"),"3連続失敗でDOWN");
        ok(s.logs("m").stream().anyMatch(x->x.contains("MONITOR DOWN")),"障害ログ");
        before=s.logs("m").size();ticks(s,80);ok(s.logs("m").size()==before,"停止中の重複通知なし");
        p.failed=false;s.invalidate("p");ticks(s,20);ok(status(s).contains("base 10.42.0.2 DOWN"),"復旧1回は未確定");ticks(s,20);
        ok(status(s).contains("base 10.42.0.2 UP"),"連続成功で復旧");
        m.failed=true;s.invalidate("m");before=s.logs("m").size();ticks(s,100);ok(status(s).contains("PAUSED")&&s.logs("m").size()==before,"監視元停止は障害扱いにしない");
        m.failed=false;s.invalidate("m");ticks(s,40);ok(status(s).contains("base 10.42.0.2 UP"),"監視元復旧");
        cmd(s,"m","monitor off");before=s.logs("m").size();p.failed=true;ticks(s,100);ok(s.logs("m").size()==before,"停止後はプローブしない");
        String saved=m.encode();ok(!cmd(s,"m","monitor add bad 999.0.0.1").changed(),"不正IP拒否");ok(saved.equals(m.encode()),"不正設定は無変更");
        ok(!cmd(s,"p","monitor on").changed(),"PCを監視元にできない");
        System.out.println("MonitorContractTest: "+checks+" checks passed");
    }
}
