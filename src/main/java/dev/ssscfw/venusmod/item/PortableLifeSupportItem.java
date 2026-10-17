package dev.ssscfw.venusmod.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemStack;

/** 金星環境用の携帯生命維持装置。 */
public final class PortableLifeSupportItem extends Item {
    private static final int ENCHANTMENT_VALUE = 15;

    public PortableLifeSupportItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return ENCHANTMENT_VALUE;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        String key = "tooltip.venusmod.equipment.portable_life_support.";
        lines.add(Component.translatable(key + "description").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(key + "usage").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable(key + "details").withStyle(ChatFormatting.DARK_GRAY));
    }
}
