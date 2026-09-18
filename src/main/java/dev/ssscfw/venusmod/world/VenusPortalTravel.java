package dev.ssscfw.venusmod.world;

import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class VenusPortalTravel {
    private VenusPortalTravel() {}

    public static DimensionTransition destination(ServerLevel source, Entity entity, BlockPos pos) {
        if (!VenusDimensionContent.allowedSource(source)) return null;
        VenusPortalFrame from = VenusPortalFrame.find(source, pos);
        if (from == null || !from.complete(source)) return null;
        ServerLevel target = source.getServer().getLevel(source.dimension().equals(VenusDimensionContent.VENUS)
                ? Level.OVERWORLD : VenusDimensionContent.VENUS);
        if (target == null) return fail(entity, "message.venusmod.dimension_missing");
        VenusPortalLinks data = VenusPortalLinks.get(source);
        VenusPortalLinks.Address fromAddress = VenusPortalLinks.Address.of(source, from);
        data.register(fromAddress);
        VenusPortalFrame to = null;
        VenusPortalLinks.Address linked = data.links.get(fromAddress);
        if (linked != null && linked.dimension().equals(target.dimension().location().toString())) {
            to = readPortal(target, linked.pos());
            if (to == null) data.forget(linked);
        }
        BlockPos desired = new BlockPos(Mth.floor(Mth.clamp(pos.getX(), target.getWorldBorder().getMinX() + 4,
                target.getWorldBorder().getMaxX() - 4)), pos.getY(),
                Mth.floor(Mth.clamp(pos.getZ(), target.getWorldBorder().getMinZ() + 4, target.getWorldBorder().getMaxZ() - 4)));
        if (to == null) {
            // 未接続の既設ゲートを優先。他の入口と対になったゲートの帰還先は奪わない。
            List<VenusPortalLinks.Address> candidates = data.portals.stream()
                    .filter(a -> a.dimension().equals(target.dimension().location().toString()) && !data.links.containsKey(a))
                    .filter(a -> Math.abs((long) a.pos().getX() - desired.getX()) <= 128
                            && Math.abs((long) a.pos().getZ() - desired.getZ()) <= 128)
                    .sorted(Comparator.comparingDouble(a -> a.pos().distSqr(desired))).toList();
            for (VenusPortalLinks.Address candidate : candidates) {
                to = readPortal(target, candidate.pos());
                if (to != null) break;
                data.forget(candidate);
            }
        }
        if (to == null) to = createExit(target, desired, from.axis());
        if (to == null) return fail(entity, "message.venusmod.no_safe_exit");
        if (entity.getBbWidth() > to.width() || entity.getBbHeight() > to.height())
            return fail(entity, "message.venusmod.entity_too_large");
        Vec3 arrival = to.axis() == Direction.Axis.X
                ? new Vec3(to.bottomLeft().getX() + to.width() / 2.0, to.bottomLeft().getY(), to.bottomLeft().getZ() + 0.5)
                : new Vec3(to.bottomLeft().getX() + 0.5, to.bottomLeft().getY(), to.bottomLeft().getZ() + to.width() / 2.0);
        double halfWidth = entity.getBbWidth() / 2.0;
        if (!target.noCollision(entity, new AABB(arrival.x - halfWidth, arrival.y, arrival.z - halfWidth,
                arrival.x + halfWidth, arrival.y + entity.getBbHeight(), arrival.z + halfWidth)))
            return fail(entity, "message.venusmod.no_safe_exit");
        data.pair(fromAddress, VenusPortalLinks.Address.of(target, to));
        float yaw = entity.getYRot() + (from.axis() == to.axis() ? 0 : 90);
        return new DimensionTransition(target, arrival, Vec3.ZERO, yaw, entity.getXRot(),
                DimensionTransition.PLAY_PORTAL_SOUND.then(DimensionTransition.PLACE_PORTAL_TICKET).then(arrived -> {
                    arrived.fallDistance = 0;
                    arrived.setPortalCooldown();
                }));
    }

    private static DimensionTransition fail(Entity entity, String key) {
        if (entity instanceof ServerPlayer player) player.displayClientMessage(Component.translatable(key), true);
        return null;
    }

    private static VenusPortalFrame readPortal(ServerLevel level, BlockPos pos) {
        if (!level.getWorldBorder().isWithinBounds(pos) || level.isOutsideBuildHeight(pos)) return null;
        // 保存座標は左下なので、最大サイズの枠を含むチャンクを先に読み込む。
        for (int x = (pos.getX() - 1) >> 4; x <= (pos.getX() + 21) >> 4; x++)
            for (int z = (pos.getZ() - 1) >> 4; z <= (pos.getZ() + 21) >> 4; z++) level.getChunk(x, z);
        VenusPortalFrame frame = VenusPortalFrame.find(level, pos);
        return frame != null && frame.complete(level) ? frame : null;
    }

    private static BlockPos offset(BlockPos origin, Direction.Axis axis, int u, int v, int depth) {
        return axis == Direction.Axis.X ? origin.offset(u, v, depth) : origin.offset(depth, v, u);
    }

    private static VenusPortalFrame createExit(ServerLevel level, BlockPos desired, Direction.Axis axis) {
        for (int radius = 0; radius <= 16; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                int x = desired.getX() + dx, z = desired.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
                BlockPos origin = new BlockPos(x, y, z);
                if (clearSite(level, origin, axis, true)) return buildExit(level, origin, axis);
            }
        }
        // 洋上・崖などで平地がなければ、既存ブロックを壊さない空中に足場付きゲートを作る。
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, desired.getX(), desired.getZ());
        for (int y = Math.max(surface + 8, 80); y < level.getMaxBuildHeight() - 4; y++) {
            BlockPos origin = new BlockPos(desired.getX(), y, desired.getZ());
            if (clearSite(level, origin, axis, false)) return buildExit(level, origin, axis);
        }
        return null;
    }

    private static boolean clearSite(ServerLevel level, BlockPos origin, Direction.Axis axis, boolean requireGround) {
        if (origin.getY() <= level.getMinBuildHeight() + 1 || origin.getY() + 4 >= level.getMaxBuildHeight()) return false;
        for (int u = -2; u <= 3; u++) for (int depth = -2; depth <= 2; depth++) {
            BlockPos ground = offset(origin, axis, u, -2, depth);
            if (!level.getWorldBorder().isWithinBounds(ground)) return false;
            if (requireGround && (!level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)
                    || !level.getFluidState(ground).isEmpty())) return false;
            for (int v = -1; v <= 3; v++) {
                BlockPos p = offset(origin, axis, u, v, depth);
                BlockState state = level.getBlockState(p);
                if ((!state.isAir() && !state.canBeReplaced()) || !state.getFluidState().isEmpty() || state.hasBlockEntity()) return false;
            }
        }
        return true;
    }

    private static VenusPortalFrame buildExit(ServerLevel level, BlockPos origin, Direction.Axis axis) {
        for (int u = -2; u <= 3; u++) for (int depth = -2; depth <= 2; depth++)
            level.setBlock(offset(origin, axis, u, -1, depth), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
        for (int u = -1; u <= 2; u++) for (int v = -1; v <= 3; v++)
            if (u == -1 || u == 2 || v == -1 || v == 3)
                level.setBlock(offset(origin, axis, u, v, 0),
                        VenusDimensionContent.GOLD_BLOCK_DUMMY.get().defaultBlockState(), 3);
        for (int u : new int[]{-2, 3}) for (int depth : new int[]{-2, 2})
            level.setBlock(offset(origin, axis, u, 0, depth), Blocks.TORCH.defaultBlockState(), 3);
        VenusPortalFrame frame = new VenusPortalFrame(origin, axis, 2, 3);
        frame.fill(level);
        return frame;
    }
}
