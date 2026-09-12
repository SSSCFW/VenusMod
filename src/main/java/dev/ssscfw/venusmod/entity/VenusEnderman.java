package dev.ssscfw.venusmod.entity;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

public class VenusEnderman extends EnderMan {
    private static final int BEHIND_TELEPORT_INTERVAL = 40;
    private static final int AURA_DURATION_TICKS = 80;
    private static final int AURA_TELEPORT_INTERVAL = 8;
    private static final double AURA_RADIUS_MIN = 1.7D;
    private static final double AURA_RADIUS_MAX = 2.7D;

    private int teleportAuraTicks;

    public VenusEnderman(EntityType<? extends EnderMan> entityType, Level level) {
        super(entityType, level);
        VenusMobStats.apply(this, 1.2D, 0.5D);
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            return;
        }

        LivingEntity target = getTarget();
        if (!(target instanceof Player player) || !player.isAlive()) {
            teleportAuraTicks = 0;
            return;
        }

        if (teleportAuraTicks > 0) {
            teleportAuraTicks--;
            if (teleportAuraTicks % AURA_TELEPORT_INTERVAL == 0) {
                teleportAround(player, false);
            }
        } else if (tickCount % BEHIND_TELEPORT_INTERVAL == 0 && distanceToSqr(player) <= 16.0D * 16.0D) {
            teleportBehind(player);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !level().isClientSide && target instanceof Player player) {
            teleportAuraTicks = AURA_DURATION_TICKS;
            teleportAround(player, true);
        }
        return hit;
    }

    private void teleportBehind(Player player) {
        double yawRadians = Math.toRadians(player.getYRot());
        double x = player.getX() + Math.sin(yawRadians) * 2.2D;
        double z = player.getZ() - Math.cos(yawRadians) * 2.2D;
        teleportNearPlayer(player, x, z);
    }

    private void teleportAround(Player player, boolean preferRearHemisphere) {
        double baseAngle = Math.toRadians(player.getYRot());
        double randomAngle;

        if (preferRearHemisphere) {
            randomAngle = baseAngle + Math.PI + (getRandom().nextDouble() - 0.5D) * Math.PI;
        } else {
            randomAngle = getRandom().nextDouble() * Math.PI * 2.0D;
        }

        double radius = AURA_RADIUS_MIN
                + getRandom().nextDouble() * (AURA_RADIUS_MAX - AURA_RADIUS_MIN);
        double x = player.getX() + Math.sin(randomAngle) * radius;
        double z = player.getZ() - Math.cos(randomAngle) * radius;
        teleportNearPlayer(player, x, z);
    }

    private void teleportNearPlayer(Player player, double x, double z) {
        double y = player.getY();
        if (randomTeleport(x, y, z, true)) {
            getLookControl().setLookAt(player, 360.0F, 360.0F);

            Vec3 towardPlayer = player.position().subtract(position());
            if (towardPlayer.lengthSqr() > 1.0E-4D) {
                setYRot((float) (Math.toDegrees(Math.atan2(towardPlayer.z, towardPlayer.x)) - 90.0D));
            }
        }
    }

    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        return EntityType.ENDERMAN.getDefaultLootTable();
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean causedByPlayer) {
        super.dropCustomDeathLoot(level, source, causedByPlayer);
        VenusMobStats.dropGoldNuggets(this);
    }
}
