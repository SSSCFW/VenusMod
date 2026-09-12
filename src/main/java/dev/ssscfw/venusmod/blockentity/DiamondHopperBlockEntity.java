package dev.ssscfw.venusmod.blockentity;

import dev.ssscfw.venusmod.block.DiamondHopperBlock;
import dev.ssscfw.venusmod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A vanilla-compatible hopper running ten transfer cycles for every eight game
 * ticks. Vanilla hoppers run one transfer cycle every eight ticks, so this gives
 * an exact 10x average transfer rate while reusing vanilla push/pull rules.
 */
public final class DiamondHopperBlockEntity extends HopperBlockEntity {
    private static final int SPEED_MULTIPLIER = 10;
    private static final String NBT_TRANSFER_ACCUMULATOR = "DiamondTransferAccumulator";

    /**
     * MOVE_ITEM_SPEED is 8 for a vanilla hopper. Adding ten credits every tick and
     * spending eight per vanilla transfer cycle yields 10 cycles per 8 ticks:
     * 25 transfer cycles/sec versus vanilla's 2.5 cycles/sec.
     */
    private int transferAccumulator;

    public DiamondHopperBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    public static void serverTick(
            Level level,
            BlockPos pos,
            BlockState state,
            DiamondHopperBlockEntity hopper) {
        hopper.transferAccumulator += SPEED_MULTIPLIER;
        int cycles = hopper.transferAccumulator / MOVE_ITEM_SPEED;
        hopper.transferAccumulator %= MOVE_ITEM_SPEED;

        for (int i = 0; i < cycles; i++) {
            // Vanilla pushItemsTick respects HopperBlock.ENABLED and performs the
            // normal push + pull logic. Reset only its transfer cooldown so each
            // earned cycle is allowed to run immediately.
            hopper.cooldownTime = 0;
            HopperBlockEntity.pushItemsTick(level, pos, state, hopper);
        }
    }

    @Override
    public BlockEntityType<?> getType() {
        return ModBlockEntities.DIAMOND_HOPPER.get();
    }

    @Override
    public boolean isValidBlockState(BlockState state) {
        return state.getBlock() instanceof DiamondHopperBlock;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.venusmod.diamond_hopper");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        transferAccumulator = Math.floorMod(tag.getInt(NBT_TRANSFER_ACCUMULATOR), MOVE_ITEM_SPEED);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(NBT_TRANSFER_ACCUMULATOR, transferAccumulator);
    }
}
