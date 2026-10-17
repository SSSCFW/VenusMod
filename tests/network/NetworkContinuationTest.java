import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** 実機器設定に対する通信結果と過負荷時の安全な停止を検査する。 */
public final class NetworkContinuationTest {
    private static int checks, failures;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void test(String name, Runnable body) {
        try { body.run(); System.out.println("PASS " + name); }
        catch (AssertionError | RuntimeException error) {
            failures++; System.out.println("FAIL " + name + ": " + error.getMessage());
        }
    }
    private static NetworkProfile host(String ip) {
        var host = new NetworkProfile(DeviceKind.PC); host.address = ip; return host;
    }
    private static NetworkSimulator pair(CableGrade grade) {
        var sim = new NetworkSimulator();
        sim.put("a", host("192.168.1.10")); sim.put("b", host("192.168.1.20"));
        sim.link(new NetworkSimulator.Link("ab", "a", 0, "b", 0, grade, 1, false));
        return sim;
    }
    private static NetworkSimulator.Link link(NetworkSimulator sim) {
        return sim.links().iterator().next();
    }
    public static void main(String[] args) {
        test("baseline IPv4 and round-trip", () -> {
            var sim = pair(CableGrade.CAT5E);
            check(sim.ping("a", "192.168.1.20").success(), "端末2台の往復通信");
            check(sim.ping("a", "192.168.1.20").bps() == 1_000_000_000L, "初期1Gbps");
            var saved = sim.nodes().get("a"); saved.ports[0].vlan=42; saved.failed=true;
            var copy=NetworkProfile.decode(DeviceKind.PC, saved.encode());
            check(copy.failed && copy.ports[0].vlan==42 && copy.address.equals(saved.address), "故障と設定を保持");
            check(NetworkProfile.failureProbability(.0005,24000,3)==0.0, "完全安定化は0%");
        });
        test("VLAN ceiling must fail closed", () -> {
            var sim=pair(CableGrade.CAT5E);
            for(int n=0;n<24;n++) {
                var sw=new NetworkProfile(DeviceKind.L2_SWITCH);
                for(int p=0;p<6;p++) sw.ports[p].vlan=n*6+p+1;
                sim.put("sw"+n,sw);
            }
            var result=sim.ping("a","192.168.1.20");
            check(!result.success() && result.message().contains("上限"), "上限超過を正常転送にしてはいけない");
            sim.remove("sw23");sim.remove("sw22");sim.remove("sw21");
            check(sim.ping("a","192.168.1.20").success(), "縮小後に再び使える");
        });
        test("bounded long Layer-2 path", () -> {
            var sim=new NetworkSimulator(); sim.put("a",host("10.0.0.1")); sim.put("b",host("10.0.0.2"));
            String prev="a"; int previousPort=0;
            for(int n=0;n<70;n++) {
                String id="long"+n;sim.put(id,new NetworkProfile(DeviceKind.L2_SWITCH));
                sim.link(new NetworkSimulator.Link("l"+n,prev,previousPort,id,0,CableGrade.CAT5E,1,false));
                prev=id;previousPort=1;
            }
            sim.link(new NetworkSimulator.Link("end",prev,1,"b",0,CableGrade.CAT5E,1,false));
            var result=sim.ping("a","10.0.0.2");
            check(!result.success() && result.message().contains("上限"), "長大な経路は無制限の経路配列を作らず拒否する");
        });
        test("fixed speed mismatch", () -> {
            var sim=pair(CableGrade.CAT6);
            var a=sim.nodes().get("a").ports[0];var b=sim.nodes().get("b").ports[0];
            a.speedLimitBps=10_000_000;b.speedLimitBps=100_000_000;
            check(sim.linkCapacity(link(sim))==0, "固定10Mと固定100Mは共通速度なし");
            b.speedLimitBps=0;
            check(sim.linkCapacity(link(sim))==10_000_000, "対向Autoは固定速度と交渉可能");
        });
        test("removed port module keeps invalid fixed setting but link down", () -> {
            var sim=pair(CableGrade.CAT6);
            var a=sim.nodes().get("a").ports[0];var b=sim.nodes().get("b").ports[0];
            a.moduleTier=b.moduleTier=1;a.speedLimitBps=10_000_000_000L;
            check(sim.linkCapacity(link(sim))==10_000_000_000L, "10G対応中");
            a.moduleTier=0;
            check(sim.linkCapacity(link(sim))==0, "モジュール抜去時は固定設定を黙って1Gに変更しない");
            check(a.speedLimitBps==10_000_000_000L, "希望設定自体は保持");
            a.speedLimitBps=0;
            check(sim.linkCapacity(link(sim))==1_000_000_000L, "Autoに戻せば1Gで接続");
        });
        test("MAC addresses do not use Java hashCode collisions", () -> {
            check("Aa".hashCode()=="BB".hashCode(), "衝突入力の前提");
            check(!NetworkSimulator.macAddress("Aa").equals(NetworkSimulator.macAddress("BB")), "異なるIDに同じMACを作らない");
            check(NetworkSimulator.macAddress("Aa").matches("02(:[0-9a-f]{2}){5}"), "ローカル管理ユニキャストMAC");
        });
        test("ARP is scoped to the incoming router interface", () -> {
            var sim=new NetworkSimulator(); var h=host("192.168.2.10");
            var r=new NetworkProfile(DeviceKind.ROUTER);
            sim.put("h",h);sim.put("r",r);
            sim.link(new NetworkSimulator.Link("hr","h",0,"r",0,CableGrade.CAT5E,1,false));
            check(!sim.route("h","192.168.2.1").success(), "LAN1に接続したままLAN2のアドレスへARPできない");
            h.address="192.168.1.10";sim.invalidate();
            check(sim.ping("h","192.168.1.1").success(), "正しいLAN1へのARPは成功");
            h.gateway="192.168.1.1";
            check(sim.ping("h","192.168.2.1").success(), "ゲートウェイ経由でルーター自身の他IFへ到達できる");
        });
        test("DHCP excludes virtual gateway addresses", () -> {
            var sim=new NetworkSimulator(); var r=new NetworkProfile(DeviceKind.ROUTER);
            r.vip="192.168.1.100";r.dhcp=true;
            sim.put("h",new NetworkProfile(DeviceKind.PC));sim.put("r",r);
            sim.link(new NetworkSimulator.Link("hr","h",0,"r",0,CableGrade.CAT5E,1,false));
            check(sim.dhcp("h").success(), "DHCPが応答");
            check(!sim.nodes().get("h").address.equals(r.vip), "仮想ゲートウェイのIPをクライアントへ貸し出さない");
        });
        test("VLAN isolation still works", () -> {
            var sim=new NetworkSimulator(); var sw=new NetworkProfile(DeviceKind.L2_SWITCH);
            sim.put("a",host("10.0.0.1"));sim.put("b",host("10.0.0.2"));sim.put("s",sw);
            sim.link(new NetworkSimulator.Link("as","a",0,"s",0,CableGrade.CAT5E,2,false));
            sim.link(new NetworkSimulator.Link("bs","b",0,"s",1,CableGrade.CAT5E,2,false));
            check(sim.ping("a","10.0.0.2").success(), "同一VLAN");
            sw.ports[1].vlan=20;sim.invalidate();
            check(!sim.ping("a","10.0.0.2").success(), "別VLANは遮断");
        });
        test("bidirectional routes and shared bandwidth", () -> {
            var sim=pair(CableGrade.CAT5E);
            check(sim.startTransfer("a","192.168.1.20",1_000_000_000).success(), "フロー1");
            check(sim.startTransfer("a","192.168.1.20",1_000_000_000).success(), "フロー2");
            sim.advance(20);
            for(var f:sim.flows())check(f.remaining>930_000_000&&f.remaining<945_000_000, "合計1Gbpsを共有");
            sim.unlink("ab");check(sim.flows().isEmpty(), "切断で未完了転送を中断");
        });
        System.out.println("NetworkContinuationTest: "+checks+" checks; "+failures+" failed cases");
        if(failures!=0)throw new AssertionError("回帰ケースの失敗: "+failures);
    }
}
