package dev.ssscfw.venusmod.compat.create;

import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Automatically repairs one SlashBlade durability point per 20 units of kinetic work. */
public final class BladeRepairStationBlockEntity extends AbstractBladeMachineBlockEntity {
    private static final int REPAIR_WORK = 20;

    public BladeRepairStationBlockEntity(BlockPos pos, BlockState state) {
        super(VenusCreateCompat.BLADE_REPAIR_STATION_BE.get(), pos, state, 1);
    }

    @Override
    protected int getWorkDuration() {
        return REPAIR_WORK;
    }

    @Override
    protected boolean canProcessBlade(ItemStack blade) {
        return SlashBladeEnchantmentCompat.isBlade(blade)
                && SlashBladeEnchantmentCompat.needsBladeRepair(blade);
    }

    @Override
    protected boolean outputsReady(ItemStack blade) {
        return outputInv.getStackInSlot(0).isEmpty();
    }

    @Override
    protected void processBlade(ItemStack blade) {
        if (!SlashBladeEnchantmentCompat.needsBladeRepair(blade)) {
            moveInputToOutput(0);
            return;
        }

        if (!SlashBladeEnchantmentCompat.repairBladeOnePoint(blade)) {
            // Reflection/API mismatch: do not consume or silently move a damaged blade.
            timer = 0;
            return;
        }

        if (!SlashBladeEnchantmentCompat.needsBladeRepair(blade)) {
            moveInputToOutput(0);
        }
    }
}
