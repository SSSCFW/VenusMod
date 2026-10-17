import dev.ssscfw.venusmod.infrastructure.network.core.*;

/** 実装前にもコンパイルできる、既存公開API経由のSTP要件。 */
public final class StpContractTest {
    public static void main(String[] args) {
        var sim=new NetworkSimulator();sim.put("s",new NetworkProfile(DeviceKind.L2_SWITCH));
        var setup=NetworkServiceCommands.run(sim,"s","stp mode classic");
        if(!setup.handled()||!setup.changed())throw new AssertionError("BPDU型STPへの切替が未実装: "+setup.lines());
        var state=NetworkServiceCommands.run(sim,"s","show spanning-tree");
        if(!state.handled()||state.changed())throw new AssertionError("状態表示は副作用なし");
        String before=sim.nodes().get("s").encode();
        var invalid=NetworkServiceCommands.run(sim,"s","stp priority 3");
        if(invalid.changed()||!before.equals(sim.nodes().get("s").encode()))throw new AssertionError("不正priorityで設定を壊さない");
        System.out.println("StpContractTest: 3 checks passed");
    }
}
