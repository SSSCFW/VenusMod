import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class SyslogLimitsTest {
    private static int checks;
    private static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static NetworkProfile host(DeviceKind kind,String ip){var p=new NetworkProfile(kind);p.address=ip;return p;}
    private static void cmd(NetworkSimulator s,String id,String text){var r=NetworkServiceCommands.run(s,id,text);if(!r.handled()||!r.changed())throw new AssertionError(text+" / "+r.lines());}
    private static NetworkSimulator net(){var s=new NetworkSimulator();s.put("a",host(DeviceKind.PC,"10.0.0.1"));s.put("b",host(DeviceKind.PC,"10.0.0.3"));s.put("c",host(DeviceKind.SERVER,"10.0.0.2"));s.put("sw",new NetworkProfile(DeviceKind.L2_SWITCH));
        s.link(new NetworkSimulator.Link("as","a",0,"sw",0,CableGrade.CAT5E,1,false));s.link(new NetworkSimulator.Link("bs","b",0,"sw",1,CableGrade.CAT5E,1,false));s.link(new NetworkSimulator.Link("cs","c",0,"sw",2,CableGrade.CAT5E,1,false));
        cmd(s,"c","syslog server on");for(String id:List.of("a","b")){cmd(s,id,"syslog target 10.0.0.2");cmd(s,id,"syslog on");}return s;}
    public static void main(String[] args){
        var s=net();for(int i=0;i<1000;i++)s.log("a","flood-"+i);ok(s.syslog().pending()==SyslogRuntime.PER_SECOND,"1秒の送信元上限");
        s.log("b","fair-b");s.advance(1);ok(s.syslog().entries("c").stream().anyMatch(e->e.message().equals("fair-b")),"他送信元が大量ログに飢餓しない");ok(s.syslog().lastWork()<=SyslogRuntime.WORK_PER_TICK,"配送予算");
        for(int i=0;i<20;i++)s.advance(1);ok(s.syslog().pending()==0&&s.syslog().entries("c").size()==17,"上限を超えたログは遅れて再送されない");
        int before=s.syslog().retained();String status=s.syslog().status("a").toString();for(int i=0;i<1000;i++){s.syslog().entries("c");s.syslog().status("a");}ok(s.syslog().retained()==before&&s.syslog().status("a").toString().equals(status),"閲覧で期限や履歴・統計を変更しない");
        s.log("a","expired");s.advance(SyslogRuntime.MAX_AGE);ok(s.syslog().entries("c").stream().noneMatch(e->e.message().equals("expired")),"長時間待ちは期限で破棄");
        s.log("a","discard-target-change");cmd(s,"a","syslog target 10.0.0.4");ok(s.syslog().pending()==0,"宛先変更で古い待ちを流用しない");cmd(s,"a","syslog target 10.0.0.2");
        s.log("a",3,"header\nforged\rline\u001b§c message 😀"+"x".repeat(2000));s.advance(1);
        var cleaned=s.syslog().entries("c").getLast();ok(cleaned.message().length()<=200&&!cleaned.message().contains("§")&&cleaned.message().chars().noneMatch(Character::isISOControl),"文字数と制御文字を制限");
        for(int batch=0;batch<20;batch++){s.advance(20);for(int i=0;i<16;i++)s.log("a","batch-"+batch+"-"+i);for(int i=0;i<8;i++)s.advance(1);}
        ok(s.syslog().entries("c").size()==256,"受信者あたり256件");ok(s.syslog().entries("c").getLast().message().equals("batch-19-15"),"古いものから削除し最新を保持");ok(s.syslog().describe("c",20).size()==21,"画面出力は20件に制限");
        boolean bad=false;try{s.syslog().describe("c",21);}catch(IllegalArgumentException e){bad=true;}ok(bad,"無制限取得を拒否");
        s.remove("a");ok(!s.syslog().entries("c").isEmpty(),"送信元撤去でも配送済み受信履歴は残る");s.remove("c");ok(s.syslog().retained()==0,"受信者削除でランタイム履歴を解放");
        var self=new NetworkSimulator();self.put("self",host(DeviceKind.SERVER,"10.0.0.1"));cmd(self,"self","syslog server on");cmd(self,"self","syslog target 10.0.0.1");cmd(self,"self","syslog on");self.log("self","self-event");self.advance(1);for(int i=0;i<200;i++)self.advance(1);
        ok(self.syslog().retained()==1&&self.syslog().pending()==0,"自己宛ても受信から再転送しない");
        var total=new NetworkSimulator();
        for(int i=0;i<17;i++){String id="c"+i;var p=host(DeviceKind.SERVER,"10.0.0.1");total.put(id,p);cmd(total,id,"syslog server on");cmd(total,id,"syslog target 10.0.0.1");cmd(total,id,"syslog on");}
        for(int batch=0;batch<16;batch++)for(int i=0;i<17;i++){String id="c"+i;total.advance(20);for(int n=0;n<16;n++)total.log(id,"keep-"+batch+"-"+n);for(int n=0;n<8;n++)total.advance(1);}
        ok(total.syslog().retained()==4096,"全体4096件に制限");int sum=0;for(int i=0;i<17;i++)sum+=total.syslog().entries("c"+i).size();ok(sum==4096,"グローバル削除と受信者索引が整合");
        var queue=new NetworkSimulator();for(int i=0;i<80;i++){var p=host(DeviceKind.PC,"10.0.0.1");p.syslog.enabled=true;p.syslog.target="10.0.0.2";queue.put("a"+i,p);for(int n=0;n<16;n++)queue.log("a"+i,"overload");}
        ok(queue.syslog().pending()==1024,"全体配送待ち1024件に制限");queue.advance(1);ok(queue.syslog().pending()==1022,"1呼出し2件だけ配送/破棄");
        for(int i=0;i<80;i++)queue.remove("a"+i);ok(queue.syslog().pending()==0,"削除後に配送待ちを残さない");
        System.out.println("SyslogLimitsTest: "+checks+" checks passed");
    }
}
