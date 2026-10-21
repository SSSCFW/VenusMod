import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class StaticRouteDraftTest {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void reject(Runnable call){checks++;try{call.run();throw new AssertionError("accepted invalid input");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        var draft=new StaticRouteDraft();
        check(draft.networkText().isEmpty()&&draft.gatewayText().isEmpty()&&draft.port()==0,"empty initial draft");
        reject(draft::addCommand);reject(draft::requestDeletion);reject(draft::confirmDeletion);
        draft.networkText("192.168.020.042/24");draft.gatewayText("010.000.000.002");draft.port(1);
        check(draft.addCommand().equals("route add 192.168.20.0/24 via 10.0.0.2 port 1"),"normalize CIDR and gateway");
        draft.requestDeletion();check(draft.deletionPending(),"pending only");
        check(draft.pendingDescription().equals("192.168.20.0/24 via 10.0.0.2 P1"),"confirmation identifies canonical route");
        check(draft.confirmDeletion().equals("route delete 192.168.20.0/24 via 10.0.0.2 port 1"),"confirm same tuple");
        check(!draft.deletionPending(),"consume confirmation");reject(draft::confirmDeletion);
        draft.requestDeletion();draft.networkText("192.168.21.0/24");reject(draft::confirmDeletion);
        draft.requestDeletion();draft.gatewayText("10.0.0.3");reject(draft::confirmDeletion);
        draft.requestDeletion();draft.port(3);reject(draft::confirmDeletion);
        draft.requestDeletion();draft.dismissDeletion();reject(draft::confirmDeletion);
        check(draft.networkText().equals("192.168.21.0/24")&&draft.gatewayText().equals("10.0.0.3")&&draft.port()==3,"dismiss preserves drafts");
        for(String cidr:List.of("0.0.0.0/0","203.0.113.77/32"," 192.168.1.44/24 ")){
            draft.networkText(cidr);check(draft.addCommand().startsWith("route add "),"valid prefix "+cidr);
        }
        draft.networkText("255.255.255.255/0");check(draft.addCommand().contains("0.0.0.0/0"),"default route masks host part");
        for(String invalid:List.of("","localhost/24","192.168.1.0","1.2.3.256/24","1.2.3.0/33","1.2.3.0/-1","1.2.3.0/+1","1.2.3.0/1.5","1.2.3.0/24\nfault","1.2.3.0/24 port 2","https://a/24","1.2.3.0/24;fault","1.2.3.0/24/24","１.2.3.0/24","1.2.3.0/24\t")){
            draft.networkText(invalid);reject(draft::addCommand);reject(draft::requestDeletion);check(!draft.deletionPending(),"invalid cannot arm");
        }
        draft.networkText("10.2.0.0/24");
        for(String invalid:List.of("","dns.local","10.0.0.256","10.0.0.1:80","10.0.0.1\nfault","10.0.0.1 port 2","http://10.0.0.1","10.0.0.1\r")){
            draft.gatewayText(invalid);reject(draft::addCommand);reject(draft::requestDeletion);
        }
        draft.gatewayText("10.0.0.2");draft.requestDeletion();
        reject(()->draft.networkText(null));reject(draft::confirmDeletion);
        draft.requestDeletion();reject(()->draft.gatewayText(null));reject(draft::confirmDeletion);
        draft.requestDeletion();reject(()->draft.port(6));reject(draft::confirmDeletion);
        reject(()->draft.port(-1));
        for(var kind:DeviceKind.values())check(StaticRouteDraft.supported(kind)==kind.routing(),"supported device "+kind);
        check(!StaticRouteDraft.supported(null),"null is unsupported");
        System.out.println("StaticRouteDraftTest: "+checks+" checks passed");
    }
}
