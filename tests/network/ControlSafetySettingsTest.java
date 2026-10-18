import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import java.io.*;
import java.nio.ByteBuffer;

/** 旧v11は変更前の実クラスで生成。状態・履歴を含めた偽の移行はしない。 */
public final class ControlSafetySettingsTest {
    private static final String V11="AAAACwAObGVnYWN5LWNvbnRyb2wACjEwLjk5LjAuMjAAAAAYAAAAAAEAAAEAAAAAAABkAQAAAAAAAQAAAAAAGAAAAAAAAAAAAQAAAAAAAQAAAAAAGAAAAAAAAAAAAQAAAAAAIgAAAAAAGAAAAAAAAAAAAQAAAAAAAQAAAAAAGAAAAAAAAAAAAQAAAAAAAQAAAAAAGAAAAAAAAAAAAQAAAAAAAQAAAAAAGAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABQAAABQAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAAEABE5PTkUABE5PTkUABE5PTkUABE5PTkUABE5PTkUABE5PTkUAAAAAAQH/////AAAAAQAAABQAAACAAAAAACgAAAGQAAABLAAAAAAAAACAAAAAAAAAAAAAgAAAAAAAAAAAAIAAAAAAAAAAAACAAAAAAAAAAAAAgAAAAAAAAAAAAIAAAAAAAAAAAAAAAAAA/egAAAAAABQAAABQAAAAAAAAAAABAAAACgAAAAAAAAAAAAkwLjAuMC4wLzAAAAICAAAABgAAAAAFAAAAAwAAAAIAAAAAAAAAAA==";
    private static int checks;
    private static void ok(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        var legacy=NetworkProfile.decode(DeviceKind.REDSTONE_TERMINAL,V11);
        ok(legacy.name.equals("legacy-control")&&legacy.failed&&legacy.ports[2].vlan==34,"古い名前/故障/VLANを保持");
        ok(!legacy.controlSafety.config().guarded(),"旧版はholdで既存出力を勝手に変えない");
        var sim=new NetworkSimulator();var device=new NetworkProfile(DeviceKind.REDSTONE_TERMINAL);device.address="10.99.0.20";sim.put("terminal",device);
        var server=new NetworkProfile(DeviceKind.SERVER);server.address="10.99.0.10";sim.put("server",server);
        sim.link(new NetworkSimulator.Link("c","terminal",0,"server",0,CableGrade.CAT5E,3,false));
        ok(sim.startTransfer("server",device.address,1_000_000_000_000L).success(),"進行中の仮想転送");
        var flow=sim.flows().getFirst();var original=sim.nodes().get("terminal");
        for(String good:List.of("control-safety stop 1","control-safety value 7 3600","control-safety hold")){
            var r=NetworkServiceCommands.run(sim,"terminal",good);ok(r.handled()&&r.changed(),good);
            ok(sim.nodes().get("terminal")==original&&sim.flows().getFirst()==flow,"方針変更で転送・機器インスタンスを消さない");
        }
        for(String bad:List.of("control-safety","control-safety stop","control-safety stop 0","control-safety stop 3601","control-safety value 16 5","control-safety value -1 5","control-safety value 5 -1","control-safety hold extra","control-safety value 5 5 extra","control-safety unknown","control-safety stop infinity","control-safety stop 99999999999999999")){
            String before=original.encode();var r=NetworkServiceCommands.run(sim,"terminal",bad);ok(r.handled()&&!r.changed()&&before.equals(original.encode()),"不正/参照操作を一部だけ反映しない: "+bad);
        }
        var denied=NetworkServiceCommands.run(sim,"server","control-safety stop 5");ok(!denied.changed()&&denied.lines().getFirst().contains("端末"),"Serverに安全出力設定を追加しない");
        NetworkServiceCommands.run(sim,"terminal","control-safety value 9 5");
        var saved=NetworkProfile.decode(DeviceKind.REDSTONE_TERMINAL,original.encode());
        ok(saved.controlSafety.config().equals(original.controlSafety.config()),"方針/期限/指定値を保存");
        ok(ByteBuffer.wrap(Base64.getDecoder().decode(original.encode())).getInt()==12,"v12で保存");
        ok(NetworkServiceCommands.run(sim,"terminal","control-safety value 9 5").changed()==false,"同じ設定の再適用は変更にしない");
        saved.controlSafety.set(new ControlSafetySettings.Config(ControlSafetySettings.Mode.STOP,3,0));ok(original.controlSafety.config().value()==9,"設定コピーを共有しない");
        for(String mode:List.of("INVALID","HOLD","STOP","VALUE")){
            byte[] old=Base64.getDecoder().decode(V11);ByteBuffer.wrap(old).putInt(12);
            var bytes=new ByteArrayOutputStream();bytes.write(old);var out=new DataOutputStream(bytes);out.writeUTF(mode);out.writeInt(mode.equals("VALUE")?0:5);out.writeInt(mode.equals("STOP")?0:7);
            boolean rejected=false;try{NetworkProfile.decode(DeviceKind.REDSTONE_TERMINAL,Base64.getEncoder().encodeToString(bytes.toByteArray()));}catch(IllegalArgumentException error){rejected=true;}
            ok(rejected==!mode.equals("STOP"),"追加節の検証 "+mode);
        }
        byte[] bytes=Base64.getDecoder().decode(original.encode());
        for(int length:new int[]{bytes.length-1,bytes.length+1}){boolean rejected=false;try{NetworkProfile.decode(DeviceKind.REDSTONE_TERMINAL,Base64.getEncoder().encodeToString(Arrays.copyOf(bytes,length)));}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"切断/余分なデータ拒否");}
        ok(sim.flows().getFirst()==flow,"診断・拒否・保存で転送は維持");
        System.out.println("ControlSafetySettingsTest: "+checks+" checks passed");
    }
}
