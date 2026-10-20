import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import java.util.function.Function;

public final class GuidePaginationTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        Function<String,List<String>> wrap=s->{var r=new ArrayList<String>();int[] points=s.codePoints().toArray();for(int i=0;i<points.length;i+=12)r.add(new String(points,i,Math.min(12,points.length-i)));return r.isEmpty()?List.of(""):r;};
        for(var recipe:NetworkGuide.recipes()){
            var expected=recipe.page().steps().get(recipe.step()).lines().stream().flatMap(s->wrap.apply(s).stream()).toList();
            var pages=GuidePagination.split(recipe.page().steps().get(recipe.step()).lines(),wrap,8);
            check(pages.stream().flatMap(Collection::stream).toList().equals(expected),"順序・内容を落とさない "+recipe.page().id());
            check(pages.stream().allMatch(p->p.size()<=8&&!p.isEmpty()),"本文8行以内");
        }
        check(GuidePagination.split(List.of("","A😀あ".repeat(20)),wrap,1).stream().allMatch(p->p.size()==1),"空白行とUnicode");
        check(GuidePagination.split(List.of(),wrap,8).equals(List.of(List.of(""))),"空でも1カード");
        try{GuidePagination.split(List.of("x"),wrap,0);throw new AssertionError("zero accepted");}catch(IllegalArgumentException expected){checks++;}
        try{GuidePagination.split(List.of("x"),wrap,8).getFirst().clear();throw new AssertionError("mutable");}catch(UnsupportedOperationException expected){checks++;}
        System.out.println("GuidePaginationTest: "+checks+" checks passed");
    }
}
