package dev.ssscfw.venusmod.infrastructure.network.client;

import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** 設定モーダル。値はNetworkFormDraftに保持し、背後のInventory操作へ入力を流さない。 */
public final class NetworkSettingsPanel {
    private final NetworkMenu menu;
    private final NetworkFormDraft draft=new NetworkFormDraft();
    private final List<AbstractWidget> widgets=new ArrayList<>(),basicWidgets=new ArrayList<>(),portWidgets=new ArrayList<>();
    private final List<Button> tabs=new ArrayList<>();
    private Font font;
    private int left,top,scope=-1;
    private long revision=-1;
    private boolean opened,loading;
    private Consumer<AbstractWidget> add,focusCallback;
    private Runnable close;
    private AbstractWidget focused;
    private EditBox name,ip,prefix,gateway,dns,portIp,portPrefix,vlan,allowedVlans;
    private Button enabledButton,modeButton,mediaButton,speedButton,apply,reload;
    private NetworkForm.Values displayed;
    public NetworkSettingsPanel(NetworkMenu menu){this.menu=menu;}

    /** resizeでもdraft/scope/未確認リクエストを保持し、座標とWidgetだけ再作成する。 */
    public void mount(Font font,int left,int top,Consumer<AbstractWidget> add,Consumer<AbstractWidget> focus,Runnable close){
        this.font=font;this.left=left;this.top=top;this.add=add;this.focusCallback=focus;this.close=close;
        loading=true;focused=null;widgets.clear();basicWidgets.clear();portWidgets.clear();tabs.clear();
        tabs.add(button("基本",8,26,56,()->page(-1),"端末のIP・ゲートウェイ・DNSと機器名。L3機器のIPはポート側で設定します。"));
        for(int p=0;p<6;p++){final int n=p;tabs.add(button("P"+p,68+p*41,26,39,()->page(n),"P"+p+" = "+NetworkCli.portName(p)+" (世界方位に固定)"));}
        name=edit(90,54,218,48,"機器名","日本語・空白を含む48文字まで。表示名だけの変更では通信を切断しません。",basicWidgets);
        ip=edit(90,82,166,15,"端末IPv4","例: 192.168.1.10。空欄は未設定です。ルーター/L3SWはポート側で指定します。",basicWidgets);
        prefix=edit(272,82,36,2,"プレフィックス","0〜32。一般的なLANの例は24です。",basicWidgets);
        gateway=edit(90,110,218,15,"ゲートウェイ","別ネットワークへ送る先のルーターIP。空欄はなし。",basicWidgets);
        dns=edit(90,138,218,15,"DNSサーバー","ゲーム内DNS機器のIPv4。空欄は未指定。外部DNSは使用しません。",basicWidgets);
        enabledButton=button("",84,54,73,()->toggle("enabled"),"OFFはポートを管理停止します。ケーブルがあっても通信できません。");portWidgets.add(enabledButton);
        mediaButton=button("",216,54,92,()->toggle("media"),"銅線/光。光への変更には対応モジュールが必要です。対向側も同じ媒体に設定してください。");portWidgets.add(mediaButton);
        modeButton=button("",84,82,104,()->toggle("mode"),"Accessは指定VLANに所属。Trunkはタグ付きVLANを中継します。VLAN欄はAccess VLAN/Native VLANです。");portWidgets.add(modeButton);
        vlan=edit(248,82,60,4,"VLAN","1〜4094。異なるVLAN同士にはL3経路が必要です。",portWidgets);
        speedButton=button("",84,110,140,()->toggle("speed"),"クリックでAuto/固定速度を切り替えます。固定速度は両端が対応する値を指定してください。");portWidgets.add(speedButton);
        portIp=edit(84,138,174,15,"ポートIPv4","ルーター/L3SW用のIP。空欄にするとこのポートの実IPを未設定に戻します。",portWidgets);
        portPrefix=edit(272,138,36,2,"ポートプレフィックス","0〜32。端末IPは基本設定側で変更します。",portWidgets);
        allowedVlans=edit(84,160,224,VlanAllowance.MAX_TEXT,"許可VLAN","Trunkで通すVLAN。all / none / 10,20-30。Native VLANも許可が必要です。Accessでは保存したリストを使いません。",portWidgets);
        apply=button("適用",8,216,94,this::submit,"このページの変更を一括検証・保存します。不正な入力や同時編集の競合では部分反映しません。");
        reload=button("再読込",113,216,94,()->{draft.reload(scope);load();},"このページの未保存入力を破棄し、最後にサーバーから届いた現在値を読み直します。");
        button("戻る",218,216,94,close,"設定は自動保存しません。未保存入力はこの画面を開いている間だけ保持します。");
        loading=false;displayed=null;synchronize();load();visibility();
    }
    private Button button(String label,int x,int y,int width,Runnable action,String help){
        var b=Button.builder(Component.literal(label),v->action.run()).bounds(left+x,top+y,width,18).build();b.setTooltip(Tooltip.create(Component.literal(help)));widgets.add(b);add.accept(b);return b;
    }
    private EditBox edit(int x,int y,int width,int length,String label,String help,List<AbstractWidget> group){
        var e=new EditBox(font,left+x,top+y,width,18,Component.literal(label));e.setMaxLength(length);e.setTooltip(Tooltip.create(Component.literal(help)));
        e.setResponder(v->{if(!loading)save();});widgets.add(e);group.add(e);add.accept(e);return e;
    }
    public void open(int selected){scope=selected;opened=true;synchronize();load();visibility();focus(null);}
    public void visible(boolean value){opened=value;visibility();if(!value)focus(null);}
    private void page(int selected){if(draft.pending())return;save();scope=selected;load();focus(null);}
    public void tick(){draft.tick();synchronize();if(!Objects.equals(displayed,draft.values(scope)))load();visibility();}
    private void synchronize(){
        if(menu.formRevision()==revision||menu.formPages().isEmpty())return;revision=menu.formRevision();
        try{draft.receive(menu.formPages(),menu.formResponseId(),menu.formAccepted(),menu.formMessage());}
        catch(IllegalArgumentException bad){/* 不完全な同期は適用せず、以前の表示・未保存入力を維持する。 */}
    }
    private void load(){
        var v=draft.values(scope);if(v==null){visibility();return;}loading=true;
        if(scope==-1){name.setValue(v.name());ip.setValue(v.address());prefix.setValue(v.prefix());gateway.setValue(v.gateway());dns.setValue(v.dns());}
        else{portIp.setValue(v.address());portPrefix.setValue(v.prefix());vlan.setValue(v.vlan());allowedVlans.setValue(v.allowedVlans());}
        displayed=v;loading=false;visibility();
    }
    private void save(){
        var v=draft.values(scope);if(loading||v==null||draft.pending())return;
        draft.edit(scope==-1?new NetworkForm.Values(scope,name.getValue(),ip.getValue(),prefix.getValue(),gateway.getValue(),dns.getValue(),v.enabled(),v.trunk(),v.optical(),v.vlan(),v.speed(),v.allowedVlans())
            :new NetworkForm.Values(scope,v.name(),portIp.getValue(),portPrefix.getValue(),v.gateway(),v.dns(),v.enabled(),v.trunk(),v.optical(),vlan.getValue(),v.speed(),allowedVlans.getValue()));
        displayed=draft.values(scope);
    }
    private void toggle(String field){
        save();var v=draft.values(scope);var view=draft.view(scope);if(v==null||view==null||draft.pending())return;
        boolean enabled=v.enabled(),trunk=v.trunk(),optical=v.optical();String speed=v.speed();
        switch(field){
            case "enabled"->enabled=!enabled;
            case "mode"->trunk=!trunk;
            case "media"->{if(optical||view.opticalAvailable())optical=!optical;}
            case "speed"->{var rates=NetworkForm.SPEEDS.stream().filter(n->n<=view.maximumBps()).map(Object::toString).toList();int i=rates.indexOf(speed);speed=rates.get((i+1)%rates.size());}
            default->throw new IllegalArgumentException("不明な入力項目");
        }
        draft.edit(new NetworkForm.Values(scope,v.name(),v.address(),v.prefix(),v.gateway(),v.dns(),enabled,trunk,optical,v.vlan(),speed,v.allowedVlans()));displayed=draft.values(scope);visibility();
    }
    private void submit(){
        save();var request=draft.submit(scope);if(request==null)return;
        PacketDistributor.sendToServer(new NetworkPackets.FormEdit(menu.containerId,request.id(),request.expected(),request.edit()));visibility();focus(null);
    }
    private void visibility(){
        if(apply==null)return;boolean ready=draft.view(scope)!=null;boolean writable=opened&&ready&&!draft.pending();
        for(var w:widgets){w.visible=opened;w.active=opened;}
        for(var w:basicWidgets){w.visible=opened&&scope==-1;w.active=writable&&scope==-1;}
        for(var w:portWidgets){w.visible=opened&&scope>=0;w.active=writable&&scope>=0;}
        var view=draft.view(scope);var v=draft.values(scope);
        if(view!=null){boolean routing=view.kind().routing();ip.active=prefix.active=gateway.active=writable&&scope==-1&&!routing;
            portIp.active=portPrefix.active=writable&&scope>=0&&routing;
            if(v!=null&&scope>=0){enabledButton.setMessage(Component.literal(v.enabled()?"ON":"OFF"));modeButton.setMessage(Component.literal(v.trunk()?"Trunk":"Access"));mediaButton.setMessage(Component.literal(v.optical()?"光":"銅線"));
                speedButton.setMessage(Component.literal(v.speed().equals("0")?"Auto":CableGrade.speed(Long.parseLong(v.speed()))));
                mediaButton.active=writable&&(v.optical()||view.opticalAvailable());allowedVlans.active=writable&&v.trunk();}
        }
        for(int i=0;i<tabs.size();i++){tabs.get(i).active=opened&&!draft.pending()&&i!=scope+1;}
        apply.active=writable;reload.active=writable;apply.setMessage(Component.literal(draft.pending()?"適用中…":"適用"));
    }
    public void render(GuiGraphics g,int x,int y,float tick){
        synchronize();if(!Objects.equals(displayed,draft.values(scope)))load();
        g.fill(left,top,left+320,top+240,0xff17212b);g.fill(left+2,top+2,left+318,top+238,0xff3b4650);
        g.drawString(font,"ネットワーク設定"+(scope<0?" / 基本":" / P"+scope+" "+NetworkCli.portName(scope)),left+9,top+9,0xfff0cf78,false);
        var view=draft.view(scope);
        if(scope==-1){label(g,"機器名",10,58);label(g,"IPv4",10,86);label(g,"/",260,86);label(g,"ゲートウェイ",10,114);label(g,"DNS",10,142);
            if(view!=null)g.drawWordWrap(font,Component.literal(view.kind().routing()?"L3機器のIPはポート側で設定。経路設定はCLIを使用します。":"IPv4/GW/DNSは空欄で未設定。変更は「適用」で一括保存します。"),left+10,top+161,298,0xffd5dfdf);
        }else{label(g,"管理状態",10,58);label(g,"媒体",174,58);label(g,"モード",10,86);label(g,"VLAN",212,86);label(g,"速度",10,114);label(g,"IPv4",10,142);label(g,"/",260,142);label(g,"許可VLAN",10,164);
            if(view!=null){g.drawString(font,"最大"+speedLabel(view.maximumBps()),left+232,top+114,0xffcfd9df,false);
                String status=scope<menu.formPorts().size()?menu.formPorts().get(scope):"状態取得中";
                g.enableScissor(left+8,top+180,left+312,top+190);g.drawWordWrap(font,Component.literal(status),left+10,top+180,298,0xffcbd7dd);g.disableScissor();}
        }
        String message=draft.stale(scope)&&draft.dirty(scope)&&!draft.pending()?"同じ項目が更新されています。入力を確認し「再読込」してください。":draft.message();
        g.enableScissor(left+8,top+194,left+312,top+212);g.drawWordWrap(font,Component.literal(message),left+10,top+194,298,0xffe0c789);g.disableScissor();
        for(var widget:widgets)if(widget.visible)widget.render(g,x,y,tick);
    }
    private static String speedLabel(long bps){return bps>=1_000_000_000?bps/1_000_000_000+"G":bps/1_000_000+"M";}
    private void label(GuiGraphics g,String text,int x,int y){g.drawString(font,text,left+x,top+y,0xffe0e5eb,false);}
    private void focus(AbstractWidget w){if(focused!=null)focused.setFocused(false);focused=w;focusCallback.accept(w);if(w!=null)w.setFocused(true);}
    public boolean mouseClicked(double x,double y,int button){for(var w:widgets)if(w.visible&&w.active&&w.mouseClicked(x,y,button)){focus(opened&&w.visible&&w.active?w:null);return true;}focus(null);return true;}
    public boolean mouseReleased(double x,double y,int button){if(focused!=null)focused.mouseReleased(x,y,button);return true;}
    public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(focused!=null)focused.mouseDragged(x,y,button,dx,dy);return true;}
    public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_ESCAPE){close.run();return true;}
        if(key==GLFW.GLFW_KEY_TAB){var active=widgets.stream().filter(w->w.visible&&w.active).toList();if(!active.isEmpty()){int current=active.indexOf(focused);focus(active.get(Math.floorMod(current+((modifiers&GLFW.GLFW_MOD_SHIFT)!=0?-1:1),active.size())));}return true;}
        if((key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER)&&focused instanceof EditBox){submit();return true;}
        if(focused!=null&&focused.active)focused.keyPressed(key,scan,modifiers);return true;
    }
    public boolean charTyped(char c,int modifiers){if(focused!=null&&focused.active)focused.charTyped(c,modifiers);return true;}
}
