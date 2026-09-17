package dev.ssscfw.venusmod.treasury;

import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import dev.ssscfw.venusmod.item.KingTreasuryItem;
import dev.ssscfw.venusmod.registry.ModMenus;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 王の宝物庫の54枠ページ式メニュー。宝物庫側のSlotは表示専用で、入出庫はサーバー側のクリック処理がSavedDataへ反映する。
 */
public final class KingsTreasuryMenu extends AbstractContainerMenu {
    public static final int TREASURY_SLOTS = TreasuryRules.PAGE_SIZE;
    public static final int BUTTON_PREVIOUS = 0;
    public static final int BUTTON_NEXT = 1;

    private static final int DATA_COUNTS_START = 0;
    private static final int DATA_PAGE = TREASURY_SLOTS;
    private static final int DATA_PAGE_COUNT = TREASURY_SLOTS + 1;
    private static final int DATA_TOTAL = TREASURY_SLOTS + 2;
    private static final int DATA_AUTO = TREASURY_SLOTS + 3;
    private static final int DATA_SIZE = TREASURY_SLOTS + 4;

    private final SimpleContainer display = new SimpleContainer(TREASURY_SLOTS);
    private final ContainerData data = new SimpleContainerData(DATA_SIZE);
    @Nullable private final KingsTreasurySavedData storage;
    @Nullable private final UUID ownerId;

    /** クライアント用。同期済みの表示コンテナとDataSlotだけを使う。 */
    public KingsTreasuryMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null);
    }

    /** サーバー用。 */
    public KingsTreasuryMenu(int containerId, Inventory inventory, @Nullable ServerPlayer owner) {
        super(ModMenus.KING_TREASURY.get(), containerId);
        this.storage = owner == null ? null : KingsTreasurySavedData.get(owner.serverLevel());
        this.ownerId = owner == null ? null : owner.getUUID();

        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 9; column++) {
                int slotIndex = column + row * 9;
                addSlot(new Slot(display, slotIndex, 8 + column * 18, 18 + row * 18) {
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                    @Override public boolean mayPickup(Player player) { return false; }
                });
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 140 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 198));
        }

        addDataSlots(data);
        if (storage != null) refreshFromStorage();
    }

    public int getLogicalCount(int treasurySlot) {
        return treasurySlot >= 0 && treasurySlot < TREASURY_SLOTS ? data.get(DATA_COUNTS_START + treasurySlot) : 0;
    }

    public int getPage() { return Math.max(0, data.get(DATA_PAGE)); }
    public int getPageCount() { return Math.max(1, data.get(DATA_PAGE_COUNT)); }
    public int getTotalCount() { return Math.max(0, data.get(DATA_TOTAL)); }
    public boolean isAutoCollectEnabled() { return data.get(DATA_AUTO) != 0; }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (storage == null || ownerId == null) return false;
        int page = getPage();
        if (id == BUTTON_PREVIOUS && page > 0) {
            data.set(DATA_PAGE, page - 1);
            refreshFromStorage();
            return true;
        }
        if (id == BUTTON_NEXT && page + 1 < getPageCount()) {
            data.set(DATA_PAGE, page + 1);
            refreshFromStorage();
            return true;
        }
        return false;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < TREASURY_SLOTS) {
            if (player.level().isClientSide || storage == null || ownerId == null) return;
            if (clickType == ClickType.QUICK_MOVE) {
                quickMoveStack(player, slotId);
                return;
            }
            if (clickType != ClickType.PICKUP || button != 0) return;

            ItemStack carried = getCarried();
            if (!carried.isEmpty()) {
                if (!SlashBladeEnchantmentCompat.isBlade(carried)) return;
                int accepted = storage.insert(ownerId, carried, carried.getCount());
                if (accepted > 0) {
                    carried.shrink(accepted);
                    setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                    refreshFromStorage();
                }
                return;
            }

            int globalIndex = getPage() * TREASURY_SLOTS + slotId;
            ItemStack extracted = storage.extractOne(ownerId, globalIndex);
            if (!extracted.isEmpty()) {
                setCarried(extracted);
                refreshFromStorage();
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotId) {
        if (slotId < 0 || slotId >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(slotId);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        if (slotId < TREASURY_SLOTS) {
            if (storage == null || ownerId == null || player.level().isClientSide) return ItemStack.EMPTY;
            int globalIndex = getPage() * TREASURY_SLOTS + slotId;
            ItemStack extracted = storage.extractOne(ownerId, globalIndex);
            if (extracted.isEmpty()) return ItemStack.EMPTY;
            ItemStack result = extracted.copy();
            if (!player.getInventory().add(extracted)) {
                storage.insert(ownerId, result, 1);
                refreshFromStorage();
                return ItemStack.EMPTY;
            }
            refreshFromStorage();
            return result;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (SlashBladeEnchantmentCompat.isBlade(stack) && storage != null && ownerId != null && !player.level().isClientSide) {
            int accepted = storage.insert(ownerId, stack, stack.getCount());
            if (accepted > 0) {
                stack.shrink(accepted);
                if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
                else slot.setChanged();
                refreshFromStorage();
                return original;
            }
            return ItemStack.EMPTY;
        }

        int playerStart = TREASURY_SLOTS;
        int hotbarStart = playerStart + 27;
        int playerEnd = hotbarStart + 9;
        if (slotId < hotbarStart) {
            if (!moveItemStackTo(stack, hotbarStart, playerEnd, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, playerStart, hotbarStart, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive() && KingTreasuryItem.hasTreasury(player);
    }

    public void refreshFromStorage() {
        if (storage == null || ownerId == null) return;
        int pageCount = storage.pageCount(ownerId, TREASURY_SLOTS);
        int page = Math.min(getPage(), pageCount - 1);
        data.set(DATA_PAGE, page);
        data.set(DATA_PAGE_COUNT, pageCount);
        data.set(DATA_TOTAL, storage.totalCount(ownerId));
        data.set(DATA_AUTO, storage.autoCollect(ownerId) ? 1 : 0);

        List<TreasuryEntry> entries = storage.page(ownerId, page, TREASURY_SLOTS);
        display.clearContent();
        for (int i = 0; i < TREASURY_SLOTS; i++) {
            if (i < entries.size()) {
                TreasuryEntry entry = entries.get(i);
                display.setItem(i, entry.template());
                data.set(DATA_COUNTS_START + i, entry.count());
            } else {
                data.set(DATA_COUNTS_START + i, 0);
            }
        }
        broadcastChanges();
    }
}
