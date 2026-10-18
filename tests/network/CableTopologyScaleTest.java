import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** 実TPSではなく、1000独立区間の索引を持つときの局所処理量を検査する。 */
public final class CableTopologyScaleTest {
    private static int checks;
    private static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    private static void settle(CableTopologyTest.World w){int calls=0;while(!w.topology.idle()&&calls++<10000){w.topology.step(128);if(w.topology.lastSteps()>128)throw new AssertionError("budget exceeded");}check(w.topology.idle()&&w.topology.error().isEmpty(),"bounded completion");}
    public static void main(String[] args){
        var w=new CableTopologyTest.World();for(int n=0;n<1000;n++)w.line(1,7,n*16,"network"+n);settle(w);
        check(w.links.size()==1000&&w.topology.trackedBlocks()==5000,"1000 intervals / 5000 cable blocks");
        var untouched=w.links.values().stream().filter(l->l.a().startsWith("network999")).findFirst().orElseThrow();
        long before=w.topology.totalSteps();w.reads.clear();w.cable(4,500*16,null);settle(w);long removal=w.topology.totalSteps()-before;
        check(w.links.size()==999&&w.links.get(untouched.id())==untouched,"999 links retained without replacement");
        check(w.reads.stream().allMatch(p->Math.abs(p.z()-500*16)<=1),"only changed neighborhood read");
        check(removal<128,"local scan cost independent of 999 other intervals");
        before=w.topology.totalSteps();w.reads.clear();w.cable(4,500*16,CableGrade.CAT6A);settle(w);long repair=w.topology.totalSteps()-before;
        check(w.links.size()==1000&&w.topology.trackedBlocks()==5000,"repaired interval and reclaimed index");
        check(w.reads.stream().allMatch(p->Math.abs(p.z()-500*16)<=1),"repair does not read distant intervals");
        check(repair<128,"repair stays local");
        // 管理者の明示再構築は全再発見。成功しても転送中断なしとは主張しない。
        w.topology.rebuild(w.devices.keySet());settle(w);check(w.links.size()==1000,"explicit rebuild recovers all intervals");
        System.out.println("CableTopologyScaleTest: "+checks+" checks; 1000 intervals / 5000 blocks; local cut="+removal+", repair="+repair+" work units (not TPS)");
    }
}
