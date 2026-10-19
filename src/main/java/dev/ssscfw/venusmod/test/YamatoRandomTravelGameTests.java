package dev.ssscfw.venusmod.test;

import com.mojang.authlib.GameProfile;
import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.yamato.YamatoRandomDestinations;
import dev.ssscfw.venusmod.yamato.YamatoRifts;
import dev.ssscfw.venusmod.yamato.YamatoTravelData;
import dev.ssscfw.venusmod.yamato.YamatoTravelRules;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** サーバーで安全な足場、洞窟と地上の分岐、半径、保存、実転送を検証する。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class YamatoRandomTravelGameTests {
    private YamatoRandomTravelGameTests() {}

    private static CompoundTag options(int range, int mode) {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("range", range);
        nbt.putInt("mode", mode);
        return nbt;
    }

    /** GameTest構造物は地下にあるため、実際のワールド地表より上へテスト足場を作る。 */
    private static BlockPos aboveTerrain(GameTestHelper helper, BlockPos relative) {
        var level = helper.getLevel();
        BlockPos column = helper.absolutePos(relative);
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                column.getX(), column.getZ());
        return new BlockPos(column.getX(), surfaceY + 3, column.getZ());
    }

    @GameTest(template = "test/wide_empty")
    public static void solidSurfaceAllowsFlowersAndGrass(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos feet = aboveTerrain(h, new BlockPos(5, 3, 5));
        level.setBlock(feet.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        level.setBlock(feet, Blocks.DANDELION.defaultBlockState(), 3);
        h.assertTrue(YamatoRandomDestinations.safeLanding(level, feet),
                "Flower above solid grassy ground must be accepted");
        h.assertTrue(feet.equals(YamatoRandomDestinations.surface(level, feet.getX(), feet.getZ())),
                "Surface heightmap should find the solid block under the flower");
        level.setBlock(feet, Blocks.SHORT_GRASS.defaultBlockState(), 3);
        h.assertTrue(YamatoRandomDestinations.safeLanding(level, feet),
                "Short grass must not be rejected as an obstruction");
        h.succeed();
    }

    @GameTest(template = "test/wide_empty")
    public static void liquidGroundOrBodyAndUnsupportedAirAreNeverDestinations(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos feet = aboveTerrain(h, new BlockPos(5, 3, 5));
        level.setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
        h.assertTrue(YamatoRandomDestinations.safeLanding(level, feet), "Dry stone floor must be valid");
        level.setBlock(feet, Blocks.WATER.defaultBlockState(), 3);
        h.assertTrue(!YamatoRandomDestinations.safeLanding(level, feet), "Standing in water was allowed");
        level.setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(feet.above(), Blocks.LAVA.defaultBlockState(), 3);
        h.assertTrue(!YamatoRandomDestinations.safeLanding(level, feet), "Lava in the headspace was allowed");
        level.setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(feet.below(), Blocks.WATER.defaultBlockState(), 3);
        h.assertTrue(!YamatoRandomDestinations.safeLanding(level, feet), "Water surface was used as ground");
        h.assertTrue(YamatoRandomDestinations.surface(level, feet.getX(), feet.getZ()) == null,
                "Surface selection accepted a water surface");
        level.setBlock(feet.below(), Blocks.LAVA.defaultBlockState(), 3);
        h.assertTrue(!YamatoRandomDestinations.safeLanding(level, feet), "Lava surface was used as ground");
        level.setBlock(feet.below(), Blocks.AIR.defaultBlockState(), 3);
        h.assertTrue(!YamatoRandomDestinations.safeLanding(level, feet), "Floating air was accepted as ground");
        level.setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(feet, Blocks.STONE.defaultBlockState(), 3);
        h.assertTrue(!YamatoRandomDestinations.safeLanding(level, feet), "Solid block inside player was accepted");
        h.succeed();
    }

    @GameTest(template = "test/wide_empty")
    public static void caveModeFindsCoveredDryFloorsInsteadOfOnlySurface(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos cave = aboveTerrain(h, new BlockPos(7, 3, 7));
        level.setBlock(cave.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(cave, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(cave.above(), Blocks.AIR.defaultBlockState(), 3);
        BlockPos roof = cave.above(6);
        level.setBlock(roof, Blocks.STONE.defaultBlockState(), 3);
        BlockPos surface = YamatoRandomDestinations.surface(level, cave.getX(), cave.getZ());
        BlockPos underground = YamatoRandomDestinations.underground(level, cave.getX(), cave.getZ(),
                RandomSource.create(7));
        h.assertTrue(surface != null && surface.getY() == roof.getY() + 1,
                "Surface-only incorrectly selected a cave below a solid roof");
        h.assertTrue(underground != null && underground.getY() < surface.getY()
                && YamatoRandomDestinations.safeLanding(level, underground),
                "Cave mode did not find the dry solid-supported room below a roof");
        h.succeed();
    }

    @GameTest(template = "test/empty")
    public static void sampledColumnsNeverExceedOneToMillionBlockRadius(GameTestHelper h) {
        Vec3 origin = new Vec3(765.5, 75, -84.5);
        RandomSource random = RandomSource.create(20261019L);
        for (int radius : new int[]{1, 2, 15, 1000, 1_000_000}) {
            int accepted = 0;
            Set<BlockPos> candidates = new HashSet<>();
            for (int i = 0; i < 400; i++) {
                BlockPos column = YamatoRandomDestinations.sampleHorizontal(origin, radius, random);
                if (column == null) continue;
                double dx = column.getX() + .5 - origin.x, dz = column.getZ() + .5 - origin.z;
                h.assertTrue(dx * dx + dz * dz <= (double)radius * radius,
                        "Radius " + radius + " allowed an out-of-range destination");
                h.assertTrue(column.getX() != (int)Math.floor(origin.x)
                        || column.getZ() != (int)Math.floor(origin.z),
                        "Random destination was the exact starting column");
                candidates.add(column);
                accepted++;
            }
            h.assertTrue(accepted > 0, "No valid positions for radius " + radius);
            h.assertTrue(radius == 1 || candidates.size() > 1,
                    "Random positions had no variation in radius " + radius);
        }
        h.succeed();
    }

    @GameTest(template = "test/empty")
    public static void randomRangeAndModePersistWithStrictValidation(GameTestHelper h) {
        var level = h.getLevel();
        YamatoTravelData data = new YamatoTravelData();
        UUID owner = UUID.randomUUID();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(owner, "yamato-random-options"), ClientInformation.createDefault());
        h.assertTrue(data.randomSettings(owner).equals(options(1000, 0)),
                "Old world default should be a 1000 block surface search");
        for (int radius : new int[]{1, 1000, 1_000_000}) for (int mode = 0; mode <= 1; mode++) {
            CompoundTag selected = options(radius, mode);
            h.assertTrue(data.apply(player, "random_settings", selected), "Valid random options rejected");
            var copy = YamatoTravelData.load(data.save(new CompoundTag(), level.registryAccess()),
                    level.registryAccess());
            h.assertTrue(copy.randomSettings(owner).equals(selected),
                    "Saved random mode/radius changed after reloading");
        }
        CompoundTag before = data.snapshot(owner);
        data.setDirty(false);
        for (int bad : new int[]{-10, 0, 1_000_001}) {
            h.assertTrue(!data.apply(player, "random_settings", options(bad, 0)),
                    "Out-of-range radius was accepted");
        }
        h.assertTrue(!data.apply(player, "random_settings", options(1000, -1))
                && !data.apply(player, "random_settings", options(1000, 2)),
                "Invalid random mode was accepted");
        CompoundTag malformed = options(1000, 0);
        malformed.putString("range", "1000");
        h.assertTrue(!data.apply(player, "random_settings", malformed),
                "String range silently converted to integer");
        h.assertTrue(data.snapshot(owner).equals(before) && !data.isDirty(),
                "Rejected random options partially modified player settings");
        h.succeed();
    }

    @GameTest(template = "test/wide_empty")
    public static void generatedSafeSurfaceCanBeConnectedAndActuallyUsed(GameTestHelper h) {
        var level = h.getLevel(); var server = level.getServer();
        BlockPos originPos = h.absolutePos(new BlockPos(7, 3, 7));
        Vec3 origin = Vec3.atBottomCenterOf(originPos);
        // 半径内に地面を作る。遠方のチャンクに触れる必要がないためGameTestは軽量。
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            BlockPos feet = originPos.offset(dx, 0, dz);
            level.setBlock(feet.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            level.setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 3);
        }
        RandomSource random = RandomSource.create(51799L);
        BlockPos destination = null;
        for (int i = 0; i < 80 && destination == null; i++)
            destination = YamatoRandomDestinations.sample(level, origin, 3, 0, random);
        h.assertTrue(destination != null, "Surface search failed despite a wide grass platform");
        h.assertTrue(YamatoRandomDestinations.safeLanding(level, destination),
                "Randomly selected destination violated the safety rule");
        Vec3 to = Vec3.atBottomCenterOf(destination);
        Vec3 from = origin.add(7, 0, 0);
        var rifts = new YamatoRifts();
        try {
            h.assertTrue(rifts.openPair(UUID.randomUUID(), "surface", YamatoRifts.Endpoint.at(level, from, 0),
                    YamatoRifts.Endpoint.at(level, to, 0), 3, 2, 1, server) != null,
                    "Could not create a portal pair to the validated random position");
            Pig pig = h.spawn(EntityType.PIG, new BlockPos(14, 3, 7));
            pig.setNoAi(true); pig.setNoGravity(true); pig.moveTo(from.x, from.y, from.z, 0, 0);
            rifts.tick(server, level.getGameTime() + 7);
            h.assertTrue(pig.position().distanceToSqr(to) < .01,
                    "Random destination wasn't reachable through the normal portal transfer");
            h.assertTrue(level.noCollision(pig, pig.getBoundingBox()),
                    "Transferred pig is inside a solid block");
        } finally {
            rifts.releaseTickets(server);
        }
        h.succeed();
    }
}
