package dev.ssscfw.venusmod.yamato;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** サーバーでのみ利用する、地上/洞窟の安全なランダム着地点探索。 */
public final class YamatoRandomDestinations {
    private static final double HALF_PLAYER_WIDTH = .31;
    private static final double PLAYER_HEIGHT = 1.85;
    private YamatoRandomDestinations() {}

    /**
     * 現在位置から水平半径range以内のブロック列を無作為に選ぶ。
     * 角度一様・距離sqrt補正で面積一様にし、丸めの結果、範囲外となる位置は拒否する。
     * 範囲1でも隣のブロック列を選べる。
     */
    public static BlockPos sampleHorizontal(Vec3 origin, int range, RandomSource random) {
        if (origin == null || random == null || !YamatoTravelRules.validRandomSettings(range, 0)) return null;
        double angle = random.nextDouble() * (Math.PI * 2);
        double distance = Math.sqrt(random.nextDouble()) * range;
        int x = Mth.floor(origin.x + Math.cos(angle) * distance);
        int z = Mth.floor(origin.z + Math.sin(angle) * distance);
        if (x == Mth.floor(origin.x) && z == Mth.floor(origin.z)) return null;
        double dx = x + .5 - origin.x, dz = z + .5 - origin.z;
        if (dx * dx + dz * dz > (double)range * range) return null;
        return new BlockPos(x, Mth.floor(origin.y), z);
    }

    /** 最大1候補列のみ探索する。呼び出し側はチャンク生成の負荷を抑えてtickごとに繰り返す。 */
    public static BlockPos sample(ServerLevel level, Vec3 origin, int range, int mode, RandomSource random) {
        if (level == null || !YamatoTravelRules.validRandomSettings(range, mode)) return null;
        BlockPos column = sampleHorizontal(origin, range, random);
        if (column == null || !level.getWorldBorder().isWithinBounds(column)) return null;
        int x = column.getX(), z = column.getZ();
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (mode == YamatoTravelRules.RANDOM_SURFACE_AND_CAVES && random.nextBoolean()) {
            BlockPos cave = underground(level, x, z, surfaceY, random);
            if (cave != null) return cave;
        }
        return surface(level, x, z, surfaceY);
    }

    /** 地上：最も高い障害物の真上。水面やマグマ面に立つことは認めない。 */
    public static BlockPos surface(ServerLevel level, int x, int z) {
        return surface(level, x, z, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
    }

    private static BlockPos surface(ServerLevel level, int x, int z, int feetY) {
        BlockPos feet = new BlockPos(x, feetY, z);
        return safeLanding(level, feet) ? feet : null;
    }

    /** 地下：自然洞窟など、天井より下にある空洞のブロック上を一様抽出する。 */
    public static BlockPos underground(ServerLevel level, int x, int z, RandomSource random) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return underground(level, x, z, y, random);
    }

    private static BlockPos underground(ServerLevel level, int x, int z, int surfaceY, RandomSource random) {
        BlockPos chosen = null;
        int possibilities = 0;
        for (int y = level.getMinBuildHeight() + 1;
                y <= surfaceY - 2 && y + 2 < level.getMaxBuildHeight(); y++) {
            BlockPos feet = new BlockPos(x, y, z);
            if (safeLanding(level, feet) && random.nextInt(++possibilities) == 0) chosen = feet;
        }
        return chosen;
    }

    /**
     * 必須条件：固体の上面に立ち、頭まで当たり判定が空き、足場と身体に水/溶岩がない。
     * 草、花、ツタなど衝突判定のない装飾ブロックは身体位置にあってもよい。
     */
    public static boolean safeLanding(ServerLevel level, BlockPos feet) {
        if (level == null || feet == null || feet.getY() <= level.getMinBuildHeight()
                || feet.getY() + 2 >= level.getMaxBuildHeight()) return false;
        BlockPos ground = feet.below();
        BlockState floor = level.getBlockState(ground);
        if (!floor.getFluidState().isEmpty() || !floor.isFaceSturdy(level, ground, Direction.UP)
                || floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CAMPFIRE)
                || floor.is(Blocks.SOUL_CAMPFIRE)) return false;
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos p = feet.above(dy);
            BlockState state = level.getBlockState(p);
            if (!state.getFluidState().isEmpty() || !state.getCollisionShape(level, p).isEmpty()) return false;
        }
        double x = feet.getX() + .5, z = feet.getZ() + .5;
        AABB body = new AABB(x - HALF_PLAYER_WIDTH, feet.getY(), z - HALF_PLAYER_WIDTH,
                x + HALF_PLAYER_WIDTH, feet.getY() + PLAYER_HEIGHT, z + HALF_PLAYER_WIDTH);
        return level.getWorldBorder().isWithinBounds(body) && level.noCollision(null, body);
    }
}
