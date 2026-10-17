package dev.ssscfw.venusmod.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** 耐圧防具の部位ごとの説明をインベントリに表示する。 */
public final class PressureArmorItem extends ArmorItem {
    private final String tooltipKey;

    public PressureArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties, String itemId) {
        super(material, type, properties);
        this.tooltipKey = "tooltip.venusmod.equipment." + itemId + ".";
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable(tooltipKey + "description").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(tooltipKey + "usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
