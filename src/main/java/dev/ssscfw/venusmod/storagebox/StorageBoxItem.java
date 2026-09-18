package dev.ssscfw.venusmod.storagebox;

import java.text.DecimalFormat;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** 1種類のItemStackを大量保存する携帯Storage Box。 */
public final class StorageBoxItem extends Item {
    private static final DecimalFormat LC_FORMAT = new DecimalFormat("0.##");

    public StorageBoxItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack box = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(box);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, menuPlayer) ->
                            new StorageBoxMenu(containerId, inventory, box),
                    Component.translatable("container.venusmod.storage_box")));
        }
        return InteractionResultHolder.sidedSuccess(box, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return StorageBoxData.storedCount(stack) > 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        ItemStack template = StorageBoxData.template(stack);
        int count = StorageBoxData.storedCount(stack);
        if (template.isEmpty()) {
            lines.add(Component.translatable("tooltip.venusmod.storage_box.empty"));
        } else {
            lines.add(Component.translatable("tooltip.venusmod.storage_box.item", template.getHoverName()));
            lines.add(Component.translatable("tooltip.venusmod.storage_box.count", count, StorageBoxRules.CAPACITY));
            lines.add(Component.translatable("tooltip.venusmod.storage_box.lc",
                    LC_FORMAT.format(StorageBoxData.largeChestEquivalent(stack))));
        }
        lines.add(Component.translatable(StorageBoxData.autoCollect(stack)
                ? "tooltip.venusmod.storage_box.auto_on" : "tooltip.venusmod.storage_box.auto_off"));
        lines.add(Component.translatable("tooltip.venusmod.storage_box.keys"));
    }
}
