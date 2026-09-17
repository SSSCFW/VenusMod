package dev.ssscfw.venusmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ssscfw.venusmod.treasury.KingsTreasuryMenu;
import dev.ssscfw.venusmod.treasury.VolleyPriority;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** 54枠の宝物庫と、インベントリ外の左サイドパネル。緑背景は射出保護スロット。 */
public final class KingsTreasuryScreen extends AbstractContainerScreen<KingsTreasuryMenu> {
    private static final int SIDEBAR_WIDTH = 108;
    private static final int SIDEBAR_GAP = 8;
    private static final int SIDEBAR_HEIGHT = 160;
    private Button previousButton;
    private Button nextButton;
    private final Button[] priorityButtons = new Button[VolleyPriority.values().length];

    public KingsTreasuryScreen(KingsTreasuryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = KingsTreasuryMenu.IMAGE_HEIGHT;
        inventoryLabelY = 143;
    }

    @Override
    protected void init() {
        super.init();
        // サイドパネルまで含めて中央配置し、GUIスケールを上げても左側を画面外へ押し出さない。
        leftPos = (width - imageWidth - SIDEBAR_WIDTH - SIDEBAR_GAP) / 2 + SIDEBAR_WIDTH + SIDEBAR_GAP;
        previousButton = addRenderableWidget(Button.builder(Component.literal("<"),
                        button -> pressMenuButton(KingsTreasuryMenu.BUTTON_PREVIOUS))
                .bounds(leftPos + 8, topPos + 17, 18, 12).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"),
                        button -> pressMenuButton(KingsTreasuryMenu.BUTTON_NEXT))
                .bounds(leftPos + 150, topPos + 17, 18, 12).build());
        for (VolleyPriority priority : VolleyPriority.values()) {
            priorityButtons[priority.ordinal()] = addRenderableWidget(Button.builder(
                            Component.literal(priority.label()), button -> pressMenuButton(
                                    KingsTreasuryMenu.BUTTON_PRIORITY_START + priority.ordinal()))
                    .bounds(sidebarLeft() + 5, topPos + 38 + priority.ordinal() * 24, SIDEBAR_WIDTH - 10, 20)
                    .tooltip(Tooltip.create(Component.literal(priority == VolleyPriority.RANDOM
                            ? "未破損・お気に入り以外の刀からランダム選択"
                            : "残り耐久ポイントで比較。お気に入り・折れた刀は除外")))
                    .build());
        }
        updateButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderLogicalCounts(graphics);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        g.fill(left, top, left + imageWidth, top + imageHeight, 0xFF17120D);
        g.fill(left + 3, top + 3, left + imageWidth - 3, top + imageHeight - 3, 0xFF8A6420);
        g.fill(left + 5, top + 30, left + imageWidth - 5, top + 140, 0xFF261C12);
        g.fill(left + 5, top + 151, left + imageWidth - 5, top + imageHeight - 5, 0xFF342818);
        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            boolean favorite = index < KingsTreasuryMenu.TREASURY_SLOTS && menu.isFavorite(index);
            int border = favorite ? 0xFF7ADC85 : index < KingsTreasuryMenu.TREASURY_SLOTS ? 0xFFCDA43A : 0xFF776342;
            int background = favorite ? 0xFF205B2D : 0xFF171717;
            g.fill(left + slot.x - 1, top + slot.y - 1, left + slot.x + 17, top + slot.y + 17, border);
            g.fill(left + slot.x, top + slot.y, left + slot.x + 16, top + slot.y + 16, background);
        }
        int side = sidebarLeft();
        g.fill(side, top + 18, side + SIDEBAR_WIDTH, top + SIDEBAR_HEIGHT, 0xFF17120D);
        g.fill(side + 2, top + 20, side + SIDEBAR_WIDTH - 2, top + SIDEBAR_HEIGHT - 2, 0xFF342818);
        g.drawCenteredString(font, Component.literal("放出優先度"), side + SIDEBAR_WIDTH / 2, top + 25, 0xFFFFE27A);
        g.drawString(font, Component.literal("緑：お気に入り"), side + 6, top + 113, 0xFF7ADC85, false);
        g.drawString(font, Component.literal("右クリックで切替"), side + 6, top + 125, 0xFFE6D4AA, false);
        g.drawString(font, Component.literal("折れた刀も射出対象外"), side + 6, top + 143, 0xFFBFB3A0, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, 0xFFFFE27A, false);
        g.drawCenteredString(font, Component.literal((menu.getPage() + 1) + " / " + menu.getPageCount()),
                88, 19, 0xFFF4D36C);
        String auto = menu.isAutoCollectEnabled() ? "自動回収: ON" : "自動回収: OFF";
        g.drawString(font, auto, 8, 143, menu.isAutoCollectEnabled() ? 0xFF7ADC85 : 0xFFCCCCCC, false);
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
        for (VolleyPriority mode : VolleyPriority.values()) {
            Button button = priorityButtons[mode.ordinal()];
            if (button != null) button.active = menu.getVolleyPriority() != mode;
        }
    }

    /** 左パネル上の空白を「インベントリ外へのアイテム投棄」として扱わない。 */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        if (mouseX >= sidebarLeft() && mouseX < leftPos
                && mouseY >= topPos + 18 && mouseY < topPos + SIDEBAR_HEIGHT) return false;
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, mouseButton);
    }

    private int sidebarLeft() { return leftPos - SIDEBAR_WIDTH - SIDEBAR_GAP; }

    private void renderLogicalCounts(GuiGraphics graphics) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 400.0F);
        pose.scale(0.5F, 0.5F, 1.0F);
        for (int index = 0; index < KingsTreasuryMenu.TREASURY_SLOTS; index++) {
            int count = menu.getLogicalCount(index);
            if (count <= 1) continue;
            Slot slot = menu.slots.get(index);
            String text = Integer.toString(count);
            int right = (leftPos + slot.x + 17) * 2;
            int y = (topPos + slot.y + 9) * 2;
            graphics.drawString(font, text, right - font.width(text), y, 0xFFFFE070, true);
        }
        pose.popPose();
    }

    private void pressMenuButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }
}
