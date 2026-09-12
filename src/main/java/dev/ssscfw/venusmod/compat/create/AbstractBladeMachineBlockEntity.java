package dev.ssscfw.venusmod.compat.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/** Shared kinetic/inventory plumbing for both blade machines. */
public abstract class AbstractBladeMachineBlockEntity extends KineticBlockEntity implements Clearable {
    protected final ItemStackHandler inputInv;
    protected final ItemStackHandler outputInv;
    private final IItemHandler capability;
    protected int timer;

    protected AbstractBladeMachineBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState state,
            int outputSlots) {
        super(type, pos, state);

        inputInv = new ItemStackHandler(1) {
            @Override
            protected void onContentsChanged(int slot) {
                timer = 0;
                inventoryChanged();
            }
        };
        outputInv = new ItemStackHandler(outputSlots) {
            @Override
            protected void onContentsChanged(int slot) {
                inventoryChanged();
            }
        };
        capability = new MachineInventoryHandler();
    }

    public IItemHandler getItemHandler() {
        return capability;
    }

    /**
     * Returns the blade that should be visible in the open work chamber.
     * Input takes priority; once processing finishes a surviving blade in output
     * stays visible until automation/player extraction removes it.
     */
    public ItemStack getDisplayedBlade() {
        ItemStack input = inputInv.getStackInSlot(0);
        if (SlashBladeEnchantmentCompat.isBlade(input)) {
            return input;
        }

        for (int slot = 0; slot < outputInv.getSlots(); slot++) {
            ItemStack output = outputInv.getStackInSlot(slot);
            if (SlashBladeEnchantmentCompat.isBlade(output)) {
                return output;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new DirectBeltInputBehaviour(this));
        super.addBehaviours(behaviours);
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide || getSpeed() == 0) {
            return;
        }

        ItemStack blade = inputInv.getStackInSlot(0);
        if (blade.isEmpty()) {
            timer = 0;
            return;
        }

        if (!canProcessBlade(blade)) {
            moveInputToOutput(0);
            return;
        }

        if (!outputsReady(blade)) {
            return;
        }

        if (timer <= 0) {
            timer = getWorkDuration();
            setChanged();
            sendData();
            return;
        }

        timer -= getProcessingSpeed();
        if (timer > 0) {
            return;
        }

        processBlade(blade);
        setChanged();
        sendData();

        ItemStack remaining = inputInv.getStackInSlot(0);
        timer = !remaining.isEmpty() && canProcessBlade(remaining) && outputsReady(remaining)
                ? getWorkDuration()
                : 0;
    }

    /** Same RPM scaling rule used by Create's millstone. */
    protected int getProcessingSpeed() {
        return Mth.clamp((int) Math.abs(getSpeed() / 16.0F), 1, 512);
    }

    protected abstract int getWorkDuration();

    protected abstract boolean canProcessBlade(ItemStack blade);

    protected abstract boolean outputsReady(ItemStack blade);

    protected abstract void processBlade(ItemStack blade);

    protected boolean canInsertBlade(ItemStack stack) {
        return SlashBladeEnchantmentCompat.isBlade(stack);
    }

    protected boolean moveInputToOutput(int outputSlot) {
        ItemStack input = inputInv.getStackInSlot(0);
        if (input.isEmpty() || outputSlot < 0 || outputSlot >= outputInv.getSlots()) {
            return false;
        }

        ItemStack remainder = outputInv.insertItem(outputSlot, input.copy(), false);
        if (!remainder.isEmpty()) {
            return false;
        }

        inputInv.setStackInSlot(0, ItemStack.EMPTY);
        return true;
    }

    public boolean insertBladeFromPlayer(ItemStack held, Player player) {
        if (held.isEmpty()) {
            return false;
        }

        ItemStack one = held.copy();
        one.setCount(1);
        ItemStack remainder = capability.insertItem(0, one, false);
        if (!remainder.isEmpty()) {
            return false;
        }

        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        return true;
    }

    /** Empty-hand interaction takes finished output first, then allows canceling the input. */
    public boolean takeOutputOrInput(Player player) {
        for (int slot = 0; slot < outputInv.getSlots(); slot++) {
            ItemStack inSlot = outputInv.getStackInSlot(slot);
            if (inSlot.isEmpty()) {
                continue;
            }
            ItemStack extracted = outputInv.extractItem(slot, inSlot.getCount(), false);
            player.getInventory().placeItemBackInInventory(extracted);
            return true;
        }

        ItemStack input = inputInv.getStackInSlot(0);
        if (input.isEmpty()) {
            return false;
        }
        inputInv.setStackInSlot(0, ItemStack.EMPTY);
        player.getInventory().placeItemBackInInventory(input);
        timer = 0;
        return true;
    }

    private void inventoryChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            sendData();
        }
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < inputInv.getSlots(); i++) {
            inputInv.setStackInSlot(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < outputInv.getSlots(); i++) {
            outputInv.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        if (level != null) {
            ItemHelper.dropContents(level, worldPosition, inputInv);
            ItemHelper.dropContents(level, worldPosition, outputInv);
        }
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.putInt("Timer", timer);
        compound.put("InputInventory", inputInv.serializeNBT(registries));
        compound.put("OutputInventory", outputInv.serializeNBT(registries));
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        timer = compound.getInt("Timer");
        inputInv.deserializeNBT(registries, compound.getCompound("InputInventory"));
        outputInv.deserializeNBT(registries, compound.getCompound("OutputInventory"));
        super.read(compound, registries, clientPacket);
    }

    /**
     * Stable automation layout:
     * slot 0 = machine input (insert-only)
     * slot 1..N = outputInv slot 0..N-1 (extract-only)
     *
     * Explicit translation avoids forwarding a capability-global slot directly into
     * the smaller child ItemStackHandler when hoppers scan every exposed slot.
     */
    private final class MachineInventoryHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 1 + outputInv.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot == 0) {
                return inputInv.getStackInSlot(0);
            }
            int outputSlot = toOutputSlot(slot);
            return outputSlot >= 0 ? outputInv.getStackInSlot(outputSlot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty() || !canInsertBlade(stack)) {
                return stack;
            }
            return inputInv.insertItem(0, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int outputSlot = toOutputSlot(slot);
            if (outputSlot < 0 || amount <= 0) {
                return ItemStack.EMPTY;
            }
            return outputInv.extractItem(outputSlot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot == 0) {
                return inputInv.getSlotLimit(0);
            }
            int outputSlot = toOutputSlot(slot);
            return outputSlot >= 0 ? outputInv.getSlotLimit(outputSlot) : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && canInsertBlade(stack) && inputInv.isItemValid(0, stack);
        }

        private int toOutputSlot(int globalSlot) {
            if (globalSlot <= 0 || globalSlot >= getSlots()) {
                return -1;
            }
            return globalSlot - 1;
        }
    }
}
