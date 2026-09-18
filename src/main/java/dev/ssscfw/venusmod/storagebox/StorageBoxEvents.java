package dev.ssscfw.venusmod.storagebox;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/** 登録済みテンプレートに一致する地面アイテムだけを自動回収する。空箱は初期化しない。 */
public final class StorageBoxEvents {
    private StorageBoxEvents() {}

    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || event.canPickup() == TriState.FALSE) return;
        ItemEntity entity = event.getItemEntity();
        if (entity.hasPickUpDelay()) return;
        if (entity.getTarget() != null && !entity.getTarget().equals(player.getUUID())) return;
        ItemStack live = entity.getItem();
        if (live.isEmpty()) return;

        Inventory inventory = player.getInventory();
        int moved = 0;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE && !live.isEmpty(); slot++) {
            ItemStack box = inventory.items.get(slot);
            if (!(box.getItem() instanceof StorageBoxItem)
                    || !StorageBoxData.autoCollect(box)
                    || StorageBoxData.template(box).isEmpty()
                    || !StorageBoxData.canAccept(box, live)) continue;
            int accepted = StorageBoxData.insert(box, live, live.getCount());
            if (accepted <= 0) continue;
            live.shrink(accepted);
            moved += accepted;
        }

        if (moved <= 0) return;
        player.take(entity, moved);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        if (live.isEmpty()) {
            entity.discard();
            event.setCanPickup(TriState.FALSE);
        }
    }
}
