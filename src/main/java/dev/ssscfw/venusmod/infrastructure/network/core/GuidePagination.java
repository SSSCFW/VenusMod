package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;
import java.util.function.Function;

/** 折返しは利用側のフォントへ委譲し、切り捨てず固定行数のカードへ分割する。 */
public final class GuidePagination {
    private GuidePagination() {}
    public static List<List<String>> split(List<String> text,Function<String,List<String>> wrap,int rows) {
        if(rows<1||rows>64)throw new IllegalArgumentException("本文行数は1〜64です");
        Objects.requireNonNull(text);Objects.requireNonNull(wrap);
        List<String> lines=new ArrayList<>();
        for(String line:text){List<String> parts=wrap.apply(Objects.requireNonNull(line));if(parts.isEmpty())lines.add("");else lines.addAll(parts);}
        if(lines.isEmpty())lines.add("");
        List<List<String>> pages=new ArrayList<>();
        for(int from=0;from<lines.size();from+=rows)pages.add(List.copyOf(lines.subList(from,Math.min(from+rows,lines.size()))));
        return List.copyOf(pages);
    }
}
