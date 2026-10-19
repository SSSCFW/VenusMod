package dev.ssscfw.venusmod.yamato;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import java.util.HashSet;
import java.util.Set;
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
    // 1地点が水中や崖でも、隣接チャンクを含む最大8ブロック先まで乾いた足場を探す。
    private static final int NEARBY_RADIUS = 8;
    private static final int MAX_CAVE_COLUMNS_PER_AREA = 12;
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

    /** 1回につきランダムな1地点を選び、周囲のFULLチャンクを必要に応じて読み込んで安全な床を探す。 */
    public static BlockPos sample(ServerLevel level, Vec3 origin, int range, int mode, RandomSource random) {
        if (level == null || !YamatoTravelRules.validRandomSettings(range, mode)) return null;
        BlockPos column = sampleHorizontal(origin, range, random);
        return findNearby(level, origin, range, mode, random, column);
    }

    /**
     * 未読込のチャンクを地形生成済み(FULL)まで準備してから、その列と近傍を探す。
     * 探索候補ごとに必要なチャンクだけを読み、辺に接する隣のチャンクも対象にする。
     * 48回の世界規模探索が一度に走らないよう、呼び出し側で1tickに1エリアに制限する。
     */
    public static BlockPos findNearby(ServerLevel level, Vec3 origin, int range, int mode,
                                      RandomSource random, BlockPos center) {
        if (level == null || origin == null || random == null || center == null
                || !YamatoTravelRules.validRandomSettings(range, mode)
                || !withinRange(origin, range, center.getX(), center.getZ())) return null;
        Set<Long> loaded = new HashSet<>(4);
        int cavesChecked = 0;
        // 中心から近い順に調べ、候補が見つかったら残りのチャンクは読み込まない。
        for (int ring = 0; ring <= NEARBY_RADIUS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                int x = center.getX() + dx, z = center.getZ() + dz;
                if (!withinRange(origin, range, x, z)) continue;
                BlockPos column = new BlockPos(x, center.getY(), z);
                if (!level.getWorldBorder().isWithinBounds(column)) continue;
                int cx = x >> 4, cz = z >> 4;
                if (loaded.add(ChunkPos.asLong(cx, cz))) {
                    // getHeight()だけに依存しない。未生成の遠隔チャンクもFULLまで読み込む。
                    level.getChunk(cx, cz);
                }
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos ground = surface(level, x, z, surfaceY);
                if (mode == YamatoTravelRules.RANDOM_SURFACE_AND_CAVES
                        && cavesChecked < MAX_CAVE_COLUMNS_PER_AREA
                        && (ground == null || random.nextBoolean())) {
                    cavesChecked++;
                    BlockPos cave = underground(level, x, z, surfaceY, random);
                    if (cave != null) return cave;
                }
                if (ground != null) return ground;
            }
        }
        return null;
    }

    private static boolean withinRange(Vec3 origin, int range, int x, int z) {
        double dx = x + .5 - origin.x, dz = z + .5 - origin.z;
        return dx * dx + dz * dz <= (double)range * range;
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
