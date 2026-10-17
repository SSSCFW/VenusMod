package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.entity.AphroditeCore;
import dev.ssscfw.venusmod.entity.BossCombatRules;
import dev.ssscfw.venusmod.world.VenusDimensionContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Create・圧力コア・制御装置なしでのアフロディーテ戦とセーブ復元を確認する。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AphroditeShieldGameTests {
    @GameTest(template = "test/empty", timeoutTicks = 235)
    public static void shieldBreaksAndReturnsAfterTenSeconds(GameTestHelper helper) {
        AphroditeCore boss = helper.spawn(VenusDimensionContent.APHRODITE_CORE.get(), new BlockPos(2, 2, 2));
        boss.setNoAi(true);
        float originalHealth = boss.getHealth();
        helper.assertTrue(boss.shieldDurability() == BossCombatRules.APHRODITE_SHIELD_CAPACITY,
                "制御装置のないワールドでもシールドは満タンで始まる");

        helper.assertTrue(boss.hurt(helper.getLevel().damageSources().generic(), 50.0F),
                "通常攻撃でシールドを削れる");
        helper.assertTrue(boss.shieldDurability() == 150.0F && boss.getHealth() == originalHealth,
                "シールドが残っている間は本体のHPが減らない");
        helper.assertTrue(boss.hurt(helper.getLevel().damageSources().generic(), 200.0F),
                "強い攻撃で残りシールドを破壊できる");
        helper.assertTrue(boss.shieldDurability() == 0.0F && boss.shieldRecoveryTicks() == 200,
                "シールド破壊から10秒のタイマーを開始");
        helper.assertTrue(boss.getHealth() == originalHealth,
                "シールドを破った攻撃の余剰ダメージは本体へ貫通しない");

        helper.assertTrue(boss.hurt(helper.getLevel().damageSources().generic(), 30.0F),
                "シールド無効中の攻撃は本体に通る");
        float exposedHealth = boss.getHealth();
        helper.assertTrue(exposedHealth < originalHealth, "シールド無効中は本体のHPが減る");

        helper.runAfterDelay(190, () -> helper.assertTrue(boss.shieldDurability() == 0.0F,
                "破壊後10秒を経過するまではシールド無効"));
        helper.runAfterDelay(207, () -> {
            helper.assertTrue(boss.shieldDurability() == BossCombatRules.APHRODITE_SHIELD_CAPACITY,
                    "10秒後にはシールドが全回復している");
            helper.assertTrue(boss.hurt(helper.getLevel().damageSources().generic(), 10.0F),
                    "復活したシールドに通常攻撃を与えられる");
            helper.assertTrue(boss.getHealth() == exposedHealth,
                    "シールド復活後は本体へのダメージが止まる");
            helper.succeed();
        });
    }

    @GameTest(template = "test/empty")
    public static void brokenShieldSurvivesSavingAndLegacyStateMigrates(GameTestHelper helper) {
        AphroditeCore boss = helper.spawn(VenusDimensionContent.APHRODITE_CORE.get(), new BlockPos(2, 2, 2));
        boss.setNoAi(true);
        boss.hurt(helper.getLevel().damageSources().generic(), 200.0F);
        CompoundTag saved = new CompoundTag();
        boss.addAdditionalSaveData(saved);
        AphroditeCore loaded = VenusDimensionContent.APHRODITE_CORE.get().create(helper.getLevel());
        helper.assertTrue(loaded != null, "アフロディーテを復元できる");
        loaded.readAdditionalSaveData(saved);
        helper.assertTrue(loaded.shieldDurability() == 0.0F && loaded.shieldRecoveryTicks() == 200,
                "シールドの破壊状態と復活までの時間を保存する");

        // 旧セーブのArenaBound/ShieldUnlockedフラグによって無敵にならない。
        CompoundTag legacy = saved.copy();
        legacy.remove("ShieldDurability");
        legacy.remove("ShieldRecoveryTicks");
        legacy.putBoolean("ArenaBound", true);
        legacy.putBoolean("ShieldUnlocked", false);
        legacy.putInt("PressureCores", 3);
        loaded.readAdditionalSaveData(legacy);
        helper.assertTrue(loaded.shieldDurability() == BossCombatRules.APHRODITE_SHIELD_CAPACITY,
                "古いセーブのシールドは新仕様で初期化される");
        helper.assertTrue(loaded.hurt(helper.getLevel().damageSources().generic(), 10.0F),
                "旧セーブのアフロディーテも攻撃でシールドを削れる");
        helper.assertTrue(loaded.shieldDurability() == 190.0F, "旧セーブの攻撃が反映される");
        boss.discard();
        loaded.discard();
        helper.succeed();
    }
}
