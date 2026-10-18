import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class SyslogGuideTest {
    public static void main(String[] args){
        var page=NetworkGuide.pages().stream().filter(p->p.id().equals("syslog")).findFirst().orElseThrow(()->new AssertionError("Syslog共通ガイド未実装"));
        String text=page.steps().toString();int checks=0;
        for(String term:List.of("syslog server on","syslog target","syslog on","syslog logs","syslog allow","UDP514","NAT","停止","保存","権限")){if(!text.contains(term))throw new AssertionError("説明不足: "+term);checks++;}
        for(var node:page.nodes()){if(!Set.of("network_router","network_l2_switch","network_server").contains(node.item()))throw new AssertionError("既存テクスチャ付きアイコンを使用");checks++;}
        if(page.steps().size()!=5)throw new AssertionError("接続・配送・制限・安全性の5手順");checks++;
        if(NetworkGuide.pages().stream().filter(p->!p.id().equals("monitor")).count()!=18||NetworkGuide.recipes().stream().filter(r->!r.page().id().equals("monitor")).count()!=72)throw new AssertionError("既存17トピック67手順に追加");checks++;
        if(!NetworkHelp.lines().toString().contains("syslog"))throw new AssertionError("CLIヘルプから辿れること");checks++;
        System.out.println("SyslogGuideTest: "+checks+" checks passed");
    }
}
