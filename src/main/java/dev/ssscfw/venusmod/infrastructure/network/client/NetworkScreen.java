package dev.ssscfw.venusmod.infrastructure.network.client;

import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** 仮想コンソールと図付きガイド。ガイド表示中は背後のスロット操作を遮断する。 */
public final class NetworkScreen extends AbstractContainerScreen<NetworkMenu> {
    private EditBox command;
    private boolean guide,configuring,guideIndex;
    private final NetworkGuideBrowser browser=new NetworkGuideBrowser(DeviceKind.PC);
    private final NetworkGuideSearchPanel searchPanel=new NetworkGuideSearchPanel(browser);
    private final NetworkSettingsPanel settings;
    private int scroll,selectedPort,guidePage;
    private String lastText="";
    private Button portButton;
    private final List<AbstractWidget> normalWidgets=new ArrayList<>(),guideWidgets=new ArrayList<>();
    public NetworkScreen(NetworkMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=320;imageHeight=252;inventoryLabelY=148;settings=new NetworkSettingsPanel(menu);}
    @Override protected void init(){
        String commandDraft=command==null?"":command.getValue();
        super.init();normalWidgets.clear();guideWidgets.clear();
        button("状態",8,22,36,()->send("show"));button("ポート",46,22,44,()->openSettings(selectedPort));
        button("DHCP",92,22,40,()->send("dhcp"));button("MAC",134,22,32,()->send("mac"));
        button("ガイド",168,22,44,()->{guidePage=NetworkGuide.initialPage(menu.kind());guide=true;scroll=0;visibility();});
        portButton=button("P"+selectedPort+":"+NetworkCli.portName(selectedPort),214,22,44,()->{selectedPort=(selectedPort+1)%6;portButton.setMessage(Component.literal("P"+selectedPort+":"+NetworkCli.portName(selectedPort)));});
        button("設定",260,22,52,()->openSettings(-1));
        button("監視",224,2,42,()->send("monitor status"));
        button("ログ",270,2,42,()->send(menu.kind()==DeviceKind.SERVER?"syslog logs":"log"));
        command=addRenderableWidget(new EditBox(font,leftPos+9,topPos+98,251,18,Component.literal("仮想CLI")));normalWidgets.add(command);
        command.setMaxLength(512);command.setValue(commandDraft);command.setHint(Component.literal("例: ip 192.168.1.10/24"));button("送信",264,97,47,this::submit);
        guideButton("前",8,216,44,()->page(-1));
        guideButton("目次・検索",56,216,96,this::openIndex);
        guideButton("戻る",156,216,108,()->{guide=false;scroll=0;visibility();});
        guideButton("次",268,216,44,()->page(1));
        searchPanel.mount(font,leftPos,topPos,w->addRenderableWidget(w),w->setFocused(w),this::openGuidePage,()->{guideIndex=false;visibility();});
        settings.mount(font,leftPos,topPos,w->addRenderableWidget(w),w->setFocused(w),()->{configuring=false;visibility();});
        visibility();if(guideIndex)searchPanel.focusSearch();
    }
    private Button button(String label,int x,int y,int width,Runnable action){var b=addRenderableWidget(Button.builder(Component.literal(label),v->action.run()).bounds(leftPos+x,topPos+y,width,18).build());normalWidgets.add(b);return b;}
    private void guideButton(String label,int x,int y,int width,Runnable action){guideWidgets.add(addRenderableWidget(Button.builder(Component.literal(label),v->action.run()).bounds(leftPos+x,topPos+y,width,18).build()));}
    private void visibility(){
        for(var w:normalWidgets){w.visible=!guide&&!configuring;w.active=w.visible;}
        for(var w:guideWidgets){w.visible=guide&&!guideIndex&&!configuring;w.active=w.visible;}
        settings.visible(configuring);searchPanel.visible(guideIndex);setFocused(null);if(command!=null)command.setFocused(false);
    }
    private void openIndex(){browser.kind(menu.kind());guideIndex=true;visibility();searchPanel.focusSearch();}
    private void openGuidePage(int page){guidePage=page;guideIndex=false;scroll=0;lastText="";visibility();}
    private void openSettings(int scope){guide=false;guideIndex=false;configuring=true;visibility();settings.open(scope);}
    @Override protected void containerTick(){super.containerTick();settings.tick();}
    private void page(int direction){guidePage=Math.floorMod(guidePage+direction,NetworkGuide.pages().size());scroll=0;}
    private void submit(){if(command!=null&&!command.getValue().isBlank()){send(command.getValue());command.setValue("");}}
    private void send(String text){scroll=0;PacketDistributor.sendToServer(new NetworkPackets.Command(menu.containerId,text));}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(configuring)return settings.keyPressed(key,scan,modifiers);
        if(guideIndex)return searchPanel.keyPressed(key,scan,modifiers);
        if(guide){if(key==GLFW.GLFW_KEY_ESCAPE){guide=false;scroll=0;visibility();}else if(key==GLFW.GLFW_KEY_RIGHT)page(1);else if(key==GLFW.GLFW_KEY_LEFT)page(-1);else if(key==GLFW.GLFW_KEY_DOWN)scroll++;else if(key==GLFW.GLFW_KEY_UP)scroll=Math.max(0,scroll-1);return true;}
        if(command!=null&&command.isFocused()){if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){submit();return true;}if(key!=GLFW.GLFW_KEY_ESCAPE)return command.keyPressed(key,scan,modifiers)||command.canConsumeInput();}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean charTyped(char c,int modifiers){return configuring?settings.charTyped(c,modifiers):guideIndex?searchPanel.charTyped(c,modifiers):guide||super.charTyped(c,modifiers);}
    @Override public boolean mouseClicked(double x,double y,int button){if(configuring)return settings.mouseClicked(x,y,button);if(guideIndex)return searchPanel.mouseClicked(x,y,button);if(guide){for(var w:guideWidgets)if(w.mouseClicked(x,y,button))return true;return true;}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseReleased(double x,double y,int button){return configuring?settings.mouseReleased(x,y,button):guide||super.mouseReleased(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return configuring?settings.mouseDragged(x,y,button,dx,dy):guideIndex?searchPanel.mouseDragged(x,y,button,dx,dy):guide||super.mouseDragged(x,y,button,dx,dy);}
    private List<FormattedCharSequence> textLines(){
        String text;if(guide){var page=NetworkGuide.pages().get(guidePage);var b=new StringBuilder();for(var step:page.steps()){b.append(step.title()).append('\n');for(var line:step.lines())b.append(line).append('\n');b.append('\n');}text=b.toString();}else text=menu.snapshot();
        if(!text.equals(lastText)){scroll=0;lastText=text;}
        List<FormattedCharSequence> lines=new ArrayList<>();for(String line:text.split("\n",-1))lines.addAll(font.split(Component.literal(line.isEmpty()?" ":line),286));return lines;
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(configuring)return true;
        if(guideIndex)return searchPanel.mouseScrolled(x,y,vertical);
        if(guide||x>=leftPos+8&&x<leftPos+312&&y>=topPos+44&&y<topPos+96){int visible=guide?12:5;scroll=Math.clamp(scroll-(int)Math.signum(vertical)*3,0,Math.max(0,textLines().size()-visible));return true;}
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public void render(GuiGraphics g,int x,int y,float tick){
        renderBackground(g,x,y,tick);
        if(configuring){settings.render(g,x,y,tick);return;}
        if(guideIndex){searchPanel.render(g,x,y,tick);return;}
        if(guide){renderGuide(g);for(var widget:guideWidgets)widget.render(g,x,y,tick);return;}
        super.render(g,x,y,tick);renderTooltip(g,x,y);
    }
    public boolean isJeiShortcutVisible(){return !guide&&!configuring&&net.neoforged.fml.ModList.get().isLoaded("jei");}
    private void panel(GuiGraphics g){int l=leftPos,t=topPos;g.fill(l,t,l+imageWidth,t+imageHeight,0xff17212b);g.fill(l+2,t+2,l+imageWidth-2,t+imageHeight-2,0xff3b4650);}
    private void renderGuide(GuiGraphics g){
        panel(g);int l=leftPos,t=topPos;var page=NetworkGuide.pages().get(guidePage);
        g.drawString(font,font.plainSubstrByWidth(page.title(),252),l+9,t+8,0xfff0cf78,false);
        g.drawString(font,(guidePage+1)+" / "+NetworkGuide.pages().size(),l+274,t+8,0xffd9e0e8,false);
        NetworkGuideRenderer.diagram(g,l+72,t+26,page,true);
        g.drawCenteredString(font,"接続の概念図 / ポートは配線した面を選択",l+160,t+89,0xffc3d1da);
        drawText(g,101,209,12);
    }
    private void drawText(GuiGraphics g,int y1,int y2,int visible){
        int l=leftPos,t=topPos;g.fill(l+7,t+y1-2,l+313,t+y2+2,0xff0c151b);
        var lines=textLines();scroll=Math.clamp(scroll,0,Math.max(0,lines.size()-visible));g.enableScissor(l+9,t+y1,l+310,t+y2);
        for(int i=0;i<visible&&scroll+i<lines.size();i++)g.drawString(font,lines.get(scroll+i),l+11,t+y1+i*9,0xffd9eadc,false);g.disableScissor();
        if(lines.size()>visible){g.fill(l+307,t+y1,l+309,t+y2,0xff3e5060);int y=t+y1+(int)((y2-y1-10.0)*scroll/Math.max(1,lines.size()-visible));g.fill(l+307,y,l+309,y+10,0xffd2b05c);}
    }
    @Override protected void renderBg(GuiGraphics g,float tick,int mouseX,int mouseY){
        panel(g);int l=leftPos,t=topPos;g.fill(l+5,t+5,l+imageWidth-5,t+19,0xff17212b);g.fill(l+7,t+118,l+313,t+148,0xff263440);g.fill(l+74,t+155,l+242,t+235,0xff263440);
        for(var slot:menu.slots){g.fill(l+slot.x-1,t+slot.y-1,l+slot.x+17,t+slot.y+17,0xffb9974c);g.fill(l+slot.x,t+slot.y,l+slot.x+16,t+slot.y+16,0xff15202b);}drawText(g,46,94,5);
        if(isJeiShortcutVisible()){g.fill(l+270,t+237,l+312,t+249,0xff17212b);g.drawString(font,"JEI",l+282,t+239,0xfff0cf78,false);}
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){g.drawString(font,title,9,8,0xfff0cf78,false);g.drawString(font,"ポート強化 P0〜P5",12,120,0xffe0c789,false);g.drawString(font,"本体強化 6枠",146,120,0xffe0c789,false);g.drawString(font,"WAN",288,120,0xffe0c789,false);g.drawString(font,"スクロールで説明を読む / 外部通信なし",8,149,0xffb9c6ce,false);}
}
