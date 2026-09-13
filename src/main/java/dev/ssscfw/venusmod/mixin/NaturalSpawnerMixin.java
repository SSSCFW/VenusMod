package dev.ssscfw.venusmod.mixin;

import dev.ssscfw.venusmod.world.VenusDimensionContent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
    @Redirect(method = "spawnForChunk", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/NaturalSpawner;spawnCategoryForChunk(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V"))
    private static void venusmod$surfaceAttempts(MobCategory category, ServerLevel level, LevelChunk chunk,
            NaturalSpawner.SpawnPredicate predicate, NaturalSpawner.AfterSpawnCallback callback,
            ServerLevel enclosingLevel, LevelChunk enclosingChunk, NaturalSpawner.SpawnState state,
            boolean friendly, boolean hostile, boolean persistent) {
        NaturalSpawner.spawnCategoryForChunk(category, level, chunk, predicate, callback);
        if (category != MobCategory.MONSTER || !level.dimension().equals(VenusDimensionContent.VENUS)) return;
        SpawnStateAccessor cap = (SpawnStateAccessor) (Object) state;
        // 通常1回 + 地表限定2回。洞窟・他ディメンション・他Mobカテゴリは変更しない。
        // バニラと同じ乱数による位置選択、距離・明るさ・衝突・地域上限判定を維持する。
        NaturalSpawner.SpawnPredicate surface = (type, pos, candidateChunk) ->
                pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ())
                        && cap.venusmod$canSpawnForCategory(category, new ChunkPos(pos))
                        && predicate.test(type, pos, candidateChunk);
        for (int extra = 0; extra < 2 && cap.venusmod$canSpawnForCategory(category, chunk.getPos()); extra++)
            NaturalSpawner.spawnCategoryForChunk(category, level, chunk, surface, callback);
    }
}
