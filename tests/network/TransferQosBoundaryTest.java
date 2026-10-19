import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class TransferQosBoundaryTest {
    private static int checks;
    private static void ok(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    private static NetworkProfile host(String ip){var p=new NetworkProfile(DeviceKind.PC);p.address=ip;return p;}
    private static void config(NetworkSimulator sim,String device,String command){var r=NetworkServiceCommands.run(sim,device,command);ok(r.changed(),command+" "+r.lines());}
    public static void main(String[] args){
        var s=new NetworkSimulator();var r=new NetworkProfile(DeviceKind.ROUTER);r.dhcp=false;r.ports[0].address="10.40.0.1";r.ports[1].address="198.51.100.1";
        var a=host("10.40.0.10");a.gateway="10.40.0.1";var b=host("198.51.100.20");
        s.put("a",a);s.put("r",r);s.put("b",b);s.link(new NetworkSimulator.Link("lan","a",0,"r",0,CableGrade.CAT5E,2,false));s.link(new NetworkSimulator.Link("wan","r",1,"b",0,CableGrade.CAT5E,2,true));
        config(s,"r","nat port 0 inside");config(s,"r","nat port 1 outside");config(s,"r","nat on");
        ok(s.startTransfer("a",b.address,1_000_000_000_000L,TransferPriority.HIGH).success(),"NAT high admitted");var high=s.flows().getLast();
        ok(s.startTransfer("a",b.address,1_000_000_000_000L,TransferPriority.LOW).success(),"NAT low admitted");var low=s.flows().getLast();
        s.advance(5);ok(Math.abs(high.allocatedBps()-16_000_000)<1e-5&&Math.abs(low.allocatedBps()-4_000_000)<1e-5,"WAN20M caps4:1");
        var nat=s.nat().sessions("r");ok(nat.size()==1,"flows may share a NAT binding");
        ok(!s.cancelTransfer("b",high.id).success()&&s.nat().sessions("r").equals(nat),"other sender cannot cancel or change NAT");
        ok(s.cancelTransfer("a",high.id).success()&&s.nat().sessions("r").size()==1,"cancel does not delete shared binding");
        for(int i=0;i<2501;i++)s.advance(1);
        ok(s.flows().size()==1&&s.nat().sessions("r").size()==1&&low.remaining<low.totalBytes,"surviving transfer retains NAT and advances");
        long computes=s.transferAllocationCount();ok(computes<8,"stable NAT flow does not recompute every tick");
        var original=s.nat().sessions("r");String bad=String.join(" ",NetworkCli.run(s,"a","transfer 198.51.100.20 100 invalid").lines());
        ok(bad.contains("優先度")&&s.nat().sessions("r").equals(original),"invalid priority does not refresh bindings");
        ok(s.cancelTransfer("a",low.id).success(),"cancel last flow");for(int i=0;i<2402;i++)s.advance(1);ok(s.nat().sessions("r").isEmpty(),"cancelled binding expires normally");
        // 全128転送は同じリンクで重み配分し、限界を越えた転送を追加しない。
        var limit=new NetworkSimulator();List<String> ids=new ArrayList<>();for(int i=0;i<8;i++){String id="p"+i;ids.add(id);var p=host("192.0.2."+(i+1));p.buffer=6;limit.put(id,p);}
        // ローカル宛先も通常の転送受付を通り、各機器容量を共有する。
        for(int i=0;i<128;i++){String id=ids.get(i%8);if(!limit.startTransfer(id,limit.nodes().get(id).address,1_000_000_000_000L,i%2==0?TransferPriority.HIGH:TransferPriority.LOW).success())throw new AssertionError("128 admissions");}
        ok(limit.flows().size()==128&&!limit.startTransfer("p0","192.0.2.1",1).success(),"global flow bound unchanged");
        limit.advance(1);for(String id:ids){double total=limit.flows().stream().filter(f->f.source.equals(id)).mapToDouble(NetworkSimulator.Flow::allocatedBps).sum();if(total>limit.nodes().get(id).capacity()+1e-5)throw new AssertionError("device oversubscription");}ok(true,"8 device capacities respected by128 transfers");
        computes=limit.transferAllocationCount();for(int i=0;i<50;i++)limit.advance(1);ok(limit.transferAllocationCount()==computes,"128-flow steady cache reused");
        limit.advance(1_000_000_000L);ok(limit.flows().isEmpty(),"large time jump consumes at most start/finish events, not billion ticks");
        System.out.println("TransferQosBoundaryTest: "+checks+" checks passed (128-flow bound tested)");
    }
}
