import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
public final class VrrpCodecTest {
 private static int checks; private static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
  // 変更前の実v4コードで生成した固定入力。現在形式の末尾を削って代用しない。
  var old=NetworkProfile.decode(DeviceKind.ROUTER,"AAAABAAN5penVlJSUOapn+WZqAAAAAAAGAAAAAABAQABAAANMTkyLjE2OC4xLjI1NAAAAIwBAAAAAAABAAsxOTIuMTY4LjEuMQAAABgAAAAAAAAAAAEAAAAAAAEACzE5Mi4xNjguMi4xAAAAGAAAAAAAAAAAAQAAAAAAAQAAAAAAGAAAAAAAAAAAAQAAAAAAAQAAAAAAGAAAAAAAAAAAAQAAAAAAAQAAAAAAGAAAAAAAAAAAAQAAAAAAAQAAAAAAGAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABQAAABQAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAAEABE5PTkUABE5PTkUABE5PTkUABE5PTkUABE5PTkUABE5PTkUAAAAA");
  check(old.vip.equals("192.168.1.254")&&old.vrrpPriority==140&&old.failed&&old.name.equals("旧VRRP機器"),"old configuration retained");
  check(old.vrrp.enabled&&old.vrrp.port==-1&&old.vrrp.group==1&&old.vrrp.preempt&&old.vrrp.advertisementTicks==20,"v4 defaults migrate");
  var unusual=NetworkProfile.decode(DeviceKind.ROUTER,"AAAABAAN5penVlJSUOapn+WZqAAAAAAAGAAAAAABAQABAAAJMjI0LjAuMC4xAAAAjAEAAAAAAAEACzE5Mi4xNjguMS4xAAAAGAAAAAAAAAAAAQAAAAAAAQALMTkyLjE2OC4yLjEAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAABAAAAAAABAAAAAAAYAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFAAAAFAAA09GRgAAAAAAA09GRgAAAAAAA09GRgAAAAAAA09GRgAAAAAAA09GRgAAAAAAA09GRgAAAAAAAQAETk9ORQAETk9ORQAETk9ORQAETk9ORQAETk9ORQAETk9ORQAAAAA=");
  check(unusual.vip.equals("224.0.0.1")&&unusual.name.equals("旧VRRP機器")&&unusual.vrrp.resolvePort(unusual)==-1,"legacy invalid VIP preserved but cannot run");
  old.vrrp.port=0;old.vrrp.group=255;old.vrrp.preempt=false;old.vrrp.advertisementTicks=200;old.vrrp.enabled=false;
  String saved=old.encode();var copy=NetworkProfile.decode(DeviceKind.ROUTER,saved);
  check(saved.equals(copy.encode()),"current format round-trip");check(!copy.vrrp.enabled&&!copy.vrrp.preempt&&copy.vrrp.group==255&&copy.vrrp.advertisementTicks==200,"all VRRP settings persist");
  for(int size:List.of(0,4,8,40,Base64.getDecoder().decode(saved).length-1)){boolean rejected=false;try{NetworkProfile.decode(DeviceKind.ROUTER,Base64.getEncoder().encodeToString(Arrays.copyOf(Base64.getDecoder().decode(saved),size)));}catch(IllegalArgumentException e){rejected=true;}check(rejected,"truncated settings rejected");}
  var p=new NetworkProfile(DeviceKind.ROUTER);p.vip="192.168.1.254";p.ports[2].address="192.168.1.2";
  check(p.vrrp.resolvePort(p)==-1,"ambiguous auto port is not silently selected");p.vrrp.port=2;check(p.vrrp.resolvePort(p)==2,"explicit port resolves ambiguity");p.vip="192.168.1.1";check(p.vrrp.resolvePort(p)==-1,"VIP must not be another local physical IP");
  check(VrrpSettings.virtualMac(255).equals("00:00:5e:00:01:ff"),"virtual MAC uses VRID byte");
  System.out.println("VrrpCodecTest: "+checks+" checks passed");
 }
}
