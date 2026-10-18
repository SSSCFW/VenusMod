package dev.ssscfw.venusmod.infrastructure.network;

import dev.ssscfw.venusmod.infrastructure.network.core.CableGrade;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
 
/** 帯域と配線ルールを全16色・無染色のアイテムに共通表示する。 */
public final class NetworkCableItem extends BlockItem {
    private final CableGrade grade;
    public NetworkCableItem(NetworkCableBlock block,Properties properties){super(block,properties);this.grade=block.grade;}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        super.appendHoverText(stack,context,lines,flag);
        lines.add(Component.translatable("tooltip.venusmod.network.max_bandwidth",CableGrade.speed(grade.bps())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.venusmod.network.max_length",grade.maxLength()).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("tooltip.venusmod.network.cable_color_rule").withStyle(ChatFormatting.DARK_GRAY));
    }
}
