import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** 公開CLIだけでログ集中収集を検査する。新APIを先取りせず未対応の失敗を確認する。 */
public final class SyslogContractTest {
    private static int checks;
    private static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static NetworkServiceCommands.Reply cmd(NetworkSimulator s,String id,String text){var r=NetworkServiceCommands.run(s,id,text);ok(r.handled(),"未対応: "+text);return r;}
    private static String logs(NetworkSimulator s){return String.join("\n",cmd(s,"c","syslog logs").lines());}
    public static void main(String[] args){
        var s=new NetworkSimulator();var a=new NetworkProfile(DeviceKind.PC);a.address="10.0.0.1";a.name="base-a";
        var c=new NetworkProfile(DeviceKind.SERVER);c.address="10.0.0.2";s.put("a",a);s.put("c",c);
        s.link(new NetworkSimulator.Link("ac","a",0,"c",0,CableGrade.CAT5E,2,false));
        ok(cmd(s,"c","syslog server on").changed(),"受信を有効化");
        ok(cmd(s,"a","syslog target 10.0.0.2").changed(),"宛先設定");ok(cmd(s,"a","syslog on").changed(),"送信を有効化");
        s.log("a","first-message");ok(!logs(s).contains("first-message"),"enqueueは同期配送しない");s.advance(1);
        ok(logs(s).contains("first-message")&&logs(s).contains("base-a"),"実経路後に機器名付きで受信");
        s.log("a","second-message");s.advance(1);ok(logs(s).contains("second-message"),"次のログも配送");
        String before=s.nodes().get("a").encode();ok(!cmd(s,"a","syslog target example.com").changed(),"外部名を拒否");ok(before.equals(s.nodes().get("a").encode()),"不正入力で設定を壊さない");
        ok(cmd(s,"a","syslog level 3").changed(),"severity閾値");s.log("a","info-filtered");s.advance(1);ok(!logs(s).contains("info-filtered"),"infoを除外");
        ok(!cmd(s,"a","syslog test 3 manual-error").changed(),"testは保存設定ではない");s.advance(1);ok(logs(s).contains("manual-error"),"severity3は受信");
        cmd(s,"a","syslog off");s.log("a","disabled-message");s.advance(20);ok(!logs(s).contains("disabled-message"),"送信off");
        cmd(s,"c","syslog clear");ok(!logs(s).contains("first-message"),"自機の受信履歴をクリア");
        ok(!cmd(s,"a","syslog server on").changed(),"PCはコレクターにならない");
        System.out.println("SyslogContractTest: "+checks+" checks passed");
    }
}
