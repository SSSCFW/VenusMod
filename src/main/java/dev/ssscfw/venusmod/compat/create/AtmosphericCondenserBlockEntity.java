package dev.ssscfw.venusmod.compat.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ssscfw.venusmod.registry.VenusPhase2;
import dev.ssscfw.venusmod.world.VenusDimensionContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class AtmosphericCondenserBlockEntity extends KineticBlockEntity {
    private double progress;
    private final FluidTank tank = new FluidTank(CondenserProcess.CAPACITY, stack -> stack.getFluid().isSame(VenusPhase2.ACID.get())) {
        @Override protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) sendData();
        }
    };
    // 自動化APIからは抽出のみ。シミュレーションはタンクを変更せず、スタック参照も公開しない。
    private final IFluidHandler output = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int slot) { return slot == 0 ? tank.getFluid().copy() : FluidStack.EMPTY; }
        @Override public int getTankCapacity(int slot) { return slot == 0 ? tank.getCapacity() : 0; }
        @Override public boolean isFluidValid(int slot, FluidStack stack) { return false; }
        @Override public int fill(FluidStack stack, FluidAction action) { return 0; }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) { return tank.drain(stack, action); }
        @Override public FluidStack drain(int amount, FluidAction action) { return amount > 0 ? tank.drain(amount, action) : FluidStack.EMPTY; }
    };
    public AtmosphericCondenserBlockEntity(BlockPos pos, BlockState state) { super(VenusAtmosphereMachines.CONDENSER_ENTITY.get(), pos, state); }
    public IFluidHandler output() { return output; }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { super.addBehaviours(behaviours); }
    private boolean hasAtmosphere() {
        return level != null && level.dimension().equals(VenusDimensionContent.VENUS) && level.canSeeSky(worldPosition.above());
    }
    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        var step = CondenserProcess.step(progress, getSpeed(), hasAtmosphere(), tank.getSpace());
        if (progress != step.progress()) { progress = step.progress(); setChanged(); }
        if (step.produced() > 0) tank.fill(new FluidStack(VenusPhase2.ACID.get(), step.produced()), IFluidHandler.FluidAction.EXECUTE);
    }
    public Component status() {
        String mode = !hasAtmosphere() ? "machine.venusmod.needs_venus_sky" : Math.abs(getSpeed()) < 16
                ? "machine.venusmod.needs_rotation" : tank.getSpace() < CondenserProcess.BATCH
                ? "machine.venusmod.tank_full" : "machine.venusmod.working";
        return Component.translatable("machine.venusmod.condenser_status", tank.getFluidAmount(), tank.getCapacity(), Component.translatable(mode));
    }
    @Override public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putDouble("CondenserProgress", progress);
        tag.put("CondenserTank", tank.writeToNBT(registries, new CompoundTag()));
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        progress = CondenserProcess.step(tag.getDouble("CondenserProgress"), 0, false, 0).progress();
        tank.readFromNBT(registries, tag.getCompound("CondenserTank"));
        if (!tank.getFluid().isEmpty()) {
            if (!tank.isFluidValid(tank.getFluid())) tank.setFluid(FluidStack.EMPTY);
            else if (tank.getFluidAmount() > tank.getCapacity()) tank.setFluid(tank.getFluid().copyWithAmount(tank.getCapacity()));
        }
    }
}
