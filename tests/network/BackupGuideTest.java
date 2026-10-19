import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class BackupGuideTest {
    public static void main(String[] args){
        var page=NetworkGuide.pages().stream().filter(p->p.id().equals("backup")).findFirst().orElseThrow(()->new AssertionError("バックアップの共通接続ガイドが未実装"));
        int checks=1;if(page.steps().size()!=5||page.nodes().size()!=3||page.edges().size()!=2)throw new AssertionError("接続図と5手順");checks++;
        String text=page.steps().toString();for(String key:List.of("backup save","backup restore","backup confirm","backup server on","backup send","backup fetch","5001","故障","DHCP","30秒","public")){
            if(!text.contains(key))throw new AssertionError("説明なし: "+key);checks++;
        }
        for(var node:page.nodes()){if(!Set.of("network_router","network_l2_switch","network_server").contains(node.item()))throw new AssertionError("未登録アイコン");checks++;}
        if(NetworkGuide.pages().stream().filter(p->!p.id().equals("backup")).count()!=20||NetworkGuide.recipes().stream().filter(r->!r.page().id().equals("backup")).count()!=84)throw new AssertionError("既存20トピック84手順を保持");checks++;
        if(!String.join(" ",NetworkHelp.lines()).contains("backup save"))throw new AssertionError("ヘルプ未接続");checks++;
        System.out.println("BackupGuideTest: "+checks+" checks passed");
    }
}
