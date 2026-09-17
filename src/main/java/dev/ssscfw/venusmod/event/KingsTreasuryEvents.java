package dev.ssscfw.venusmod.event;

import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import dev.ssscfw.venusmod.item.KingTreasuryItem;
import dev.ssscfw.venusmod.treasury.KingsTreasuryMenu;
import dev.ssscfw.venusmod.treasury.KingsTreasurySavedData;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** 王の宝物庫への自動回収。地面拾得と/inventoryへの直接付与の両方をSavedDataへ確定保存する。 */
public final class KingsTreasuryEvents {
    private KingsTreasuryEvents() {}

    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || event.canPickup() == TriState.FALSE) return;

        ItemEntity itemEntity = event.getItemEntity();
        if (itemEntity.hasPickUpDelay()) return;
        UUID target = itemEntity.getTarget();
        if (target != null && !target.equals(player.getUUID())) return;

        ItemStack liveStack = itemEntity.getItem();
        if (liveStack.isEmpty() || !SlashBladeEnchantmentCompat.isBlade(liveStack)) return;
        if (!KingTreasuryItem.hasTreasury(player)) return;

        KingsTreasurySavedData storage = KingsTreasurySavedData.get(player.serverLevel());
        if (!storage.autoCollect(player.getUUID())) return;

        int accepted = storage.insert(player.getUUID(), liveStack, liveStack.getCount());
        if (accepted <= 0) return;

        player.take(itemEntity, accepted);
        liveStack.shrink(accepted);
        if (liveStack.isEmpty()) itemEntity.discard();

        event.setCanPickup(TriState.FALSE);
        if (player.containerMenu instanceof KingsTreasuryMenu menu) menu.refreshFromStorage();
    }

    /**
     * /giveなどItemEntityPickupEventを通らず直接Inventoryへ入った刀も、
     * 宝物庫を所持かつ自動回収ONなら次のサーバーtickで収納する。
     */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!KingTreasuryItem.hasTreasury(player)) return;

        KingsTreasurySavedData storage = KingsTreasurySavedData.get(player.serverLevel());
        if (!storage.autoCollect(player.getUUID())) return;

        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !SlashBladeEnchantmentCompat.isBlade(stack)) continue;

            int accepted = storage.insert(player.getUUID(), stack, stack.getCount());
            if (accepted <= 0) continue;

            stack.shrink(accepted);
            if (stack.isEmpty()) player.getInventory().setItem(slot, ItemStack.EMPTY);
            changed = true;
        }

        if (!changed) return;
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        if (player.containerMenu instanceof KingsTreasuryMenu menu) menu.refreshFromStorage();
    }
}
