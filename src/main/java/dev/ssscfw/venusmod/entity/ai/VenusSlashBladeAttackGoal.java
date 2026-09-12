package dev.ssscfw.venusmod.entity.ai;

import dev.ssscfw.venusmod.entity.VenusZombie;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Long-reach SlashBlade combat goal for blade-wielding Venus zombies.
 */
public final class VenusSlashBladeAttackGoal extends Goal {
    private static final double COMBO_RANGE_SQR = 5.5D * 5.5D;
    private static final double RAPID_SLASH_RANGE_SQR = 7.0D * 7.0D;
    private static final double KEEP_CHASING_RANGE_SQR = 12.0D * 12.0D;

    private final VenusZombie zombie;
    private final double speedModifier;

    public VenusSlashBladeAttackGoal(VenusZombie zombie, double speedModifier) {
        this.zombie = zombie;
        this.speedModifier = speedModifier;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = zombie.getTarget();
        return zombie.isSlashBladeWielder()
                && zombie.hasSlashBladeEquipped()
                && target != null
                && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = zombie.getTarget();
        return zombie.isSlashBladeWielder()
                && zombie.hasSlashBladeEquipped()
                && target != null
                && target.isAlive()
                && zombie.distanceToSqr(target) <= KEEP_CHASING_RANGE_SQR;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        zombie.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = zombie.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }

        zombie.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (zombie.isBladeTechniqueActive()) {
            zombie.getNavigation().stop();
            return;
        }

        double distanceSqr = zombie.distanceToSqr(target);
        if (zombie.getBladeTechniqueCooldown() > 0) {
            if (distanceSqr > COMBO_RANGE_SQR) {
                zombie.getNavigation().moveTo(target, speedModifier);
            } else {
                zombie.getNavigation().stop();
            }
            return;
        }

        if (!zombie.getSensing().hasLineOfSight(target)) {
            zombie.getNavigation().moveTo(target, speedModifier);
            return;
        }

        if (distanceSqr <= RAPID_SLASH_RANGE_SQR && distanceSqr > 3.0D * 3.0D) {
            // At extended blade range, prefer the Shift+Forward+Right-click style dash slash.
            if (distanceSqr > COMBO_RANGE_SQR || zombie.getRandom().nextFloat() < 0.40F) {
                zombie.getNavigation().stop();
                zombie.startRapidSlash(target);
                return;
            }
        }

        if (distanceSqr <= COMBO_RANGE_SQR) {
            zombie.getNavigation().stop();
            zombie.startBladeCombo(target);
            return;
        }

        zombie.getNavigation().moveTo(target, speedModifier);
    }
}
