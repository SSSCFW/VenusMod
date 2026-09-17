package dev.ssscfw.venusmod.compat.create.client;

import dev.ssscfw.venusmod.compat.create.VenusBladeForgeBlockEntity;
import dev.ssscfw.venusmod.compat.create.VenusBladeForgeMenu;
import dev.ssscfw.venusmod.registry.ModItems;
import dev.ssscfw.venusmod.registry.VenusPhase2;
import dev.ssscfw.venusmod.upgrade.BladeUpgradeRules;
import dev.ssscfw.venusmod.world.VenusDimensionContent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 刀1本・強化項目・必要素材と進行条件を1画面で操作する金星刀鍛錬機GUI。 */
public final class VenusBladeForgeScreen extends AbstractContainerScreen<VenusBladeForgeMenu> {
    private final Inventory playerInventory;
    private Button upgradeButton;

    public VenusBladeForgeScreen(VenusBladeForgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.playerInventory = inventory;
        imageWidth = 230;
        imageHeight = 216;
        inventoryLabelX = 34;
        inventoryLabelY = 121;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("<"),
                        button -> pressMenuButton(VenusBladeForgeMenu.BUTTON_PREVIOUS))
                .bounds(leftPos + 48, topPos + 22, 18, 16)
                .build());
        addRenderableWidget(Button.builder(Component.literal(">"),
                        button -> pressMenuButton(VenusBladeForgeMenu.BUTTON_NEXT))
                .bounds(leftPos + 194, topPos + 22, 18, 16)
                .build());
        upgradeButton = addRenderableWidget(Button.builder(
                        Component.literal("強化"),
                        button -> pressMenuButton(VenusBladeForgeMenu.BUTTON_UPGRADE))
                .bounds(leftPos + 148, topPos + 99, 64, 18)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        guiGraphics.fill(left, top, left + imageWidth, top + imageHeight, 0xFF17120D);
        guiGraphics.fill(left + 3, top + 3, left + imageWidth - 3, top + imageHeight - 3, 0xFF9B6C20);
        guiGraphics.fill(left + 6, top + 18, left + imageWidth - 6, top + 121, 0xFF261C12);
        guiGraphics.fill(left + 27, top + 127, left + imageWidth - 27, top + imageHeight - 7, 0xFF342818);

        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            int border = index == VenusBladeForgeMenu.BLADE_SLOT ? 0xFFFFD766 : 0xFF776342;
            guiGraphics.fill(left + slot.x - 1, top + slot.y - 1,
                    left + slot.x + 17, top + slot.y + 17, border);
            guiGraphics.fill(left + slot.x, top + slot.y,
                    left + slot.x + 16, top + slot.y + 16, 0xFF171717);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 8, 6, 0xFFFFE27A, false);

        Component selected = Component.translatable(
                "upgrade.venusmod." + menu.getSelectedType().getSerializedName());
        guiGraphics.drawCenteredString(font, selected, 130, 26, 0xFFFFE27A);

        ItemStack blade = menu.getBlade();
        if (blade.isEmpty()) {
            guiGraphics.drawString(font,
                    Component.literal("抜刀剣をセットしてください"),
                    48, 47, 0xFFB9B1A2, false);
        } else {
            guiGraphics.drawString(font,
                    Component.literal("Lv " + menu.getCurrentLevel() + " / " + menu.getMaxLevel()),
                    48, 47, 0xFFFFFFFF, false);
        }

        int rpmColor = menu.getRpm() >= (int) VenusBladeForgeBlockEntity.REQUIRED_RPM
                ? 0xFF74E082 : 0xFFFF7777;
        guiGraphics.drawString(font,
                Component.literal("回転数: " + menu.getRpm() + " / "
                        + (int) VenusBladeForgeBlockEntity.REQUIRED_RPM + " RPM"),
                48, 60, rpmColor, false);

        BladeUpgradeRules.Cost cost = menu.getNextCost();
        if (cost == null && !blade.isEmpty()) {
            guiGraphics.drawString(font,
                    Component.literal("最大レベル"),
                    48, 76, 0xFFFFE27A, false);
        } else if (cost != null) {
            drawCost(guiGraphics, VenusPhase2.PRESSURE_ALLOY.get(), cost.pressureAlloy(), 48, 76);
            drawCost(guiGraphics, ModItems.NICKEL_INGOT.get(), cost.nickel(), 132, 76);
            drawCost(guiGraphics, VenusPhase2.VENESITE.get(), cost.venesite(), 48, 88);
            drawCost(guiGraphics, VenusPhase2.SULFUR.get(), cost.sulfur(), 132, 88);

            boolean core = contains(VenusDimensionContent.VENUS_CORE.get());
            Component coreStatus = status(VenusDimensionContent.VENUS_CORE.get(), true, core);
            guiGraphics.drawString(font, coreStatus, 48, 102, 0xFFFFFFFF, false);

            boolean soulRequired = cost.soulStoneRequired();
            boolean soul = contains(VenusDimensionContent.VENUS_SOUL_STONE.get());
            Component soulStatus = status(VenusDimensionContent.VENUS_SOUL_STONE.get(), soulRequired, soul);
            guiGraphics.drawString(font, soulStatus, 48, 113, 0xFFFFFFFF, false);
        }

        guiGraphics.drawString(font, Component.translatable("container.inventory"),
                inventoryLabelX, inventoryLabelY, 0xFFE6D4AA, false);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (upgradeButton != null) {
            upgradeButton.active = !menu.getBlade().isEmpty()
                    && menu.getCurrentLevel() < menu.getMaxLevel();
        }
    }

    private void drawCost(GuiGraphics guiGraphics, Item item, int required, int x, int y) {
        int have = count(item);
        int color = have >= required ? 0xFF74E082 : 0xFFFF7777;
        Component name = new ItemStack(item).getHoverName();
        guiGraphics.drawString(font,
                Component.empty().append(name).append(Component.literal(" " + have + "/" + required)),
                x, y, color, false);
    }

    private Component status(Item item, boolean required, boolean owned) {
        Component name = new ItemStack(item).getHoverName();
        String text;
        ChatFormatting color;
        if (!required) {
            text = "不要";
            color = ChatFormatting.GRAY;
        } else if (owned) {
            text = "所持";
            color = ChatFormatting.GREEN;
        } else {
            text = "未所持";
            color = ChatFormatting.RED;
        }
        return Component.empty().append(name).append(Component.literal(": "))
                .append(Component.literal(text).withStyle(color));
    }

    private boolean contains(Item item) { return count(item) > 0; }

    private int count(Item item) {
        int total = 0;
        for (int slot = 0; slot < playerInventory.getContainerSize(); slot++) {
            ItemStack stack = playerInventory.getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private void pressMenuButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }
}
