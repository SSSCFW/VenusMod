package dev.ssscfw.venusmod.treasure;

import dev.ssscfw.venusmod.compat.SlashBladeTreasuryCompat;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.joml.Vector3f;

/** 刀の投影。刀とモードは召喚時に固定し、実アイテムは1回だけ返却/消費する。 */
public final class RoyalBladeEntity extends Projectile {
    private static final EntityDataAccessor<ItemStack> BLADE = SynchedEntityData.defineId(RoyalBladeEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> LAUNCHED = SynchedEntityData.defineId(RoyalBladeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> PHANTASM = SynchedEntityData.defineId(RoyalBladeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vector3f> AIM_DIRECTION = SynchedEntityData.defineId(RoyalBladeEntity.class, EntityDataSerializers.VECTOR3);
    private static final double FAR_PARTICLE_RANGE_SQR = 512.0D * 512.0D;
    private int formationSlot;
    private int formationSize = TreasureRules.MAX_BLADES;
    private int flightTicks;
    private boolean returnedToTreasury;

    public RoyalBladeEntity(EntityType<? extends RoyalBladeEntity> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BLADE, ItemStack.EMPTY);
        builder.define(LAUNCHED, false);
        builder.define(PHANTASM, false);
        builder.define(AIM_DIRECTION, new Vector3f(0, 0, 1));
    }
    public ItemStack blade() { return entityData.get(BLADE); }
    public boolean launched() { return entityData.get(LAUNCHED); }
    public boolean phantasm() { return entityData.get(PHANTASM); }
    public Vec3 aimDirection() {
        Vector3f d = entityData.get(AIM_DIRECTION);
        return safeDirection(new Vec3(d.x(), d.y(), d.z()), new Vec3(0, 0, 1));
    }
    public void stage(LivingEntity owner, ItemStack template, int slot) { stage(owner, template, slot, owner.getLookAngle()); }
    public void stage(LivingEntity owner, ItemStack template, int slot, Vec3 direction) { stage(owner, template, slot, direction, TreasureRules.MAX_BLADES); }
    public void stage(LivingEntity owner, ItemStack template, int slot, Vec3 direction, int total) { stage(owner, template, slot, direction, total, false); }
    public void stage(LivingEntity owner, ItemStack template, int slot, Vec3 summonDirection, int totalBlades, boolean phantasm) {
        setOwner(owner);
        entityData.set(BLADE, template.copyWithCount(1));
        entityData.set(PHANTASM, phantasm);
        formationSize = Math.max(1, Math.min(TreasureRules.MAX_BLADES, totalBlades));
        formationSlot = Math.max(0, Math.min(formationSize - 1, slot));
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        Vec3 direction = safeDirection(summonDirection, owner.getLookAngle());
        setAimDirection(direction);
        placeAtSummon(owner, direction);
        setAimRotation(direction);
    }
    public void launch(Vec3 target) { launch(target.subtract(position()), target, 1.0D); }
    public void launch(Vec3 parallelDirection, Vec3 focusPoint, double convergence) {
        if (launched() || isRemoved()) return;
        Vec3 parallel = safeDirection(parallelDirection, getOwner() == null ? Vec3.ZERO : getOwner().getLookAngle());
        Vec3 towardFocus = safeDirection(focusPoint == null ? parallel : focusPoint.subtract(position()), parallel);
        double factor = Math.max(0, Math.min(1, convergence));
        Vec3 direction = safeDirection(parallel.scale(1 - factor).add(towardFocus.scale(factor)), parallel);
        entityData.set(LAUNCHED, true);
        setAimDirection(direction);
        shoot(direction.x, direction.y, direction.z, (float)TreasureRules.SPEED, 0);
        setAimRotation(direction);
        flightTicks = 0;
    }
    private void placeAtSummon(LivingEntity owner, Vec3 direction) {
        Vec3 horizontal = new Vec3(direction.x, 0, direction.z);
        if (horizontal.lengthSqr() < 1.0E-8D) {
            double yaw = Math.toRadians(owner.getYRot());
            horizontal = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        Vec3 forward = horizontal.normalize();
        Vec3 right = new Vec3(forward.z, 0, -forward.x);
        TreasureRules.FormationOffset offset = TreasureRules.formationOffset(formationSlot, formationSize);
        setPos(owner.getEyePosition().subtract(forward.scale(offset.back())).add(right.scale(offset.right())).add(0, offset.up(), 0));
    }
    private void setAimDirection(Vec3 direction) {
        Vec3 d = safeDirection(direction, new Vec3(0, 0, 1));
        entityData.set(AIM_DIRECTION, new Vector3f((float)d.x, (float)d.y, (float)d.z));
    }
    private void setAimRotation(Vec3 direction) {
        Vec3 d = safeDirection(direction, new Vec3(0, 0, 1));
        setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z)));
        setXRot((float)Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
    }
    private static Vec3 safeDirection(Vec3 direction, Vec3 fallback) {
        if (direction != null && direction.lengthSqr() >= 1.0E-8D) return direction.normalize();
        if (fallback != null && fallback.lengthSqr() >= 1.0E-8D) return fallback.normalize();
        return new Vec3(0, 0, 1);
    }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (launched()) setPos(position().add(getDeltaMovement()));
            return;
        }
        if (!(getOwner() instanceof LivingEntity owner)) { discard(); return; }
        if (!owner.isAlive() || owner.isRemoved() || owner.level() != level() || owner.isSpectator()) {
            if (launched()) finishFlight(owner); else discard();
            return;
        }
        if (!launched()) { if (tickCount >= TreasureRules.PREPARE_TICKS) discard(); return; }
        if (++flightTicks >= TreasureRules.FLIGHT_TICKS) { finishFlight(owner); return; }
        Vec3 start = position();
        Vec3 end = start.add(getDeltaMovement());
        HitResult block = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(level(), this, start, limit,
                getBoundingBox().expandTowards(getDeltaMovement()).inflate(0.3), candidate -> canDamage(owner, candidate));
        HitResult hit = entity == null ? block : entity;
        if (hit.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hit)) {
            setPos(hit.getLocation());
            burst(owner, entity == null ? null : entity.getEntity());
        } else {
            setPos(end);
            if ((flightTicks & 1) == 0 && level() instanceof ServerLevel server)
                sendFarParticles(server, ParticleTypes.END_ROD, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        }
    }
    public static boolean canDamage(LivingEntity owner, Entity candidate) {
        if (!(candidate instanceof LivingEntity target) || target == owner || !target.isAlive()
                || target.isSpectator() || !target.isAttackable() || owner.isAlliedTo(target)) return false;
        if (target instanceof OwnableEntity pet && owner.getUUID().equals(pet.getOwnerUUID())) return false;
        return !(owner instanceof Player player && target instanceof Player other) || player.canHarmPlayer(other);
    }
    private void burst(LivingEntity owner, Entity directTarget) {
        if (!(level() instanceof ServerLevel level) || isRemoved()) return;
        Vec3 center = position().subtract(getDeltaMovement().normalize().scale(0.03));
        sendFarParticles(level, phantasm() ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION,
                center.x, center.y, center.z, 1, 0, 0, 0, 0);
        // 通常/幻想とも着弾地点で1.2F。ブロック破壊・ブロック着火は行わない。
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, TreasureRules.EXPLOSION_VOLUME, 1.35F);
        DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(KingsTreasure.DAMAGE_TYPE), this, owner);
        ItemStack firedBlade = blade();
        double radius = RoyalBladeEffectsRules.blastRadius(phantasm());
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), target -> canDamage(owner, target))) {
            double distance = target.getBoundingBox().getCenter().distanceTo(center);
            boolean direct = target == directTarget;
            if (!direct && distance >= radius) continue;
            if (!direct && level.clip(new ClipContext(center, target.getEyePosition(), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) continue;
            float damage = RoyalBladeEffectsRules.damage(SlashBladeTreasuryCompat.damageAgainst(level, firedBlade, target, source, direct), phantasm());
            if (damage > 0) {
                target.invulnerableTime = 0;
                if (target.hurt(source, damage)) RoyalBladeEffects.ignite(level, firedBlade, target);
            }
        }
        finishFlight(owner, true);
    }
    private void finishFlight(LivingEntity owner) { finishFlight(owner, false); }
    private void finishFlight(LivingEntity owner, boolean impacted) {
        if (!returnedToTreasury) {
            returnedToTreasury = true;
            if (RoyalBladeEffectsRules.returnsBlade(phantasm(), impacted) && owner instanceof ServerPlayer player)
                KingsTreasure.returnSpentBlade(player, blade());
        }
        discard();
    }
    private static <T extends ParticleOptions> void sendFarParticles(ServerLevel level, T particle,
            double x, double y, double z, int count, double xOffset, double yOffset, double zOffset, double speed) {
        for (ServerPlayer viewer : level.getServer().getPlayerList().getPlayers()) {
            if (viewer.level() != level || viewer.distanceToSqr(x, y, z) > FAR_PARTICLE_RANGE_SQR) continue;
            level.sendParticles(viewer, particle, true, x, y, z, count, xOffset, yOffset, zOffset, speed);
        }
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); discard(); }
}
