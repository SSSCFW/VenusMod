import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** フォームの入力から発行される命令と、古い削除確認を使えないことを検査する。 */
public final class AclDraftTest {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void reject(Runnable call){checks++;try{call.run();throw new AssertionError("invalid input accepted");}catch(IllegalArgumentException expected){}}
    private static AclDraft draft(){
        var d=new AclDraft();d.port(1);d.direction(NetworkPolicy.Direction.OUT);d.sequenceText("10");
        d.permit(false);d.protocol(NetworkPolicy.Protocol.TCP);d.sourceText("192.168.020.042/24");
        d.destinationText("203.000.113.020");d.destinationPortText("80");return d;
    }
    public static void main(String[] args){
        var d=draft();
        check(d.addCommand().equals("acl add 1 out 10 deny tcp 192.168.20.0/24 203.0.113.20/32 80"),"canonical full add command");
        d.requestDeletion();
        check(d.deletionPending()&&d.pendingDescription().contains("P1 out #10 deny tcp")&&d.pendingDescription().contains("dst-port=80"),"confirmation identifies full rule");
        check(d.confirmDeletion().equals("acl delete-match 1 out 10 deny tcp 192.168.20.0/24 203.0.113.20/32 80"),"confirmation uses matching deletion");
        reject(d::confirmDeletion);
        for(var edit:List.<java.util.function.Consumer<AclDraft>>of(
                x->x.port(2),x->x.direction(NetworkPolicy.Direction.IN),x->x.sequenceText("11"),x->x.permit(true),
                x->x.protocol(NetworkPolicy.Protocol.UDP),x->x.sourceText("any"),x->x.destinationText("any"),x->x.destinationPortText("81"),
                AclDraft::dismissDeletion,AclDraft::addCommand)){
            var edited=draft();edited.requestDeletion();edit.accept(edited);reject(edited::confirmDeletion);
        }
        d.requestDeletion();d.dismissDeletion();
        check(d.sourceText().equals("192.168.020.042/24")&&d.destinationPortText().equals("80"),"cancel preserves entered values");
        for(String text:List.of("","0","65536","-1","+1","1.5","１","10\nfault","10\t")){
            d.sequenceText(text);reject(d::addCommand);reject(d::requestDeletion);check(!d.deletionPending(),"invalid sequence never arms");
        }
        d.sequenceText("65535");d.destinationPortText("0");
        check(d.addCommand().contains("65535 deny tcp")&&d.addCommand().endsWith(" 0"),"maximum sequence and port zero valid");
        for(String text:List.of("","65536","-1","+1","1.5","80 fault","80\r")){
            d.destinationPortText(text);reject(d::addCommand);
        }
        d.destinationPortText("65535");check(d.addCommand().endsWith(" 65535"),"maximum service port valid");
        d.protocol(NetworkPolicy.Protocol.ICMP);reject(d::addCommand);
        d.destinationPortText(" any ");d.sourceText(" ANY ");d.destinationText("255.255.255.255/0");
        check(d.addCommand().equals("acl add 1 out 65535 deny icmp 0.0.0.0/0 0.0.0.0/0 any"),"any and /0 canonical");
        for(String text:List.of("","host.local","1.2.3.256","1.2.3.4/33","1.2.3.4/-1","1.2.3.4/+1","1.2.3.4/24/24","any\nacl","any\t","any anything","https://host","１.2.3.4")){
            d.sourceText(text);reject(d::addCommand);reject(d::requestDeletion);
        }
        d.sourceText("203.0.113.1/32");d.destinationText("any");
        for(var protocol:NetworkPolicy.Protocol.values()){d.protocol(protocol);check(d.addCommand().contains(" "+protocol.name().toLowerCase(Locale.ROOT)+" "),"all supported protocols with any port");}
        d.requestDeletion();reject(()->d.sourceText(null));reject(d::confirmDeletion);
        d.sourceText("any");d.requestDeletion();reject(()->d.destinationText("x".repeat(65)));reject(d::confirmDeletion);
        d.requestDeletion();reject(()->d.port(6));reject(d::confirmDeletion);
        reject(()->d.port(-1));reject(()->d.direction(null));reject(()->d.protocol(null));
        for(var kind:DeviceKind.values())check(AclDraft.supported(kind)==kind.routing(),"only routing devices supported");
        check(!AclDraft.supported(null),"unknown device refused");
        System.out.println("AclDraftTest: "+checks+" checks passed");
    }
}
