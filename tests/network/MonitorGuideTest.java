import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class MonitorGuideTest {
    public static void main(String[] args){
        var p=NetworkGuide.pages().stream().filter(x->x.id().equals("monitor")).findFirst().orElseThrow();
        if(p.steps().size()!=4||p.nodes().size()!=3||p.edges().size()!=2)throw new AssertionError("監視接続図と4手順");
        String text=p.toString();for(String word:List.of("monitor add","monitor on","monitor thresholds","monitor status","syslog","PAUSED","UNKNOWN","ACL","16対象"))if(!text.contains(word))throw new AssertionError(word);
        if(NetworkGuide.pages().stream().filter(x->!x.id().equals("monitor")).count()!=18||NetworkGuide.recipes().stream().filter(x->!x.page().id().equals("monitor")).count()!=72)throw new AssertionError("既存18トピック72手順を維持");
        if(NetworkHelp.lines().stream().noneMatch(x->x.contains("monitor status")))throw new AssertionError("共通ヘルプ");
        System.out.println("MonitorGuideTest: 12 checks passed");
    }
}
