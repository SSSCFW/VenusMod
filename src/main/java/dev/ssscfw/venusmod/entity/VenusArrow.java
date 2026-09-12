package dev.ssscfw.venusmod.entity;

import dev.ssscfw.venusmod.registry.ModEntities;
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
        if (hit instanceof LivingEntity living) {
            int previousInvulnerableTime = living.invulnerableTime;
            living.invulnerableTime = 0;

            if (previousInvulnerableTime > 0 && living instanceof VenusZombie venusZombie) {
                Entity owner = getOwner();
                venusZombie.reactToInvulnerabilityPiercingHit(owner != null ? owner : this);
            }

            try {
                super.onHitEntity(result);
            } finally {
                living.invulnerableTime = Math.max(previousInvulnerableTime, living.invulnerableTime);
            }
            return;
        }

        super.onHitEntity(result);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.ARROW);
    }
}
