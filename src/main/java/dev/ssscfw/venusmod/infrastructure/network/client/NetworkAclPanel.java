package dev.ssscfw.venusmod.infrastructure.network.client;

import dev.ssscfw.venusmod.infrastructure.network.NetworkMenu;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/** ACL専用モーダル。管理認可はサーバーへ委ね、削除確認には規則の全内容を使う。 */
final class NetworkAclPanel {
    private final NetworkMenu menu;
    private final AclDraft draft=new AclDraft();
    private final List<AbstractWidget> widgets=new ArrayList<>();
    private final List<Button> requests=new ArrayList<>();
    private Font font;
    private int left,top,cooldown,scroll;
    private boolean visible,invalid;
    private Button port,direction,permit,protocol,delete,cancel;
    private AbstractWidget focused;
    private Consumer<AbstractWidget> focus;
    private Consumer<String> send;
    private Runnable close;
    private String notice="小さい順序番号から評価。重複番号は上書きしません。",snapshot;
    private List<FormattedCharSequence> result=List.of();

    NetworkAclPanel(NetworkMenu menu){this.menu=menu;}
    void mount(Font font,int left,int top,Consumer<AbstractWidget> add,Consumer<AbstractWidget> focus,
               Consumer<String> send,Runnable close,Runnable guide){
        this.font=font;this.left=left;this.top=top;this.focus=focus;this.send=send;this.close=close;
        dismiss();widgets.clear();requests.clear();focused=null;snapshot=null;
        port=button(add,portLabel(),10,29,88,()->{dismiss();draft.port((draft.port()+1)%6);port.setMessage(Component.literal(portLabel()));refresh();});
        direction=button(add,directionLabel(),104,29,100,()->{dismiss();draft.direction(draft.direction()==NetworkPolicy.Direction.IN?NetworkPolicy.Direction.OUT:NetworkPolicy.Direction.IN);direction.setMessage(Component.literal(directionLabel()));refresh();});
        permit=button(add,permitLabel(),210,29,100,()->{dismiss();draft.permit(!draft.permit());permit.setMessage(Component.literal(permitLabel()));refresh();});
        field(add,"ACL順序番号",10,66,82,draft.sequenceText(),draft::sequenceText);
        protocol=button(add,protocolLabel(),98,66,107,()->{dismiss();var values=NetworkPolicy.Protocol.values();draft.protocol(values[(draft.protocol().ordinal()+1)%values.length]);protocol.setMessage(Component.literal(protocolLabel()));refresh();});
        field(add,"ACL宛先ポート",212,66,98,draft.destinationPortText(),draft::destinationPortText);
        field(add,"ACL送信元",10,103,144,draft.sourceText(),draft::sourceText);
        field(add,"ACL宛先",166,103,144,draft.destinationText(),draft::destinationText);
        requests.add(button(add,"規則を追加",10,128,84,()->act(false)));
        delete=button(add,"個別削除",100,128,104,()->act(true));requests.add(delete);
        cancel=button(add,"確認を中止",210,128,100,()->{dismiss();refresh();});
        requests.add(button(add,"ACL一覧",10,216,94,()->{dismiss();submit("acl");}));
        button(add,"ガイド",110,216,94,guide);button(add,"戻る",210,216,100,close);refresh();
    }
    private String portLabel(){return "P"+draft.port()+" / "+NetworkCli.portName(draft.port());}
    private String directionLabel(){return draft.direction()==NetworkPolicy.Direction.IN?"方向: in (受信)":"方向: out (送信)";}
    private String permitLabel(){return draft.permit()?"動作: 許可":"動作: 拒否";}
    private String protocolLabel(){return "プロトコル: "+draft.protocol();}
    private void field(Consumer<AbstractWidget> add,String label,int x,int y,int width,String value,Consumer<String> change){
        var field=new EditBox(font,left+x,top+y,width,18,Component.literal(label));
        field.setMaxLength(AclDraft.MAX_FIELD_TEXT);field.setValue(value);
        field.setResponder(text->{dismiss();change.accept(text);refresh();});widgets.add(field);add.accept(field);
    }
    private Button button(Consumer<AbstractWidget> add,String label,int x,int y,int width,Runnable action){
        var button=Button.builder(Component.literal(label),b->action.run()).bounds(left+x,top+y,width,18).build();widgets.add(button);add.accept(button);return button;
    }
    private void act(boolean removing){
        if(!visible||cooldown>0||!AclDraft.supported(menu.kind()))return;
        try{
            if(!removing){dismiss();submit(draft.addCommand());}
            else if(draft.deletionPending())submit(draft.confirmDeletion());
            else{draft.requestDeletion();notice="未送信: この内容の規則を削除しますか？";invalid=false;cooldown=10;refresh();}
        }catch(IllegalArgumentException error){dismiss();notice="未送信: "+error.getMessage();invalid=true;refresh();}
    }
    private void submit(String command){
        if(!visible||cooldown>0||!AclDraft.supported(menu.kind()))return;
        send.accept(command);notice="送信: "+command;invalid=false;cooldown=10;scroll=0;refresh();
    }
    private void dismiss(){
        if(draft.deletionPending()){notice="削除確認を取り消しました（未送信）";invalid=false;}draft.dismissDeletion();
    }
    void visible(boolean value){visible=value;if(!value){dismiss();select(null);}refresh();}
    void tick(){if(cooldown>0&&--cooldown==0)refresh();}
    private void refresh(){
        for(var widget:widgets){widget.visible=visible;widget.active=visible;}
        for(var button:requests)button.active=visible&&cooldown==0&&AclDraft.supported(menu.kind());
        if(delete!=null)delete.setMessage(Component.literal(draft.deletionPending()?"削除を確定":"個別削除"));
        if(cancel!=null){cancel.visible=visible&&draft.deletionPending();cancel.active=cancel.visible;}
    }
    private void select(AbstractWidget child){for(var widget:widgets)widget.setFocused(widget==child);focused=child;if(focus!=null)focus.accept(child);}
    boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_ESCAPE){close.run();return true;}
        if(key==GLFW.GLFW_KEY_TAB){
            var choices=widgets.stream().filter(w->w.visible&&w.active).toList();int step=(modifiers&GLFW.GLFW_MOD_SHIFT)==0?1:-1,index=choices.indexOf(focused);
            if(index<0)index=step>0?-1:0;if(!choices.isEmpty())select(choices.get(Math.floorMod(index+step,choices.size())));
        }else if(focused!=null&&focused.active)focused.keyPressed(key,scan,modifiers);return true;
    }
    boolean charTyped(char c,int modifiers){if(focused instanceof EditBox e)e.charTyped(c,modifiers);return true;}
    boolean mouseClicked(double x,double y,int button){
        for(var widget:widgets)if(widget.visible&&widget.active&&widget.isMouseOver(x,y)){select(widget);widget.mouseClicked(x,y,button);return true;}
        select(null);return true;
    }
    boolean mouseDragged(double x,double y,int button,double dx,double dy){if(focused instanceof EditBox e)e.mouseDragged(x,y,button,dx,dy);return true;}
    boolean mouseScrolled(double x,double y,double amount){
        if(!draft.deletionPending()&&x>=left+8&&x<left+312&&y>=top+176&&y<top+212){updateResult();scroll=Math.clamp(scroll-(int)Math.signum(amount)*3,0,Math.max(0,result.size()-4));}return true;
    }
    private void updateResult(){
        String text=menu.snapshot();if(text.equals(snapshot))return;snapshot=text;
        var lines=new ArrayList<FormattedCharSequence>();for(String line:text.split("\n",-1))lines.addAll(font.split(Component.literal(line.isEmpty()?" ":line),288));
        result=List.copyOf(lines);scroll=Math.clamp(scroll,0,Math.max(0,result.size()-4));
    }
    void render(GuiGraphics g,int x,int y,float tick){
        updateResult();g.fill(left,top,left+320,top+252,0xff17212b);g.fill(left+2,top+2,left+318,top+250,0xff3b4650);
        g.drawString(font,"ACL / 通信の許可・拒否",left+10,top+9,0xfff0cf78,false);
        g.drawString(font,"順序 1〜65535",left+10,top+54,0xffd9e0e8,false);
        g.drawString(font,"通信プロトコル",left+98,top+54,0xffd9e0e8,false);
        g.drawString(font,"宛先ポート / any",left+212,top+54,0xffd9e0e8,false);
        g.drawString(font,"送信元 IPv4/CIDR/any",left+10,top+91,0xffd9e0e8,false);
        g.drawString(font,"宛先 IPv4/CIDR/any",left+166,top+91,0xffd9e0e8,false);
        g.drawString(font,font.plainSubstrByWidth(notice,298),left+10,top+151,invalid?0xffffb0a0:0xffc3d1da,false);
        boolean confirming=draft.deletionPending();
        g.drawString(font,confirming?"確認した全内容が現在値と一致する場合だけ削除":"最後のサーバー表示（前回結果を含む・手動更新）",left+10,top+165,0xffc3d1da,false);
        g.fill(left+7,top+176,left+313,top+212,0xff0c151b);g.enableScissor(left+9,top+177,left+309,top+212);
        var lines=confirming?font.split(Component.literal(draft.pendingDescription()),288):result;int start=confirming?0:scroll;
        for(int i=0;i<4&&i+start<lines.size();i++)g.drawString(font,lines.get(i+start),left+11,top+177+9*i,confirming?0xfff0cf78:0xffd9eadc,false);g.disableScissor();
        for(var widget:widgets)if(widget.visible)widget.render(g,x,y,tick);
        g.drawString(font,confirming?"最後の規則を削除すると、この向きは全許可。":"規則のある向き: 未一致は拒否 / 返信も別に評価",left+10,top+238,0xfff0cf78,false);
        if(x>=left+10&&x<left+310&&y>=top+149&&y<top+161)g.renderTooltip(font,Component.literal(notice),x,y);
        if(confirming&&x>=left+9&&x<left+309&&y>=top+177&&y<top+212)g.renderTooltip(font,Component.literal(draft.pendingDescription()),x,y);
    }
}
