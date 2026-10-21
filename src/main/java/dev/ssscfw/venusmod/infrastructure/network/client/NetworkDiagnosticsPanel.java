package dev.ssscfw.venusmod.infrastructure.network.client;

import dev.ssscfw.venusmod.infrastructure.network.NetworkMenu;
import dev.ssscfw.venusmod.infrastructure.network.core.NetworkDiagnostics;
import dev.ssscfw.venusmod.infrastructure.network.core.TransferPriority;
import dev.ssscfw.venusmod.infrastructure.network.core.TransferControlDraft;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/** 明示的なボタン操作だけを送信する診断フォーム。背後のスロット/CLIとは入力を分離する。 */
final class NetworkDiagnosticsPanel {
    private static final int RESULT_ROWS = 9;
    private final NetworkMenu menu;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final List<Button> requests = new ArrayList<>();
    private final List<AbstractWidget> diagnosticWidgets = new ArrayList<>(), controlWidgets = new ArrayList<>();
    private final TransferControlDraft controls = new TransferControlDraft();
    private boolean managing;
    private Button modeButton, controlPriorityButton, stopButton, dismissButton;
    private Font font;
    private int left, top, scroll, cooldown;
    private boolean visible, invalid;
    private EditBox target, amount;
    private Button priorityButton;
    private AbstractWidget focused;
    private String targetDraft = "", amountDraft = "100";
    private TransferPriority priority = TransferPriority.NORMAL;
    private String notice = "操作を選んで実行。入力だけでは送信しません。";
    private String previousSnapshot;
    private List<FormattedCharSequence> resultLines = List.of();
    private Consumer<AbstractWidget> focus;
    private Consumer<String> send;
    private Runnable close;

    NetworkDiagnosticsPanel(NetworkMenu menu) { this.menu = menu; }

    void mount(Font font, int left, int top, Consumer<AbstractWidget> add,
               Consumer<AbstractWidget> focus, Consumer<String> send, Runnable close, Runnable guide) {
        this.font = font; this.left = left; this.top = top;
        this.focus = focus; this.send = send; this.close = close;
        dismissStop();
        widgets.clear(); requests.clear(); diagnosticWidgets.clear(); controlWidgets.clear();
        focused = null; previousSnapshot = null;
        target = field(add, "診断の宛先", 10, 37, 300, NetworkDiagnostics.MAX_TARGET, targetDraft,
                value -> targetDraft = value);
        target.setHint(Component.literal("例: 192.168.1.20 / base.local"));
        request(add, "Ping", 10, 60, 94, NetworkDiagnostics.Action.PING);
        request(add, "経路確認", 110, 60, 94, NetworkDiagnostics.Action.TRACE);
        request(add, "名前解決", 210, 60, 100, NetworkDiagnostics.Action.DNS);
        amount = field(add, "診断の転送量MB", 64, 83, 85, NetworkDiagnostics.MAX_SIZE_TEXT, amountDraft,
                value -> amountDraft = value);
        amount.setTooltip(Tooltip.create(Component.literal("1〜1,000,000MB。仮想データ量で、実ファイルを作りません。")));
        priorityButton = button(add, "", 155, 83, 74, () -> {
            priority = TransferPriority.values()[(priority.ordinal() + 1) % TransferPriority.values().length];
            priorityButton.setMessage(Component.literal(priority.label()));
        });
        priorityButton.setMessage(Component.literal(priority.label()));
        priorityButton.setTooltip(Tooltip.create(Component.literal("相対優先度 low:normal:high = 1:2:4。物理帯域は増えません。")));
        request(add, "転送開始", 235, 83, 75, NetworkDiagnostics.Action.TRANSFER);
        diagnosticWidgets.addAll(widgets);
        field(add, "操作する転送ID", 10, 37, 300, TransferControlDraft.MAX_ID_TEXT, controls.idText(), value -> {
            dismissStop(); controls.idText(value); refresh();
        }).setHint(Component.literal("一覧のIDを入力（例: #12）"));
        controlPriorityButton = button(add, controls.priority().label(), 10, 60, 74, () -> {
            dismissStop();
            controls.priority(TransferPriority.values()[(controls.priority().ordinal() + 1) % TransferPriority.values().length]);
            controlPriorityButton.setMessage(Component.literal(controls.priority().label())); refresh();
        });
        requests.add(button(add, "優先度変更", 90, 60, 108, () -> manage(false)));
        stopButton = button(add, "転送停止", 204, 60, 106, () -> manage(true));
        requests.add(stopButton);
        dismissButton = button(add, "確認を中止", 224, 83, 86, () -> { dismissStop(); refresh(); });
        controlWidgets.addAll(widgets.subList(diagnosticWidgets.size(), widgets.size()));
        request(add, "転送一覧", 10, 216, 72, NetworkDiagnostics.Action.FLOWS);
        modeButton = button(add, "転送操作", 88, 216, 72, () -> {
            dismissStop(); managing = !managing; select(null); refresh();
        });
        button(add, "ガイド", 166, 216, 68, guide);
        button(add, "戻る", 240, 216, 70, close);
        refresh();
    }

    private EditBox field(Consumer<AbstractWidget> add, String label, int x, int y, int width,
                          int maximum, String value, Consumer<String> responder) {
        var field = new EditBox(font, left + x, top + y, width, 18, Component.literal(label));
        field.setMaxLength(maximum); field.setValue(value); field.setResponder(responder);
        widgets.add(field); add.accept(field); return field;
    }
    private Button button(Consumer<AbstractWidget> add, String label, int x, int y, int width, Runnable action) {
        var button = Button.builder(Component.literal(label), b -> action.run())
                .bounds(left + x, top + y, width, 18).build();
        widgets.add(button); add.accept(button); return button;
    }
    private void request(Consumer<AbstractWidget> add, String label, int x, int y, int width, NetworkDiagnostics.Action action) {
        requests.add(button(add, label, x, y, width, () -> request(action)));
    }
    private void request(NetworkDiagnostics.Action action) {
        if (!visible || cooldown > 0) return;
        try {
            String command = NetworkDiagnostics.command(action, targetDraft, amountDraft, priority);
            dismissStop();
            submit(command);
        } catch (IllegalArgumentException error) {
            // 前回の結果を今回の成功と混同させず、ローカルで未送信だったことを明示する。
            notice = "未送信: " + error.getMessage(); invalid = true;
        }
    }
    private void manage(boolean stop) {
        if (!visible || !managing || cooldown > 0) return;
        try {
            if (!stop) { String command = controls.priorityCommand(); dismissStop(); submit(command); }
            else if (controls.cancellationPending()) submit(controls.confirmCancellation());
            else {
                controls.requestCancellation();
                notice = "未送信: 転送 " + controls.idText() + " を停止しますか？";
                invalid = false; cooldown = 10; refresh();
            }
        } catch (IllegalArgumentException error) {
            dismissStop(); notice = "未送信: " + error.getMessage(); invalid = true; refresh();
        }
    }
    private void submit(String command) {
        send.accept(command); notice = "送信: " + command;
        invalid = false; cooldown = 10; scroll = 0; refresh();
    }
    private void dismissStop() {
        if (controls.cancellationPending()) {
            notice = "停止確認を取り消しました（未送信）"; invalid = false;
        }
        controls.dismissCancellation();
    }
    void visible(boolean value) { visible = value; if (!visible) dismissStop(); refresh(); if (!visible) select(null); }
    void tick() { if (cooldown > 0 && --cooldown == 0) refresh(); }
    private void refresh() {
        for (var widget : widgets) { widget.visible = visible; widget.active = visible; }
        for (var widget : diagnosticWidgets) { widget.visible = visible && !managing; widget.active = widget.visible; }
        for (var widget : controlWidgets) { widget.visible = visible && managing; widget.active = widget.visible; }
        for (var button : requests) button.active = button.visible && cooldown == 0;
        if (modeButton != null) modeButton.setMessage(Component.literal(managing ? "診断へ" : "転送操作"));
        if (stopButton != null) stopButton.setMessage(Component.literal(controls.cancellationPending() ? "停止を確定" : "転送停止"));
        if (dismissButton != null) {
            dismissButton.visible = visible && managing && controls.cancellationPending();
            dismissButton.active = dismissButton.visible;
        }
    }
    private void select(AbstractWidget widget) {
        for (var child : widgets) child.setFocused(child == widget);
        focused = widget; focus.accept(widget);
    }
    boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { close.run(); return true; }
        if (key == GLFW.GLFW_KEY_TAB) {
            var choices = widgets.stream().filter(w -> w.visible && w.active).toList();
            int direction = (modifiers & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : -1;
            int index = choices.indexOf(focused);
            if (index < 0) index = direction > 0 ? -1 : 0;
            if (!choices.isEmpty()) select(choices.get(Math.floorMod(index + direction, choices.size())));
        } else if (focused != null && focused.active) {
            // EditBox上のEnterでは送信せず、ボタンにフォーカスした場合だけ実行する。
            focused.keyPressed(key, scan, modifiers);
        }
        return true;
    }
    boolean charTyped(char value, int modifiers) { if (focused instanceof EditBox field) field.charTyped(value, modifiers); return true; }
    boolean mouseClicked(double x, double y, int button) {
        for (var widget : widgets) if (widget.visible && widget.active && widget.isMouseOver(x, y)) {
            select(widget); widget.mouseClicked(x, y, button); return true;
        }
        select(null); return true;
    }
    boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (focused instanceof EditBox field) field.mouseDragged(x, y, button, dx, dy); return true;
    }
    boolean mouseScrolled(double x, double y, double vertical) {
        if (x >= left + 8 && x < left + 312 && y >= top + 127 && y < top + 212) {
            updateResult(); scroll = Math.clamp(scroll - (int) Math.signum(vertical) * 3, 0, Math.max(0, resultLines.size() - RESULT_ROWS));
        }
        return true;
    }
    private void updateResult() {
        String snapshot = menu.snapshot();
        if (snapshot.equals(previousSnapshot)) return;
        previousSnapshot = snapshot;
        var lines = new ArrayList<FormattedCharSequence>();
        for (String line : snapshot.split("\n", -1)) lines.addAll(font.split(Component.literal(line.isEmpty() ? " " : line), 288));
        resultLines = List.copyOf(lines); scroll = Math.clamp(scroll, 0, Math.max(0, lines.size() - RESULT_ROWS));
    }
    void render(GuiGraphics g, int mouseX, int mouseY, float tick) {
        updateResult();
        g.fill(left, top, left + 320, top + 252, 0xff17212b);
        g.fill(left + 2, top + 2, left + 318, top + 250, 0xff3b4650);
        g.drawString(font, managing ? "転送操作 / 自機が送信元の転送のみ" : "通信診断 / ゲーム内ネットワーク", left + 10, top + 9, 0xfff0cf78, false);
        g.drawString(font, managing ? "転送ID（下の転送一覧で確認）" : "宛先IPv4 / 名前（:ポートは転送のみ）", left + 10, top + 24, 0xffd9e0e8, false);
        if (managing) g.drawString(font, controls.cancellationPending() ? "確定するまで停止しません。" : "一覧は手動更新。完了済みのIDは操作できません。", left + 10, top + 88, 0xffd9e0e8, false);
        else g.drawString(font, "仮想MB", left + 10, top + 88, 0xffd9e0e8, false);
        g.drawString(font, font.plainSubstrByWidth(notice, 298), left + 10, top + 107, invalid ? 0xffffb0a0 : 0xffc3d1da, false);
        g.drawString(font, "最後のサーバー表示（CLIと共通・前回結果を含む）", left + 10, top + 118, 0xffc3d1da, false);
        g.fill(left + 7, top + 128, left + 313, top + 212, 0xff0c151b);
        g.enableScissor(left + 9, top + 130, left + 309, top + 211);
        for (int i = 0; i < RESULT_ROWS && scroll + i < resultLines.size(); i++)
            g.drawString(font, resultLines.get(scroll + i), left + 11, top + 130 + i * 9, 0xffd9eadc, false);
        g.disableScissor();
        if (resultLines.size() > RESULT_ROWS) {
            g.fill(left + 308, top + 130, left + 310, top + 211, 0xff3e5060);
            int y = top + 130 + (int) (71.0 * scroll / (resultLines.size() - RESULT_ROWS));
            g.fill(left + 308, y, left + 310, y + 10, 0xffd2b05c);
        }
        for (var widget : widgets) if (widget.visible) widget.render(g, mouseX, mouseY, tick);
        g.drawString(font, "ホイールで結果 / 一覧は再クリックで更新", left + 10, top + 238, 0xffc3d1da, false);
        if (mouseX >= left + 10 && mouseX < left + 310 && mouseY >= top + 105 && mouseY < top + 117)
            g.renderTooltip(font, Component.literal(notice), mouseX, mouseY);
    }
}
