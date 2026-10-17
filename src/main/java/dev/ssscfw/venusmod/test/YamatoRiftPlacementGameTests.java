package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.yamato.YamatoRifts;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 埋まった保存地点から通れる出口を探し、保存値や地形を壊さない回帰試験。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class YamatoRiftPlacementGameTests {
    private YamatoRiftPlacementGameTests() {}

    private static YamatoRifts.Endpoint resolve(MinecraftServer server, YamatoRifts.Endpoint target)
            throws ReflectiveOperationException {
        Method method = YamatoRifts.class.getDeclaredMethod("resolveBuriedEndpoint",
                MinecraftServer.class, YamatoRifts.Endpoint.class);
        method.setAccessible(true);
        return (YamatoRifts.Endpoint)method.invoke(null, server, target);
    }

    private static boolean safe(GameTestHelper h, YamatoRifts.Endpoint endpoint) {
        Vec3 p = endpoint.feet();
        return h.getLevel().noCollision(null, new AABB(p.x - .31, p.y, p.z - .31,
                p.x + .31, p.y + 1.85, p.z + .31));
    }

    @GameTest(template = "test/wide_empty")
    public static void clearDestinationPreservesExactCoordinatesAndOrientation(GameTestHelper h)
            throws ReflectiveOperationException {
        var level = h.getLevel();
        Vec3 saved = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8, 3, 8)));
        var original = YamatoRifts.Endpoint.at(level, saved, 42, -25, 1.5);
        var resolved = resolve(level.getServer(), original);
        h.assertTrue(resolved.feet().equals(saved), "Unobstructed saved location moved unexpectedly");
        h.assertTrue(resolved.id().equals(original.id()) && resolved.yaw() == original.yaw()
                && resolved.pitch() == original.pitch() && resolved.size() == original.size(),
                "Portal identity, direction or scale changed unexpectedly");
        h.succeed();
    }

    @GameTest(template = "test/wide_empty")
    public static void buriedDestinationMovesIntoNearbyAirAndTeleports(GameTestHelper h)
            throws ReflectiveOperationException {
        var level = h.getLevel(); var server = level.getServer();
        Vec3 start = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 3, 2)));
        Vec3 saved = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(9, 3, 9)));
        BlockPos base = BlockPos.containing(saved);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            for (int dy = 0; dy <= 3; dy++)
                level.setBlock(base.offset(dx, dy, dz), Blocks.STONE.defaultBlockState(), 3);
        var original = YamatoRifts.Endpoint.at(level, saved, 60, 35, 1.0);
        h.assertTrue(!safe(h, original), "Fixture destination was not obstructed");
        var moved = resolve(server, original);
        h.assertTrue(!moved.feet().equals(saved) && moved.feet().distanceTo(saved) <= 12,
                "Blocked destination was not relocated into nearby space");
        h.assertTrue(safe(h, moved), "Relocated destination is still inside blocks");
        h.assertTrue(moved.id().equals(original.id()) && moved.yaw() == original.yaw()
                && moved.pitch() == original.pitch() && moved.size() == original.size(),
                "Relocation changed rift identity, direction or size");
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            for (int dy = 0; dy <= 3; dy++)
                h.assertTrue(level.getBlockState(base.offset(dx, dy, dz)).is(Blocks.STONE),
                        "Resolving embedded destination broke terrain");
        var rifts = new YamatoRifts();
        try {
            h.assertTrue(rifts.openPair(UUID.randomUUID(), "rescued", YamatoRifts.Endpoint.at(level, start, 60, 35, 1),
                    moved, 3, 2, 1, server) != null, "Portal did not open at the safe resolved position");
            Pig pig = h.spawn(EntityType.PIG, new BlockPos(2, 3, 2));
            pig.setNoAi(true); pig.setNoGravity(true); pig.moveTo(start.x, start.y, start.z, 0, 0);
            rifts.tick(server, level.getGameTime() + 7);
            h.assertTrue(pig.position().distanceToSqr(moved.feet()) < .01,
                    "Transferring through buried destination did not arrive at nearby exit");
            h.assertTrue(level.noCollision(pig, pig.getBoundingBox()), "Animal arrived embedded in solid blocks");
        } finally {
            rifts.releaseTickets(server);
        }
        h.succeed();
    }
}
