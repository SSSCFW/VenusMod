import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
public final class PortFaultGuideTest {
    public static void main(String[] args){
        int checks=0;var text=NetworkGuide.pages().toString();
        for(String key:List.of("port 0 fault","ポート故障","Shift","全ポート","設定復元")){
            if(!text.contains(key))throw new AssertionError("missing shared guide: "+key);checks++;
        }
        if(!String.join(" ",NetworkHelp.lines()).contains("port 0 fault"))throw new AssertionError("CLI help missing");checks++;
        if(NetworkGuide.pages().stream().filter(q->!q.id().equals("transfer_qos")).count()!=22||NetworkGuide.recipes().stream().filter(q->!q.page().id().equals("transfer_qos")).count()!=93)throw new AssertionError("existing guide pages/steps retained");checks++;
        System.out.println("PortFaultGuideTest: "+checks+" checks passed");
    }
}
