package dev.ssscfw.venusmod.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** 機械の用途と使用条件をアイテムのツールチップに表示する。 */
public final class MachineDescriptionItem extends BlockItem {
    private final String tooltipKey;

    public MachineDescriptionItem(Block block, Properties properties, String machineId) {
        super(block, properties);
        this.tooltipKey = "tooltip.venusmod.machine." + machineId;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable(tooltipKey + ".description").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(tooltipKey + ".usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
