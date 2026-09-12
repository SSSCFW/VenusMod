package dev.ssscfw.venusmod.compat.create;

import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Automatically repairs one durability point per 20 units of kinetic work. */
public final class BladeRepairStationBlockEntity extends AbstractBladeMachineBlockEntity {
    private static final int REPAIR_WORK = 20;

    public BladeRepairStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 1);
    }

    @Override
    protected int getWorkDuration() {
        return REPAIR_WORK;
    }

    @Override
    protected boolean canProcessBlade(ItemStack blade) {
        return SlashBladeEnchantmentCompat.isBlade(blade) && blade.getDamageValue() > 0;
    }

    @Override
    protected boolean outputsReady(ItemStack blade) {
        return outputInv.getStackInSlot(0).isEmpty();
    }

    @Override
    protected void processBlade(ItemStack blade) {
        int damage = blade.getDamageValue();
        if (damage <= 0) {
            moveInputToOutput(0);
            return;
        }

        blade.setDamageValue(Math.max(0, damage - 1));

        // ItemSlashBlade#setDamage automatically clears the broken flag at zero
        // when the blade is not sealed, preserving SlashBlade's native repair rules.
        if (blade.getDamageValue() <= 0) {
            moveInputToOutput(0);
        }
    }
}
