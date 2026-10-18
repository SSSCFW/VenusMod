import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class MonitorServiceGuideTest {
    public static void main(String[] args){
        var page=MonitorGuide.page();if(page.steps().size()!=7)throw new AssertionError("監視の従来4手順とサービス3手順が必要です");
        String added=page.steps().subList(4,7).toString();int checks=1;
        for(String term:List.of("monitor add site web","monitor add resolver dns","monitor add files transfer","monitor add signal control","8080","80","UDP53","ping","出力","NAT","ICMP","HTTP")){
            if(!added.contains(term))throw new AssertionError("ガイド語句: "+term);checks++;
        }
        if(NetworkGuide.pages().size()!=19||NetworkGuide.recipes().size()!=79)throw new AssertionError("既存ページを維持して3手順追加");checks++;
        if(MonitorCommands.help().stream().noneMatch(x->x.contains("monitor add site web")))throw new AssertionError("CLIヘルプ");checks++;
        System.out.println("MonitorServiceGuideTest: "+checks+" checks passed");
    }
}
