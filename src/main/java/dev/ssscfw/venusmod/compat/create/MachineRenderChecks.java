package dev.ssscfw.venusmod.compat.create;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import dev.ssscfw.venusmod.VenusMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Minecraft自身の面表示判定を全機械・全BlockState・全6方向で検査する。 */
public final class MachineRenderChecks {
    private MachineRenderChecks() {}
    public static void verify(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(3, 3, 3));
        var level = helper.getLevel();
        var machineTag = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, "machines"));
        int checks = 0, machines = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            var id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals(VenusMod.MOD_ID) || !(block instanceof KineticBlock)) continue;
            machines++;
            helper.assertTrue(block instanceof VenusMachineBlock, id + " bypasses VenusMachineBlock");
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                helper.assertTrue(state.is(machineTag), id + " missing machines tag/model validation");
                helper.assertTrue(!state.canOcclude(), id + " must not cull neighbouring blocks");
                helper.assertTrue(state.getOcclusionShape(level, center).isEmpty(), id + " nonempty occlusion shape");
                level.setBlockAndUpdate(center, state);
                for (Direction direction : Direction.values()) {
                    BlockPos adjacent = center.relative(direction);
                    for (Block neighbour : new Block[]{Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.STONE, Blocks.GLASS, Blocks.OAK_LEAVES}) {
                        BlockState neighbourState = neighbour.defaultBlockState();
                        level.setBlockAndUpdate(adjacent, neighbourState);
                        helper.assertTrue(Block.shouldRenderFace(neighbourState, level, adjacent,
                                direction.getOpposite(), center), id + " hides " + neighbour + " at " + direction);
                        level.setBlockAndUpdate(adjacent, Blocks.AIR.defaultBlockState());
                        checks++;
                    }
                }
                level.setBlockAndUpdate(center, Blocks.AIR.defaultBlockState());
            }
        }
        helper.assertTrue(machines >= 3, "Create test did not register the existing machines");
        System.out.println("Venus machine neighbour-face assertions: " + checks + "; machines: " + machines);
        helper.succeed();
    }
}
