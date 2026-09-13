package dev.ssscfw.venusmod.world;

import dev.ssscfw.venusmod.entity.AphroditeCore;
import dev.ssscfw.venusmod.entity.VenusGeneral;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.EventHooks;

/** 第二/最終ボスの進行アイテム付き一度限り召喚処理。 */
public final class BossAltarBlockEntity extends BlockEntity {
    private long nextMissingRequirementMessage;

    public BossAltarBlockEntity(BlockPos pos, BlockState state) {
        super(VenusDimensionContent.BOSS_ALTAR_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BossAltarBlockEntity altar) {
        if (!(level instanceof ServerLevel server) || server.getGameTime() % 10 != 0 || server.getDifficulty() == Difficulty.PEACEFUL) return;
        if (!(state.getBlock() instanceof BossAltarBlock block)) return;
        ServerPlayer player = server.getEntitiesOfClass(ServerPlayer.class,
                new AABB(pos).inflate(12.0D), p -> p.isAlive() && !p.isSpectator()).stream().findFirst().orElse(null);
        if (player == null) return;

        Item required = block.kind() == BossAltarBlock.Kind.GENERAL
                ? VenusDimensionContent.VENUS_CORE.get()
                : VenusDimensionContent.VENUS_SOUL_STONE.get();
        if (!hasItem(player, required)) {
            if (server.getGameTime() >= altar.nextMissingRequirementMessage) {
                String key = block.kind() == BossAltarBlock.Kind.GENERAL
                        ? "message.venusmod.general_requires_core"
                        : "message.venusmod.aphrodite_requires_soul_stone";
                player.displayClientMessage(Component.translatable(key), true);
                altar.nextMissingRequirementMessage = server.getGameTime() + 80;
            }
            return;
        }

        Mob boss;
        if (block.kind() == BossAltarBlock.Kind.GENERAL) {
            VenusGeneral general = VenusDimensionContent.GENERAL.get().create(server);
            boss = general;
        } else {
            AphroditeCore core = VenusDimensionContent.APHRODITE_CORE.get().create(server);
            if (core != null) core.bindArena(pos);
            boss = core;
        }
        if (boss == null) return;

        boss.moveTo(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 180.0F, 0.0F);
        if (!server.noCollision(boss)) return;
        EventHooks.finalizeMobSpawn(boss, server, server.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
        boss.setPersistenceRequired();
        if (server.addFreshEntity(boss)) {
            server.setBlock(pos, block.kind() == BossAltarBlock.Kind.GENERAL
                    ? Blocks.GOLD_BLOCK.defaultBlockState()
                    : Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
        }
    }

    private static boolean hasItem(ServerPlayer player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(item)) return true;
        }
        return player.getOffhandItem().is(item) || player.getMainHandItem().is(item);
    }
}
