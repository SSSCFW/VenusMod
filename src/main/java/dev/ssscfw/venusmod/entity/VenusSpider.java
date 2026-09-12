package dev.ssscfw.venusmod.entity;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;

public class VenusSpider extends Spider {
    public VenusSpider(EntityType<? extends Spider> entityType, Level level) {
        super(entityType, level);
        VenusMobStats.apply(this, 1.2D, 0.5D);
    }

    @Override
    public ResourceKey<LootTable> getLootTable() {
        return EntityType.SPIDER.getDefaultLootTable();
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean causedByPlayer) {
        super.dropCustomDeathLoot(level, source, causedByPlayer);
        VenusMobStats.dropGoldNuggets(this);
    }
}
