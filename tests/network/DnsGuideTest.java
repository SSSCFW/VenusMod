import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
public final class DnsGuideTest {
    private static int checks;
    private static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    public static void main(String[] args){
        var found=NetworkGuide.pages().stream().filter(p->p.id().equals("dns")).findFirst();ok(found.isPresent(),"DNS接続図が未実装");var page=found.orElseThrow();
        ok(page.steps().size()==5,"DNSの5手順が必要");
        String text=page.steps().toString();for(String key:List.of("dns-record set","TTL","nslookup","dns-cache clear","ACL"))ok(text.contains(key),"説明なし: "+key);
        for(var node:page.nodes())ok(Set.of("network_pc","network_l2_switch","network_server").contains(node.item()),"未知の機器アイコン");
        var beforeForm=NetworkGuide.pages().stream().filter(p->!Set.of("settings_gui","syslog","monitor","control-safety","backup").contains(p.id())).toList();
        ok(beforeForm.size()==16&&beforeForm.stream().mapToInt(p->p.steps().size()).sum()==63,"DNSまでの既存16トピック63手順を維持");
        ok(String.join(" ",NetworkHelp.lines()).contains("dns-cache"),"CLIにDNSキャッシュの説明なし");
        System.out.println("DnsGuideTest: "+checks+" DNS guide checks passed");
    }
}
