package dev.ssscfw.venusmod.entity;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

public class VenusSkeleton extends Skeleton {
    private static final float[] SPREAD_DEGREES = {-12.0F, -6.0F, 0.0F, 6.0F, 12.0F};

    public VenusSkeleton(EntityType<? extends Skeleton> entityType, Level level) {
        super(entityType, level);
        VenusMobStats.apply(this, 1.2D, 0.5D);
    }

    @Override
    public int getTicksUsingItem() {
        int vanillaTicks = super.getTicksUsingItem();
        if (getUseItem().is(Items.BOW)) {
            return Mth.ceil(vanillaTicks * 1.5F);
        }
        return vanillaTicks;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        if (level().isClientSide) {
            return;
        }

        ItemStack bow = getMainHandItem();
        Vec3 baseDirection = target.getEyePosition()
                .subtract(new Vec3(getX(), getEyeY() - 0.1D, getZ()));
        double horizontalDistance = Math.sqrt(baseDirection.x * baseDirection.x + baseDirection.z * baseDirection.z);

        for (float spreadDegrees : SPREAD_DEGREES) {
            double radians = Math.toRadians(spreadDegrees);
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);

            double x = baseDirection.x * cos - baseDirection.z * sin;
            double z = baseDirection.x * sin + baseDirection.z * cos;
            double y = baseDirection.y + horizontalDistance * 0.2D;

            VenusArrow arrow = new VenusArrow(this, level(), bow.copy());
            arrow.setBaseDamage(4.0D);
            arrow.shoot(
                    x,
                    y,
                    z,
                    1.6F,
                    (float) (14 - level().getDifficulty().getId() * 4)
            );
            level().addFreshEntity(arrow);
        }

        playSound(SoundEvents.SKELETON_SHOOT, 1.0F,
                1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
    }

    @Override
    public ResourceKey<LootTable> getLootTable() {
        return EntityType.SKELETON.getDefaultLootTable();
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean causedByPlayer) {
        super.dropCustomDeathLoot(level, source, causedByPlayer);
        VenusMobStats.dropGoldNuggets(this);
    }
}
