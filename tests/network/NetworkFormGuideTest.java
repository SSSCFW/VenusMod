import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class NetworkFormGuideTest {
    private static int checks;
    private static void check(boolean ok,String msg){checks++;if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args){
        var page=NetworkGuide.pages().stream().filter(p->p.id().equals("settings_gui")).findFirst().orElseThrow();
        check(page.steps().size()==4,"4 configuration steps");String text=page.steps().toString();
        for(String word:List.of("設定","P0","速度","VLAN","適用","再読込","未保存","CLI","所有権"))check(text.contains(word),"guide missing "+word);
        for(var node:page.nodes())check(Set.of("network_pc","network_l2_switch","network_router").contains(node.item()),"existing textured icons");
        check(NetworkGuide.pages().stream().filter(p->!p.id().equals("syslog")).count()==17&&NetworkGuide.recipes().stream().filter(r->!r.page().id().equals("syslog")).count()==67,"all preexisting steps plus four form steps");
        check(String.join(" ",NetworkHelp.lines()).contains("設定フォーム"),"console explains GUI option");
        System.out.println("NetworkFormGuideTest: "+checks+" checks passed");
    }
}
