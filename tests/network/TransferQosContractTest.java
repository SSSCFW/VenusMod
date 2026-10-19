import dev.ssscfw.venusmod.infrastructure.network.core.*;

/** 公開APIの挙動。低速経路の未使用割当を高速転送へ返す。 */
public final class TransferQosContractTest {
    private static NetworkProfile host(String ip){var p=new NetworkProfile(DeviceKind.PC);p.address=ip;return p;}
    public static void main(String[] args){
        var s=new NetworkSimulator();var a=host("10.50.0.10");var b=host("10.50.0.20");var c=host("10.50.0.30");
        b.ports[0].speedLimitBps=100_000_000L;
        s.put("a",a);s.put("b",b);s.put("c",c);s.put("sw",new NetworkProfile(DeviceKind.L2_SWITCH));
        s.link(new NetworkSimulator.Link("a-sw","a",0,"sw",0,CableGrade.CAT5E,1,false));
        s.link(new NetworkSimulator.Link("sw-b","sw",1,"b",0,CableGrade.CAT5E,1,false));
        s.link(new NetworkSimulator.Link("sw-c","sw",2,"c",0,CableGrade.CAT5E,1,false));
        if(!s.startTransfer("a",b.address,1_000_000_000L).success()||!s.startTransfer("a",c.address,1_000_000_000L).success())throw new AssertionError("transfer setup");
        s.advance(21);double fast=s.flows().stream().filter(f->f.destination.equals("c")).findFirst().orElseThrow().remaining;
        if(fast>890_000_000)throw new AssertionError("shared1G must give unused bandwidth to fast flow; remaining="+fast);
        if(!NetworkCli.run(s,"a","transfer 10.50.0.30 100 high").lines().getFirst().contains("開始"))throw new AssertionError("priority CLI required");
        System.out.println("TransferQosContractTest: 2 checks passed");
    }
}
