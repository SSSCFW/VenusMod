import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** 公開転送APIとCLIの検査。時間境界試験だけは純Javaの待ち時間を固定する。 */
public final class TransferQosTest {
    private static int checks;
    private static void ok(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
    private static void near(double a,double b,String s){ok(Math.abs(a-b)<=Math.max(1,Math.abs(b))*1e-6,s+" "+a+" vs "+b);}
    private static NetworkProfile host(String address){var p=new NetworkProfile(DeviceKind.PC);p.address=address;return p;}
    private static NetworkSimulator fixture(){
        var s=new NetworkSimulator();s.put("a",host("10.50.0.10"));s.put("b",host("10.50.0.20"));s.put("c",host("10.50.0.30"));s.put("sw",new NetworkProfile(DeviceKind.L2_SWITCH));
        for(int i=0;i<3;i++)s.link(new NetworkSimulator.Link("wire"+i,"sw",i,""+(char)('a'+i),0,CableGrade.CAT5E,1,false));return s;
    }
    private static String cli(NetworkSimulator s,String id,String command){return String.join("\n",NetworkCli.run(s,id,command).lines());}
    private static NetworkSimulator.Flow start(NetworkSimulator s,String target,long bytes,TransferPriority priority){ok(s.startTransfer("a",target,bytes,priority).success(),"start "+priority);return s.flows().getLast();}
    public static void main(String[] args){
        var s=fixture();var high=start(s,"10.50.0.20",1_000_000_000L,TransferPriority.HIGH);var low=start(s,"10.50.0.30",1_000_000_000L,TransferPriority.LOW);
        s.advance(1);near(high.allocatedBps(),800_000_000,"high4 share");near(low.allocatedBps(),200_000_000,"low1 share");ok(low.remaining<low.totalBytes,"low progresses");
        long before=s.transferAllocationCount();s.advance(10);ok(s.transferAllocationCount()==before,"stable allocation reused");
        ok(cli(s,"b","transfer priority "+high.id+" low").contains("見つかりません"),"other source cannot edit priority");ok(high.priority()==TransferPriority.HIGH,"no side effect after denial");
        ok(cli(s,"b","transfer cancel "+low.id).contains("見つかりません")&&s.flows().size()==2,"other source cannot cancel");
        ok(cli(s,"a","transfer priority "+high.id+" normal").contains("変更"),"change priority through CLI");s.advance(1);
        near(high.allocatedBps(),1_000_000_000.0*2/3,"new weight applied");near(low.allocatedBps(),1_000_000_000.0/3,"low still progresses");
        ok(cli(s,"a","flows").contains("normal")&&cli(s,"a","flows").contains("割当"),"status exposes priority and allocation");
        ok(cli(s,"a","transfer cancel "+high.id).contains("停止"),"cancel self");s.advance(1);near(low.allocatedBps(),1_000_000_000,"remaining flow gets full link");
        String profile=s.nodes().get("a").encode();int logs=s.logs("a").size();
        ok(cli(s,"a","transfer 10.50.0.20 100 turbo").contains("優先度"),"invalid priority rejected");ok(s.flows().size()==1&&s.logs("a").size()==logs&&s.nodes().get("a").encode().equals(profile),"invalid priority is nonmutating");
        ok(cli(s,"a","transfer 10.50.0.20 -1 high").contains("転送量"),"negative bytes rejected");ok(cli(s,"a","transfer 10.50.0.20 9223372036854775807 high").contains("入力"),"overflow rejected");
        ok(cli(s,"a","transfer 10.50.0.20 100").contains("開始")&&s.flows().getLast().priority()==TransferPriority.NORMAL,"old CLI keeps normal default");
        ok(cli(s,"a","transfer cancel -1").contains("ID"),"invalid ID rejected");
        // 時間をまとめて進めても、完了した短い転送の空きを同じ区間内で再配分する。
        var batch=fixture();var tiny=start(batch,"10.50.0.20",1_000_000,TransferPriority.NORMAL);var bulk=start(batch,"10.50.0.30",1_000_000_000,TransferPriority.NORMAL);
        tiny.delayTicks=bulk.delayTicks=0;batch.advance(20);near(bulk.remaining,876_000_000,"small completion redistributes unused second");ok(batch.flows().size()==1,"finished flow removed");
        var ticks=fixture();var x=start(ticks,"10.50.0.20",1_000_000,TransferPriority.NORMAL);var y=start(ticks,"10.50.0.30",1_000_000_000,TransferPriority.NORMAL);
        x.delayTicks=y.delayTicks=0;for(int i=0;i<20;i++)ticks.advance(1);near(y.remaining,bulk.remaining,"batch matches per-tick transfer");
        // 待機中の転送は共有帯域を奪わず、開始後だけ共有する。
        var delayed=fixture();var live=start(delayed,"10.50.0.20",1_000_000_000,TransferPriority.NORMAL);var wait=start(delayed,"10.50.0.30",1_000_000_000,TransferPriority.NORMAL);
        live.delayTicks=0;wait.delayTicks=10;delayed.advance(20);near(live.remaining,906_250_000,"waiting flow consumes no bandwidth");near(wait.remaining,968_750_000,"waiting flow starts after delay");
        delayed.unlink("wire2");ok(delayed.flows().isEmpty(),"existing topology invalidation preserved");
        // ハードウェア全体が律速する場合も、優先度で容量を越えない。
        var box=fixture();for(var p:box.nodes().values())for(var port:p.ports)port.moduleTier=2;
        // ケーブルは1Gのままなので速度優先度だけでは物理上限を突破しない。
        var big=start(box,"10.50.0.20",1_000_000_000,TransferPriority.HIGH);box.advance(1);near(big.allocatedBps(),1_000_000_000,"priority does not upgrade cable");
        ok(!box.startTransfer("a","10.50.0.20",1,null).success(),"null priority rejected before networking");
        var save=NetworkProfile.decode(DeviceKind.PC,box.nodes().get("a").encode());ok(save.address.equals("10.50.0.10"),"profile compatibility unchanged");
        System.out.println("TransferQosTest: "+checks+" checks passed");
    }
}
