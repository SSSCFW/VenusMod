package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.world.BossAltarBlock;
import dev.ssscfw.venusmod.world.BossAltarBlockEntity;
import dev.ssscfw.venusmod.world.VenusDimensionContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 実際に配置したボス施設の祭壇・召喚空間と旧装置の除去を4方向から検査。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BossStructureGameTests {
    private static final BlockPos GENERAL_ALTAR = new BlockPos(20, 4, 47);
    private static final BlockPos APHRODITE_ALTAR = new BlockPos(24, 4, 41);
    private static final BlockPos APHRODITE_DECORATIVE_LIGHT = new BlockPos(24, 4, 29);
    private static final BlockPos[] APHRODITE_DECORATIVE_PEDESTALS = {
            new BlockPos(12, 4, 39), new BlockPos(36, 4, 39), new BlockPos(24, 4, 51)
    };

    @GameTest(template = "venus_general_fortress", rotationSteps = 0)
    public static void venusGeneralFortressRotation0(GameTestHelper helper) {
        verifyGeneralFortress(helper);
    }

    @GameTest(template = "venus_general_fortress", rotationSteps = 1)
    public static void venusGeneralFortressRotation1(GameTestHelper helper) {
        verifyGeneralFortress(helper);
    }

    @GameTest(template = "venus_general_fortress", rotationSteps = 2)
    public static void venusGeneralFortressRotation2(GameTestHelper helper) {
        verifyGeneralFortress(helper);
    }

    @GameTest(template = "venus_general_fortress", rotationSteps = 3)
    public static void venusGeneralFortressRotation3(GameTestHelper helper) {
        verifyGeneralFortress(helper);
    }

    @GameTest(template = "aphrodite_sanctum", rotationSteps = 0)
    public static void aphroditeSanctumRotation0(GameTestHelper helper) {
        verifyAphroditeSanctum(helper);
    }

    @GameTest(template = "aphrodite_sanctum", rotationSteps = 1)
    public static void aphroditeSanctumRotation1(GameTestHelper helper) {
        verifyAphroditeSanctum(helper);
    }

    @GameTest(template = "aphrodite_sanctum", rotationSteps = 2)
    public static void aphroditeSanctumRotation2(GameTestHelper helper) {
        verifyAphroditeSanctum(helper);
    }

    @GameTest(template = "aphrodite_sanctum", rotationSteps = 3)
    public static void aphroditeSanctumRotation3(GameTestHelper helper) {
        verifyAphroditeSanctum(helper);
    }

    @GameTest(template = "test/empty")
    public static void bossStructuresAreRegistered(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        Registry<Structure> structures = registries.registryOrThrow(Registries.STRUCTURE);
        Registry<StructureTemplatePool> templatePools = registries.registryOrThrow(Registries.TEMPLATE_POOL);
        Registry<StructureSet> structureSets = registries.registryOrThrow(Registries.STRUCTURE_SET);

        assertWorldgenRegistration(helper, structures, templatePools, structureSets,
                "venus_general_fortress", "venus_general_fortress/start", "venus_general_keep");
        assertWorldgenRegistration(helper, structures, templatePools, structureSets,
                "aphrodite_sanctum", "aphrodite_sanctum/start", "aphrodite_reactor");
        helper.succeed();
    }

    private static void verifyGeneralFortress(GameTestHelper helper) {
        BlockPos altarPos = templatePos(helper, GENERAL_ALTAR);
        assertAltar(helper, altarPos, VenusDimensionContent.GENERAL_ALTAR.get(), BossAltarBlock.Kind.GENERAL);
        assertBossClearance(helper, VenusDimensionContent.GENERAL.get(), altarPos, "Venus General");
        helper.succeed();
    }

    private static void verifyAphroditeSanctum(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos altarPos = templatePos(helper, APHRODITE_ALTAR);
        assertAltar(helper, altarPos, VenusDimensionContent.APHRODITE_ALTAR.get(), BossAltarBlock.Kind.APHRODITE);
        assertBossClearance(helper, VenusDimensionContent.APHRODITE_CORE.get(), altarPos, "Aphrodite Core");

        for (BlockPos pedestal : APHRODITE_DECORATIVE_PEDESTALS) {
            BlockPos pos = templatePos(helper, pedestal);
            helper.assertTrue(level.getBlockState(pos).is(Blocks.CRYING_OBSIDIAN),
                    "Old pressure core is now decorative at " + pedestal);
        }
        BlockPos lightPos = templatePos(helper, APHRODITE_DECORATIVE_LIGHT);
        helper.assertTrue(level.getBlockState(lightPos).is(Blocks.SEA_LANTERN),
                "Old shield controller is now decorative lighting");
        helper.assertTrue(level.getBlockEntity(lightPos) == null,
                "The decorative block must not restore the old controller block entity");
        helper.succeed();
    }

    private static BlockPos templatePos(GameTestHelper helper, BlockPos localPos) {
        // GameTestの原点はテンプレートの1ブロック下にあるストラクチャーブロック。
        return helper.absolutePos(localPos.above());
    }

    private static void assertAltar(GameTestHelper helper, BlockPos altarPos, Block expectedBlock,
                                    BossAltarBlock.Kind expectedKind) {
        BlockState state = helper.getLevel().getBlockState(altarPos);
        helper.assertTrue(state.is(expectedBlock), "Expected " + expectedKind + " altar at " + altarPos);
        BlockEntity altarEntity = helper.getLevel().getBlockEntity(altarPos);
        helper.assertTrue(altarEntity instanceof BossAltarBlockEntity,
                expectedKind + " altar block entity was not restored by the structure template");
        helper.assertTrue(altarEntity.getType() == VenusDimensionContent.BOSS_ALTAR_ENTITY.get(),
                expectedKind + " altar has the wrong block entity type");
        helper.assertTrue(((BossAltarBlock) state.getBlock()).kind() == expectedKind,
                "Wrong boss kind at altar " + altarPos);
    }

    private static void assertBossClearance(GameTestHelper helper, EntityType<? extends Mob> entityType,
                                            BlockPos altarPos, String bossName) {
        Mob boss = entityType.create(helper.getLevel());
        helper.assertTrue(boss != null, bossName + " entity type could not be created");
        boss.moveTo(altarPos.getX() + 0.5D, altarPos.getY() + 1.0D, altarPos.getZ() + 0.5D, 180.0F, 0.0F);
        helper.assertTrue(helper.getLevel().noCollision(boss), bossName + " has no collision-free altar spawn space");
        boss.discard();
    }

    private static void assertWorldgenRegistration(GameTestHelper helper, Registry<Structure> structures,
                                                   Registry<StructureTemplatePool> templatePools,
                                                   Registry<StructureSet> structureSets,
                                                   String structurePath, String poolPath, String setPath) {
        var structureId = VenusDimensionContent.id(structurePath);
        var poolId = VenusDimensionContent.id(poolPath);
        var setId = VenusDimensionContent.id(setPath);
        helper.assertTrue(structures.containsKey(structureId), "Worldgen structure is not registered: " + structureId);
        Structure structure = structures.get(structureId);
        helper.assertTrue(structure.type() == StructureType.JIGSAW, "Boss structure is not a jigsaw: " + structureId);
        helper.assertTrue(templatePools.containsKey(poolId), "Worldgen template pool is not registered: " + poolId);
        helper.assertTrue(structureSets.containsKey(setId), "Worldgen structure set is not registered: " + setId);
        StructureSet set = structureSets.get(setId);
        helper.assertTrue(set.structures().stream().anyMatch(entry ->
                        entry.structure().unwrapKey().map(key -> key.location().equals(structureId)).orElse(false)),
                "Worldgen structure set does not include " + structureId + ": " + setId);
    }
}
