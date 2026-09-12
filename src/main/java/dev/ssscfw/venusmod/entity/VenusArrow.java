package dev.ssscfw.venusmod.entity;

import dev.ssscfw.venusmod.registry.ModEntities;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

public class VenusArrow extends AbstractArrow {
    public VenusArrow(EntityType<? extends VenusArrow> entityType, Level level) {
        super(entityType, level);
    }

    public VenusArrow(LivingEntity owner, Level level, ItemStack firedFromWeapon) {
        super(ModEntities.VENUS_ARROW.get(), owner, level, new ItemStack(Items.ARROW), firedFromWeapon);
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide && inGround && inGroundTime >= 60) {
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (entity instanceof AbstractSkeleton) {
            return false;
        }
        return super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity hit = result.getEntity();
        if (!(hit instanceof LivingEntity living)) {
            super.onHitEntity(result);
            return;
        }

        Entity owner = getOwner();
        Entity damageOwner = owner != null ? owner : this;
        DamageSource damageSource = level().damageSources().arrow(this, damageOwner);

        double scaledDamage = getDeltaMovement().length() * getBaseDamage();
        int damage = Math.max(1, Mth.ceil(Mth.clamp(scaledDamage, 0.0D, Integer.MAX_VALUE)));
        int previousInvulnerableTime = living.invulnerableTime;

        // Venus arrows always bypass the target's vanilla damage cooldown.  Damage is
        // applied explicitly here instead of relying on AbstractArrow's hit path so a
        // custom Venus projectile cannot visually connect without actually hurting.
        living.invulnerableTime = 0;
        boolean damaged = living.hurt(damageSource, (float) damage);

        if (damaged) {
            if (!level().isClientSide) {
                living.setArrowCount(living.getArrowCount() + 1);
            }
            doKnockback(living, damageSource);
            doPostHurtEffects(living);

            if (previousInvulnerableTime > 0 && living instanceof VenusZombie venusZombie) {
                venusZombie.reactToInvulnerabilityPiercingHit(damageOwner);
            }
        }

        // Keep the cooldown at zero so every arrow in the five-arrow volley can deal
        // damage even when several arrows arrive during the same tick.
        living.invulnerableTime = 0;
        discard();
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.ARROW);
    }
}
