import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class NetworkJeiCatalogTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        var specs=NetworkJeiCatalog.specs();
        check(specs.size()==36,"8ケーブル規格・8機器・18部品・2ゴーグル");
        Set<String> ids=new HashSet<>(),items=new HashSet<>();
        Set<String> topics=new HashSet<>();for(var page:NetworkGuide.pages())topics.add(page.id());
        for(var spec:specs){
            check(ids.add(spec.id()),"unique spec "+spec.id());
            check(!spec.lines().isEmpty()&&!spec.title().isBlank(),"説明が空でない");
            check(!spec.topics().isEmpty()&&topics.containsAll(spec.topics()),"既存トピックへの接続");
            for(String item:spec.items())check(items.add(item),"item appears once "+item);
        }
        check(items.size()==164,"登録アイテムをすべて網羅");
        for(var grade:CableGrade.values()){
            var spec=NetworkJeiCatalog.find(grade.id()).orElseThrow();
            check(spec.items().size()==17,"16色と無染色");
            check(spec.lines().stream().anyMatch(s->s.contains(CableGrade.speed(grade.bps()))),"規格速度");
            check(spec.lines().stream().anyMatch(s->s.contains(Integer.toString(grade.maxLength()))),"最大長");
            check(NetworkJeiCatalog.find(grade.id()+"_light_blue").orElseThrow()==spec,"正確な色違い索引");
        }
        check(NetworkJeiCatalog.find("cat5e_cable_blue_unknown").isEmpty(),"未登録suffixを推測しない");
        check(NetworkJeiCatalog.find("minecraft:stone").isEmpty(),"他modへ誤一致しない");
        var optical=NetworkGuide.pages().stream().filter(p->p.id().equals("optical")).findFirst().orElseThrow();
        var basic=NetworkGuide.pages().stream().filter(p->p.id().equals("basic")).findFirst().orElseThrow();
        check(NetworkJeiCatalog.ingredients(optical).contains("om4_fiber_cable_blue"),"色付き光も接続ガイドを検索");
        check(NetworkJeiCatalog.ingredients(basic).contains("cat8_cable_red"),"色付き銅線もLAN手順を検索");
        check(!NetworkJeiCatalog.ingredients(basic).contains("network_wan_5"),"全アイテムを全手順へ登録しない");
        check(NetworkJeiCatalog.find("network_stability_perfect").orElseThrow().lines().stream().anyMatch(s->s.contains("0%")),"完全安定化の説明");
        for(var page:NetworkGuide.pages()){
            var ingredients=NetworkJeiCatalog.ingredients(page);
            check(ingredients.size()==new HashSet<>(ingredients).size(),"検索素材を重複させない");
            for(var node:page.nodes())check(ingredients.contains(node.item()),"図中機器は必ず検索可能");
        }
        try{specs.clear();throw new AssertionError("mutable specs");}catch(UnsupportedOperationException expected){checks++;}
        System.out.println("NetworkJeiCatalogTest: "+checks+" checks passed");
    }
}
