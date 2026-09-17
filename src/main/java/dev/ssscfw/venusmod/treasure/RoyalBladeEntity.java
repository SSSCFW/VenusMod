package dev.ssscfw.venusmod.treasure;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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

/** 拾得・保存できない刀の投影。SlashBladeのクラスには直接リンクしない。 */
public final class RoyalBladeEntity extends Projectile {
    private static final EntityDataAccessor<ItemStack> BLADE =
            SynchedEntityData.defineId(RoyalBladeEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> LAUNCHED =
            SynchedEntityData.defineId(RoyalBladeEntity.class, EntityDataSerializers.BOOLEAN);
    private int formationSlot;
    private int flightTicks;

    public RoyalBladeEntity(EntityType<? extends RoyalBladeEntity> type, Level level) {
        super(type, level);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BLADE, ItemStack.EMPTY);
        builder.define(LAUNCHED, false);
    }
    public ItemStack blade() { return entityData.get(BLADE); }
    public boolean launched() { return entityData.get(LAUNCHED); }

    public void stage(LivingEntity owner, ItemStack template, int slot) {
        setOwner(owner);
        entityData.set(BLADE, template.copyWithCount(1));
        formationSlot = Math.max(0, Math.min(TreasureRules.MAX_BLADES - 1, slot));
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        placeAtSummon(owner);
    }

    public void launch(Vec3 target) {
        if (launched() || isRemoved()) return;
        Vec3 direction = target.subtract(position()).normalize();
        if (direction.lengthSqr() < 1.0E-6) {
            direction = getOwner() == null ? new Vec3(0, 0, 1) : getOwner().getLookAngle();
        }
        entityData.set(LAUNCHED, true);
        shoot(direction.x, direction.y, direction.z, (float) TreasureRules.SPEED, 0);
        flightTicks = 0;
    }

    /** 展開した瞬間だけ所有者の姿勢から座標を決め、以後はプレイヤーを追従しない。 */
    private void placeAtSummon(LivingEntity owner) {
        double yaw = Math.toRadians(owner.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        TreasureRules.FormationOffset offset = TreasureRules.formationOffset(formationSlot);
        Vec3 summonPosition = owner.getEyePosition()
                .subtract(forward.scale(offset.back()))
                .add(right.scale(offset.right()))
                .add(0, offset.up(), 0);
        setPos(summonPosition);
        setYRot(owner.getYRot());
        setXRot(owner.getXRot());
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (launched()) {
                Vec3 velocity = getDeltaMovement();
                setPos(position().add(velocity));
                if ((tickCount & 1) == 0) {
                    level().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0, 0, 0);
                }
            }
            return;
        }
        if (!(getOwner() instanceof LivingEntity owner) || !owner.isAlive() || owner.isRemoved()
                || owner.level() != level() || owner.isSpectator()) {
            discard();
            return;
        }
        if (!launched()) {
            if (tickCount >= TreasureRules.PREPARE_TICKS) discard();
            return;
        }
        if (++flightTicks >= TreasureRules.FLIGHT_TICKS) {
            discard();
            return;
        }
        Vec3 start = position();
        Vec3 end = start.add(getDeltaMovement());
        HitResult block = level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(
                level(), this, start, limit,
                getBoundingBox().expandTowards(getDeltaMovement()).inflate(0.3),
                candidate -> canDamage(owner, candidate));
        HitResult hit = entity == null ? block : entity;
        if (hit.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hit)) {
            setPos(hit.getLocation());
            burst(owner, entity == null ? null : entity.getEntity());
        } else {
            setPos(end);
        }
    }

    public static boolean canDamage(LivingEntity owner, Entity candidate) {
        if (!(candidate instanceof LivingEntity target) || target == owner || !target.isAlive()
                || target.isSpectator() || !target.isAttackable() || owner.isAlliedTo(target)) {
            return false;
        }
        if (target instanceof OwnableEntity pet && owner.getUUID().equals(pet.getOwnerUUID())) return false;
        return !(owner instanceof Player player && target instanceof Player other) || player.canHarmPlayer(other);
    }

    private void burst(LivingEntity owner, Entity directTarget) {
        if (!(level() instanceof ServerLevel level) || isRemoved()) return;
        Vec3 center = position().subtract(getDeltaMovement().normalize().scale(0.03));
        discard();
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 0.35F, 1.35F);
        DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(KingsTreasure.DAMAGE_TYPE), this, owner);
        double radius = TreasureRules.BLAST_RADIUS;
        for (LivingEntity target : level.getEntitiesOfClass(
                LivingEntity.class, new AABB(center, center).inflate(radius),
                target -> canDamage(owner, target))) {
            double distance = target.getBoundingBox().getCenter().distanceTo(center);
            boolean direct = target == directTarget;
            if (!direct && distance >= radius) continue;
            if (!direct && level.clip(new ClipContext(
                    center, target.getEyePosition(), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) {
                continue;
            }
            float damage = direct
                    ? TreasureRules.DAMAGE
                    : (float) (TreasureRules.DAMAGE * 0.5 * (1 - distance / radius));
            if (damage > 0) {
                target.invulnerableTime = 0;
                target.hurt(source, damage);
            }
        }
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        discard();
    }
}
