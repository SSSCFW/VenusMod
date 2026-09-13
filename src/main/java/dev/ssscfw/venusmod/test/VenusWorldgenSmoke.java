package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.world.VenusDimensionContent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** 通常プレイでは無効。CI専用プロパティ時のみ新規ワールドの実生成を検証して終了する。 */
@EventBusSubscriber(modid = VenusMod.MOD_ID)
public final class VenusWorldgenSmoke {
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("venusmod.verifyWorldgen")) return;
        var server = event.getServer();
        var venus = server.getLevel(VenusDimensionContent.VENUS);
        if (venus == null) throw new IllegalStateException("Venus ServerLevel is missing");
        var chunk = venus.getChunk(0, 0);
        int y = venus.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
        if (y <= venus.getMinBuildHeight() || chunk == null) throw new IllegalStateException("Venus terrain did not generate");
        System.out.println("VENUS_WORLDGEN_SMOKE_PASSED: real dimension loaded and chunk generated at surface " + y);
        server.execute(() -> server.halt(false));
    }
}
