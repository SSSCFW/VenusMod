package dev.ssscfw.venusmod.storagebox;

import dev.ssscfw.venusmod.registry.ModMenus;
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

/** Storage Boxの仮想IN/OUT 2枠とプレイヤーInventoryを同期する。 */
public final class StorageBoxMenu extends AbstractContainerMenu {
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int BUTTON_AUTO_COLLECT = 0;
    private static final int DATA_COUNT = 0;
    private static final int DATA_AUTO = 1;
    private static final int DATA_SIZE = 2;

    private final SimpleContainer display = new SimpleContainer(2);
    private final ContainerData data = new SimpleContainerData(DATA_SIZE);
    private final Player owner;
    private final ItemStack box;

    public StorageBoxMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, inventory.player.getMainHandItem());
    }

    public StorageBoxMenu(int containerId, Inventory inventory, ItemStack box) {
        super(ModMenus.STORAGE_BOX.get(), containerId);
        this.owner = inventory.player;
        this.box = box;

        addSlot(new Slot(display, INPUT_SLOT, 44, 45) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        });
        addSlot(new Slot(display, OUTPUT_SLOT, 116, 45) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        });

        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 104 + row * 18));
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 162));
        }
        addDataSlots(data);
        refresh();
    }

    public int storedCount() { return Math.max(0, data.get(DATA_COUNT)); }
    public boolean autoCollect() { return data.get(DATA_AUTO) != 0; }
    public ItemStack template() { return display.getItem(OUTPUT_SLOT).copyWithCount(1); }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!serverOwner(player) || id != BUTTON_AUTO_COLLECT) return false;
        StorageBoxData.toggleAutoCollect(box);
        refresh();
        return true;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == INPUT_SLOT || slotId == OUTPUT_SLOT) {
            if (!serverOwner(player)) return;
            if (clickType == ClickType.QUICK_MOVE && slotId == OUTPUT_SLOT) {
                quickMoveStack(player, slotId);
                return;
            }
            if (clickType != ClickType.PICKUP || button != 0) return;
            if (slotId == INPUT_SLOT) {
                ItemStack carried = getCarried();
                if (carried.isEmpty()) return;
                int accepted = StorageBoxData.insert(box, carried, carried.getCount());
                if (accepted > 0) {
                    carried.shrink(accepted);
                    setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                    refresh();
                }
                return;
            }

            ItemStack current = getCarried();
            ItemStack template = StorageBoxData.template(box);
            if (template.isEmpty()) return;
            int room;
            if (current.isEmpty()) {
                room = template.getMaxStackSize();
            } else {
                if (!ItemStack.isSameItemSameComponents(current, template)) return;
                room = Math.max(0, current.getMaxStackSize() - current.getCount());
            }
            ItemStack extracted = StorageBoxData.extract(box, room);
            if (extracted.isEmpty()) return;
            if (current.isEmpty()) setCarried(extracted);
            else current.grow(extracted.getCount());
            refresh();
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotId) {
        if (!serverOwner(player) || slotId < 0 || slotId >= slots.size()) return ItemStack.EMPTY;
        if (slotId == INPUT_SLOT) return ItemStack.EMPTY;
        if (slotId == OUTPUT_SLOT) {
            ItemStack extracted = StorageBoxData.extract(box, Integer.MAX_VALUE);
            if (extracted.isEmpty()) return ItemStack.EMPTY;
            ItemStack result = extracted.copy();
            player.getInventory().add(extracted);
            if (!extracted.isEmpty()) StorageBoxData.insert(box, extracted, extracted.getCount());
            refresh();
            return result;
        }

        Slot slot = slots.get(slotId);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int accepted = StorageBoxData.insert(box, stack, stack.getCount());
        if (accepted <= 0) return ItemStack.EMPTY;
        stack.shrink(accepted);
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        refresh();
        return original;
    }

    private boolean serverOwner(Player player) {
        return !player.level().isClientSide && player == owner && stillValid(player);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive() && player.getMainHandItem() == box;
    }

    public void refresh() {
        ItemStack template = StorageBoxData.template(box);
        int count = StorageBoxData.storedCount(box);
        data.set(DATA_COUNT, count);
        data.set(DATA_AUTO, StorageBoxData.autoCollect(box) ? 1 : 0);
        display.setItem(INPUT_SLOT, ItemStack.EMPTY);
        if (template.isEmpty()) {
            display.setItem(OUTPUT_SLOT, ItemStack.EMPTY);
        } else {
            display.setItem(OUTPUT_SLOT, template.copyWithCount(Math.min(count, template.getMaxStackSize())));
        }
        broadcastChanges();
    }
}
