import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class PortDegradationModelTest {
    private static int checks;
    private static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void reject(Runnable action,String message){boolean failed=false;try{action.run();}catch(IllegalArgumentException expected){failed=true;}ok(failed,message);}
    public static void main(String[] args){
        var p=new NetworkProfile(DeviceKind.L2_SWITCH);
        ok(PortFaults.degradePort(p,0),"new degradation");
        ok(p.ports[0].degraded&&p.ports[0].operational()&&!p.ports[0].failed&&!p.failed,"degradation does not stop port/body");
        ok(p.ports[0].capacity()==1_000_000_000L,"installed capacity is not rewritten");
        ok(!PortFaults.degradePort(p,0),"same degradation no-op");
        ok(PortFaults.hasFault(p)&&PortFaults.status(p).contains("劣化")&&PortFaults.status(p).contains("P0"),"warning includes location");
        ok(PortFaults.degradedBandwidth(p.ports[0],p.ports[1],1_000_000_000L)==100_000_000L,"one endpoint derates negotiated rate");
        PortFaults.degradePort(p,1);
        ok(PortFaults.degradedBandwidth(p.ports[0],p.ports[1],1_000_000_000L)==100_000_000L,"both endpoints not multiplied twice");
        ok(PortFaults.degradedBandwidth(p.ports[0],p.ports[1],400_000_000_000L)==40_000_000_000L,"400G uses long arithmetic");
        ok(PortFaults.degradedBandwidth(p.ports[0],p.ports[1],0)==0,"down never becomes positive");
        ok(PortFaults.failPort(p,0)&&p.ports[0].failed&&!p.ports[0].degraded,"full fault replaces degradation");
        ok(!PortFaults.degradePort(p,0)&&p.ports[0].failed,"cannot downgrade a full fault without repair");
        p.ports[1].enabled=false;p.ports[1].speedLimitBps=100_000_000L;
        ok(PortFaults.repairPort(p,1)&&!p.ports[1].degraded&&!p.ports[1].enabled,"selective repair leaves admin state");
        ok(p.ports[0].failed&&p.ports[1].speedLimitBps==100_000_000L,"repair preserves other faults and speed");
        ok(!PortFaults.repairPort(p,1),"healthy selective repair consumes no action");
        PortFaults.degradePort(p,2);p.failed=true;
        ok(PortFaults.repairAll(p)&&!PortFaults.hasFault(p),"all repair clears both physical conditions");
        for(int i:new int[]{-1,6,999})reject(()->PortFaults.degradePort(p,i),"invalid port rejected");
        p.stability=3;
        var event=PortFaults.sample(p,0.1,1200,1,1,()->{throw new AssertionError("perfect must not roll");},b->{throw new AssertionError();});
        ok(event.kind()==PortFaults.Kind.NONE&&!PortFaults.hasFault(p),"perfect protects degradation too");
        p.stability=0;
        event=PortFaults.sample(p,0,1200,1,1,()->{throw new AssertionError("OFF must not roll");},b->0);
        ok(event.kind()==PortFaults.Kind.NONE,"server OFF");
        event=PortFaults.sample(p,0.1,24000,1,1,()->0,b->0);
        ok(event.kind()==PortFaults.Kind.DEGRADED&&event.port()==0&&p.ports[0].degraded,"failure outcome selects degradation");
        event=PortFaults.sample(p,0.1,24000,1,1,()->0,b->0);
        ok(event.kind()==PortFaults.Kind.PORT&&p.ports[0].failed&&!p.ports[0].degraded,"repeated physical failure escalates to down");
        PortFaults.repairAll(p);
        event=PortFaults.sample(p,0.1,24000,1,0,()->0,b->0);
        ok(event.kind()==PortFaults.Kind.PORT,"zero degradation share retains old port-down behavior");
        PortFaults.repairAll(p);
        event=PortFaults.sample(p,0.1,24000,0,1,()->0,b->{throw new AssertionError();});
        ok(event.kind()==PortFaults.Kind.DEVICE,"port share zero keeps whole-device failure");
        PortFaults.repairAll(p);
        event=PortFaults.sample(p,0.1,24000,1,1,()->0.9,b->{throw new AssertionError();});
        ok(event.kind()==PortFaults.Kind.NONE,"same overall failure gate");
        for(double share:new double[]{-0.1,1.1,Double.NaN,Double.POSITIVE_INFINITY})reject(()->PortFaults.sample(p,0.1,1200,1,share,()->0,b->0),"invalid degradation share rejected");
        for(var port:p.ports)port.enabled=false;
        event=PortFaults.sample(p,0.1,1200,1,1,()->0,b->{throw new AssertionError();});
        ok(event.kind()==PortFaults.Kind.DEVICE,"no active candidates falls back to body");
        System.out.println("PortDegradationModelTest: "+checks+" checks passed");
    }
}
