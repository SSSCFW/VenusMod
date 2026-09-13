package dev.ssscfw.venusmod.compat.create;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import dev.ssscfw.venusmod.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/**
 * Converts one iron nugget per cycle into nickel (30%), copper (35%), or nothing (35%).
 */
public final class MetalSeparatorBlockEntity extends KineticBlockEntity implements Clearable {
    private static final int PROCESS_WORK = 20;
    private static final float NICKEL_CHANCE = 0.30F;
    private static final float COPPER_CUMULATIVE_CHANCE = 0.65F;

    private final ItemStackHandler inputInv;
    private final ItemStackHandler outputInv;
    private final IItemHandler capability;
    private int timer;

    public MetalSeparatorBlockEntity(BlockPos pos, BlockState state) {
        super(VenusCreateCompat.METAL_SEPARATOR_BE.get(), pos, state);

        inputInv = new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return stack.is(Items.IRON_NUGGET);
            }

            @Override
            protected void onContentsChanged(int slot) {
                timer = 0;
                inventoryChanged();
            }
        };

        outputInv = new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return false;
            }

            @Override
            protected void onContentsChanged(int slot) {
                inventoryChanged();
            }
        };

        capability = new SeparatorInventoryHandler();
    }

    public IItemHandler getItemHandler() {
        return capability;
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

        ItemStack input = inputInv.getStackInSlot(0);
        if (input.isEmpty() || !input.is(Items.IRON_NUGGET)) {
            timer = 0;
            return;
        }

        if (!outputInv.getStackInSlot(0).isEmpty()) {
            return;
        }

        if (timer <= 0) {
            timer = PROCESS_WORK;
            setChanged();
            sendData();
            return;
        }

        timer -= getProcessingSpeed();
        if (timer > 0) {
            return;
        }

        inputInv.extractItem(0, 1, false);

        float roll = level.getRandom().nextFloat();
        if (roll < NICKEL_CHANCE) {
            outputInv.setStackInSlot(0, new ItemStack(ModItems.NICKEL_NUGGET.get()));
        } else if (roll < COPPER_CUMULATIVE_CHANCE) {
            outputInv.setStackInSlot(0, new ItemStack(AllItems.COPPER_NUGGET.get()));
        }

        setChanged();
        sendData();

        ItemStack remaining = inputInv.getStackInSlot(0);
        timer = outputInv.getStackInSlot(0).isEmpty()
                && !remaining.isEmpty()
                && remaining.is(Items.IRON_NUGGET)
                ? PROCESS_WORK
                : 0;
    }

    private int getProcessingSpeed() {
        return Mth.clamp((int) Math.abs(getSpeed() / 16.0F), 1, 512);
    }

    public boolean insertIronNuggetFromPlayer(ItemStack held, Player player) {
        if (held.isEmpty() || !held.is(Items.IRON_NUGGET)) {
            return false;
        }

        ItemStack one = held.copy();
        one.setCount(1);
        ItemStack remainder = inputInv.insertItem(0, one, false);
        if (!remainder.isEmpty()) {
            return false;
        }

        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        return true;
    }

    public boolean takeOutputOrInput(Player player) {
        ItemStack output = outputInv.getStackInSlot(0);
        if (!output.isEmpty()) {
            ItemStack extracted = outputInv.extractItem(0, output.getCount(), false);
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
        inputInv.setStackInSlot(0, ItemStack.EMPTY);
        outputInv.setStackInSlot(0, ItemStack.EMPTY);
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

    /** Slot 0 is insert-only input, slot 1 is extract-only output. */
    private final class SeparatorInventoryHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return switch (slot) {
                case 0 -> inputInv.getStackInSlot(0);
                case 1 -> outputInv.getStackInSlot(0);
                default -> ItemStack.EMPTY;
            };
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty() || !stack.is(Items.IRON_NUGGET)) {
                return stack;
            }
            return inputInv.insertItem(0, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 1 && amount > 0
                    ? outputInv.extractItem(0, amount, simulate)
                    : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return switch (slot) {
                case 0 -> inputInv.getSlotLimit(0);
                case 1 -> outputInv.getSlotLimit(0);
                default -> 0;
            };
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && stack.is(Items.IRON_NUGGET);
        }
    }
}
