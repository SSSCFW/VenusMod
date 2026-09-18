package dev.ssscfw.venusmod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** bottomLeftはX/Zの正方向を幅方向にしたときの左下の内側ブロック。 */
public record VenusPortalFrame(BlockPos bottomLeft, Direction.Axis axis, int width, int height) {
    public static VenusPortalFrame find(LevelAccessor level, BlockPos pos) {
        VenusPortalFrame frame = find(level, pos, Direction.Axis.X);
        return frame != null ? frame : find(level, pos, Direction.Axis.Z);
    }

    public static VenusPortalFrame find(LevelAccessor level, BlockPos pos, Direction.Axis axis) {
        int fixed = axis == Direction.Axis.X ? pos.getZ() : pos.getX();
        int startU = axis == Direction.Axis.X ? pos.getX() : pos.getZ();
        PortalRectangle rectangle = PortalRectangle.find((u, v) -> {
            BlockPos sample = axis == Direction.Axis.X ? new BlockPos(u, v, fixed) : new BlockPos(fixed, v, u);
            if (level.isOutsideBuildHeight(sample) || !level.hasChunkAt(sample)) return PortalRectangle.BLOCKED;
            BlockState state = level.getBlockState(sample);
            if (VenusPortalFrameRules.accepts(
                    state.is(Blocks.GOLD_BLOCK), state.is(VenusDimensionContent.GOLD_BLOCK_DUMMY.get()))) {
                return PortalRectangle.FRAME;
            }
            if (state.is(VenusDimensionContent.PORTAL.get())) {
                return state.getValue(VenusPortalBlock.AXIS) == axis ? PortalRectangle.PORTAL : PortalRectangle.BLOCKED;
            }
            return state.isAir() || state.is(BlockTags.FIRE) ? PortalRectangle.EMPTY : PortalRectangle.BLOCKED;
        }, startU, pos.getY(), level.getMinBuildHeight(), level.getMaxBuildHeight());
        if (rectangle == null) return null;
        BlockPos bottom = axis == Direction.Axis.X
                ? new BlockPos(rectangle.u(), rectangle.v(), fixed)
                : new BlockPos(fixed, rectangle.v(), rectangle.u());
        return new VenusPortalFrame(bottom, axis, rectangle.width(), rectangle.height());
    }

    public BlockPos at(int u, int v) {
        return axis == Direction.Axis.X ? bottomLeft.offset(u, v, 0) : bottomLeft.offset(0, v, u);
    }

    public boolean complete(LevelAccessor level) {
        for (int u = 0; u < width; u++) for (int v = 0; v < height; v++) {
            BlockState state = level.getBlockState(at(u, v));
            if (!state.is(VenusDimensionContent.PORTAL.get()) || state.getValue(VenusPortalBlock.AXIS) != axis) return false;
        }
        return true;
    }

    public void fill(LevelAccessor level) {
        BlockState portal = VenusDimensionContent.PORTAL.get().defaultBlockState().setValue(VenusPortalBlock.AXIS, axis);
        // 枠内を埋め終わる前の隣接更新で部分的なポータルが消えるのを防ぐ。
        for (int u = 0; u < width; u++) for (int v = 0; v < height; v++) level.setBlock(at(u, v), portal, 18);
    }
}
