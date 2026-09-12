package dev.ssscfw.venusmod.compat.create;

import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/** Breaks an intact SlashBlade into proud-soul fragments and, when applicable, a broken blade. */
public final class BladeBreakerBlockEntity extends AbstractBladeMachineBlockEntity {
    private static final int BREAK_WORK = 100;

    public BladeBreakerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 2);
    }

    @Override
    protected int getWorkDuration() {
        return BREAK_WORK;
    }

    @Override
    protected boolean canInsertBlade(ItemStack stack) {
        return super.canInsertBlade(stack) && !SlashBladeEnchantmentCompat.isBrokenBlade(stack);
    }

    @Override
    protected boolean canProcessBlade(ItemStack blade) {
        return SlashBladeEnchantmentCompat.isBlade(blade)
                && !SlashBladeEnchantmentCompat.isBrokenBlade(blade);
    }

    @Override
    protected boolean outputsReady(ItemStack blade) {
        return outputInv.getStackInSlot(0).isEmpty()
                && outputInv.getStackInSlot(1).isEmpty();
    }

    @Override
    protected void processBlade(ItemStack blade) {
        Optional<SlashBladeEnchantmentCompat.BladeBreakResult> optionalResult =
                SlashBladeEnchantmentCompat.breakBladeForMachine(blade);
        if (optionalResult.isEmpty()) {
            return;
        }

        SlashBladeEnchantmentCompat.BladeBreakResult result = optionalResult.get();
        inputInv.setStackInSlot(0, ItemStack.EMPTY);

        ItemStack survivingBlade = result.survivingBlade();
        if (!survivingBlade.isEmpty()) {
            outputInv.setStackInSlot(0, survivingBlade);
        }

        ItemStack soulOutput = result.soulOutput();
        if (!soulOutput.isEmpty()) {
            outputInv.setStackInSlot(1, soulOutput);
        }
    }
}
