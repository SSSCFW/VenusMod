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

    public static final DeferredHolder<Block, Block> NICKEL_ORE =
            BLOCKS.register(
                    "nickel_ore",
                    () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE)));

    public static final DeferredHolder<Block, Block> DEEPSLATE_NICKEL_ORE =
            BLOCKS.register(
                    "deepslate_nickel_ore",
                    () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_IRON_ORE)));

    private ModBlocks() {
    }
}
