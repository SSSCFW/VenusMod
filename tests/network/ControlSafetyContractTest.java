import dev.ssscfw.venusmod.infrastructure.network.core.*;

/** 既存公開CLIから、安全出力設定が実際に追加されたことを確認する。 */
public final class ControlSafetyContractTest {
    public static void main(String[] args) {
        var s=new NetworkSimulator();s.put("terminal",new NetworkProfile(DeviceKind.REDSTONE_TERMINAL));
        var r=NetworkServiceCommands.run(s,"terminal","control-safety stop 5");
        if(!r.handled()||!r.changed())throw new AssertionError("control-safety stop 5 が未対応: "+r.lines());
        System.out.println("ControlSafetyContractTest: passed");
    }
}
