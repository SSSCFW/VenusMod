package dev.ssscfw.venusmod.storagebox;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** Storage Box内の1個を一時的な手持ちとして元Itemの通常処理へ委譲する。 */
public final class StorageBoxUseHandler {
    private StorageBoxUseHandler() {}

    public static boolean supportsContinuousUse(ItemStack template, LivingEntity user) {
        if (template == null || template.isEmpty()) return false;
        UseAnim animation = template.getUseAnimation();
        if (animation != UseAnim.EAT && animation != UseAnim.DRINK) return false;
        return user == null || template.getUseDuration(user) > 0;
    }

    public static InteractionResultHolder<ItemStack> useAir(
            Level level, Player player, InteractionHand hand, ItemStack box) {
        ItemStack template = StorageBoxData.template(box);
        if (template.isEmpty() || template.getUseDuration(player) > 0) {
            return InteractionResultHolder.pass(box);
        }
        ItemStack working = template.copyWithCount(1);
        player.setItemInHand(hand, working);
        InteractionResultHolder<ItemStack> result;
        try {
            result = working.getItem().use(level, player, hand);
        } finally {
            player.setItemInHand(hand, box);
        }
        ItemStack after = result.getObject() == null ? ItemStack.EMPTY : result.getObject().copy();
        if (!level.isClientSide && result.getResult().consumesAction() && player instanceof ServerPlayer serverPlayer) {
            reconcileOne(serverPlayer, box, template, after);
        }
        return new InteractionResultHolder<>(result.getResult(), box);
    }

    public static InteractionResult useOn(UseOnContext original, ItemStack box) {
        Player player = original.getPlayer();
        if (player == null) return InteractionResult.PASS;
        ItemStack template = StorageBoxData.template(box);
        if (template.isEmpty()) return InteractionResult.PASS;
        ItemStack working = template.copyWithCount(1);
        player.setItemInHand(original.getHand(), working);
        InteractionResult result;
        try {
            UseOnContext delegated = new UseOnContext(player, original.getHand(),
                    new BlockHitResult(original.getClickLocation(), original.getClickedFace(),
                            original.getClickedPos(), original.isInside()));
            result = working.getItem().useOn(delegated);
            working = player.getItemInHand(original.getHand()).copy();
        } finally {
            player.setItemInHand(original.getHand(), box);
        }
        if (!original.getLevel().isClientSide && result.consumesAction() && player instanceof ServerPlayer serverPlayer) {
            reconcileOne(serverPlayer, box, template, working);
        }
        return result;
    }

    public static InteractionResult useOnEntity(
            ItemStack box, Player player, LivingEntity target, InteractionHand hand) {
        ItemStack template = StorageBoxData.template(box);
        if (template.isEmpty()) return InteractionResult.PASS;
        ItemStack working = template.copyWithCount(1);
        player.setItemInHand(hand, working);
        InteractionResult result;
        try {
            result = working.getItem().interactLivingEntity(working, player, target, hand);
            working = player.getItemInHand(hand).copy();
        } finally {
            player.setItemInHand(hand, box);
        }
        if (!player.level().isClientSide && result.consumesAction() && player instanceof ServerPlayer serverPlayer) {
            reconcileOne(serverPlayer, box, template, working);
        }
        return result;
    }

    public static ItemStack finishContinuousUse(ItemStack box, Level level, LivingEntity user) {
        ItemStack template = StorageBoxData.template(box);
        if (template.isEmpty() || !supportsContinuousUse(template, user)) return box;
        ItemStack working = template.copyWithCount(1);
        ItemStack result = working.finishUsingItem(level, user);
        if (!level.isClientSide && user instanceof ServerPlayer player) {
            if (result.isEmpty() || !ItemStack.isSameItemSameComponents(template, result)) {
                StorageBoxData.consume(box, 1);
                if (!result.isEmpty()) StorageBoxActions.giveOrDrop(player, result);
            }
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
        return box;
    }

    private static void reconcileOne(ServerPlayer player, ItemStack box, ItemStack template, ItemStack after) {
        if (after == null || after.isEmpty()) {
            StorageBoxData.consume(box, 1);
        } else if (!ItemStack.isSameItemSameComponents(template, after)) {
            StorageBoxData.consume(box, 1);
            StorageBoxActions.giveOrDrop(player, after);
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }
}
