package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.world.VenusDimensionContent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VenusGameTests {
    @GameTest(template = "test/empty")
    public static void dimensionLoads(GameTestHelper helper) {
        // GameTestServerはバニラの固定ディメンションで起動する。カスタム定義は実Codecで別に検査する。
        var resource = helper.getLevel().getServer().getResourceManager().getResource(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, "dimension/venus.json")).orElseThrow();
        try (var reader = resource.openAsReader()) {
            var json = com.google.gson.JsonParser.parseReader(reader);
            var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, helper.getLevel().registryAccess());
            var stem = net.minecraft.world.level.dimension.LevelStem.CODEC.parse(ops, json).getOrThrow();
            helper.assertTrue(stem.type().value().monsterSettings().monsterSpawnBlockLightLimit() == 0, "Torch-safe light setting lost");
        } catch (java.io.IOException exception) { throw new RuntimeException(exception); }
        helper.succeed();
    }

    @GameTest(template = "test/empty")
    public static void machineNeighbourFaces(GameTestHelper helper) {
        if (!ModList.get().isLoaded("create")) {
            helper.assertTrue(!Boolean.getBoolean("venusmod.requireCreateTests"), "Create runtime missing in required test configuration");
            System.out.println("Machine face checks skipped: optional Create is absent (no-Create compatibility run).");
            helper.succeed();
            return;
        }
        dev.ssscfw.venusmod.compat.create.MachineRenderChecks.verify(helper);
    }

    @GameTest(template = "test/empty")
    public static void phase2Recipes(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        for (String name : new String[]{"portable_life_support", "pressure_alloy_blend", "pressure_alloy_smelting", "pressure_alloy_blasting",
                "pressure_helmet", "pressure_chestplate", "pressure_leggings", "pressure_boots", "venus_glass"}) {
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
