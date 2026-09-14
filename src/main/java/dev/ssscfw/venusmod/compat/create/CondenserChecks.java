package dev.ssscfw.venusmod.compat.create;

import dev.ssscfw.venusmod.registry.VenusPhase2;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class CondenserChecks {
    private CondenserChecks() {}
    public static void verify(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var state = VenusAtmosphereMachines.CONDENSER.get().defaultBlockState();
        var machine = new AtmosphericCondenserBlockEntity(BlockPos.ZERO, state);
        var initial = new FluidTank(8000);
        initial.fill(new FluidStack(VenusPhase2.ACID.get(), 1500), FluidAction.EXECUTE);
        var tag = new CompoundTag();
        tag.put("CondenserTank", initial.writeToNBT(registries, new CompoundTag()));
        tag.putDouble("CondenserProgress", 40);
        machine.read(tag, registries, false);
        var output = machine.output();
        helper.assertTrue(output.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 0, "Condenser accepted foreign fluid");
        helper.assertTrue(output.fill(new FluidStack(VenusPhase2.ACID.get(), 1000), FluidAction.EXECUTE) == 0, "Condenser input must be disabled");
        output.getFluidInTank(0).setAmount(1);
        helper.assertTrue(output.getFluidInTank(0).getAmount() == 1500, "Capability leaked mutable tank reference");
        helper.assertTrue(output.drain(1000, FluidAction.SIMULATE).getAmount() == 1000, "Drain simulation returned wrong amount");
        helper.assertTrue(output.getFluidInTank(0).getAmount() == 1500, "Simulation changed tank");
        helper.assertTrue(output.drain(1000, FluidAction.EXECUTE).getAmount() == 1000, "Actual drain failed");
        var saved = new CompoundTag(); machine.write(saved, registries, false);
        var loaded = new AtmosphericCondenserBlockEntity(BlockPos.ZERO, state); loaded.read(saved, registries, false);
        helper.assertTrue(loaded.output().getFluidInTank(0).getAmount() == 500, "Tank NBT roundtrip failed");
        helper.assertTrue(saved.getDouble("CondenserProgress") == 40, "Progress NBT roundtrip failed");
        var invalid = new FluidTank(8000); invalid.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        tag.put("CondenserTank", invalid.writeToNBT(registries, new CompoundTag()));
        tag.putDouble("CondenserProgress", Double.NaN); loaded.read(tag, registries, false);
        helper.assertTrue(loaded.output().getFluidInTank(0).isEmpty(), "Invalid saved fluid was retained");
        helper.succeed();
    }
}
