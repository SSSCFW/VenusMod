package dev.ssscfw.venusmod.client;

import dev.ssscfw.venusmod.storagebox.StorageBoxMenu;
import dev.ssscfw.venusmod.storagebox.StorageBoxRules;
import java.text.DecimalFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Storage Boxの独自GUI。IN/OUTはサーバー側の仮想スロットとして扱う。 */
public final class StorageBoxScreen extends AbstractContainerScreen<StorageBoxMenu> {
    private static final DecimalFormat LC_FORMAT = new DecimalFormat("0.##");
    private Button autoButton;

    public StorageBoxScreen(StorageBoxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 186;
        inventoryLabelY = 92;
    }

    @Override
    protected void init() {
        super.init();
        autoButton = addRenderableWidget(Button.builder(Component.empty(), b -> press(StorageBoxMenu.BUTTON_AUTO_COLLECT))
                .bounds(leftPos + 53, topPos + 72, 70, 16).build());
        updateButton();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButton();
    }

    private void updateButton() {
        if (autoButton != null) {
            autoButton.setMessage(Component.translatable(menu.autoCollect()
                    ? "gui.venusmod.storage_box.auto_on" : "gui.venusmod.storage_box.auto_off"));
        }
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int l = leftPos, t = topPos;
        g.fill(l, t, l + imageWidth, t + imageHeight, 0xFF17120D);
        g.fill(l + 3, t + 3, l + imageWidth - 3, t + imageHeight - 3, 0xFF8A6420);
        g.fill(l + 5, t + 28, l + imageWidth - 5, t + 91, 0xFF261C12);
        g.fill(l + 5, t + 101, l + imageWidth - 5, t + imageHeight - 5, 0xFF342818);

        g.fill(l + 43, t + 44, l + 61, t + 62, 0xFFCDA43A);
        g.fill(l + 44, t + 45, l + 60, t + 61, 0xFF171717);
        g.fill(l + 115, t + 44, l + 133, t + 62, 0xFFCDA43A);
        g.fill(l + 116, t + 45, l + 132, t + 61, 0xFF171717);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 7, 0xFFFFE27A, false);
        g.drawCenteredString(font, Component.literal("IN"), 52, 33, 0xFFE7D69B);
        g.drawCenteredString(font, Component.literal("OUT"), 124, 33, 0xFFE7D69B);
        ItemStack template = menu.template();
        String name = template.isEmpty()
                ? Component.translatable("tooltip.venusmod.storage_box.empty").getString()
                : template.getHoverName().getString();
        g.drawCenteredString(font, Component.literal(name), 88, 64, 0xFFFFFFFF);
        g.drawString(font, Component.translatable("gui.venusmod.storage_box.count",
                menu.storedCount(), StorageBoxRules.CAPACITY), 8, 89, 0xFFF4D36C, false);
        double lc = template.isEmpty() ? 0.0D
                : StorageBoxRules.largeChestEquivalent(menu.storedCount(), template.getMaxStackSize());
        String lcText = Component.translatable("gui.venusmod.storage_box.lc", LC_FORMAT.format(lc)).getString();
        g.drawString(font, lcText, imageWidth - 8 - font.width(lcText), 89, 0xFFF4D36C, false);
    }
}
