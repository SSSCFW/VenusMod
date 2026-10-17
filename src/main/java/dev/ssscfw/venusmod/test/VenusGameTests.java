package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.entity.VenusGeneral;
import dev.ssscfw.venusmod.entity.VenusZombie;
import dev.ssscfw.venusmod.entity.ai.VenusBladeMotionTimeline;
import dev.ssscfw.venusmod.world.VenusDimensionContent;
import dev.ssscfw.venusmod.world.VenusPortalFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VenusGameTests {
    @GameTest(template = "test/empty")
    public static void dimensionLoads(GameTestHelper helper) {
        var resource = helper.getLevel().getServer().getResourceManager().getResource(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, "dimension/venus.json")).orElseThrow();
        try (var reader = resource.openAsReader()) {
            var json = com.google.gson.JsonParser.parseReader(reader);
            var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, helper.getLevel().registryAccess());
            var stem = net.minecraft.world.level.dimension.LevelStem.CODEC.parse(ops, json).getOrThrow();
            helper.assertTrue(stem.type().value().monsterSettings().monsterSpawnBlockLightLimit() == 0, "Torch-safe light setting lost");
        } catch (java.io.IOException exception) { throw new RuntimeException(exception); }
        helper.succeed();
    }

    @GameTest(template = "test/empty")
    public static void venusGeneralHasSlashBladePlayerAttributes(GameTestHelper helper) {
        VenusGeneral general = VenusDimensionContent.GENERAL.get().create(helper.getLevel());
        helper.assertTrue(general != null, "Venus General creation failed");
        helper.assertTrue(general.getAttribute(Attributes.SWEEPING_DAMAGE_RATIO) != null,
                "Venus General must register SWEEPING_DAMAGE_RATIO because SlashBlade AttackHelper reads it from Mob owners");
        helper.assertTrue(general.getAttributeValue(Attributes.SWEEPING_DAMAGE_RATIO) == 0.0D,
                "Venus General sweeping ratio must remain neutral");
        helper.assertTrue(general.getAttribute(Attributes.ENTITY_INTERACTION_RANGE) != null,
                "Venus General must register interaction range for SlashBlade reach");
        helper.assertTrue(general.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) >= 7.0D,
                "Venus General SlashBlade interaction range regressed");
        helper.succeed();
    }

    @GameTest(template = "test/empty")
    public static void bladeComboSwingTiming(GameTestHelper helper) {
        int[] previous = {0, 4, 8, 11, 15, 17};
        int[] expected = {0, 6, 13, 18, 25, 29};
        helper.assertTrue(VenusZombie.getBladeComboHitCount() == expected.length,
                "抜刀剣の6連撃が維持されていない");
        for (int i = 0; i < expected.length; i++) {
            helper.assertTrue(VenusZombie.getBladeComboHitTick(i) == expected[i],
                    "抜刀剣の攻撃と本体アニメーションが使用するtickがずれている");
            if (i > 0) {
                helper.assertTrue(expected[i] > expected[i - 1],
                        "攻撃間隔は正である必要がある");
            }
        }
        // 5回の攻撃間の増分が合計12tick = 平均2.4tick（0.12秒）。
        helper.assertTrue(expected[expected.length - 1] - previous[previous.length - 1] == 12,
                "斬撃間隔の増分が平均0.12秒でなくなった");
        helper.assertTrue(VenusZombie.BLADE_COMBO_END_TICK >= expected[expected.length - 1] + 4,
                "最後の斬撃後の回復アニメーション時間が不足している");
        helper.succeed();
    }

    @GameTest(template = "test/empty")
    public static void bladeUsesOriginalPlayerVmdTimeline(GameTestHelper helper) {
        // SlashBladeのプレイヤー操作ではA1(1撃)→A2(1撃)→A3(2撃)→A4(2撃)。
        int technique = VenusZombie.BLADE_TECHNIQUE_COMBO;
        helper.assertTrue(VenusBladeMotionTimeline.sample(technique, 0.0F).motion()
                        == VenusBladeMotionTimeline.Motion.COMBO_A1,
                "1撃目がSlashBladeプレイヤーのcombo_a1ではない");
        helper.assertTrue(VenusBladeMotionTimeline.sample(technique, 6.0F).motion()
                        == VenusBladeMotionTimeline.Motion.COMBO_A2,
                "2撃目がSlashBladeプレイヤーのcombo_a2ではない");
        helper.assertTrue(VenusBladeMotionTimeline.sample(technique, 13.0F).motion()
                        == VenusBladeMotionTimeline.Motion.COMBO_A3,
                "3撃目がSlashBladeプレイヤーのcombo_a3ではない");
        helper.assertTrue(VenusBladeMotionTimeline.sample(technique, 18.0F).motion()
                        == VenusBladeMotionTimeline.Motion.COMBO_A3,
                "4撃目で原作combo_a3が途中から別クリップへ切り替わっている");
        helper.assertTrue(VenusBladeMotionTimeline.sample(technique, 25.0F).motion()
                        == VenusBladeMotionTimeline.Motion.COMBO_A4,
                "5撃目がSlashBladeプレイヤーのcombo_a4ではない");
        helper.assertTrue(VenusBladeMotionTimeline.sample(technique, 29.0F).motion()
                        == VenusBladeMotionTimeline.Motion.COMBO_A4,
                "6撃目で原作combo_a4が途中から別クリップへ切り替わっている");

        helper.assertTrue(VenusBladeMotionTimeline.sample(technique, 13.0F).frameOffset() == 2.0F
                        && VenusBladeMotionTimeline.sample(technique, 18.0F).frameOffset() == 6.0F,
                "SlashBladeプレイヤーのcombo_a3の2連撃キーフレームと同期していない");
        helper.assertTrue(VenusBladeMotionTimeline.sample(technique, 25.0F).frameOffset() == 8.0F
                        && VenusBladeMotionTimeline.sample(technique, 29.0F).frameOffset() == 9.0F,
                "SlashBladeプレイヤーのcombo_a4の2連撃キーフレームと同期していない");
        helper.assertTrue(VenusBladeMotionTimeline.sample(
                        VenusZombie.BLADE_TECHNIQUE_RAPID_SLASH, 2.0F).motion()
                        == VenusBladeMotionTimeline.Motion.RAPID_SLASH,
                "高速斬撃でプレイヤーのrapid_slashを使っていない");
        helper.assertTrue(VenusBladeMotionTimeline.sample(
                        VenusZombie.BLADE_TECHNIQUE_IAI, 2.0F).motion()
                        == VenusBladeMotionTimeline.Motion.JUDGEMENT_CUT,
                "居合いでプレイヤーのjudgement_cutを使っていない");
        helper.succeed();
    }

    @GameTest(template = "test/empty")
    public static void portalAcceptsMixedGoldAndDummyFrame(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos inside = helper.absolutePos(new BlockPos(4, 3, 4));
        for (int u = -1; u <= 2; u++) {
            for (int v = -1; v <= 3; v++) {
                BlockPos pos = inside.offset(u, v, 0);
                boolean border = u == -1 || u == 2 || v == -1 || v == 3;
                if (border) {
                    level.setBlock(pos, ((u + v) & 1) == 0
                            ? Blocks.GOLD_BLOCK.defaultBlockState()
                            : VenusDimensionContent.GOLD_BLOCK_DUMMY.get().defaultBlockState(), 3);
                } else {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        VenusPortalFrame frame = VenusPortalFrame.find(level, inside);
        helper.assertTrue(frame != null && frame.width() == 2 && frame.height() == 3,
                "金ブロックと金ブロックダミーの混在枠を認識できない");
        helper.assertTrue(VenusDimensionContent.GOLD_BLOCK_DUMMY.get().getLootTable() == BuiltInLootTables.EMPTY,
                "金ブロックダミーは破壊/爆発でLootを生成してはいけない");
        helper.succeed();
    }

    @GameTest(template = "test/empty")
    public static void machineNeighbourFaces(GameTestHelper helper) {
        if (!ModList.get().isLoaded("create")) { helper.assertTrue(!Boolean.getBoolean("venusmod.requireCreateTests"), "Create runtime missing in required test configuration"); System.out.println("Machine face checks skipped: optional Create is absent (no-Create compatibility run)."); helper.succeed(); return; }
        dev.ssscfw.venusmod.compat.create.MachineRenderChecks.verify(helper);
    }

    @GameTest(template = "test/empty")
    public static void phase2Recipes(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        for (String name : new String[]{"portable_life_support", "pressure_alloy_blend", "pressure_alloy_smelting", "pressure_alloy_blasting", "pressure_helmet", "pressure_chestplate", "pressure_leggings", "pressure_boots", "venus_glass"}) {
            helper.assertTrue(manager.byKey(dev.ssscfw.venusmod.registry.VenusPhase2.id(name)).isPresent(), "Recipe codec failed: " + name);
        }
        boolean hasCondenser = manager.byKey(dev.ssscfw.venusmod.registry.VenusPhase2.id("atmospheric_condenser")).isPresent();
        helper.assertTrue(hasCondenser == ModList.get().isLoaded("create"), "Condenser recipe Create condition failed");
        helper.succeed();
    }

    @GameTest(template = "test/empty")
    public static void condenserAutomation(GameTestHelper helper) {
        if (!ModList.get().isLoaded("create")) { helper.succeed(); return; }
        dev.ssscfw.venusmod.compat.create.CondenserChecks.verify(helper);
    }
}
