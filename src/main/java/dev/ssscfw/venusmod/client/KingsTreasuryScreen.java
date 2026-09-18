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

/** 緑=全射出禁止、赤=幻想のみ禁止。左パネルに4種類の優先度を配置する。 */
public final class KingsTreasuryScreen extends AbstractContainerScreen<KingsTreasuryMenu> {
    private static final int SIDEBAR_WIDTH = 108;
    private static final int SIDEBAR_GAP = 8;
    private static final int SIDEBAR_HEIGHT = 184;
    private Button previousButton;
    private Button nextButton;
    private final Button[] priorityButtons = new Button[VolleyPriority.values().length];
    public KingsTreasuryScreen(KingsTreasuryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176; imageHeight = KingsTreasuryMenu.IMAGE_HEIGHT; inventoryLabelY = 143;
    }
    @Override protected void init() {
        super.init();
        leftPos = (width - imageWidth - SIDEBAR_WIDTH - SIDEBAR_GAP) / 2 + SIDEBAR_WIDTH + SIDEBAR_GAP;
        previousButton = addRenderableWidget(Button.builder(Component.literal("<"), b -> pressMenuButton(KingsTreasuryMenu.BUTTON_PREVIOUS))
                .bounds(leftPos + 8, topPos + 17, 18, 12).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), b -> pressMenuButton(KingsTreasuryMenu.BUTTON_NEXT))
                .bounds(leftPos + 150, topPos + 17, 18, 12).build());
        for (VolleyPriority priority : VolleyPriority.values()) {
            String hint = switch (priority) {
                case RANDOM -> "保護設定に従い、在庫の本数に比例してランダム選択";
                case RANK_LOW -> "消滅型の通常刀→通常刀→印→妖刀。同ランクはベースダメージの低い順";
                default -> "残り耐久ポイントで比較。折れた刀・保護された刀は除外";
            };
            priorityButtons[priority.ordinal()] = addRenderableWidget(Button.builder(Component.literal(priority.label()),
                            b -> pressMenuButton(KingsTreasuryMenu.BUTTON_PRIORITY_START + priority.ordinal()))
                    .bounds(sidebarLeft() + 5, topPos + 38 + priority.ordinal() * 24, SIDEBAR_WIDTH - 10, 20)
                    .tooltip(Tooltip.create(Component.literal(hint))).build());
        }
        updateButtons();
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        renderLogicalCounts(g);
        renderTooltip(g, mouseX, mouseY);
    }
    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int l = leftPos, t = topPos;
        g.fill(l, t, l + imageWidth, t + imageHeight, 0xFF17120D);
        g.fill(l + 3, t + 3, l + imageWidth - 3, t + imageHeight - 3, 0xFF8A6420);
        g.fill(l + 5, t + 30, l + imageWidth - 5, t + 140, 0xFF261C12);
        g.fill(l + 5, t + 151, l + imageWidth - 5, t + imageHeight - 5, 0xFF342818);
        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            boolean favorite = index < KingsTreasuryMenu.TREASURY_SLOTS && menu.isFavorite(index);
            boolean protectedPhantasm = index < KingsTreasuryMenu.TREASURY_SLOTS && menu.isPhantasmProtected(index);
            int border = favorite ? 0xFF7ADC85 : protectedPhantasm ? 0xFFFF8888 : index < KingsTreasuryMenu.TREASURY_SLOTS ? 0xFFCDA43A : 0xFF776342;
            int background = favorite ? 0xFF205B2D : protectedPhantasm ? 0xFF662020 : 0xFF171717;
            g.fill(l + slot.x - 1, t + slot.y - 1, l + slot.x + 17, t + slot.y + 17, border);
            g.fill(l + slot.x, t + slot.y, l + slot.x + 16, t + slot.y + 16, background);
        }
        int side = sidebarLeft();
        g.fill(side, t + 18, side + SIDEBAR_WIDTH, t + SIDEBAR_HEIGHT, 0xFF17120D);
        g.fill(side + 2, t + 20, side + SIDEBAR_WIDTH - 2, t + SIDEBAR_HEIGHT - 2, 0xFF342818);
        g.drawCenteredString(font, Component.literal("放出優先度"), side + SIDEBAR_WIDTH / 2, t + 25, 0xFFFFE27A);
        g.drawString(font, "緑：お気に入り", side + 6, t + 137, 0xFF7ADC85, false);
        g.drawString(font, "赤：壊れた幻想禁止", side + 6, t + 149, 0xFFFF8888, false);
        g.drawString(font, "右クリックで3状態切替", side + 6, t + 167, 0xFFBFB3A0, false);
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, 0xFFFFE27A, false);
        g.drawCenteredString(font, Component.literal((menu.getPage() + 1) + " / " + menu.getPageCount()), 88, 19, 0xFFF4D36C);
        g.drawString(font, menu.isAutoCollectEnabled() ? "自動回収: ON" : "自動回収: OFF", 8, 143,
                menu.isAutoCollectEnabled() ? 0xFF7ADC85 : 0xFFCCCCCC, false);
        String total = menu.getTotalCount() + "/10000";
        g.drawString(font, total, imageWidth - 8 - font.width(total), 143, 0xFFF4D36C, false);
    }
    @Override protected void containerTick() { super.containerTick(); updateButtons(); }
    private void updateButtons() {
        if (previousButton != null) previousButton.active = menu.getPage() > 0;
        if (nextButton != null) nextButton.active = menu.getPage() + 1 < menu.getPageCount();
        for (VolleyPriority p : VolleyPriority.values()) if (priorityButtons[p.ordinal()] != null)
            priorityButtons[p.ordinal()].active = menu.getVolleyPriority() != p;
    }
    @Override protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int button) {
        if (mouseX >= sidebarLeft() && mouseX < leftPos && mouseY >= topPos + 18 && mouseY < topPos + SIDEBAR_HEIGHT) return false;
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, button);
    }
    private int sidebarLeft() { return leftPos - SIDEBAR_WIDTH - SIDEBAR_GAP; }
    private void renderLogicalCounts(GuiGraphics g) {
        PoseStack pose = g.pose();
        pose.pushPose(); pose.translate(0, 0, 400); pose.scale(0.5F, 0.5F, 1);
        for (int i = 0; i < KingsTreasuryMenu.TREASURY_SLOTS; i++) {
            int count = menu.getLogicalCount(i);
            if (count <= 1) continue;
            Slot slot = menu.slots.get(i);
            String text = Integer.toString(count);
            g.drawString(font, text, (leftPos + slot.x + 17) * 2 - font.width(text), (topPos + slot.y + 9) * 2, 0xFFFFE070, true);
        }
        pose.popPose();
    }
    private void pressMenuButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }
}
