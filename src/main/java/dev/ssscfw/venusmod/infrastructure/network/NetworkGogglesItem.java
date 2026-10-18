package dev.ssscfw.venusmod.infrastructure.network;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;

/** Createのクラスを参照せず、頭装備で使えるネットワーク用ゴーグル。 */
public final class NetworkGogglesItem extends Item implements Equipable {
    private final boolean combined;

    public NetworkGogglesItem(boolean combined, Properties properties) {
        super(properties);
        this.combined = combined;
        DispenserBlock.registerBehavior(this, ArmorItem.DISPENSE_ITEM_BEHAVIOR);
    }

    @Override public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
                                         List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("tooltip.venusmod.network."
                + (combined ? "combined_network_goggles" : "network_goggles"))
                .withStyle(ChatFormatting.GRAY));
    }
}
