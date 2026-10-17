package dev.ssscfw.venusmod.infrastructure.network.client;

import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.core.NetworkCli;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** 安全な仮想コンソール、ポート操作、共通ガイドを一画面にまとめる。 */
public final class NetworkScreen extends AbstractContainerScreen<NetworkMenu> {
    private EditBox command;
    private boolean guide;
    private int scroll,selectedPort;
    private String lastText="";
    private Button portButton;
    public NetworkScreen(NetworkMenu menu, Inventory inventory, Component title) {
        super(menu,inventory,title); imageWidth=320; imageHeight=240;
        inventoryLabelY=148;
    }
    @Override protected void init() {
        super.init();
        button("状態",8,22,36,()->send("show"));
        button("ポート",46,22,44,()->send("ports"));
        button("DHCP",92,22,40,()->send("dhcp"));
        button("MAC",134,22,32,()->send("mac"));
        button("ガイド",168,22,44,()->{guide=!guide;scroll=0;});
        portButton=button("P0:北",214,22,44,()->{selectedPort=(selectedPort+1)%6;portButton.setMessage(Component.literal("P"+selectedPort+":"+new String[]{"北","東","南","西","上","下"}[selectedPort]));});
        button("設定",260,22,52,()->{guide=false;command.setValue("port "+selectedPort+" speed auto");setFocused(command);command.setFocused(true);});
        command=addRenderableWidget(new EditBox(font,leftPos+9,topPos+98,251,18,Component.literal("仮想CLI")));
        command.setMaxLength(512);command.setHint(Component.literal("例: ip 192.168.1.10/24"));
        button("送信",264,97,47,this::submit);
    }
    private Button button(String label,int x,int y,int width,Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(leftPos+x,topPos+y,width,18).build());
    }
    private void submit(){if(command!=null&&!command.getValue().isBlank()){send(command.getValue());command.setValue("");}}
    private void send(String text){guide=false;scroll=0;PacketDistributor.sendToServer(new NetworkPackets.Command(menu.containerId,text));}
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(command!=null&&command.isFocused()) {
            if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){submit();return true;}
            if(key!=GLFW.GLFW_KEY_ESCAPE)return command.keyPressed(key,scan,modifiers)||command.canConsumeInput();
        }
        return super.keyPressed(key,scan,modifiers);
    }
    private List<FormattedCharSequence> textLines() {
        String text=guide?String.join("\n",NetworkCli.guide()):menu.snapshot();
        if(!text.equals(lastText)){scroll=0;lastText=text;}
        List<FormattedCharSequence> lines=new ArrayList<>();
        for(String line:text.split("\n",-1))lines.addAll(font.split(Component.literal(line.isEmpty()?" ":line),286));
        return lines;
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if(x>=leftPos+8&&x<leftPos+312&&y>=topPos+44&&y<topPos+96){scroll=Math.clamp(scroll-(int)Math.signum(vertical)*3,0,Math.max(0,textLines().size()-5));return true;}
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public void render(GuiGraphics g,int x,int y,float tick) {
        renderBackground(g,x,y,tick);super.render(g,x,y,tick);renderTooltip(g,x,y);
    }
    @Override protected void renderBg(GuiGraphics g,float tick,int mouseX,int mouseY) {
        int l=leftPos,t=topPos;
        g.fill(l,t,l+imageWidth,t+imageHeight,0xff17212b);
        g.fill(l+2,t+2,l+imageWidth-2,t+imageHeight-2,0xff3b4650);
        g.fill(l+5,t+5,l+imageWidth-5,t+19,0xff17212b);
        g.fill(l+7,t+43,l+313,t+96,0xff0c151b);
        g.fill(l+7,t+118,l+313,t+148,0xff263440);
        g.fill(l+74,t+155,l+242,t+235,0xff263440);
        for(var slot:menu.slots){g.fill(l+slot.x-1,t+slot.y-1,l+slot.x+17,t+slot.y+17,0xffb9974c);g.fill(l+slot.x,t+slot.y,l+slot.x+16,t+slot.y+16,0xff15202b);}
        List<FormattedCharSequence> lines=textLines();scroll=Math.clamp(scroll,0,Math.max(0,lines.size()-5));
        g.enableScissor(l+9,t+45,l+310,t+94);
        for(int i=0;i<5&&scroll+i<lines.size();i++)g.drawString(font,lines.get(scroll+i),l+11,t+46+i*9,0xffd9eadc,false);
        g.disableScissor();
        if(lines.size()>5){g.fill(l+307,t+47,l+309,t+92,0xff3e5060);int y=t+47+(int)(35.0*scroll/Math.max(1,lines.size()-5));g.fill(l+307,y,l+309,y+10,0xffd2b05c);}
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y) {
        g.drawString(font,title,9,8,0xfff0cf78,false);
        g.drawString(font,"ポート強化 P0〜P5",12,120,0xffe0c789,false);
        g.drawString(font,"本体強化 6枠",146,120,0xffe0c789,false);
        g.drawString(font,"WAN",288,120,0xffe0c789,false);
        g.drawString(font,"スクロールで説明を読む / 外部通信なし",8,149,0xffb9c6ce,false);
    }
}
