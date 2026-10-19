import dev.ssscfw.venusmod.infrastructure.network.core.*;

public final class TrunkContractTest {
    public static void main(String[] args) {
        var sim=new NetworkSimulator();var sw=new NetworkProfile(DeviceKind.L2_SWITCH);sim.put("switch",sw);
        var response=NetworkCli.run(sim,"switch","port 0 allowed 10,20-30");
        if(!response.changed())throw new AssertionError("許可VLANをポートに設定できる: "+response.lines());
        System.out.println("TrunkContractTest: 1 check passed");
    }
}
