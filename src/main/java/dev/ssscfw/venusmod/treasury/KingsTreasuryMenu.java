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

/** 54枠ページ。保護状態は刀自身に保存し、スロットの色とともに同期する。 */
public final class KingsTreasuryMenu extends AbstractContainerMenu {
    public static final int TREASURY_SLOTS = TreasuryRules.PAGE_SIZE;
    public static final int IMAGE_HEIGHT = 236;
    public static final int BUTTON_PREVIOUS = 0;
    public static final int BUTTON_NEXT = 1;
    public static final int BUTTON_PRIORITY_START = 2;
    private static final int DATA_COUNTS_START = 0;
    private static final int DATA_PAGE = TREASURY_SLOTS;
    private static final int DATA_PAGE_COUNT = TREASURY_SLOTS + 1;
    private static final int DATA_TOTAL = TREASURY_SLOTS + 2;
    private static final int DATA_AUTO = TREASURY_SLOTS + 3;
    private static final int DATA_FAVORITES_START = TREASURY_SLOTS + 4;
    private static final int DATA_PRIORITY = DATA_FAVORITES_START + TREASURY_SLOTS;
    private static final int DATA_SIZE = DATA_PRIORITY + 1;
    private final SimpleContainer display = new SimpleContainer(TREASURY_SLOTS);
    private final ContainerData data = new SimpleContainerData(DATA_SIZE);
    @Nullable private final KingsTreasurySavedData storage;
    @Nullable private final UUID ownerId;

    public KingsTreasuryMenu(int containerId, Inventory inventory) { this(containerId, inventory, null); }
    public KingsTreasuryMenu(int containerId, Inventory inventory, @Nullable ServerPlayer owner) {
        super(ModMenus.KING_TREASURY.get(), containerId);
        storage = owner == null ? null : KingsTreasurySavedData.get(owner.serverLevel());
        ownerId = owner == null ? null : owner.getUUID();
        for (int row = 0; row < 6; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(display, column + row * 9, 8 + column * 18, 32 + row * 18) {
                // 仮想ItemStackをバニラのドラッグ/ダブルクリックで複製しない。
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
            });
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 154 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 212));
        addDataSlots(data);
        if (storage != null) refreshFromStorage();
    }
    public int getLogicalCount(int slot) { return slot >= 0 && slot < TREASURY_SLOTS ? data.get(DATA_COUNTS_START + slot) : 0; }
    public boolean isFavorite(int slot) { return slot >= 0 && slot < TREASURY_SLOTS && data.get(DATA_FAVORITES_START + slot) == 1; }
    public boolean isPhantasmProtected(int slot) { return slot >= 0 && slot < TREASURY_SLOTS && data.get(DATA_FAVORITES_START + slot) == 2; }
    public VolleyPriority getVolleyPriority() { return VolleyPriority.fromOrdinal(data.get(DATA_PRIORITY)); }
    public int getPage() { return Math.max(0, data.get(DATA_PAGE)); }
    public int getPageCount() { return Math.max(1, data.get(DATA_PAGE_COUNT)); }
    public int getTotalCount() { return Math.max(0, data.get(DATA_TOTAL)); }
    public boolean isAutoCollectEnabled() { return data.get(DATA_AUTO) != 0; }
    private boolean serverOwner(Player player) { return !player.level().isClientSide && storage != null && ownerId != null && ownerId.equals(player.getUUID()) && stillValid(player); }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (!serverOwner(player)) return false;
        int page = getPage();
        if (id == BUTTON_PREVIOUS && page > 0) { data.set(DATA_PAGE, page - 1); refreshFromStorage(); return true; }
        if (id == BUTTON_NEXT && page + 1 < getPageCount()) { data.set(DATA_PAGE, page + 1); refreshFromStorage(); return true; }
        int priority = id - BUTTON_PRIORITY_START;
        if (priority >= 0 && priority < VolleyPriority.values().length) {
            storage.setVolleyPriority(ownerId, VolleyPriority.fromOrdinal(priority));
            refreshFromStorage();
            return true;
        }
        return false;
    }
    @Override public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < TREASURY_SLOTS) {
            if (!serverOwner(player)) return;
            if (clickType == ClickType.PICKUP && button == 1) {
                if (getCarried().isEmpty()) {
                    storage.cycleProtection(ownerId, getPage() * TREASURY_SLOTS + slotId, display.getItem(slotId));
                    refreshFromStorage();
                }
                return;
            }
            if (clickType == ClickType.QUICK_MOVE) { quickMoveStack(player, slotId); return; }
            if (clickType != ClickType.PICKUP || button != 0) return;
            ItemStack carried = getCarried();
            if (!carried.isEmpty()) {
                if (!SlashBladeEnchantmentCompat.isBlade(carried)) return;
                int accepted = storage.insert(ownerId, carried, carried.getCount());
                if (accepted > 0) { carried.shrink(accepted); setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried); refreshFromStorage(); }
                return;
            }
            ItemStack extracted = storage.extractOne(ownerId, getPage() * TREASURY_SLOTS + slotId);
            if (!extracted.isEmpty()) { setCarried(extracted); refreshFromStorage(); }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }
    @Override public ItemStack quickMoveStack(Player player, int slotId) {
        if (slotId < 0 || slotId >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(slotId);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        if (slotId < TREASURY_SLOTS) {
            if (!serverOwner(player)) return ItemStack.EMPTY;
            ItemStack extracted = storage.extractOne(ownerId, getPage() * TREASURY_SLOTS + slotId);
            if (extracted.isEmpty()) return ItemStack.EMPTY;
            ItemStack result = extracted.copy();
            if (!player.getInventory().add(extracted)) {
                storage.restoreOne(ownerId, extracted);
                refreshFromStorage();
                return ItemStack.EMPTY;
            }
            refreshFromStorage();
            return result;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (SlashBladeEnchantmentCompat.isBlade(stack)) {
            if (!serverOwner(player)) return ItemStack.EMPTY;
            int accepted = storage.insert(ownerId, stack, stack.getCount());
            if (accepted > 0) {
                stack.shrink(accepted);
                if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
                refreshFromStorage();
                return original;
            }
            return ItemStack.EMPTY;
        }
        int playerStart = TREASURY_SLOTS, hotbarStart = playerStart + 27, playerEnd = hotbarStart + 9;
        if (slotId < hotbarStart) { if (!moveItemStackTo(stack, hotbarStart, playerEnd, false)) return ItemStack.EMPTY; }
        else if (!moveItemStackTo(stack, playerStart, hotbarStart, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }
    @Override public boolean stillValid(Player player) { return player.isAlive() && KingTreasuryItem.hasTreasury(player); }
    public void refreshFromStorage() {
        if (storage == null || ownerId == null) return;
        int pageCount = storage.pageCount(ownerId, TREASURY_SLOTS);
        int page = Math.min(getPage(), pageCount - 1);
        data.set(DATA_PAGE, page);
        data.set(DATA_PAGE_COUNT, pageCount);
        data.set(DATA_TOTAL, storage.totalCount(ownerId));
        data.set(DATA_AUTO, storage.autoCollect(ownerId) ? 1 : 0);
        data.set(DATA_PRIORITY, storage.volleyPriority(ownerId).ordinal());
        List<TreasuryEntry> entries = storage.page(ownerId, page, TREASURY_SLOTS);
        display.clearContent();
        for (int i = 0; i < TREASURY_SLOTS; i++) {
            if (i < entries.size()) {
                TreasuryEntry entry = entries.get(i);
                display.setItem(i, entry.template());
                data.set(DATA_COUNTS_START + i, entry.count());
                data.set(DATA_FAVORITES_START + i, entry.favorite() ? 1 : entry.phantasmProtected() ? 2 : 0);
            } else { data.set(DATA_COUNTS_START + i, 0); data.set(DATA_FAVORITES_START + i, 0); }
        }
        broadcastChanges();
    }
}
