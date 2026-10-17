package dev.ssscfw.venusmod.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 金星将軍の背後テレポートは壁抜け・床抜け・窒息を許さない。 */
public final class VenusGeneralTeleportRules {
    private static final double MAX_TELEPORT_DISTANCE = 18.0D;
    private static final double MAX_HEIGHT_DIFFERENCE = 1.75D;

    private VenusGeneralTeleportRules() {
    }

    public static boolean canTeleport(ServerLevel level, Mob general, Vec3 destination) {
        if (general.position().distanceToSqr(destination)
                    > MAX_TELEPORT_DISTANCE * MAX_TELEPORT_DISTANCE
                || Math.abs(general.getY() - destination.y) > MAX_HEIGHT_DIFFERENCE) {
            return false;
        }

        AABB landing = general.getBoundingBox().move(destination.subtract(general.position()));
        if (!level.getWorldBorder().isWithinBounds(landing)
                || !level.noCollision(general, landing)) {
            return false;
        }

        // 移動先にはしっかりした足場と空気が必要（穴、溶岩、流体への瞬間移動を避ける）。
        BlockPos feet = BlockPos.containing(destination);
        BlockPos floor = BlockPos.containing(destination.x, destination.y - 0.20D, destination.z);
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                || !level.getFluidState(feet).isEmpty()
                || !level.getFluidState(feet.above()).isEmpty()) {
            return false;
        }

        // 移動元から移動先まで、身体の高さと幅をサンプリングしてブロック越えを防ぐ。
        // 目的地のnoCollision判定だけだと、薄い壁の向こう側へ瞬間移動できてしまう。
        double halfWidth = Math.min(0.3D, general.getBbWidth() * 0.40D);
        double[] heights = {0.20D, general.getBbHeight() * 0.5D, general.getBbHeight() - 0.20D};
        double[][] offsets = {{0.0D, 0.0D}, {-halfWidth, -halfWidth},
                {halfWidth, -halfWidth}, {-halfWidth, halfWidth}, {halfWidth, halfWidth}};
        for (double height : heights) {
            for (double[] offset : offsets) {
                Vec3 start = general.position().add(offset[0], height, offset[1]);
                Vec3 end = destination.add(offset[0], height, offset[1]);
                if (level.clip(new ClipContext(start, end,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, general))
                        .getType() != HitResult.Type.MISS) {
                    return false;
                }
            }
        }
        return true;
    }
}
