package dev.ssscfw.venusmod.registry;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.block.DiamondHopperBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VenusMod.MOD_ID);

    public static final DeferredHolder<Block, DiamondHopperBlock> DIAMOND_HOPPER =
            BLOCKS.register(
                    "diamond_hopper",
                    () -> new DiamondHopperBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.HOPPER)));

    private ModBlocks() {
    }
}
