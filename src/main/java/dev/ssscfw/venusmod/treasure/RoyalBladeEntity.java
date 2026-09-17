package dev.ssscfw.venusmod.treasure;

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

/** 拾得・保存できない刀の投影。SlashBladeのクラスには直接リンクしない。 */
public final class RoyalBladeEntity extends Projectile {
    private static final EntityDataAccessor<ItemStack> BLADE =
            SynchedEntityData.defineId(RoyalBladeEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> LAUNCHED =
            SynchedEntityData.defineId(RoyalBladeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vector3f> AIM_DIRECTION =
            SynchedEntityData.defineId(RoyalBladeEntity.class, EntityDataSerializers.VECTOR3);
    private static final double FAR_PARTICLE_RANGE_SQR = 512.0D * 512.0D;

    private int formationSlot;
    private int flightTicks;
    private boolean returnedToTreasury;

    public RoyalBladeEntity(EntityType<? extends RoyalBladeEntity> type, Level level) {
        super(type, level);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BLADE, ItemStack.EMPTY);
        builder.define(LAUNCHED, false);
        builder.define(AIM_DIRECTION, new Vector3f(0.0F, 0.0F, 1.0F));
    }

    public ItemStack blade() { return entityData.get(BLADE); }
    public boolean launched() { return entityData.get(LAUNCHED); }

    /** クライアント描画でも同じ向きを使うため、yaw/pitchではなく実方向を直接同期する。 */
    public Vec3 aimDirection() {
        Vector3f direction = entityData.get(AIM_DIRECTION);
        return safeDirection(
                new Vec3(direction.x(), direction.y(), direction.z()),
                new Vec3(0.0D, 0.0D, 1.0D));
    }

    public void stage(LivingEntity owner, ItemStack template, int slot) {
        stage(owner, template, slot, owner.getLookAngle());
    }

    public void stage(LivingEntity owner, ItemStack template, int slot, Vec3 summonDirection) {
        setOwner(owner);
        entityData.set(BLADE, template.copyWithCount(1));
        formationSlot = Math.max(0, Math.min(TreasureRules.MAX_BLADES - 1, slot));
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        Vec3 direction = safeDirection(summonDirection, owner.getLookAngle());
        setAimDirection(direction);
        placeAtSummon(owner, direction);
        setAimRotation(direction);
    }

    /** 既存GameTest等で一点へ直接飛ばすための互換入口。 */
    public void launch(Vec3 target) {
        Vec3 toTarget = target.subtract(position());
        launch(toTarget, target, 1.0D);
    }

    /**
     * 平行方向と収束焦点を混ぜて射出する。convergence=0なら完全平行、1なら焦点へ完全収束。
     * 王の財宝本体は0または中程度値だけを渡す。
     */
    public void launch(Vec3 parallelDirection, Vec3 focusPoint, double convergence) {
        if (launched() || isRemoved()) return;
        Vec3 parallel = safeDirection(
                parallelDirection, getOwner() == null ? Vec3.ZERO : getOwner().getLookAngle());
        Vec3 towardFocus = focusPoint == null ? parallel : focusPoint.subtract(position());
        towardFocus = safeDirection(towardFocus, parallel);
        double factor = Math.max(0.0D, Math.min(1.0D, convergence));
        Vec3 direction = parallel.scale(1.0D - factor).add(towardFocus.scale(factor));
        direction = safeDirection(direction, parallel);
        entityData.set(LAUNCHED, true);
        setAimDirection(direction);
        shoot(direction.x, direction.y, direction.z, (float) TreasureRules.SPEED, 0);
        setAimRotation(direction);
        flightTicks = 0;
    }

    /** 展開した瞬間だけ所有者の姿勢から座標を決め、以後はプレイヤーを追従しない。 */
    private void placeAtSummon(LivingEntity owner, Vec3 summonDirection) {
        Vec3 horizontal = new Vec3(summonDirection.x, 0.0D, summonDirection.z);
        if (horizontal.lengthSqr() < 1.0E-8D) {
            double yaw = Math.toRadians(owner.getYRot());
            horizontal = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        }
        Vec3 forward = horizontal.normalize();
        Vec3 right = new Vec3(forward.z, 0.0D, -forward.x);
        TreasureRules.FormationOffset offset = TreasureRules.formationOffset(formationSlot);
        Vec3 summonPosition = owner.getEyePosition()
                .subtract(forward.scale(offset.back()))
                .add(right.scale(offset.right()))
                .add(0, offset.up(), 0);
        setPos(summonPosition);
    }

    private void setAimDirection(Vec3 direction) {
        Vec3 normalized = safeDirection(direction, new Vec3(0.0D, 0.0D, 1.0D));
        entityData.set(AIM_DIRECTION, new Vector3f(
                (float) normalized.x, (float) normalized.y, (float) normalized.z));
    }

    /**
     * 当たり判定やバニラのEntity補間用の回転値も維持する。
     * 王の財宝の描画はこのyaw/pitchではなくAIM_DIRECTIONを直接使用する。
     */
    private void setAimRotation(Vec3 direction) {
        Vec3 normalized = safeDirection(direction, new Vec3(0.0D, 0.0D, 1.0D));
        double horizontal = Math.sqrt(normalized.x * normalized.x + normalized.z * normalized.z);
        setYRot((float) Math.toDegrees(Math.atan2(-normalized.x, normalized.z)));
        setXRot((float) Math.toDegrees(-Math.atan2(normalized.y, horizontal)));
    }

    private static Vec3 safeDirection(Vec3 direction, Vec3 fallback) {
        if (direction != null && direction.lengthSqr() >= 1.0E-8D) return direction.normalize();
        if (fallback != null && fallback.lengthSqr() >= 1.0E-8D) return fallback.normalize();
        return new Vec3(0.0D, 0.0D, 1.0D);
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (launched()) setPos(position().add(getDeltaMovement()));
            return;
        }

        Entity rawOwner = getOwner();
        if (!(rawOwner instanceof LivingEntity owner)) {
            discard();
            return;
        }
        if (!owner.isAlive() || owner.isRemoved() || owner.level() != level() || owner.isSpectator()) {
            if (launched()) finishFlight(owner);
            else discard();
            return;
        }
        if (!launched()) {
            if (tickCount >= TreasureRules.PREPARE_TICKS) discard();
            return;
        }
        if (++flightTicks >= TreasureRules.FLIGHT_TICKS) {
            finishFlight(owner);
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
            if ((flightTicks & 1) == 0 && level() instanceof ServerLevel server) {
                sendFarParticles(server, ParticleTypes.END_ROD,
                        getX(), getY(), getZ(), 1, 0, 0, 0, 0);
            }
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
        sendFarParticles(level, ParticleTypes.EXPLOSION,
                center.x, center.y, center.z, 1, 0, 0, 0, 0);
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
        finishFlight(owner);
    }

    /** 射出終了時に1回だけ耐久を消費して宝物庫へ返す。 */
    private void finishFlight(LivingEntity owner) {
        if (!returnedToTreasury && owner instanceof ServerPlayer player) {
            returnedToTreasury = true;
            KingsTreasure.returnSpentBlade(player, blade());
        }
        discard();
    }

    /** force=trueで通常の32ブロック制限を越えて同ディメンションのプレイヤーへ送る。 */
    private static <T extends ParticleOptions> void sendFarParticles(
            ServerLevel level, T particle,
            double x, double y, double z, int count,
            double xOffset, double yOffset, double zOffset, double speed) {
        for (ServerPlayer viewer : level.getServer().getPlayerList().getPlayers()) {
            if (viewer.level() != level || viewer.distanceToSqr(x, y, z) > FAR_PARTICLE_RANGE_SQR) continue;
            level.sendParticles(viewer, particle, true,
                    x, y, z, count, xOffset, yOffset, zOffset, speed);
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
