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

/** 静的経路の設定専用モーダル。入力はローカルで保持し、明示した要求だけを送信する。 */
final class NetworkRoutePanel {
    private final NetworkMenu menu;
    private final StaticRouteDraft draft = new StaticRouteDraft();
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final List<Button> requests = new ArrayList<>();
    private Font font;
    private int left, top, cooldown, scroll;
    private boolean visible, invalid;
    private Button port, delete, cancel;
    private AbstractWidget focused;
    private Consumer<AbstractWidget> focus;
    private Consumer<String> send;
    private Runnable close;
    private String notice = "保存しても疎通成功とは限りません。戻りの経路も必要です。", snapshot;
    private List<FormattedCharSequence> result = List.of();

    NetworkRoutePanel(NetworkMenu menu) { this.menu = menu; }
    void mount(Font font, int left, int top, Consumer<AbstractWidget> add, Consumer<AbstractWidget> focus,
               Consumer<String> send, Runnable close, Runnable guide) {
        this.font = font; this.left = left; this.top = top; this.focus = focus; this.send = send; this.close = close;
        dismiss(); widgets.clear(); requests.clear(); focused = null; snapshot = null;
        field(add, "静的経路の宛先CIDR", 10, 37, 190, draft.networkText(), value -> { dismiss(); draft.networkText(value); refresh(); });
        port = button(add, portLabel(), 206, 37, 104, () -> { dismiss(); draft.port((draft.port()+1)%6); port.setMessage(Component.literal(portLabel())); refresh(); });
        field(add, "静的経路の次ホップ", 10, 74, 300, draft.gatewayText(), value -> { dismiss(); draft.gatewayText(value); refresh(); });
        requests.add(button(add, "経路を追加", 10, 99, 84, () -> act(false)));
        delete = button(add, "個別削除", 100, 99, 104, () -> act(true)); requests.add(delete);
        cancel = button(add, "確認を中止", 210, 99, 100, () -> { dismiss(); refresh(); });
        requests.add(button(add, "静的経路一覧", 10, 216, 94, () -> { dismiss(); submit("routes"); }));
        button(add, "ガイド", 110, 216, 94, guide);
        button(add, "戻る", 210, 216, 100, close);
        refresh();
    }
    private String portLabel() { return "P"+draft.port()+" / "+NetworkCli.portName(draft.port()); }
    private void field(Consumer<AbstractWidget> add, String label, int x, int y, int width, String value, Consumer<String> change) {
        var field = new EditBox(font,left+x,top+y,width,18,Component.literal(label));
        field.setMaxLength(StaticRouteDraft.MAX_FIELD_TEXT); field.setValue(value); field.setResponder(change);
        widgets.add(field); add.accept(field);
    }
    private Button button(Consumer<AbstractWidget> add, String label, int x, int y, int width, Runnable action) {
        var button = Button.builder(Component.literal(label), b -> action.run()).bounds(left+x,top+y,width,18).build();
        widgets.add(button); add.accept(button); return button;
    }
    private void act(boolean removing) {
        if (!visible || cooldown>0 || !StaticRouteDraft.supported(menu.kind())) return;
        try {
            if (!removing) { dismiss(); submit(draft.addCommand()); }
            else if (draft.deletionPending()) submit(draft.confirmDeletion());
            else { draft.requestDeletion(); notice="未送信: 下の経路を削除しますか？"; invalid=false; cooldown=10; refresh(); }
        } catch (IllegalArgumentException error) { dismiss(); notice="未送信: "+error.getMessage(); invalid=true; refresh(); }
    }
    private void submit(String command) {
        if (!visible || cooldown>0 || !StaticRouteDraft.supported(menu.kind())) return;
        send.accept(command); notice="送信: "+command; invalid=false; cooldown=10; scroll=0; refresh();
    }
    private void dismiss() {
        if (draft.deletionPending()) { notice="削除確認を取り消しました（未送信）"; invalid=false; }
        draft.dismissDeletion();
    }
    void visible(boolean value) { visible=value; if (!value) { dismiss(); select(null); } refresh(); }
    void tick() { if (cooldown>0 && --cooldown==0) refresh(); }
    private void refresh() {
        for (var widget:widgets) { widget.visible=visible; widget.active=visible; }
        for (var button:requests) button.active=visible && cooldown==0 && StaticRouteDraft.supported(menu.kind());
        if (delete!=null) delete.setMessage(Component.literal(draft.deletionPending()?"削除を確定":"個別削除"));
        if (cancel!=null) { cancel.visible=visible && draft.deletionPending(); cancel.active=cancel.visible; }
    }
    private void select(AbstractWidget child) {
        for (var widget:widgets) widget.setFocused(widget==child);
        focused=child; if (focus!=null) focus.accept(child);
    }
    boolean keyPressed(int key,int scan,int modifiers) {
        if (key==GLFW.GLFW_KEY_ESCAPE) { close.run(); return true; }
        if (key==GLFW.GLFW_KEY_TAB) {
            var choices=widgets.stream().filter(w->w.visible && w.active).toList();
            int step=(modifiers & GLFW.GLFW_MOD_SHIFT)==0?1:-1, index=choices.indexOf(focused);
            if (index<0) index=step>0?-1:0;
            if (!choices.isEmpty()) select(choices.get(Math.floorMod(index+step,choices.size())));
        } else if (focused!=null && focused.active) focused.keyPressed(key,scan,modifiers);
        return true;
    }
    boolean charTyped(char c,int modifiers) { if (focused instanceof EditBox e) e.charTyped(c,modifiers); return true; }
    boolean mouseClicked(double x,double y,int button) {
        for (var widget:widgets) if (widget.visible && widget.active && widget.isMouseOver(x,y)) {
            select(widget); widget.mouseClicked(x,y,button); return true;
        }
        select(null); return true;
    }
    boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        if (focused instanceof EditBox e) e.mouseDragged(x,y,button,dx,dy); return true;
    }
    boolean mouseScrolled(double x,double y,double amount) {
        if (x>=left+8 && x<left+312 && y>=top+158 && y<top+212) {
            updateResult(); scroll=Math.clamp(scroll-(int)Math.signum(amount)*3,0,Math.max(0,result.size()-5));
        }
        return true;
    }
    private void updateResult() {
        String text=menu.snapshot(); if (text.equals(snapshot)) return; snapshot=text;
        var lines=new ArrayList<FormattedCharSequence>();
        for (String line:text.split("\n",-1)) lines.addAll(font.split(Component.literal(line.isEmpty()?" ":line),288));
        result=List.copyOf(lines); scroll=Math.clamp(scroll,0,Math.max(0,result.size()-5));
    }
    void render(GuiGraphics g,int x,int y,float tick) {
        updateResult(); g.fill(left,top,left+320,top+252,0xff17212b); g.fill(left+2,top+2,left+318,top+250,0xff3b4650);
        g.drawString(font,"静的経路 / ルーター・L3スイッチ",left+10,top+9,0xfff0cf78,false);
        g.drawString(font,"宛先ネットワーク / 長さ",left+10,top+25,0xffd9e0e8,false);
        g.drawString(font,"出力ポート",left+206,top+25,0xffd9e0e8,false);
        g.drawString(font,"次ホップIPv4（例: 192.168.2.2）",left+10,top+62,0xffd9e0e8,false);
        g.drawString(font,font.plainSubstrByWidth(notice,298),left+10,top+122,invalid?0xffffb0a0:0xffc3d1da,false);
        String detail=draft.deletionPending()?draft.pendingDescription():"/0は既定経路、/32は単一ホスト。ホスト部は正規化。";
        g.drawString(font,font.plainSubstrByWidth(detail,298),left+10,top+134,0xfff0cf78,false);
        g.drawString(font,"最後のサーバー表示（CLIと共通・前回結果を含む）",left+10,top+146,0xffc3d1da,false);
        g.fill(left+7,top+158,left+313,top+212,0xff0c151b);g.enableScissor(left+9,top+160,left+309,top+211);
        for(int i=0;i<5 && i+scroll<result.size();i++)g.drawString(font,result.get(i+scroll),left+11,top+160+9*i,0xffd9eadc,false);
        g.disableScissor();
        for(var widget:widgets)if(widget.visible)widget.render(g,x,y,tick);
        g.drawString(font,"一覧は手動更新 / 全消去はしません / 外部通信なし",left+10,top+238,0xffc3d1da,false);
        if(x>=left+10&&x<left+310&&y>=top+120&&y<top+144)
            g.renderTooltip(font,Component.literal(y<top+132?notice:detail),x,y);
    }
}
