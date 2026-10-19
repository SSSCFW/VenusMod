import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** 抽選回数と機器の物理状態を、サーバー/乱数実装に依存せず検査する。 */
public final class PortFaultModelTest {
    private static int checks;
    private static void ok(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
    public static void main(String[] args){
        var p=new NetworkProfile(DeviceKind.ROUTER);p.ports[1].vlan=77;p.ports[1].trunk=true;
        p.ports[1].allowedVlans=VlanAllowance.parse("1,77");p.ports[1].moduleTier=3;p.ports[1].speedLimitBps=100_000_000_000L;
        ok(PortFaults.failPort(p,1),"new port fault");ok(!p.failed&&p.ports[1].failed,"body separate from port");
        ok(!p.accepts(1,CableGrade.CAT5E)&&!p.ports[1].carriesVlan(77),"failed port rejects physical and VLAN traffic");
        ok(!PortFaults.failPort(p,1),"idempotent port failure");ok(PortFaults.hasFault(p),"fault lamp summary");
        ok(PortFaults.status(p).contains("P1"),"localized failure summary");
        p.ports[1].enabled=false;ok(PortFaults.repairPort(p,1),"repair selected port");
        ok(!p.ports[1].failed&&!p.ports[1].enabled,"repair does not enable disabled port");
        ok(p.ports[1].vlan==77&&p.ports[1].allowedVlans.contains(77)&&p.ports[1].moduleTier==3&&p.ports[1].speedLimitBps==100_000_000_000L,"repair keeps configuration and module");
        ok(!PortFaults.repairPort(p,1),"healthy port is not a repair");
        PortFaults.failPort(p,0);PortFaults.failPort(p,2);p.failed=true;
        ok(PortFaults.repairPort(p,0)&&p.failed&&p.ports[2].failed,"single repair leaves body and other faults");
        ok(PortFaults.repairAll(p)&&!PortFaults.hasFault(p),"whole device repair");
        ok(!PortFaults.repairAll(p),"healthy repair no action");
        for(int invalid:new int[]{-1,6,999}){boolean fail=false;try{PortFaults.failPort(p,invalid);}catch(IllegalArgumentException e){fail=true;}ok(fail,"invalid index rejected");}
        p.stability=3;
        var event=PortFaults.sample(p,0.1,1200,1,()->{throw new AssertionError("perfect stabilization draws no randomness");},bound->{throw new AssertionError();});
        ok(event.kind()==PortFaults.Kind.NONE&&!PortFaults.hasFault(p),"perfect protects both kinds");
        p.stability=0;event=PortFaults.sample(p,0,1200,1,()->{throw new AssertionError("disabled failure draws no randomness");},b->0);
        ok(event.kind()==PortFaults.Kind.NONE,"server failure OFF");
        event=PortFaults.sample(p,0.1,1200,1,()->0,b->b-1);
        ok(event.kind()==PortFaults.Kind.PORT&&event.port()==5&&p.ports[5].failed&&!p.failed,"select healthy active port after failed roll");
        PortFaults.repairAll(p);event=PortFaults.sample(p,0.1,1200,0,()->0,b->0);
        ok(event.kind()==PortFaults.Kind.DEVICE&&p.failed,"share zero keeps original whole stop");
        event=PortFaults.sample(p,0.1,1200,1,()->{throw new AssertionError("stopped body not resampled");},b->0);
        ok(event.kind()==PortFaults.Kind.NONE,"already stopped body skipped");
        PortFaults.repairAll(p);for(var port:p.ports)port.enabled=false;
        p.ports[4].enabled=true;event=PortFaults.sample(p,0.1,1200,1,()->0,b->{ok(b==1,"only enabled healthy candidate");return 0;});
        ok(event.port()==4&&p.ports[4].failed,"disabled ports excluded");
        event=PortFaults.sample(p,0.1,1200,1,()->0,b->{throw new AssertionError("no healthy candidates");});
        ok(event.kind()==PortFaults.Kind.DEVICE,"no candidates falls back to body");
        PortFaults.repairAll(p);event=PortFaults.sample(p,0.1,1200,1,()->0.999,b->0);
        ok(event.kind()==PortFaults.Kind.NONE,"no failure when event not selected");
        for(double share:new double[]{Double.NaN,-0.1,1.1}){boolean refused=false;try{PortFaults.sample(p,0.1,1200,share,()->0,b->0);}catch(IllegalArgumentException e){refused=true;}ok(refused,"invalid probability refused");}
        ok(NetworkProfile.failureProbability(0.0005,24000,3)==0,"zero hazard with full stabilization");
        System.out.println("PortFaultModelTest: "+checks+" checks passed");
    }
}
