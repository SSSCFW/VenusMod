package dev.ssscfw.venusmod.compat.create;

import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import dev.ssscfw.venusmod.registry.ModEnchantments;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/** Breaks an intact SlashBlade into a Soul Fragment and, when applicable, a broken blade. */
public final class BladeBreakerBlockEntity extends AbstractBladeMachineBlockEntity {
    private static final int BREAK_WORK = 100;

    public BladeBreakerBlockEntity(BlockPos pos, BlockState state) {
        super(VenusCreateCompat.BLADE_BREAKER_BE.get(), pos, state, 2);
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
        ItemStack survivingBlade = result.survivingBlade();
        ItemStack soulOutput = result.soulOutput();

        // If the blade is consumed completely, preserve VenusMod's three blade-only
        // enchantments on the Soul Fragment. A surviving broken blade keeps them itself,
        // so copying in that case would duplicate the enchantments.
        if (survivingBlade.isEmpty()
                && !soulOutput.isEmpty()
                && level != null
                && ModEnchantments.hasAnyBladeSpecialEnchantment(blade, level.registryAccess())) {
            ModEnchantments.mergeBladeSpecialEnchantments(blade, soulOutput, level.registryAccess());
        }

        inputInv.setStackInSlot(0, ItemStack.EMPTY);

        if (!survivingBlade.isEmpty()) {
            outputInv.setStackInSlot(0, survivingBlade);
        }
        if (!soulOutput.isEmpty()) {
            outputInv.setStackInSlot(1, soulOutput);
        }
    }
}
