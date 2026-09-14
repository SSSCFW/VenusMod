package dev.ssscfw.venusmod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.EventHooks;

/** 成功したスポーンと同時に通常ブロックに変わるため、撃破後も再起動後も再召喚しない。 */
public final class GuardianAltarBlockEntity extends BlockEntity {
    public GuardianAltarBlockEntity(BlockPos pos, BlockState state) { super(VenusDimensionContent.ALTAR_ENTITY.get(), pos, state); }
    public static void tick(Level level, BlockPos pos, BlockState state, GuardianAltarBlockEntity altar) {
        if (!(level instanceof ServerLevel server) || server.getGameTime() % 20 != 0
                || server.getDifficulty() == Difficulty.PEACEFUL
                || !server.hasNearbyAlivePlayer(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 12)) return;
        VenusGuardian guardian = VenusDimensionContent.GUARDIAN.get().create(server);
        if (guardian == null) return;
        guardian.moveTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 180, 0);
        if (!server.noCollision(guardian)) return;
        EventHooks.finalizeMobSpawn(guardian, server, server.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
        guardian.setPersistenceRequired();
        if (server.addFreshEntity(guardian)) server.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
    }
}
