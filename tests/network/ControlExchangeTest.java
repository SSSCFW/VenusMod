import dev.ssscfw.venusmod.infrastructure.network.core.*;

/** 命令を認可する前にNAT取引を確定しないこと、実制御ポートの判定。 */
public final class ControlExchangeTest {
    private static int checks;
    private static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void config(NetworkSimulator s,String id,String line){var r=NetworkServiceCommands.run(s,id,line);if(!r.changed())throw new AssertionError(line+" "+r.lines());}
    public static void main(String[] args){
        var sim=new NetworkSimulator();var source=new NetworkProfile(DeviceKind.PC);source.address="10.90.0.10";source.gateway="10.90.0.1";
        var router=new NetworkProfile(DeviceKind.ROUTER);router.ports[0].address="10.90.0.1";router.ports[1].address="198.51.100.1";
        var target=new NetworkProfile(DeviceKind.REDSTONE_TERMINAL);target.address="198.51.100.20";
        sim.put("source",source);sim.put("router",router);sim.put("target",target);
        sim.link(new NetworkSimulator.Link("a","source",0,"router",0,CableGrade.CAT5E,2,false));sim.link(new NetworkSimulator.Link("b","router",1,"target",0,CableGrade.CAT5E,2,false));
        config(sim,"router","nat port 0 inside");config(sim,"router","nat port 1 outside");config(sim,"router","nat on");
        var endpoint=new ServiceAddress(target.address,25570);
        var no=sim.controlRequest("source",endpoint,id->"権限なし");ok(!no.success()&&no.message().contains("権限"),"サービス認可の拒否");ok(sim.nat().sessions("router").isEmpty(),"認可拒否でNAT表を作らない");
        var yes=sim.controlRequest("source",endpoint,id->id.equals("target")?"":"別機器");ok(yes.success()&&yes.destination().equals("target"),"本当の到達先IDで認可");
        long deadline=sim.nat().sessions("router").getFirst().expiresAt();sim.advance(5);
        no=sim.controlRequest("source",endpoint,id->"権限を解除");ok(!no.success()&&sim.nat().sessions("router").getFirst().expiresAt()==deadline,"権限失効後にNAT期限を延長しない");
        no=sim.controlRequest("source",new ServiceAddress(target.address,80),id->"");ok(!no.success(),"制御25570以外の宛先ポートを拒否");
        config(sim,"router","acl add 0 out 10 deny tcp any any");
        no=sim.controlRequest("source",endpoint,id->"");ok(!no.success()&&no.message().contains("ACL"),"戻りACLも検査");ok(sim.nat().sessions("router").isEmpty(),"戻り失敗はNAT未確定");
        var bad=new NetworkProfile(DeviceKind.SERVER);bad.address="198.51.100.20";sim.put("target",bad);
        no=sim.controlRequest("source",endpoint,id->"");ok(!no.success()&&no.message().contains("端末"),"受信機器の種類を検査");
        System.out.println("ControlExchangeTest: "+checks+" checks passed");
    }
}
