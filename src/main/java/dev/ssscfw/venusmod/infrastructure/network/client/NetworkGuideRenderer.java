package dev.ssscfw.venusmod.infrastructure.network.client;

import dev.ssscfw.venusmod.infrastructure.network.NetworkContent;
import dev.ssscfw.venusmod.infrastructure.network.core.NetworkGuide;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** 静的な概念図。GUIとJEIで同じ図を描き、既存のテクスチャ付きアイテムを使う。 */
public final class NetworkGuideRenderer {
    private NetworkGuideRenderer(){}
    public static ItemStack icon(NetworkGuide.Node node){var item=NetworkContent.NETWORK_ITEMS.get(node.item());return item==null?ItemStack.EMPTY:new ItemStack(item.get());}
    public static void diagram(GuiGraphics g,int x,int y,NetworkGuide.Page page,boolean items){
        g.fill(x,y,x+176,y+60,0xff15222e);
        for(var edge:page.edges()){
            var a=page.nodes().get(edge.a());var b=page.nodes().get(edge.b());
            int ax=x+a.x()+8,ay=y+a.y()+8,bx=x+b.x()+8,by=y+b.y()+8;
            int color=edge.standby()?0xffb48644:0xff72c6bb;
            line(g,ax,ay,bx,ay,color,edge.standby());line(g,bx,ay,bx,by,color,edge.standby());
        }
        var font=Minecraft.getInstance().font;
        for(var node:page.nodes()){
            g.fill(x+node.x()-1,y+node.y()-1,x+node.x()+17,y+node.y()+17,0xffa78d51);
            g.fill(x+node.x(),y+node.y(),x+node.x()+16,y+node.y()+16,0xff263541);
            if(items)g.renderItem(icon(node),x+node.x(),y+node.y());
            g.drawCenteredString(font,node.label(),x+node.x()+8,y+node.y()+18,0xffe0e8ed);
        }
    }
    private static void line(GuiGraphics g,int ax,int ay,int bx,int by,int color,boolean dashed){
        if(!dashed){g.fill(Math.min(ax,bx),Math.min(ay,by),Math.max(ax,bx)+2,Math.max(ay,by)+2,color);return;}
        int length=Math.max(Math.abs(bx-ax),Math.abs(by-ay));
        for(int i=0;i<=length;i+=7){int x=ax+Integer.signum(bx-ax)*i,y=ay+Integer.signum(by-ay)*i;int count=Math.min(3,length-i)+1;g.fill(Math.min(x,x+Integer.signum(bx-ax)*(count-1)),Math.min(y,y+Integer.signum(by-ay)*(count-1)),Math.max(x,x+Integer.signum(bx-ax)*(count-1))+2,Math.max(y,y+Integer.signum(by-ay)*(count-1))+2,color);}
    }
}
