package dev.ssscfw.venusmod.world;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/** 第一ダンジョンの守護者。専用EntityTypeなので通常の金星ゾンビには影響しない。 */
public final class VenusGuardian extends Zombie {
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1, 0.7F, 0.05F), 1.4F);
    private final ServerBossEvent boss = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
    private int slamTicks = 160;

    public VenusGuardian(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        xpReward = 100;
        setPersistenceRequired();
        setCanPickUpLoot(false);
    }
    public static AttributeSupplier.Builder attributes() {
        return Zombie.createAttributes().add(Attributes.MAX_HEALTH, 300).add(Attributes.ATTACK_DAMAGE, 18)
                .add(Attributes.ARMOR, 12).add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.FOLLOW_RANGE, 40);
    }
    @Override public void setBaby(boolean baby) { super.setBaby(false); }
    @Override protected boolean isSunSensitive() { return false; }
    @Override protected boolean convertsInWater() { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean canAttack(LivingEntity target) { return level().getDifficulty() != Difficulty.PEACEFUL && super.canAttack(target); }
    @Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); boss.addPlayer(player); }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); boss.removePlayer(player); }
    @Override public void die(DamageSource source) { boss.removeAllPlayers(); super.die(source); }
    @Override public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel server)) return;
        boss.setName(getDisplayName());
        boss.setProgress(Math.max(0, getHealth() / getMaxHealth()));
        if (!isAlive() || server.getDifficulty() == Difficulty.PEACEFUL) { setTarget(null); return; }
        if (getTarget() == null || distanceToSqr(getTarget()) > 144) return;
        slamTicks--;
        if (slamTicks <= 30 && slamTicks % 5 == 0) {
            // 衝撃波の半径を1.5秒前から表示。通常攻撃とは独立した回避可能な予告。
            for (int i = 0; i < 32; i++) {
                double angle = i * Math.PI / 16;
                server.sendParticles(GOLD, getX() + 6 * Math.cos(angle), getY() + 0.15,
                        getZ() + 6 * Math.sin(angle), 1, 0, 0.05, 0, 0);
            }
        }
        if (slamTicks <= 0) {
            playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.5F, 0.6F);
            for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(6, 3, 6))) {
                if (!player.isSpectator() && !player.isCreative() && distanceToSqr(player) <= 36 && hasLineOfSight(player)
                        && player.hurt(damageSources().mobAttack(this), 14))
                    player.knockback(1.0, getX() - player.getX(), getZ() - player.getZ());
            }
            slamTicks = 160;
        }
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putInt("VenusSlamTicks", slamTicks); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        slamTicks = tag.contains("VenusSlamTicks") ? Math.max(1, Math.min(160, tag.getInt("VenusSlamTicks"))) : 160;
        setPersistenceRequired();
    }
}
