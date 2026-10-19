import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
public final class PortDegradationGuideTest {
    public static void main(String[] args){
        int checks=0;String text=NetworkGuide.pages().toString();
        for(String word:List.of("port 0 degrade","帯域劣化","10%","両端","Shift","設定復元")){
            if(!text.contains(word))throw new AssertionError("missing degradation guide: "+word);checks++;
        }
        if(!NetworkHelp.lines().toString().contains("port 0 degrade"))throw new AssertionError("missing CLI help");checks++;
        if(NetworkGuide.pages().stream().filter(q->!q.id().equals("transfer_qos")).count()!=22||NetworkGuide.recipes().stream().filter(q->!q.page().id().equals("transfer_qos")).count()!=93)throw new AssertionError("unrelated guide topics preserved");checks++;
        System.out.println("PortDegradationGuideTest: "+checks+" checks passed");
    }
}
