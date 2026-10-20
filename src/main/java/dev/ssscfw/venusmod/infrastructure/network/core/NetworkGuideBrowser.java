package dev.ssscfw.venusmod.infrastructure.network.core;

import java.text.Normalizer;
import java.util.*;

/** 公開ガイドの検索目次。実機器の設定・所有者情報は参照せず、検索条件が変わった時だけ絞り込む。 */
public final class NetworkGuideBrowser {
    public static final int MAX_QUERY=96, ROWS=7;
    public record Match(int page,String id,String title) {}
    private record Entry(Match match,String text,Set<DeviceKind> kinds) {}
    private static final List<Entry> INDEX=index();
    private DeviceKind kind;
    private String query="", normalized="";
    private boolean relatedOnly;
    private List<Match> results=INDEX.stream().map(Entry::match).toList();
    private int selected,firstRow;

    public NetworkGuideBrowser(DeviceKind kind){this.kind=Objects.requireNonNull(kind);}
    public String query(){return query;}
    public boolean relatedOnly(){return relatedOnly;}
    public List<Match> results(){return results;}
    public List<Match> visible(){return results.subList(firstRow,Math.min(results.size(),firstRow+ROWS));}
    public int selected(){return selected;}
    public int firstRow(){return firstRow;}
    public int selectedPage(){return results.isEmpty()?-1:results.get(selected).page();}
    public void kind(DeviceKind value){Objects.requireNonNull(value);if(kind!=value){kind=value;if(relatedOnly)refresh();}}
    public void relatedOnly(boolean value){if(relatedOnly!=value){relatedOnly=value;refresh();}}
    public void query(String value){
        String text=value==null?"":value;
        int end=Math.min(text.length(),MAX_QUERY);
        if(end>0&&end<text.length()&&Character.isHighSurrogate(text.charAt(end-1)))end--;
        query=text.substring(0,end);
        String next=normalize(query);
        if(!next.equals(normalized)){normalized=next;refresh();}
    }
    public void move(int delta){if(!results.isEmpty())choose((int)Math.clamp((long)selected+delta,0,results.size()-1));}
    public void choose(int index){
        if(index<0||index>=results.size())return;
        selected=index;
        if(selected<firstRow)firstRow=selected;
        if(selected>=firstRow+ROWS)firstRow=selected-ROWS+1;
        firstRow=Math.clamp(firstRow,0,Math.max(0,results.size()-ROWS));
    }
    private void refresh(){
        int previous=selectedPage();String[] words=normalized.isEmpty()?new String[0]:normalized.split(" ");
        results=INDEX.stream().filter(e->!relatedOnly||e.kinds.contains(kind))
                .filter(e->Arrays.stream(words).allMatch(e.text::contains)).map(Entry::match).toList();
        selected=0;firstRow=0;
        for(int i=0;i<results.size();i++)if(results.get(i).page()==previous){selected=i;break;}
        if(!results.isEmpty())choose(selected);
    }
    private static String normalize(String text){
        String value=Normalizer.normalize(text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder result=new StringBuilder();boolean space=true;
        for(int cp:value.codePoints().toArray()){
            if(Character.isWhitespace(cp)||Character.isSpaceChar(cp)||Character.isISOControl(cp)){
                if(!space){result.append(' ');space=true;}
            }else{result.appendCodePoint(cp);space=false;}
        }
        return result.toString().strip();
    }
    private static List<Entry> index(){
        List<Entry> entries=new ArrayList<>();var pages=NetworkGuide.pages();
        for(int i=0;i<pages.size();i++){
            var page=pages.get(i);StringBuilder text=new StringBuilder(page.id()).append(' ').append(page.title());
            for(var node:page.nodes())text.append(' ').append(node.item()).append(' ').append(node.label());
            for(var step:page.steps()){text.append(' ').append(step.title());for(String line:step.lines())text.append(' ').append(line);}
            Set<DeviceKind> kinds=EnumSet.noneOf(DeviceKind.class);
            for(var kind:DeviceKind.values())if(NetworkJeiCatalog.find(kind.id()).orElseThrow().topics().contains(page.id())
                    ||page.nodes().stream().anyMatch(n->n.item().equals(kind.id())))kinds.add(kind);
            entries.add(new Entry(new Match(i,page.id(),page.title()),normalize(text.toString()),Set.copyOf(kinds)));
        }
        return List.copyOf(entries);
    }
}
