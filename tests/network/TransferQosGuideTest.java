import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
public final class TransferQosGuideTest {
    public static void main(String[] args){
        int checks=0;var page=NetworkGuide.pages().stream().filter(p->p.id().equals("transfer_qos")).findFirst().orElseThrow(()->new AssertionError("QoS guide missing"));checks++;
        if(page.steps().size()!=4)throw new AssertionError("four steps");checks++;
        for(String text:List.of("transfer","priority","cancel","flows","low","normal","high","1:2:4","保存","DSCP")){if(!page.steps().toString().contains(text))throw new AssertionError("missing instruction "+text);checks++;}
        for(var node:page.nodes()){if(!Set.of("network_pc","network_l2_switch","network_server").contains(node.item()))throw new AssertionError("unregistered icon");checks++;}
        if(!NetworkHelp.lines().toString().contains("transfer help"))throw new AssertionError("missing CLI help");checks++;
        if(NetworkGuide.pages().stream().filter(p->!p.id().equals("transfer_qos")).count()!=22||NetworkGuide.recipes().stream().filter(p->!p.page().id().equals("transfer_qos")).count()!=93)throw new AssertionError("old guide content not retained");checks++;
        System.out.println("TransferQosGuideTest: "+checks+" checks passed");
    }
}
