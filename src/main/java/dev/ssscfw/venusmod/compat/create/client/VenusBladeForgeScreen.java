package dev.ssscfw.venusmod.compat.create.client;

import dev.ssscfw.venusmod.compat.create.VenusBladeForgeBlockEntity;
import dev.ssscfw.venusmod.compat.create.VenusBladeForgeMenu;
import dev.ssscfw.venusmod.registry.ModItems;
import dev.ssscfw.venusmod.registry.VenusPhase2;
import dev.ssscfw.venusmod.upgrade.BladeUpgradeRules;
import dev.ssscfw.venusmod.upgrade.ForgeScreenLayout;
import dev.ssscfw.venusmod.world.VenusDimensionContent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 素材は左側4行、進行条件と強化ボタンは右側。文字幅は描画前に測定する。 */
public final class VenusBladeForgeScreen extends AbstractContainerScreen<VenusBladeForgeMenu> {
    private final Inventory playerInventory;
    private Button upgradeButton;

    public VenusBladeForgeScreen(VenusBladeForgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        playerInventory = inventory;
        imageWidth = ForgeScreenLayout.WIDTH;
        imageHeight = ForgeScreenLayout.HEIGHT;
        inventoryLabelX = ForgeScreenLayout.INVENTORY_X;
        inventoryLabelY = 143;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("<"),
                        button -> pressMenuButton(VenusBladeForgeMenu.BUTTON_PREVIOUS))
                .bounds(leftPos + 50, topPos + 22, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"),
                        button -> pressMenuButton(VenusBladeForgeMenu.BUTTON_NEXT))
                .bounds(leftPos + 274, topPos + 22, 18, 18).build());
        upgradeButton = addRenderableWidget(Button.builder(Component.literal("強化"),
                        button -> pressMenuButton(VenusBladeForgeMenu.BUTTON_UPGRADE))
                .bounds(leftPos + ForgeScreenLayout.INFO_X, topPos + ForgeScreenLayout.UPGRADE_Y,
                        ForgeScreenLayout.INFO_RIGHT - ForgeScreenLayout.INFO_X, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        // 省略表示した素材名は、行にマウスを重ねれば正式名と個数を確認できる。
        BladeUpgradeRules.Cost cost = menu.getNextCost();
        if (cost != null) {
            Item[] materials = materials();
            int[] required = required(cost);
            for (int i = 0; i < materials.length; i++) {
                int y = topPos + ForgeScreenLayout.costY(i);
                if (mouseX >= leftPos + ForgeScreenLayout.COST_X && mouseX < leftPos + ForgeScreenLayout.COST_RIGHT
                        && mouseY >= y && mouseY < y + 16) {
                    Component text = Component.empty().append(new ItemStack(materials[i]).getHoverName())
                            .append("  " + count(materials[i]) + " / " + required[i]);
                    g.renderTooltip(font, text, mouseX, mouseY);
                    break;
                }
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int l = leftPos;
        int t = topPos;
        g.fill(l, t, l + imageWidth, t + imageHeight, 0xFF17120D);
        g.fill(l + 3, t + 3, l + imageWidth - 3, t + imageHeight - 3, 0xFF9B6C20);
        g.fill(l + 6, t + 18, l + imageWidth - 6, t + 140, 0xFF261C12);
        g.fill(l + 168, t + 54, l + 169, t + 136, 0xFF6C512C);
        g.fill(l + ForgeScreenLayout.INVENTORY_X - 5, t + 152,
                l + ForgeScreenLayout.INVENTORY_X + 167, t + imageHeight - 5, 0xFF342818);
        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            int border = index == VenusBladeForgeMenu.BLADE_SLOT ? 0xFFFFD766 : 0xFF776342;
            g.fill(l + slot.x - 1, t + slot.y - 1, l + slot.x + 17, t + slot.y + 17, border);
            g.fill(l + slot.x, t + slot.y, l + slot.x + 16, t + slot.y + 16, 0xFF171717);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        drawClipped(g, title, 8, 6, imageWidth - 16, 0xFFFFE27A);
        Component selected = Component.translatable("upgrade.venusmod." + menu.getSelectedType().getSerializedName());
        String name = fitted(selected, 194);
        g.drawString(font, name, 171 - font.width(name) / 2, 27, 0xFFFFE27A, false);
        Component level = menu.getBlade().isEmpty() ? Component.literal("刀をセット")
                : Component.literal("Lv " + menu.getCurrentLevel() + " / " + menu.getMaxLevel());
        drawClipped(g, level, 50, 45, 114, 0xFFFFFFFF);
        int rpmColor = menu.getRpm() >= (int) VenusBladeForgeBlockEntity.REQUIRED_RPM ? 0xFF74E082 : 0xFFFF7777;
        drawClipped(g, Component.literal("回転数 " + menu.getRpm() + " / "
                        + (int) VenusBladeForgeBlockEntity.REQUIRED_RPM + " RPM"),
                ForgeScreenLayout.INFO_X, 45, ForgeScreenLayout.INFO_RIGHT - ForgeScreenLayout.INFO_X, rpmColor);
        drawClipped(g, Component.literal("素材（所持 / 必要）"), 14, 54, 150, 0xFFE6D4AA);
        BladeUpgradeRules.Cost cost = menu.getNextCost();
        if (cost == null) {
            g.drawString(font, Component.literal("最大レベル"), 34, 70, 0xFFFFE27A, false);
        } else {
            Item[] items = materials();
            int[] required = required(cost);
            for (int i = 0; i < items.length; i++) drawCost(g, items[i], required[i], i);
            drawKey(g, VenusDimensionContent.VENUS_CORE.get(), true, 64);
            drawKey(g, VenusDimensionContent.VENUS_SOUL_STONE.get(), cost.soulStoneRequired(), 88);
        }
        g.drawString(font, Component.translatable("container.inventory"),
                inventoryLabelX, inventoryLabelY, 0xFFE6D4AA, false);
    }

    private void drawCost(GuiGraphics g, Item item, int required, int row) {
        int have = count(item);
        int y = ForgeScreenLayout.costY(row);
        int color = have >= required ? 0xFF74E082 : 0xFFFF7777;
        ItemStack stack = new ItemStack(item);
        g.renderItem(stack, ForgeScreenLayout.COST_X, y);
        String amount = have + "/" + required;
        int countWidth = font.width(amount);
        drawClipped(g, stack.getHoverName(), ForgeScreenLayout.COST_NAME_X, y + 4,
                ForgeScreenLayout.nameWidth(countWidth), color);
        g.drawString(font, amount, ForgeScreenLayout.COST_RIGHT - countWidth, y + 4, color, false);
    }

    private void drawKey(GuiGraphics g, Item item, boolean required, int y) {
        int x = ForgeScreenLayout.INFO_X;
        ItemStack stack = new ItemStack(item);
        boolean owned = count(item) > 0;
        g.renderItem(stack, x, y + 2);
        drawClipped(g, stack.getHoverName(), x + 20, y, ForgeScreenLayout.INFO_RIGHT - x - 20, 0xFFFFFFFF);
        String state = !required ? "不要" : owned ? "所持（消費なし）" : "未所持";
        int color = !required ? 0xFFAAAAAA : owned ? 0xFF74E082 : 0xFFFF7777;
        drawClipped(g, Component.literal(state), x + 20, y + 11,
                ForgeScreenLayout.INFO_RIGHT - x - 20, color);
    }

    private String fitted(Component text, int width) {
        if (width <= 0) return "";
        String value = text.getString();
        if (font.width(value) <= width) return value;
        String suffix = font.width("...") <= width ? "..." : "";
        return font.plainSubstrByWidth(value, Math.max(0, width - font.width(suffix))) + suffix;
    }

    private void drawClipped(GuiGraphics g, Component text, int x, int y, int width, int color) {
        g.drawString(font, fitted(text, width), x, y, color, false);
    }

    private Item[] materials() {
        return new Item[]{VenusPhase2.PRESSURE_ALLOY.get(), ModItems.NICKEL_INGOT.get(),
                VenusPhase2.VENESITE.get(), VenusPhase2.SULFUR.get()};
    }

    private int[] required(BladeUpgradeRules.Cost cost) {
        return new int[]{cost.pressureAlloy(), cost.nickel(), cost.venesite(), cost.sulfur()};
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (upgradeButton != null) upgradeButton.active = !menu.getBlade().isEmpty()
                && menu.getCurrentLevel() < menu.getMaxLevel();
    }

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
