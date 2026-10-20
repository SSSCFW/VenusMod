package dev.ssscfw.venusmod.infrastructure.network.jei;

import dev.ssscfw.venusmod.infrastructure.network.NetworkContent;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** JEI登録時だけ折り返す。元のガイド本文は変更せず、全行をカードへ保持する。 */
public final class NetworkJeiCards {
    public static final int TEXT_WIDTH=172, BODY_ROWS=8;
    public record GuideCard(NetworkGuide.Recipe recipe,int part,int parts,List<String> lines){public GuideCard{lines=List.copyOf(lines);}}
    public record SpecCard(NetworkJeiCatalog.Spec spec,int part,int parts,List<String> lines){public SpecCard{lines=List.copyOf(lines);}}
    private NetworkJeiCards(){}
    public static List<String> wrap(String text){
        return Minecraft.getInstance().font.getSplitter().splitLines(text,TEXT_WIDTH,Style.EMPTY).stream().map(t->t.getString()).toList();
    }
    public static List<GuideCard> guides(){
        List<GuideCard> result=new ArrayList<>();
        for(var recipe:NetworkGuide.recipes()){
            var parts=GuidePagination.split(recipe.page().steps().get(recipe.step()).lines(),NetworkJeiCards::wrap,BODY_ROWS);
            for(int i=0;i<parts.size();i++)result.add(new GuideCard(recipe,i,parts.size(),parts.get(i)));
        }
        return List.copyOf(result);
    }
    public static List<SpecCard> specifications(){
        List<SpecCard> result=new ArrayList<>();
        for(var spec:NetworkJeiCatalog.specs()){
            if(items(spec.items()).isEmpty())continue;
            var parts=GuidePagination.split(spec.lines(),NetworkJeiCards::wrap,BODY_ROWS);
            for(int i=0;i<parts.size();i++)result.add(new SpecCard(spec,i,parts.size(),parts.get(i)));
        }
        return List.copyOf(result);
    }
    public static List<ItemStack> items(Collection<String> ids){
        List<ItemStack> result=new ArrayList<>();
        for(String id:ids){
            if(id.equals("combined_network_goggles")&&!ModList.get().isLoaded("create"))continue;
            var holder=NetworkContent.NETWORK_ITEMS.get(id);
            if(holder==null)throw new IllegalArgumentException("未登録のガイドアイコン: "+id);
            result.add(new ItemStack(holder.get()));
        }
        return result;
    }
}
