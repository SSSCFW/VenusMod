package dev.ssscfw.venusmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ssscfw.venusmod.treasury.KingsTreasuryMenu;
import dev.ssscfw.venusmod.treasury.SummonPattern;
import dev.ssscfw.venusmod.treasury.VolleyPriority;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** 緑=全射出禁止、赤=幻想のみ禁止。左パネルで優先度・召喚配置・収束率を設定する。 */
public final class KingsTreasuryScreen extends AbstractContainerScreen<KingsTreasuryMenu> {
    private static final int SIDEBAR_WIDTH = 132;
    private static final int SIDEBAR_GAP = 8;
    private static final int SIDEBAR_HEIGHT = 236;
    private Button previousButton;
    private Button nextButton;
    private Button autoCollectButton;
    private final Button[] priorityButtons = new Button[VolleyPriority.values().length];
    private final Button[] patternButtons = new Button[SummonPattern.values().length];
    private ConvergenceSlider convergenceSlider;

    public KingsTreasuryScreen(KingsTreasuryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = KingsTreasuryMenu.IMAGE_HEIGHT;
        inventoryLabelY = 143;
    }

    @Override
    protected void init() {
        super.init();
        leftPos = (width - imageWidth - SIDEBAR_WIDTH - SIDEBAR_GAP) / 2 + SIDEBAR_WIDTH + SIDEBAR_GAP;
        previousButton = addRenderableWidget(Button.builder(Component.literal("<"),
                        b -> pressMenuButton(KingsTreasuryMenu.BUTTON_PREVIOUS))
                .bounds(leftPos + 8, topPos + 17, 18, 12).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"),
                        b -> pressMenuButton(KingsTreasuryMenu.BUTTON_NEXT))
                .bounds(leftPos + 150, topPos + 17, 18, 12).build());
        autoCollectButton = addRenderableWidget(Button.builder(Component.empty(),
                        b -> pressMenuButton(KingsTreasuryMenu.BUTTON_AUTO_COLLECT))
                .bounds(leftPos + 8, topPos + 140, 82, 12)
                .tooltip(Tooltip.create(Component.literal("自動回収のON/OFFを切り替え"))).build());

        int side = sidebarLeft();
        for (VolleyPriority priority : VolleyPriority.values()) {
            String hint = switch (priority) {
                case RANDOM -> "保護設定に従い、在庫本数に比例してランダム選択";
                case DURABILITY_LOW -> "残り耐久が少ない刀から選択";
                case DURABILITY_HIGH -> "残り耐久が多い刀から選択";
                case RANK_LOW -> "消滅型の通常刀→通常刀→印→妖刀。同ランクは低攻撃力から";
                case RANK_HIGH -> "妖刀→印→通常刀→消滅型の通常刀。同ランクは高攻撃力から";
                case BROKEN_ONLY -> "折れた刀だけを選択。強制的に壊れた幻想となり、着弾で消滅";
            };
            priorityButtons[priority.ordinal()] = addRenderableWidget(Button.builder(
                            Component.literal(priority.label()),
                            b -> pressMenuButton(KingsTreasuryMenu.BUTTON_PRIORITY_START + priority.ordinal()))
                    .bounds(side + 5, topPos + 29 + priority.ordinal() * 15, SIDEBAR_WIDTH - 10, 14)
                    .tooltip(Tooltip.create(Component.literal(hint))).build());
        }

        for (SummonPattern pattern : SummonPattern.values()) {
            String hint = switch (pattern) {
                case DEFAULT -> "従来どおり、世界の上下方向を基準に半円を展開";
                case VIEW_PARALLEL -> "視線に合わせて半円面も傾ける。下を向くと門が水平に開く";
                case VIEW_RING -> "視線に直交する面で自分を中心に円状配置。ブロックと重なる位置は空ける";
            };
            patternButtons[pattern.ordinal()] = addRenderableWidget(Button.builder(
                            Component.literal(pattern.label()),
                            b -> pressMenuButton(KingsTreasuryMenu.BUTTON_PATTERN_START + pattern.ordinal()))
                    .bounds(side + 5, topPos + 137 + pattern.ordinal() * 18, SIDEBAR_WIDTH - 10, 16)
                    .tooltip(Tooltip.create(Component.literal(hint))).build());
        }

        convergenceSlider = addRenderableWidget(new ConvergenceSlider(
                side + 5, topPos + 207, SIDEBAR_WIDTH - 10, 16, menu.getConvergencePercent()));
        updateButtons();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        renderLogicalCounts(g);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int l = leftPos, t = topPos;
        g.fill(l, t, l + imageWidth, t + imageHeight, 0xFF17120D);
        g.fill(l + 3, t + 3, l + imageWidth - 3, t + imageHeight - 3, 0xFF8A6420);
        g.fill(l + 5, t + 30, l + imageWidth - 5, t + 140, 0xFF261C12);
        g.fill(l + 5, t + 151, l + imageWidth - 5, t + imageHeight - 5, 0xFF342818);

        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            boolean favorite = index < KingsTreasuryMenu.TREASURY_SLOTS && menu.isFavorite(index);
            boolean protectedPhantasm = index < KingsTreasuryMenu.TREASURY_SLOTS && menu.isPhantasmProtected(index);
            int border = favorite ? 0xFF7ADC85
                    : protectedPhantasm ? 0xFFFF8888
                    : index < KingsTreasuryMenu.TREASURY_SLOTS ? 0xFFCDA43A : 0xFF776342;
            int background = favorite ? 0xFF205B2D : protectedPhantasm ? 0xFF662020 : 0xFF171717;
            g.fill(l + slot.x - 1, t + slot.y - 1, l + slot.x + 17, t + slot.y + 17, border);
            g.fill(l + slot.x, t + slot.y, l + slot.x + 16, t + slot.y + 16, background);
        }

        int side = sidebarLeft();
        g.fill(side, t, side + SIDEBAR_WIDTH, t + SIDEBAR_HEIGHT, 0xFF17120D);
        g.fill(side + 2, t + 2, side + SIDEBAR_WIDTH - 2, t + SIDEBAR_HEIGHT - 2, 0xFF342818);
        g.drawCenteredString(font, Component.literal("放出優先度"), side + SIDEBAR_WIDTH / 2, t + 18, 0xFFFFE27A);
        g.drawCenteredString(font, Component.literal("召喚パターン"), side + SIDEBAR_WIDTH / 2, t + 124, 0xFFFFE27A);
        g.drawCenteredString(font, Component.literal("Shift召喚の収束率"), side + SIDEBAR_WIDTH / 2, t + 194, 0xFFFFE27A);
        g.drawString(font, "緑=お気に入り  赤=幻想禁止", side + 5, t + 226, 0xFFBFB3A0, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, 0xFFFFE27A, false);
        g.drawCenteredString(font, Component.literal((menu.getPage() + 1) + " / " + menu.getPageCount()),
                88, 19, 0xFFF4D36C);
        String total = menu.getTotalCount() + "/10000";
        g.drawString(font, total, imageWidth - 8 - font.width(total), 143, 0xFFF4D36C, false);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
    }

    private void updateButtons() {
        if (previousButton != null) previousButton.active = menu.getPage() > 0;
        if (nextButton != null) nextButton.active = menu.getPage() + 1 < menu.getPageCount();
        if (autoCollectButton != null) {
            autoCollectButton.setMessage(Component.literal(
                    menu.isAutoCollectEnabled() ? "自動回収 ON" : "自動回収 OFF"));
        }
        for (VolleyPriority priority : VolleyPriority.values()) {
            Button button = priorityButtons[priority.ordinal()];
            if (button != null) button.active = menu.getVolleyPriority() != priority;
        }
        for (SummonPattern pattern : SummonPattern.values()) {
            Button button = patternButtons[pattern.ordinal()];
            if (button != null) button.active = menu.getSummonPattern() != pattern;
        }
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int button) {
        if (mouseX >= sidebarLeft() && mouseX < leftPos
                && mouseY >= topPos && mouseY < topPos + SIDEBAR_HEIGHT) return false;
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, button);
    }

    private int sidebarLeft() {
        return leftPos - SIDEBAR_WIDTH - SIDEBAR_GAP;
    }

    private void renderLogicalCounts(GuiGraphics g) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 400);
        pose.scale(0.5F, 0.5F, 1);
        for (int i = 0; i < KingsTreasuryMenu.TREASURY_SLOTS; i++) {
            int count = menu.getLogicalCount(i);
            if (count <= 1) continue;
            Slot slot = menu.slots.get(i);
            String text = Integer.toString(count);
            g.drawString(font, text,
                    (leftPos + slot.x + 17) * 2 - font.width(text),
                    (topPos + slot.y + 9) * 2, 0xFFFFE070, true);
        }
        pose.popPose();
    }

    private void pressMenuButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private final class ConvergenceSlider extends AbstractSliderButton {
        private int sentPercent;

        private ConvergenceSlider(int x, int y, int width, int height, int percent) {
            super(x, y, width, height, Component.empty(),
                    Math.max(0.0D, Math.min(1.0D, percent / 100.0D)));
            sentPercent = percent;
            updateMessage();
        }

        private int currentPercent() {
            return (int)Math.round(value * 100.0D);
        }

        private void sendCurrentPercent() {
            int percent = currentPercent();
            if (percent == sentPercent) return;
            sentPercent = percent;
            pressMenuButton(KingsTreasuryMenu.BUTTON_CONVERGENCE_START + percent);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("収束率: " + currentPercent() + "%"));
        }

        /**
         * AbstractSliderButton本体がクリック位置とドラッグ中のvalueを連続更新する。
         * 通信はドロップ時だけ行い、ドラッグ中はローカル表示を滑らかに更新する。
         */
        @Override
        protected void applyValue() {
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            sendCurrentPercent();
            super.onRelease(mouseX, mouseY);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            int before = currentPercent();
            boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
            if (handled && currentPercent() != before) sendCurrentPercent();
            return handled;
        }
    }
}
