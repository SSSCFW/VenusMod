import java.util.*;

/** GUI/JEIで共有する接続図の契約。OpenGL描画を確認したという意味ではない。 */
public final class NetworkGuideTest {
    public static void main(String[] args)throws Exception {
        Class<?> guide;
        try {guide=Class.forName("dev.ssscfw.venusmod.infrastructure.network.core.NetworkGuide");}
        catch(ClassNotFoundException e){throw new AssertionError("GUI/JEI共通の接続図ガイドが未実装です");}
        var pages=(List<?>)guide.getMethod("pages").invoke(null);
        if(pages.size()<8)throw new AssertionError("必要なトピックが不足しています");
        Set<String> ids=new HashSet<>();int cases=0;
        for(Object page:pages){
            String id=(String)page.getClass().getMethod("id").invoke(page);if(!ids.add(id))throw new AssertionError("重複ID");
            List<?> nodes=(List<?>)page.getClass().getMethod("nodes").invoke(page);
            List<?> edges=(List<?>)page.getClass().getMethod("edges").invoke(page);
            List<?> steps=(List<?>)page.getClass().getMethod("steps").invoke(page);
            if(nodes.size()<2||nodes.size()>5||steps.isEmpty())throw new AssertionError("接続例が不足: "+id);
            for(Object n:nodes){int x=(int)n.getClass().getMethod("x").invoke(n),y=(int)n.getClass().getMethod("y").invoke(n);
                if(x<0||x+16>176||y<0||y+26>60)throw new AssertionError("接続図が領域外: "+id);cases++;}
            for(Object edge:edges){int a=(int)edge.getClass().getMethod("a").invoke(edge),b=(int)edge.getClass().getMethod("b").invoke(edge);
                if(a<0||a>=nodes.size()||b<0||b>=nodes.size()||a==b)throw new AssertionError("不正な接続: "+id);cases++;}
            for(Object step:steps){List<?> lines=(List<?>)step.getClass().getMethod("lines").invoke(step);if(lines.isEmpty())throw new AssertionError("説明なし");cases++;}
        }
        if(!ids.containsAll(List.of("acl","dhcp_relay","optical","upgrades","basic","ospf","nat")))throw new AssertionError("必須トピックなし");
        System.out.println("NetworkGuideTest: "+pages.size()+" topics / "+cases+" diagram and text checks passed");
    }
}
