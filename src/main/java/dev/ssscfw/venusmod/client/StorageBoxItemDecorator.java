package dev.ssscfw.venusmod.client;

import dev.ssscfw.venusmod.storagebox.StorageBoxData;
import dev.ssscfw.venusmod.storagebox.StorageBoxItem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;

/** 非空Storage Boxを「中身アイコン＋小さな箱バッジ＋総数」で表示する。 */
public final class StorageBoxItemDecorator implements IItemDecorator {
    @Override
    public boolean render(GuiGraphics graphics, Font font, ItemStack box, int x, int y) {
        ItemStack template = StorageBoxData.template(box);
        if (template.isEmpty() || template.getItem() instanceof StorageBoxItem) return false;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 180.0F);
        graphics.renderItem(template, x, y);

        // 外部StorageBoxの画像は使わず、VenusMod独自の小さな箱記号を描く。
        graphics.fill(x + 11, y, x + 16, y + 6, 0xFF3D260F);
        graphics.fill(x + 12, y + 1, x + 15, y + 5, 0xFFB8893E);
        graphics.fill(x + 12, y + 3, x + 15, y + 4, 0xFFD2A957);

        String count = compact(StorageBoxData.storedCount(box));
        graphics.drawString(font, count, x + 17 - font.width(count), y + 9, 0xFFFFFFFF, true);
        graphics.pose().popPose();
        return false;
    }

    static String compact(int count) {
        if (count < 1_000) return Integer.toString(Math.max(0, count));
        if (count < 1_000_000) return trim(count / 1_000.0D) + "K";
        if (count < 1_000_000_000) return trim(count / 1_000_000.0D) + "M";
        return trim(count / 1_000_000_000.0D) + "B";
    }

    private static String trim(double value) {
        if (value >= 100.0D) return Long.toString(Math.round(value));
        double rounded = Math.floor(value * 10.0D) / 10.0D;
        return rounded == Math.floor(rounded)
                ? Long.toString((long) rounded)
                : Double.toString(rounded);
    }
}
