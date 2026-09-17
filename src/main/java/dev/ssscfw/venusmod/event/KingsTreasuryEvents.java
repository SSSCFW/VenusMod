package dev.ssscfw.venusmod.event;

import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import dev.ssscfw.venusmod.item.KingTreasuryItem;
import dev.ssscfw.venusmod.treasury.KingsTreasuryMenu;
import dev.ssscfw.venusmod.treasury.KingsTreasurySavedData;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/** 王の宝物庫への自動回収。通常インベントリへ入る前にSavedDataへ確定保存する。 */
public final class KingsTreasuryEvents {
    private KingsTreasuryEvents() {}

    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || event.canPickup().isFalse()) return;

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

        // 今回処理した分をバニラInventoryへ二重投入させない。部分格納時も残りは地面に維持する。
        event.setCanPickup(TriState.FALSE);
        if (player.containerMenu instanceof KingsTreasuryMenu menu) menu.refreshFromStorage();
    }
}
