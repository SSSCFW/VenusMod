package dev.ssscfw.venusmod.entity.ai;

import dev.ssscfw.venusmod.entity.VenusZombie;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Long-reach SlashBlade combat goal for blade-wielding Venus zombies.
 */
public final class VenusSlashBladeAttackGoal extends Goal {
    private static final double COMBO_RANGE_SQR = 5.5D * 5.5D;
    private static final double RAPID_SLASH_RANGE_SQR = 7.0D * 7.0D;
    private static final double KEEP_CHASING_RANGE_SQR = 12.0D * 12.0D;
    private static final double ATTACK_APPROACH_STOP_SQR = 3.4D * 3.4D;
    private static final double ATTACK_APPROACH_START_SQR = 4.4D * 4.4D;
    private static final double ATTACK_APPROACH_DISTANCE = 3.8D;

    private final VenusZombie zombie;
    private final double speedModifier;
    private boolean approachingDuringAttack;

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
                && zombie.distanceToSqr(target) <= (zombie.getBladeTechnique() == VenusZombie.BLADE_TECHNIQUE_IAI
                        ? 26.0D * 26.0D : KEEP_CHASING_RANGE_SQR);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        approachingDuringAttack = false;
        zombie.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = zombie.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }

        zombie.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double distanceSqr = zombie.distanceToSqr(target);
        if (zombie.isBladeTechniqueActive()) {
            approachDuringAttack(target, distanceSqr);
            return;
        }

        approachingDuringAttack = false;
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
    /**
     * 将軍だけは斬撃中も通常時の45%の移動速度で接近する。
     * 最寄りのターゲット座標まで突っ込まず、3.8ブロック程度で止める。
     * 3.4/4.4ブロックのヒステリシスで停止/再開の振動を防ぐ。
     */
    private void approachDuringAttack(LivingEntity target, double distanceSqr) {
        double multiplier = zombie.getBladeTechniqueApproachSpeedMultiplier();
        if (multiplier <= 0.0D || !zombie.hasVisibleBladeTechniqueTarget()) {
            approachingDuringAttack = false;
            zombie.getNavigation().stop();
            return;
        }

        if (distanceSqr <= ATTACK_APPROACH_STOP_SQR) {
            approachingDuringAttack = false;
        } else if (distanceSqr >= ATTACK_APPROACH_START_SQR) {
            approachingDuringAttack = true;
        }

        if (!approachingDuringAttack) {
            zombie.getNavigation().stop();
            return;
        }

        Vec3 away = zombie.position().subtract(target.position());
        Vec3 horizontal = new Vec3(away.x, 0.0D, away.z);
        if (horizontal.lengthSqr() < 1.0E-4D) {
            zombie.getNavigation().stop();
            return;
        }

        // ターゲットから一定距離の地点へパス探索し、壁を無視して突進しない。
        Vec3 destination = target.position().add(horizontal.normalize().scale(ATTACK_APPROACH_DISTANCE));
        if (zombie.getNavigation().isDone() || zombie.tickCount % 5 == 0) {
            zombie.getNavigation().moveTo(destination.x, target.getY(), destination.z,
                    speedModifier * multiplier);
        }
    }

}
