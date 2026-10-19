import dev.ssscfw.venusmod.infrastructure.network.core.*;

public final class TrunkGuideTest {
    public static void main(String[] args){
        var page=NetworkGuide.pages().stream().filter(p->p.id().equals("vlan")).findFirst().orElseThrow();
        String text=page.steps().toString();
        for(String term:new String[]{"allowed 10,20-30","all","none","Native","Access","ポートGUI"})if(!text.contains(term))throw new AssertionError("VLANガイドの説明不足: "+term);
        if(page.nodes().size()!=4||page.steps().size()!=3)throw new AssertionError("既存構成図・手順数を維持する");
        if(NetworkHelp.lines().stream().noneMatch(s->s.contains("port 1 allowed")))throw new AssertionError("CLIヘルプにも説明する");
        System.out.println("TrunkGuideTest: 8 checks passed");
    }
}
