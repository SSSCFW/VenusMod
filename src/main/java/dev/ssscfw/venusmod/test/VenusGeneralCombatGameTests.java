package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.entity.VenusGeneral;
import dev.ssscfw.venusmod.entity.ai.VenusGeneralHitStreak;
import dev.ssscfw.venusmod.entity.ai.VenusGeneralTeleportRules;
import dev.ssscfw.venusmod.world.VenusDimensionContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** 金星将軍の連撃中断・安全テレポートのサーバー側回帰テスト。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VenusGeneralCombatGameTests {
    private VenusGeneralCombatGameTests() {
    }

    @GameTest(template = "test/empty")
    public static void comboEndsImmediatelyAfterVictimDies(GameTestHelper helper) {
        var level = helper.getLevel();
        VenusGeneral general = VenusDimensionContent.GENERAL.get().create(level);
        Zombie victim = EntityType.ZOMBIE.create(level);
        helper.assertTrue(general != null && victim != null, "Could not create test mobs");

        BlockPos start = helper.absolutePos(new BlockPos(3, 4, 3));
        general.moveTo(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D, 0.0F, 0.0F);
        victim.moveTo(start.getX() + 3.5D, start.getY(), start.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(general);
        level.addFreshEntity(victim);
        victim.setNoAi(true);
        general.setTarget(victim);
        general.startBladeCombo(victim);
        helper.assertTrue(general.isBladeTechniqueActive(), "Failed to begin test combo");

        victim.discard();
        general.tick();
        helper.assertTrue(!general.isBladeTechniqueActive(),
                "コンボ途中でターゲットが死んだのに攻撃を続けた");
        helper.succeed();
    }

    @GameTest(template = "test/empty")
    public static void comboEndsWhenTargetGoesBehindWall(GameTestHelper helper) {
        var level = helper.getLevel();
        VenusGeneral general = VenusDimensionContent.GENERAL.get().create(level);
        Zombie victim = EntityType.ZOMBIE.create(level);
        helper.assertTrue(general != null && victim != null, "Could not create test mobs");

        BlockPos start = helper.absolutePos(new BlockPos(3, 4, 3));
        general.moveTo(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D, 0.0F, 0.0F);
        victim.moveTo(start.getX() + 4.5D, start.getY(), start.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(general);
        level.addFreshEntity(victim);
        victim.setNoAi(true);
        general.setTarget(victim);
        general.startBladeCombo(victim);

        for (int y = 0; y < 3; y++) {
            level.setBlock(start.offset(2, y, 0), Blocks.STONE.defaultBlockState(), 3);
        }
        general.tick();
        helper.assertTrue(!general.isBladeTechniqueActive(),
                "ターゲットとの視線が遮られてもコンボが中断しない");
        helper.succeed();
    }

    @GameTest(template = "test/empty")
    public static void teleportHitStreakNeedsEightToFifteenHits(GameTestHelper helper) {
        UUID first = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID second = UUID.fromString("22222222-2222-2222-2222-222222222222");
        VenusGeneralHitStreak streak = new VenusGeneralHitStreak();
        for (int i = 1; i < 8; i++) {
            helper.assertTrue(!streak.recordHit(first, 5L * i, () -> 8),
                    "8回より前にテレポート判定が発生した");
        }
        helper.assertTrue(streak.recordHit(first, 40, () -> 8),
                "8回目でテレポート判定が発生しない");

        streak.reset();
        for (int i = 1; i < 15; i++) {
            helper.assertTrue(!streak.recordHit(first, 100 + 5L * i, () -> 15),
                    "15回より前にテレポート判定が発生した");
        }
        helper.assertTrue(streak.recordHit(first, 175, () -> 15),
                "15回目でテレポート判定が発生しない");

        streak.reset();
        for (int i = 0; i < 7; i++) {
            streak.recordHit(first, 200 + i * 5L, () -> 8);
        }
        helper.assertTrue(!streak.recordHit(first, 291, () -> 8),
                "3秒を超えた被弾間隔を連続扱いしている");
        helper.assertTrue(!streak.recordHit(second, 292, () -> 8),
                "別の攻撃者のダメージを連続扱いしている");
        helper.succeed();
    }

    // 着地点は相対x=7.5。7幅のテンプレートではx=7の外周バリア内になってしまう。
    @GameTest(template = "test/wide_empty")
    public static void teleportRejectsWallAndMissingFloor(GameTestHelper helper) {
        var level = helper.getLevel();
        VenusGeneral general = VenusDimensionContent.GENERAL.get().create(level);
        helper.assertTrue(general != null, "Could not create Venus General");
        helper.assertTrue(general.getBladeTechniqueApproachSpeedMultiplier() == 0.45D,
                "将軍の攻撃中移動速度は通常時の45%");

        BlockPos start = helper.absolutePos(new BlockPos(2, 4, 2));
        for (int dx = 0; dx <= 6; dx++) {
            level.setBlock(start.offset(dx, -1, 0), Blocks.STONE.defaultBlockState(), 3);
        }
        general.moveTo(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(general);
        Vec3 destination = new Vec3(start.getX() + 5.5D, start.getY(), start.getZ() + 0.5D);

        helper.assertTrue(VenusGeneralTeleportRules.canTeleport(level, general, destination),
                "平坦な安全地形でテレポートを拒否した");
        level.setBlock(start.offset(3, 0, 0), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(start.offset(3, 1, 0), Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(!VenusGeneralTeleportRules.canTeleport(level, general, destination),
                "着地点が空気でも間に壁があればテレポートできてはいけない");

        level.setBlock(start.offset(3, 0, 0), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(start.offset(3, 1, 0), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(start.offset(5, -1, 0), Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(!VenusGeneralTeleportRules.canTeleport(level, general, destination),
                "足場のない場所へはテレポートできてはいけない");
        helper.succeed();
    }
}
