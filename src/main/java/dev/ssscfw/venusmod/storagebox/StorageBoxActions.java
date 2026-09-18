package dev.ssscfw.venusmod.storagebox;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/** キー操作とブロックInventory排出のサーバー側実処理。 */
public final class StorageBoxActions {
    private StorageBoxActions() {}

    public static int insertAll(ServerPlayer player, ItemStack box) {
        if (!valid(player, box)) return 0;
        Inventory inventory = player.getInventory();
        int moved = 0;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (slot == inventory.selected) continue;
            ItemStack candidate = inventory.items.get(slot);
            if (candidate.isEmpty() || !StorageBoxData.canAccept(box, candidate)) continue;
            int accepted = StorageBoxData.insert(box, candidate, candidate.getCount());
            if (accepted <= 0) continue;
            candidate.shrink(accepted);
            if (candidate.isEmpty()) inventory.items.set(slot, ItemStack.EMPTY);
            moved += accepted;
            if (StorageBoxData.storedCount(box) == StorageBoxRules.CAPACITY) break;
        }
        if (moved > 0) sync(player);
        return moved;
    }

    public static int extractToInventory(ServerPlayer player, ItemStack box) {
        if (!valid(player, box)) return 0;
        ItemStack extracted = StorageBoxData.extract(box, Integer.MAX_VALUE);
        if (extracted.isEmpty()) return 0;
        int original = extracted.getCount();
        player.getInventory().add(extracted);
        int inserted = original - extracted.getCount();
        if (!extracted.isEmpty()) StorageBoxData.insert(box, extracted, extracted.getCount());
        if (inserted > 0) sync(player);
        return inserted;
    }

    public static int dropStack(ServerPlayer player, ItemStack box) {
        if (!valid(player, box)) return 0;
        ItemStack extracted = StorageBoxData.extract(box, Integer.MAX_VALUE);
        if (extracted.isEmpty()) return 0;
        int count = extracted.getCount();
        ItemEntity dropped = player.drop(extracted, false);
        if (dropped == null) {
            StorageBoxData.insert(box, extracted, extracted.getCount());
            return 0;
        }
        dropped.setNoPickUpDelay();
        dropped.setTarget(player.getUUID());
        sync(player);
        return count;
    }

    public static boolean toggleAutoCollect(ServerPlayer player, ItemStack box) {
        if (!valid(player, box)) return false;
        boolean enabled = StorageBoxData.toggleAutoCollect(box);
        sync(player);
        return enabled;
    }

    /**
     * @return -1=対象Inventoryなし、0以上=実際に移動した個数。
     */
    public static int dumpIntoBlock(ServerPlayer player, ItemStack box, BlockPos pos, Direction side) {
        if (!valid(player, box) || StorageBoxData.storedCount(box) <= 0) return -1;
        IItemHandler handler = player.serverLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        if (handler == null) return -1;

        int moved = 0;
        while (StorageBoxData.storedCount(box) > 0) {
            ItemStack extracted = StorageBoxData.extract(box, Integer.MAX_VALUE);
            if (extracted.isEmpty()) break;
            int before = extracted.getCount();
            ItemStack remainder = extracted;
            for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
                remainder = handler.insertItem(slot, remainder, false);
            }
            int inserted = before - remainder.getCount();
            moved += inserted;
            if (!remainder.isEmpty()) StorageBoxData.insert(box, remainder, remainder.getCount());
            if (inserted <= 0) break;
        }
        if (moved > 0) sync(player);
        return moved;
    }

    public static boolean hasBlockInventory(Level level, BlockPos pos, Direction side) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side) != null;
    }

    static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            ItemEntity dropped = player.drop(stack, false);
            if (dropped != null) {
                dropped.setNoPickUpDelay();
                dropped.setTarget(player.getUUID());
            }
        }
    }

    private static boolean valid(ServerPlayer player, ItemStack box) {
        return player != null && player.isAlive() && !player.isSpectator()
                && box != null && !box.isEmpty()
                && player.getMainHandItem() == box;
    }

    private static void sync(ServerPlayer player) {
        player.getInventory().setChanged();
        if (player.containerMenu instanceof StorageBoxMenu menu) menu.refresh();
        player.containerMenu.broadcastChanges();
    }
}
