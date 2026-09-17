package dev.ssscfw.venusmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ssscfw.venusmod.treasury.KingsTreasuryMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** 54枠×ページ式の王の宝物庫GUI。論理スタック数は通常ItemStack数とは別に描画する。 */
public final class KingsTreasuryScreen extends AbstractContainerScreen<KingsTreasuryMenu> {
    private Button previousButton;
    private Button nextButton;

    public KingsTreasuryScreen(KingsTreasuryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 222;
        inventoryLabelY = 128;
    }

    @Override
    protected void init() {
        super.init();
        previousButton = addRenderableWidget(Button.builder(Component.literal("<"),
                        button -> pressMenuButton(KingsTreasuryMenu.BUTTON_PREVIOUS))
                .bounds(leftPos + 118, topPos + 3, 18, 14)
                .build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"),
                        button -> pressMenuButton(KingsTreasuryMenu.BUTTON_NEXT))
                .bounds(leftPos + 139, topPos + 3, 18, 14)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderLogicalCounts(guiGraphics);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        guiGraphics.fill(left, top, left + imageWidth, top + imageHeight, 0xFF17120D);
        guiGraphics.fill(left + 3, top + 3, left + imageWidth - 3, top + imageHeight - 3, 0xFF8A6420);
        guiGraphics.fill(left + 5, top + 17, left + imageWidth - 5, top + 126, 0xFF261C12);
        guiGraphics.fill(left + 5, top + 137, left + imageWidth - 5, top + imageHeight - 5, 0xFF342818);

        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            int border = index < KingsTreasuryMenu.TREASURY_SLOTS ? 0xFFCDA43A : 0xFF776342;
            guiGraphics.fill(left + slot.x - 1, top + slot.y - 1, left + slot.x + 17, top + slot.y + 17, border);
            guiGraphics.fill(left + slot.x, top + slot.y, left + slot.x + 16, top + slot.y + 16, 0xFF171717);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 8, 6, 0xFFFFE2৭A, false);
        guiGraphics.drawCenteredString(font,
                Component.translatable("gui.venusmod.king_treasury.page", menu.getPage() + 1, menu.getPageCount()),
                91, 6, 0xFFF4D36C);

        Component auto = Component.translatable(menu.isAutoCollectEnabled()
                        ? "gui.venusmod.king_treasury.auto_on"
                        : "gui.venusmod.king_treasury.auto_off")
                .withStyle(menu.isAutoCollectEnabled() ? ChatFormatting.GREEN : ChatFormatting.GRAY);
        guiGraphics.drawString(font, auto, 8, 128, 0xFFFFFF, false);

        Component total = Component.translatable("gui.venusmod.king_treasury.total", menu.getTotalCount());
        guiGraphics.drawString(font, total, imageWidth - 8 - font.width(total), 128, 0xFFF4D36C, false);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (previousButton != null) previousButton.active = menu.getPage() > 0;
        if (nextButton != null) nextButton.active = menu.getPage() + 1 < menu.getPageCount();
    }

    private void renderLogicalCounts(GuiGraphics guiGraphics) {
        PoseStack pose = guiGraphics.pose();
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
            guiGraphics.drawString(font, text, right - font.width(text), y, 0xFFFFE070, true);
        }
        pose.popPose();
    }

    private void pressMenuButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }
}
