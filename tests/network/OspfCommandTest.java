import dev.ssscfw.venusmod.infrastructure.network.core.*;
public final class OspfCommandTest {
    public static void main(String[] args){
        var sim=new NetworkSimulator();sim.put("r",new NetworkProfile(DeviceKind.ROUTER));
        var result=NetworkServiceCommands.run(sim,"r","ospf on");
        if(!result.handled()||!result.changed())throw new AssertionError("GUI CLIからOSPFを有効にできない: "+result);
        String saved=sim.nodes().get("r").encode();
        var invalid=NetworkServiceCommands.run(sim,"r","ospf timers 1 1");
        if(invalid.changed()||!saved.equals(sim.nodes().get("r").encode()))throw new AssertionError("不正なタイマーで設定を変更してはならない");
        System.out.println("OspfCommandTest: enabled and rejected invalid timer without data loss");
    }
}
