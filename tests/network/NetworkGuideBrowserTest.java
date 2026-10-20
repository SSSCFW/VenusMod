import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** 公開説明だけの検索・目次状態。ワールドやJEIへ依存しない。 */
public final class NetworkGuideBrowserTest {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static Set<String> ids(NetworkGuideBrowser b){var ids=new HashSet<String>();for(var r:b.results())ids.add(r.id());return ids;}
    public static void main(String[] args){
        var b=new NetworkGuideBrowser(DeviceKind.PC);
        check(b.results().size()==NetworkGuide.pages().size(),"empty query exposes all existing topics");
        check(b.visible().size()==NetworkGuideBrowser.ROWS,"bounded result window");
        b.query("ｖＬＡＮ　ＴＲＵＮＫ");check(ids(b).contains("vlan"),"full width, case and Unicode spaces");
        var results=b.results();b.query("vlan trunk");check(b.results()==results,"equivalent normalization reuses results");
        b.query("port 1 allowed");check(ids(b).contains("vlan"),"body commands searchable");
        b.query("故障");check(ids(b).contains("redundancy"),"Japanese body searchable");
        b.query("this-term-does-not-exist");check(b.results().isEmpty()&&b.visible().isEmpty(),"no result");
        check(b.selectedPage()==-1,"no implicit selection on zero matches");
        b.move(Integer.MAX_VALUE);b.move(Integer.MIN_VALUE);check(b.firstRow()==0,"bounded empty navigation");
        b.query("");b.move(Integer.MAX_VALUE);
        check(b.selected()==b.results().size()-1,"large movement clamps");
        check(b.firstRow()==Math.max(0,b.results().size()-NetworkGuideBrowser.ROWS),"last row visible");
        b.move(Integer.MIN_VALUE);check(b.selected()==0&&b.firstRow()==0,"negative movement clamps");
        b.choose(12);check(b.selected()==12&&b.visible().stream().anyMatch(r->r.page()==b.selectedPage()),"direct selection follows visible window");
        int selectedPage=b.selectedPage();b.relatedOnly(false);check(b.selectedPage()==selectedPage,"no-op filter preserves selection");
        b.choose(-1);b.choose(10000);check(b.selectedPage()==selectedPage,"out of range input ignored");
        for(var kind:DeviceKind.values()){
            b.kind(kind);b.query("");b.relatedOnly(true);check(!b.results().isEmpty(),"every machine has help: "+kind);
            Set<String> expected=new HashSet<>(NetworkJeiCatalog.find(kind.id()).orElseThrow().topics());
            for(var page:NetworkGuide.pages())if(page.nodes().stream().anyMatch(n->n.item().equals(kind.id())))expected.add(page.id());
            check(ids(b).equals(expected),"related filter agrees with JEI catalog and diagrams: "+kind);
            b.relatedOnly(false);check(b.results().size()==NetworkGuide.pages().size(),"all topics recoverable: "+kind);
        }
        b.query("abc".repeat(10000));check(b.query().length()<=NetworkGuideBrowser.MAX_QUERY,"input capped before normalization");
        b.query(" ".repeat(10000));check(b.results().size()==NetworkGuide.pages().size(),"large blank input harmless");
        b.query(null);check(b.query().isEmpty(),"null input clears safely");
        b.query(".*");check(b.results().isEmpty(),"query is literal, not regex");
        b.query("\u0000\n");check(b.results().size()==NetworkGuide.pages().size(),"controls treated as whitespace");
        b.query("BGP");var first=b.results();check(ids(b).contains("bgp"),"protocol search");
        check(first.stream().map(NetworkGuideBrowser.Match::page).toList().equals(first.stream().map(NetworkGuideBrowser.Match::page).sorted().toList()),"stable original guide order");
        boolean immutable=false;try{b.results().clear();}catch(UnsupportedOperationException ok){immutable=true;}check(immutable,"immutable results");
        immutable=false;try{b.visible().clear();}catch(UnsupportedOperationException ok){immutable=true;}check(immutable,"immutable window");
        check(NetworkGuide.recipes().size()==97,"no content removed");
        System.out.println("NetworkGuideBrowserTest: "+checks+" checks passed");
    }
}
