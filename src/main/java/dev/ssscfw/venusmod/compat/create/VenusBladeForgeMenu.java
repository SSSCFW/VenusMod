package dev.ssscfw.venusmod.compat.create;

import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import dev.ssscfw.venusmod.upgrade.BladeUpgradeData;
import dev.ssscfw.venusmod.upgrade.BladeUpgradeRules;
import dev.ssscfw.venusmod.upgrade.BladeUpgradeType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/** 金星刀鍛錬機の1刀スロットGUIと、強化項目選択・実行ボタンのサーバー処理。 */
public final class VenusBladeForgeMenu extends AbstractContainerMenu {
    public static final int BUTTON_PREVIOUS = 0;
    public static final int BUTTON_NEXT = 1;
    public static final int BUTTON_UPGRADE = 2;
    public static final int BLADE_SLOT = 0;

    private static final int DATA_SELECTED = 0;
    private static final int DATA_RPM = 1;
    private static final int DATA_SIZE = 2;

    @Nullable private final VenusBladeForgeBlockEntity forge;
    private final ContainerData data;

    /** クライアント用。実スロットと状態はサーバー同期で上書きされる。 */
    public VenusBladeForgeMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null);
    }

    /** サーバー用。 */
    public VenusBladeForgeMenu(int containerId, Inventory inventory, @Nullable VenusBladeForgeBlockEntity forge) {
        super(VenusCreateCompat.VENUS_BLADE_FORGE_MENU.get(), containerId);
        this.forge = forge;

        IItemHandler bladeHandler = forge == null ? new ItemStackHandler(1) : forge.getBladeHandler();
        addSlot(new SlotItemHandler(bladeHandler, 0, 18, 42) {
            @Override public boolean mayPlace(ItemStack stack) {
                return SlashBladeEnchantmentCompat.isBlade(stack);
            }
            @Override public int getMaxStackSize() { return 1; }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        34 + column * 18, 132 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 34 + column * 18, 190));
        }

        data = forge == null ? new SimpleContainerData(DATA_SIZE) : new ContainerData() {
            @Override public int get(int index) {
                return switch (index) {
                    case DATA_SELECTED -> forge.getSelectedType().ordinal();
                    case DATA_RPM -> Math.round(Math.abs(forge.getSpeed()));
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) {
                // サーバー側データはBlockEntity/BlockStateが正とする。
            }
            @Override public int getCount() { return DATA_SIZE; }
        };
        addDataSlots(data);
    }

    public BladeUpgradeType getSelectedType() {
        BladeUpgradeType[] values = BladeUpgradeType.values();
        int index = Math.max(0, Math.min(values.length - 1, data.get(DATA_SELECTED)));
        return values[index];
    }

    public int getRpm() { return Math.max(0, data.get(DATA_RPM)); }
    public ItemStack getBlade() { return slots.get(BLADE_SLOT).getItem(); }

    public int getCurrentLevel() {
        ItemStack blade = getBlade();
        return SlashBladeEnchantmentCompat.isBlade(blade)
                ? BladeUpgradeData.getLevel(blade, getSelectedType())
                : 0;
    }

    public int getMaxLevel() { return getSelectedType().maxLevel(); }

    @Nullable
    public BladeUpgradeRules.Cost getNextCost() {
        int current = getCurrentLevel();
        return current >= getMaxLevel()
                ? null
                : BladeUpgradeRules.cost(getSelectedType().category(), current + 1);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (forge == null || !(player instanceof ServerPlayer serverPlayer)) return false;
        if (id == BUTTON_PREVIOUS) {
            forge.selectPrevious();
            broadcastChanges();
            return true;
        }
        if (id == BUTTON_NEXT) {
            forge.selectNext();
            broadcastChanges();
            return true;
        }
        if (id == BUTTON_UPGRADE) {
            forge.tryUpgrade(serverPlayer, forge.getSelectedType());
            broadcastChanges();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotId) {
        if (slotId < 0 || slotId >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(slotId);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        final int playerStart = 1;
        final int hotbarStart = 28;
        final int playerEnd = 37;

        if (slotId == BLADE_SLOT) {
            if (!moveItemStackTo(stack, playerStart, playerEnd, true)) return ItemStack.EMPTY;
        } else if (SlashBladeEnchantmentCompat.isBlade(stack)) {
            if (!moveItemStackTo(stack, BLADE_SLOT, BLADE_SLOT + 1, false)) return ItemStack.EMPTY;
        } else if (slotId < hotbarStart) {
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
        if (forge == null) return true;
        if (forge.getLevel() == null || forge.isRemoved()
                || forge.getLevel().getBlockEntity(forge.getBlockPos()) != forge) return false;
        return player.distanceToSqr(
                forge.getBlockPos().getX() + 0.5D,
                forge.getBlockPos().getY() + 0.5D,
                forge.getBlockPos().getZ() + 0.5D) <= 64.0D;
    }
}
