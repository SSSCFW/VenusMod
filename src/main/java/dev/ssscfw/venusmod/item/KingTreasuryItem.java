package dev.ssscfw.venusmod.item;

import dev.ssscfw.venusmod.registry.ModItems;
import dev.ssscfw.venusmod.treasury.KingsTreasuryMenu;
import dev.ssscfw.venusmod.treasure.KingsTreasure;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/** プレイヤー固有の抜刀剣ストレージを開く鍵。アイテム自体には刀を保存しない。 */
public final class KingTreasuryItem extends Item {
    public KingTreasuryItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        if (!ModList.get().isLoaded("slashblade")) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.venusmod.king_treasury_requires_slashblade"), true);
            }
            return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
        }

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                int closed = KingsTreasure.closePrepared(serverPlayer);
                serverPlayer.displayClientMessage(Component.literal(
                        closed > 0
                                ? "王の財宝：展開中の刀 " + closed + " 本を収納しました。"
                                : "王の財宝：展開中の刀はありません。"), true);
            }
            return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, menuPlayer) ->
                            new KingsTreasuryMenu(containerId, inventory, serverPlayer),
                    Component.translatable("container.venusmod.king_treasury")));
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    /** 自動回収は宝物庫を実際に所持している間だけ有効。 */
    public static boolean hasTreasury(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(ModItems.KING_TREASURY.get())) {
                return true;
            }
        }
        return player.getMainHandItem().is(ModItems.KING_TREASURY.get())
                || player.getOffhandItem().is(ModItems.KING_TREASURY.get());
    }
}
