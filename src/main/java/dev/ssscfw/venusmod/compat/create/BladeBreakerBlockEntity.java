package dev.ssscfw.venusmod.compat.create;

import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import dev.ssscfw.venusmod.registry.ModEnchantments;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Breaks an intact SlashBlade using the same soul-drop rules as player breakage. */
public final class BladeBreakerBlockEntity extends AbstractBladeMachineBlockEntity {
    private static final int BREAK_WORK = 100;

    /**
     * SlashBlade's configured enchanted-soul cap can be raised to 64. Reserve one
     * slot for a surviving blade, up to 64 enchanted tiny souls, and one normal
     * tiny-soul stack.
     */
    private static final int OUTPUT_SLOTS = 66;

    public BladeBreakerBlockEntity(BlockPos pos, BlockState state) {
        super(VenusCreateCompat.BLADE_BREAKER_BE.get(), pos, state, OUTPUT_SLOTS);
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
        for (int slot = 0; slot < outputInv.getSlots(); slot++) {
            if (!outputInv.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void processBlade(ItemStack blade) {
        if (level == null) {
            return;
        }

        Optional<SlashBladeEnchantmentCompat.BladeBreakResult> optionalResult =
                SlashBladeEnchantmentCompat.breakBladeForMachine(
                        blade,
                        level.registryAccess(),
                        level.getRandom());
        if (optionalResult.isEmpty()) {
            return;
        }

        SlashBladeEnchantmentCompat.BladeBreakResult result = optionalResult.get();
        ItemStack survivingBlade = result.survivingBlade();
        List<ItemStack> soulOutputs = new ArrayList<>(result.soulOutputs());

        // Keep VenusMod's previous consumed-blade enchantment preservation without
        // changing SlashBlade's native soul item type or output count. The final soul
        // stack is the ordinary (non-random-enchanted) proudsoul_tiny stack.
        if (survivingBlade.isEmpty()
                && !soulOutputs.isEmpty()
                && ModEnchantments.hasAnyBladeSpecialEnchantment(blade, level.registryAccess())) {
            ItemStack transferTarget = soulOutputs.get(soulOutputs.size() - 1);
            ModEnchantments.mergeBladeSpecialEnchantments(blade, transferTarget, level.registryAccess());
        }

        inputInv.setStackInSlot(0, ItemStack.EMPTY);

        // Slot 0 is reserved for the blade so automation always sees a stable layout.
        if (!survivingBlade.isEmpty()) {
            outputInv.setStackInSlot(0, survivingBlade);
        }

        int outputSlot = 1;
        for (ItemStack soul : soulOutputs) {
            if (soul.isEmpty()) {
                continue;
            }
            if (outputSlot >= outputInv.getSlots()) {
                break;
            }
            outputInv.setStackInSlot(outputSlot++, soul);
        }
    }
}
