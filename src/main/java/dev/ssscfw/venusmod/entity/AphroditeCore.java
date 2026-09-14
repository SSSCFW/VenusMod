package dev.ssscfw.venusmod.entity;

import dev.ssscfw.venusmod.world.PressureShieldControllerBlockEntity;
import dev.ssscfw.venusmod.world.VenusDimensionContent;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 最終ボス「金星圧力生命体 アフロディーテ・コア」。
 * 1500HP、Create回転力で圧力シールドを解除し、周囲3つの圧力コア破壊で段階的に弱体化する。
 */
public final class AphroditeCore extends Blaze {
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.58F, 0.08F), 1.4F);
    private static final DustParticleOptions SHIELD = new DustParticleOptions(new Vector3f(0.35F, 0.85F, 1.0F), 1.2F);

    private final ServerBossEvent boss = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    private final ServerBossEvent shieldBoss = new ServerBossEvent(Component.translatable("bossbar.venusmod.aphrodite_shield"),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);

    private boolean arenaBound;
    private BlockPos arenaCenter = BlockPos.ZERO;
    private int pressureCores;
    private boolean shieldUnlocked;
    private int controllerCharge;
    private int arenaRefreshTicks;
    private int waveCooldown = 80;
    private int beamCooldown = 120;
    private int beamWindup;
    private UUID beamTarget;

    public AphroditeCore(EntityType<? extends Blaze> type, Level level) {
        super(type, level);
        xpReward = 1000;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return Blaze.createAttributes()
                .add(Attributes.MAX_HEALTH, 1500.0D)
                .add(Attributes.ATTACK_DAMAGE, 28.0D)
                .add(Attributes.ARMOR, 24.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 12.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 56.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.24D);
    }

    public void bindArena(BlockPos altarPos) {
        arenaBound = true;
        arenaCenter = altarPos.immutable();
        arenaRefreshTicks = 0;
    }

    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public boolean canAttack(LivingEntity target) {
        return level().getDifficulty() != Difficulty.PEACEFUL && super.canAttack(target);
    }

    @Override public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        boss.addPlayer(player);
        if (arenaBound && !shieldUnlocked) shieldBoss.addPlayer(player);
    }

    @Override public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        boss.removePlayer(player);
        shieldBoss.removePlayer(player);
    }

    @Override public void die(DamageSource source) {
        boss.removeAllPlayers();
        shieldBoss.removeAllPlayers();
        super.die(source);
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        boss.setProgress(Math.max(0.0F, getHealth() / getMaxHealth()));
        if (!isAlive() || server.getDifficulty() == Difficulty.PEACEFUL) {
            setTarget(null);
            return;
        }

        if (arenaBound) {
            if (arenaRefreshTicks-- <= 0) {
                refreshArenaState(server);
                arenaRefreshTicks = 10;
            }
            if (!shieldUnlocked) {
                renderShield(server);
                boss.setName(Component.translatable("bossbar.venusmod.aphrodite_core_shielded"));
                shieldBoss.setName(Component.translatable("bossbar.venusmod.aphrodite_shield", controllerCharge));
                shieldBoss.setProgress(Math.max(0.0F, 1.0F - controllerCharge / 100.0F));
                for (ServerPlayer player : boss.getPlayers()) shieldBoss.addPlayer(player);
            } else {
                shieldBoss.removeAllPlayers();
                boss.setName(Component.translatable("bossbar.venusmod.aphrodite_core", pressureCores));
            }
        } else {
            // スポーンエッグ/コマンドでのテスト時は不死化させない。
            shieldUnlocked = true;
            pressureCores = 0;
            boss.setName(getDisplayName());
        }

        if (waveCooldown > 0) waveCooldown--;
        if (beamCooldown > 0) beamCooldown--;
        if (beamWindup > 0) tickBeam(server);

        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;
        boolean enraged = getHealth() / getMaxHealth() <= 0.25F;

        if (waveCooldown <= 0 && distanceToSqr(target) <= (enraged ? 196.0D : 121.0D)) {
            pressureWave(server, enraged);
            waveCooldown = enraged ? 55 : pressureCores == 0 ? 75 : 95;
        }
        if (shieldUnlocked && beamCooldown <= 0 && beamWindup <= 0 && distanceToSqr(target) <= 900.0D) {
            beamWindup = enraged ? 18 : 28;
            beamTarget = target.getUUID();
            beamCooldown = enraged ? 75 : 110;
            playSound(SoundEvents.BEACON_POWER_SELECT, 1.3F, 0.65F);
        }
    }

    private void refreshArenaState(ServerLevel server) {
        int cores = 0;
        PressureShieldControllerBlockEntity controller = null;
        BlockPos min = arenaCenter.offset(-18, -5, -18);
        BlockPos max = arenaCenter.offset(18, 8, 18);
        for (BlockPos scan : BlockPos.betweenClosed(min, max)) {
            if (server.getBlockState(scan).is(VenusDimensionContent.PRESSURE_CORE.get())) cores++;
            if (controller == null && server.getBlockState(scan).is(VenusDimensionContent.SHIELD_CONTROLLER.get())
                    && server.getBlockEntity(scan) instanceof PressureShieldControllerBlockEntity found) controller = found;
        }
        pressureCores = Math.min(3, cores);
        if (controller != null) {
            controllerCharge = controller.charge();
            if (controller.isUnlocked()) shieldUnlocked = true;
        }
    }

    private void renderShield(ServerLevel server) {
        if (tickCount % 2 != 0) return;
        for (int i = 0; i < 20; i++) {
            double angle = i * Math.PI * 2.0D / 20.0D + tickCount * 0.05D;
            double radius = 1.65D;
            server.sendParticles(SHIELD,
                    getX() + Math.cos(angle) * radius,
                    getY() + 1.0D + Math.sin(angle * 2.0D) * 0.55D,
                    getZ() + Math.sin(angle) * radius,
                    1, 0, 0, 0, 0);
        }
    }

    private void pressureWave(ServerLevel server, boolean enraged) {
        double radius = enraged ? 14.0D : 10.0D;
        float damage = enraged ? 18.0F : 14.0F;
        for (int ring = 1; ring <= 3; ring++) {
            double r = radius * ring / 3.0D;
            for (int i = 0; i < 36; i++) {
                double angle = i * Math.PI * 2.0D / 36.0D;
                server.sendParticles(GOLD, getX() + Math.cos(angle) * r, getY() + 0.25D,
                        getZ() + Math.sin(angle) * r, 1, 0, 0.04D, 0, 0);
            }
        }
        for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(radius, 5.0D, radius))) {
            if (player.isCreative() || player.isSpectator() || distanceToSqr(player) > radius * radius) continue;
            Vec3 away = player.position().subtract(position());
            player.invulnerableTime = 0;
            player.hurt(damageSources().mobAttack(this), damage);
            player.invulnerableTime = 0;
            Vec3 horizontal = new Vec3(away.x, 0, away.z);
            if (horizontal.lengthSqr() > 1.0E-4D) {
                Vec3 push = horizontal.normalize().scale(enraged ? 1.7D : 1.25D);
                player.push(push.x, 0.45D, push.z);
            }
        }
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.5F, enraged ? 0.55F : 0.75F);
    }

    private void tickBeam(ServerLevel server) {
        beamWindup--;
        net.minecraft.world.entity.player.Player found = beamTarget == null ? null : server.getPlayerByUUID(beamTarget);
        ServerPlayer target = found instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if (target == null || !target.isAlive()) {
            beamWindup = 0;
            beamTarget = null;
            return;
        }
        Vec3 from = position().add(0, 1.1D, 0);
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.55D, 0);
        for (int i = 0; i <= 18; i++) {
            double t = i / 18.0D;
            Vec3 point = from.lerp(to, t);
            if (beamWindup <= 5) server.sendParticles(GOLD, point.x, point.y, point.z, 1, 0.015D, 0.015D, 0.015D, 0.0D);
            else server.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 1, 0.015D, 0.015D, 0.015D, 0.0D);
        }
        if (beamWindup == 0) {
            if (distanceToSqr(target) <= 900.0D && hasLineOfSight(target)) {
                float damage = getHealth() / getMaxHealth() <= 0.25F ? 30.0F : 24.0F;
                target.invulnerableTime = 0;
                target.hurt(damageSources().mobAttack(this), damage);
                target.invulnerableTime = 0;
                playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2F, 1.4F);
            }
            beamTarget = null;
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (arenaBound && !shieldUnlocked) {
            if (!level().isClientSide && level() instanceof ServerLevel server && tickCount % 4 == 0)
                server.sendParticles(SHIELD, getX(), getY() + 1.0D, getZ(), 18, 0.65D, 0.9D, 0.65D, 0.02D);
            playSound(SoundEvents.SHIELD_BLOCK, 0.8F, 0.55F);
            return false;
        }
        float adjusted = BossCombatRules.aphroditeDamage(amount, pressureCores);
        if (pressureCores == 0) adjusted *= 1.15F;
        return super.hurt(source, adjusted);
    }

    @Override public void knockback(double strength, double x, double z) { /* 圧力核はノックバックしない */ }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("ArenaBound", arenaBound);
        tag.putLong("ArenaCenter", arenaCenter.asLong());
        tag.putBoolean("ShieldUnlocked", shieldUnlocked);
        tag.putInt("PressureCores", pressureCores);
        tag.putInt("WaveCooldown", waveCooldown);
        tag.putInt("BeamCooldown", beamCooldown);
        tag.putInt("BeamWindup", beamWindup);
        if (beamTarget != null) tag.putUUID("BeamTarget", beamTarget);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        arenaBound = tag.getBoolean("ArenaBound");
        if (tag.contains("ArenaCenter")) arenaCenter = BlockPos.of(tag.getLong("ArenaCenter"));
        shieldUnlocked = tag.getBoolean("ShieldUnlocked");
        pressureCores = Math.clamp(tag.getInt("PressureCores"), 0, 3);
        waveCooldown = Math.max(0, tag.getInt("WaveCooldown"));
        beamCooldown = Math.max(0, tag.getInt("BeamCooldown"));
        beamWindup = Math.max(0, tag.getInt("BeamWindup"));
        if (tag.hasUUID("BeamTarget")) beamTarget = tag.getUUID("BeamTarget");
        setPersistenceRequired();
    }
}
