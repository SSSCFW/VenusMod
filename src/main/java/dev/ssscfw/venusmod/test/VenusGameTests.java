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
        helper.assertTrue(helper.getLevel().getServer().getLevel(VenusDimensionContent.VENUS) != null,
                "Venus dimension was not loaded through Minecraft codecs");
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
}
