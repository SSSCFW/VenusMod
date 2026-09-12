package dev.ssscfw.venusmod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.storage.loot.LootTable;

public class VenusCreeper extends Creeper {
    private static final int FIRE_RADIUS = 5;
    private static final int FIRE_ATTEMPTS = 40;

    public VenusCreeper(EntityType<? extends Creeper> entityType, Level level) {
        super(entityType, level);
        VenusMobStats.apply(this, 2.0D, 0.5D);
        this.explosionRadius *= 2;
    }

    @Override
    public void tick() {
        boolean wasPrimed = !level().isClientSide && (getSwellDir() > 0 || isIgnited());
        BlockPos explosionCenter = blockPosition();

        super.tick();

        if (wasPrimed
                && !level().isClientSide
                && isRemoved()
                && getRemovalReason() == Entity.RemovalReason.DISCARDED
                && level() instanceof ServerLevel serverLevel) {
            igniteAround(serverLevel, explosionCenter);
        }
    }

    private void igniteAround(ServerLevel level, BlockPos center) {
        for (int i = 0; i < FIRE_ATTEMPTS; i++) {
            int dx = getRandom().nextInt(FIRE_RADIUS * 2 + 1) - FIRE_RADIUS;
            int dz = getRandom().nextInt(FIRE_RADIUS * 2 + 1) - FIRE_RADIUS;
            int dy = getRandom().nextInt(3) - 1;

            if (dx * dx + dz * dz > FIRE_RADIUS * FIRE_RADIUS) {
                continue;
            }

            BlockPos pos = center.offset(dx, dy, dz);
            if (!level.getBlockState(pos).isAir()) {
                continue;
            }

            if (BaseFireBlock.canBePlacedAt(level, pos, Direction.UP)) {
                level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
            }
        }
    }

    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        return EntityType.CREEPER.getDefaultLootTable();
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean causedByPlayer) {
        super.dropCustomDeathLoot(level, source, causedByPlayer);
        VenusMobStats.dropGoldNuggets(this);
    }
}
