import dev.ssscfw.venusmod.infrastructure.network.core.*;

/** 製品CLIを通してポート故障を発生させる契約。 */
public final class PortFaultContractTest {
    public static void main(String[] args) {
        var sim=new NetworkSimulator();var p=new NetworkProfile(DeviceKind.PC);
        sim.put("pc",p);
        var reply=NetworkCli.run(sim,"pc","port 0 fault");
        if(!reply.changed())throw new AssertionError("port 0 fault must be supported: "+reply.lines());
        if(!PortFaults.portStatus(sim,"pc",0).contains("故障"))throw new AssertionError("unwired fault must remain visible");
        if(sim.nodes().get("pc").failed)throw new AssertionError("port failure must not stop whole device");
        System.out.println("PortFaultContractTest: 3 checks passed");
    }
}
