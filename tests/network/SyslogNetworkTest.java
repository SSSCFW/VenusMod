import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class SyslogNetworkTest {
    private static int checks;
    private static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void cmd(NetworkSimulator sim,String id,String input){var r=NetworkServiceCommands.run(sim,id,input);if(!r.handled()||!r.changed())throw new AssertionError(input+" / "+r.lines());}
    private static NetworkProfile host(DeviceKind kind,String ip,String gw){var p=new NetworkProfile(kind);p.address=ip;p.gateway=gw;return p;}
    private static NetworkSimulator network(){
        var s=new NetworkSimulator();s.put("a",host(DeviceKind.PC,"10.10.1.10","10.10.1.1"));
        var r=new NetworkProfile(DeviceKind.ROUTER);r.ports[0].address="10.10.1.1";r.ports[1].address="203.0.113.1";r.dhcp=false;s.put("r",r);
        s.put("c",host(DeviceKind.SERVER,"203.0.113.20",""));
        s.link(new NetworkSimulator.Link("ar","a",0,"r",0,CableGrade.CAT5E,2,false));s.link(new NetworkSimulator.Link("rc","r",1,"c",0,CableGrade.CAT5E,2,false));
        cmd(s,"c","syslog server on");cmd(s,"a","syslog target 203.0.113.20");cmd(s,"a","syslog on");return s;
    }
    private static void event(NetworkSimulator s,String source,String text){s.log(source,3,text);for(int i=0;i<4;i++)s.advance(1);}
    private static boolean has(NetworkSimulator s,String receiver,String text){return s.syslog().entries(receiver).stream().anyMatch(e->e.message().equals(text));}
    public static void main(String[] args){
        var s=network();event(s,"a","one-way");ok(has(s,"c","one-way"),"Syslogは返信経路なしで届く");
        ok(!s.ping("a","203.0.113.20").success(),"同じ構成でpingの返信は失敗する");
        cmd(s,"r","acl add 0 out 10 deny udp any any");cmd(s,"r","acl add 0 out 20 permit ip any any");event(s,"a","reply-denied");ok(has(s,"c","reply-denied"),"返信側ACLをSyslogに要求しない");
        cmd(s,"r","acl add 0 in 10 deny udp any any 514");cmd(s,"r","acl add 0 in 20 permit ip any any");event(s,"a","blocked");
        ok(!has(s,"c","blocked"),"送信経路のUDP514 ACLを反映");for(int i=0;i<50;i++)s.advance(1);ok(s.syslog().pending()==0,"配送失敗を再帰で転送しない");ok(s.syslog().status("a").toString().contains("ACL"),"診断に拒否理由");
        cmd(s,"r","acl clear 0 in");cmd(s,"r","acl clear 0 out");cmd(s,"r","nat port 0 inside");cmd(s,"r","nat port 1 outside");cmd(s,"r","nat on");
        event(s,"a","nat-out");ok(has(s,"c","nat-out")&&s.nat().sessions("r").size()==1,"片方向NAPTを確定");
        var nat=s.syslog().entries("c").stream().filter(e->e.message().equals("nat-out")).findFirst().orElseThrow();
        ok(nat.sourceAddress().equals("203.0.113.1")&&nat.origin().equals("a"),"観測された送信元IPと元機器IDを分離");
        long session=s.nat().sessions("r").getFirst().id(),expiry=s.nat().sessions("r").getFirst().expiresAt();cmd(s,"c","syslog allow 10.10.1.0/24");event(s,"a","filter-blocked");
        ok(!has(s,"c","filter-blocked"),"収集元フィルターはNAT変換後IPを検査");ok(s.nat().sessions("r").getFirst().id()==session,"Syslog設定で既存NATを消さない");
        ok(s.nat().sessions("r").getFirst().expiresAt()==expiry,"受信拒否で既存NATの期限を延長しない");
        cmd(s,"c","syslog allow 203.0.113.1/32");event(s,"a","filter-allowed");ok(has(s,"c","filter-allowed"),"変換後IPを許可すると収集");
        cmd(s,"c","syslog server off");s.nat().clearRouters(Set.of("r"));event(s,"a","stopped");ok(!has(s,"c","stopped")&&s.nat().sessions("r").isEmpty(),"受信停止で変換を確定しない");
        cmd(s,"c","syslog server on");s.unlink("rc");event(s,"a","disconnected");ok(!has(s,"c","disconnected"),"断線を迂回しない");
        s.link(new NetworkSimulator.Link("rc","r",1,"c",0,CableGrade.CAT5E,2,false));event(s,"a","restored");ok(has(s,"c","restored")&&!has(s,"c","disconnected"),"復旧後の新規ログだけ配送、過去失敗は再送しない");
        // 外側ポート転送: 一方向のため、内部受信サーバーのdefault gatewayは不要。
        var inbound=new NetworkSimulator();inbound.put("a",host(DeviceKind.PC,"203.0.113.20",""));inbound.put("c",host(DeviceKind.SERVER,"10.20.0.20",""));
        var r=new NetworkProfile(DeviceKind.ROUTER);r.ports[0].address="203.0.113.1";r.ports[1].address="10.20.0.1";inbound.put("r",r);
        inbound.link(new NetworkSimulator.Link("ar","a",0,"r",0,CableGrade.CAT5E,1,false));inbound.link(new NetworkSimulator.Link("rc","r",1,"c",0,CableGrade.CAT5E,1,false));
        cmd(inbound,"r","nat port 0 outside");cmd(inbound,"r","nat port 1 inside");cmd(inbound,"r","nat on");cmd(inbound,"r","nat forward add 0 udp 5514 10.20.0.20 514");
        cmd(inbound,"c","syslog server on");cmd(inbound,"a","syslog target 203.0.113.1 5514");cmd(inbound,"a","syslog on");event(inbound,"a","forwarded");
        ok(has(inbound,"c","forwarded")&&inbound.nat().sessions("r").size()==1,"任意公開ポート5514→内部514");
        cmd(inbound,"r","nat forward delete 0 udp 5514");cmd(inbound,"r","nat forward add 0 udp 5514 10.20.0.20 515");event(inbound,"a","wrong-port");
        ok(!has(inbound,"c","wrong-port")&&inbound.nat().sessions("r").isEmpty(),"最終UDP515をSyslog514として受け取らない");
        // 同じIPの独立網を横断しない。故障状態のイベントも瞬間移動させない。
        var isolated=new NetworkSimulator();
        for(int i=0;i<2;i++){isolated.put("a"+i,host(DeviceKind.PC,"10.0.0.1",""));isolated.put("c"+i,host(DeviceKind.SERVER,"10.0.0.2",""));isolated.link(new NetworkSimulator.Link("l"+i,"a"+i,0,"c"+i,0,CableGrade.CAT5E,1,false));cmd(isolated,"c"+i,"syslog server on");cmd(isolated,"a"+i,"syslog target 10.0.0.2");cmd(isolated,"a"+i,"syslog on");}
        event(isolated,"a0","only-lan0");ok(has(isolated,"c0","only-lan0")&&!has(isolated,"c1","only-lan0"),"同じIPの別物理網へ漏れない");
        isolated.log("a0","queued-before-failure");isolated.nodes().get("a0").failed=true;event(isolated,"a0","failed-device");
        ok(!has(isolated,"c0","failed-device")&&!has(isolated,"c0","queued-before-failure"),"完全停止機器は待ちも新規ログも送れない");
        ok(isolated.logs("a0").contains("failed-device"),"送れなくてもローカルログは残る");
        System.out.println("SyslogNetworkTest: "+checks+" checks passed");
    }
}
