package dev.ssscfw.venusmod.infrastructure.network.client;

import dev.ssscfw.venusmod.infrastructure.network.core.NetworkGuideBrowser;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** 内蔵ガイドの目次。検索入力・移動をこのパネル内だけで処理し、背後のInventoryへ渡さない。 */
final class NetworkGuideSearchPanel {
    private final NetworkGuideBrowser browser;
    private final List<AbstractWidget> widgets=new ArrayList<>();
    private final List<Button> rows=new ArrayList<>();
    private Font font;
    private int left,top;
    private boolean visible;
    private EditBox search;
    private Button filter,previous,next;
    private Consumer<AbstractWidget> focus;
    private IntConsumer open;
    private Runnable close;

    NetworkGuideSearchPanel(NetworkGuideBrowser browser){this.browser=browser;}
    void mount(Font font,int left,int top,Consumer<AbstractWidget> add,Consumer<AbstractWidget> focus,IntConsumer open,Runnable close){
        this.font=font;this.left=left;this.top=top;this.focus=focus;this.open=open;this.close=close;
        widgets.clear();rows.clear();
        search=new EditBox(font,left+10,top+30,200,18,Component.literal("ガイド検索"));
        search.setMaxLength(NetworkGuideBrowser.MAX_QUERY);search.setHint(Component.literal("例: VLAN / 故障 / port allowed"));
        search.setValue(browser.query());search.setResponder(value->{browser.query(value);refresh();});widgets.add(search);add.accept(search);
        button(add,"消去",214,30,34,()->{search.setValue("");focusSearch();});
        filter=button(add,"",252,30,58,()->{browser.relatedOnly(!browser.relatedOnly());refresh();});
        filter.setTooltip(Tooltip.create(Component.literal("全項目／この機器に関連する説明。設定値や他プレイヤーの情報は検索しません。")));
        for(int i=0;i<NetworkGuideBrowser.ROWS;i++){
            final int row=i;
            rows.add(button(add,"",10,64+i*20,300,()->{browser.choose(browser.firstRow()+row);openSelected();}));
        }
        previous=button(add,"前へ",10,214,64,()->{browser.move(-NetworkGuideBrowser.ROWS);refresh();});
        button(add,"本文に戻る",80,214,160,close);
        next=button(add,"次へ",246,214,64,()->{browser.move(NetworkGuideBrowser.ROWS);refresh();});
        refresh();
    }
    private Button button(Consumer<AbstractWidget> add,String title,int x,int y,int width,Runnable action){
        var button=Button.builder(Component.literal(title),v->action.run()).bounds(left+x,top+y,width,18).build();widgets.add(button);add.accept(button);return button;
    }
    void visible(boolean value){visible=value;refresh();if(!visible&&search!=null)search.setFocused(false);}
    void focusSearch(){if(visible){focus.accept(search);search.setFocused(true);}}
    private void openSelected(){int page=browser.selectedPage();if(page>=0)open.accept(page);}
    private void refresh(){
        if(search==null)return;
        for(var widget:widgets){widget.visible=visible;widget.active=visible;}
        filter.setMessage(Component.literal(browser.relatedOnly()?"この機器":"全項目"));
        var matches=browser.visible();
        for(int i=0;i<rows.size();i++){
            var row=rows.get(i);row.visible=visible&&i<matches.size();row.active=row.visible;
            if(i<matches.size()){
                var match=matches.get(i);String marker=browser.firstRow()+i==browser.selected()?"> ":"  ";
                row.setMessage(Component.literal(marker+font.plainSubstrByWidth(match.title(),278)));
                row.setTooltip(Tooltip.create(Component.literal(match.title()+" / "+match.id())));
            }
        }
        previous.active=visible&&browser.selected()>0;
        next.active=visible&&browser.selected()<browser.results().size()-1;
    }
    boolean keyPressed(int key,int scan,int modifiers){
        switch(key){
            case GLFW.GLFW_KEY_ESCAPE -> close.run();
            case GLFW.GLFW_KEY_ENTER,GLFW.GLFW_KEY_KP_ENTER -> openSelected();
            case GLFW.GLFW_KEY_DOWN -> {browser.move(1);refresh();}
            case GLFW.GLFW_KEY_UP -> {browser.move(-1);refresh();}
            case GLFW.GLFW_KEY_PAGE_DOWN -> {browser.move(NetworkGuideBrowser.ROWS);refresh();}
            case GLFW.GLFW_KEY_PAGE_UP -> {browser.move(-NetworkGuideBrowser.ROWS);refresh();}
            default -> {if(search.isFocused())search.keyPressed(key,scan,modifiers);}
        }
        return true;
    }
    boolean charTyped(char value,int modifiers){if(search.isFocused())search.charTyped(value,modifiers);return true;}
    boolean mouseClicked(double x,double y,int button){
        for(var widget:widgets)if(widget.visible&&widget.active&&widget.isMouseOver(x,y)){
            focus.accept(widget);search.setFocused(widget==search);widget.mouseClicked(x,y,button);return true;
        }
        search.setFocused(false);focus.accept(null);return true;
    }
    boolean mouseDragged(double x,double y,int button,double dx,double dy){if(search.isFocused())search.mouseDragged(x,y,button,dx,dy);return true;}
    boolean mouseScrolled(double x,double y,double amount){
        if(x>=left+7&&x<left+313&&y>=top+60&&y<top+208){browser.move(-(int)Math.signum(amount)*3);refresh();}return true;
    }
    void render(GuiGraphics graphics,int mouseX,int mouseY,float tick){
        graphics.fill(left,top,left+320,top+252,0xff17212b);graphics.fill(left+2,top+2,left+318,top+250,0xff3b4650);
        graphics.drawString(font,"ネットワークガイドの目次",left+10,top+10,0xfff0cf78,false);
        graphics.drawString(font,"該当 "+browser.results().size()+" 件 / 本文・コマンドも検索",left+10,top+52,0xffd9e0e8,false);
        if(browser.results().isEmpty())graphics.drawWordWrap(font,Component.literal("該当する説明はありません。検索語を消すか「全項目」に切り替えてください。"),left+14,top+90,288,0xffd9eadc);
        for(var widget:widgets)if(widget.visible)widget.render(graphics,mouseX,mouseY,tick);
        graphics.drawString(font,"↑↓で選択 / Enterで開く / Escで本文へ",left+10,top+237,0xffc3d1da,false);
    }
}
