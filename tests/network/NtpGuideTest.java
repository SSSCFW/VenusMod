import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
public final class NtpGuideTest {
    public static void main(String[] args){
        int checks=0;var p=NetworkGuide.pages().stream().filter(x->x.id().equals("ntp")).findFirst().orElseThrow(()->new AssertionError("NTP guide missing"));checks++;
        if(p.steps().size()!=4||p.nodes().size()!=3||p.edges().size()!=2)throw new AssertionError("diagram and four steps");checks++;
        for(String key:List.of("ntp server on","ntp target","ntp status","123","Syslog","tick","NAT","補正","未同期")){if(!p.steps().toString().contains(key))throw new AssertionError("Missing guide: "+key);checks++;}
        for(var node:p.nodes()){if(!Set.of("network_pc","network_l2_switch","network_server").contains(node.item()))throw new AssertionError("unknown texture icon");checks++;}
        if(!String.join(" ",NetworkHelp.lines()).contains("ntp help"))throw new AssertionError("help disconnected");checks++;
        if(NetworkGuide.pages().stream().filter(x->!x.id().equals("ntp")).count()!=21||NetworkGuide.recipes().stream().filter(x->!x.page().id().equals("ntp")).count()!=89)throw new AssertionError("preserve all previous 21 topics/89 steps");checks++;
        System.out.println("NtpGuideTest: "+checks+" checks passed");
    }
}
