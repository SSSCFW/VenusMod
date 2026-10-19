import dev.ssscfw.venusmod.infrastructure.network.core.*;
public final class PortDegradationContractTest {
    public static void main(String[] args){
        var sim=new NetworkSimulator();sim.put("pc",new NetworkProfile(DeviceKind.PC));
        var result=NetworkCli.run(sim,"pc","port 0 degrade");
        if(!result.changed())throw new AssertionError("port 0 degrade must be accepted: "+result.lines());
        if(!PortFaults.portStatus(sim,"pc",0).contains("劣化"))throw new AssertionError("unwired degradation must be visible");
        System.out.println("PortDegradationContractTest: 2 checks passed");
    }
}
