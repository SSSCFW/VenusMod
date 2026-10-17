package dev.ssscfw.venusmod.test;

import com.mojang.datafixers.util.Pair;
import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.world.BossAltarBlock;
import dev.ssscfw.venusmod.world.BossAltarBlockEntity;
import dev.ssscfw.venusmod.world.VenusDimensionContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
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
        verifyNaturalBossAltar(venus, "venus_general_fortress", new BlockPos(20, 4, 47),
                VenusDimensionContent.GENERAL_ALTAR.get(), BossAltarBlock.Kind.GENERAL);
        verifyNaturalBossAltar(venus, "aphrodite_sanctum", new BlockPos(24, 4, 41),
                VenusDimensionContent.APHRODITE_ALTAR.get(), BossAltarBlock.Kind.APHRODITE);
        System.out.println("VENUS_WORLDGEN_SMOKE_PASSED: real dimension loaded at surface " + y
                + " and both natural boss altars generated");
        server.execute(() -> server.halt(false));
    }

    private static void verifyNaturalBossAltar(ServerLevel venus, String structurePath, BlockPos templateAltar,
                                               Block expectedBlock, BossAltarBlock.Kind expectedKind) {
        ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE,
                VenusDimensionContent.id(structurePath));
        Registry<Structure> structures = venus.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Holder<Structure> structure = structures.getHolderOrThrow(structureKey);
        Pair<BlockPos, Holder<Structure>> nearest = venus.getChunkSource().getGenerator()
                .findNearestMapStructure(venus, HolderSet.direct(structure), BlockPos.ZERO, 64, false);
        if (nearest == null || !nearest.getSecond().is(structureKey)) {
            throw new IllegalStateException("Could not find natural Venus structure " + structureKey + " within search radius 64");
        }

        BlockPos locatePos = nearest.getFirst();
        ChunkAccess structureChunk = venus.getChunkSource().getChunk(locatePos.getX() >> 4, locatePos.getZ() >> 4,
                ChunkStatus.STRUCTURE_STARTS, true);
        if (structureChunk == null) {
            throw new IllegalStateException("Natural Venus structure start chunk is unavailable: " + structureKey + " at " + locatePos);
        }
        StructureStart start = structureChunk.getStartForStructure(structure.value());
        if (start == null || !start.isValid()) {
            throw new IllegalStateException("Natural Venus structure has no valid start: " + structureKey + " at " + locatePos);
        }

        PoolElementStructurePiece rootPiece = start.getPieces().stream()
                .filter(PoolElementStructurePiece.class::isInstance)
                .map(PoolElementStructurePiece.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Natural Venus structure has no pool element piece: " + structureKey));
        BlockPos altarPos = rootPiece.getPosition().offset(StructureTemplate.transform(
                templateAltar, Mirror.NONE, rootPiece.getRotation(), BlockPos.ZERO));

        // 祭壇のチャンクを完全生成し、自然配置されたブロックと復元済みブロックエンティティを確認する。
        venus.getChunk(altarPos.getX() >> 4, altarPos.getZ() >> 4);
        var state = venus.getBlockState(altarPos);
        if (!state.is(expectedBlock) || !(state.getBlock() instanceof BossAltarBlock altarBlock)
                || altarBlock.kind() != expectedKind) {
            throw new IllegalStateException("Natural " + structureKey + " altar mismatch at " + altarPos
                    + ": expected " + expectedKind + ", found " + state);
        }
        var blockEntity = venus.getBlockEntity(altarPos);
        if (!(blockEntity instanceof BossAltarBlockEntity altarEntity)
                || altarEntity.getType() != VenusDimensionContent.BOSS_ALTAR_ENTITY.get()) {
            throw new IllegalStateException("Natural " + structureKey + " altar block entity missing or wrong at " + altarPos);
        }
        System.out.println("VENUS_WORLDGEN_STRUCTURE_PASSED: " + structureKey + " altar at " + altarPos);
    }
}
