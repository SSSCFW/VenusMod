package dev.ssscfw.venusmod.client;

import dev.ssscfw.venusmod.entity.VenusZombie;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** SlashBladeのVMD/フォールバック姿勢を通常金星ゾンビと将軍で共有するモデル。 */
public final class VenusZombieModel<T extends VenusZombie> extends ZombieModel<T> {
    public VenusZombieModel(ModelPart root) {
        super(root);
    }

    private static boolean isBladeWielderForRender(VenusZombie zombie) {
        return zombie.isSlashBladeWielder() || zombie.hasSlashBladeEquipped();
    }

    @Override
    public boolean isAggressive(T zombie) {
        return !isBladeWielderForRender(zombie) && super.isAggressive(zombie);
    }

    @Override
    public void setupAnim(T zombie, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        boolean bladeWielder = isBladeWielderForRender(zombie);
        if (bladeWielder) {
            this.attackTime = 0.0F;
        }

        super.setupAnim(zombie, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        if (!bladeWielder) {
            return;
        }

        boolean usedSlashBladeVmd = PlayerAnimatorCompat.applySlashBladeAnimation(this, zombie, ageInTicks);
        if (!usedSlashBladeVmd) {
            applyBladePose(zombie);
        }
    }

    private void applyBladePose(VenusZombie zombie) {
        int technique = zombie.getBladeTechnique();
        int tick = zombie.getBladeTechniqueTick();

        if (technique == VenusZombie.BLADE_TECHNIQUE_COMBO) {
            float phase = tick * 0.90F;
            float sweep = Mth.sin(phase);

            body.yRot = sweep * 0.32F;
            rightArm.xRot = -1.25F + Mth.cos(phase) * 0.72F;
            rightArm.yRot = -0.55F + sweep * 0.95F;
            rightArm.zRot = 0.10F + sweep * 0.25F;
            leftArm.xRot = -0.48F - Mth.cos(phase) * 0.18F;
            leftArm.yRot = 0.18F - sweep * 0.25F;
            return;
        }

        if (technique == VenusZombie.BLADE_TECHNIQUE_RAPID_SLASH) {
            float slash = Mth.sin(Math.min(tick, 4) * 1.25F);
            body.xRot = 0.28F;
            body.yRot = -0.30F + slash * 0.45F;
            rightArm.xRot = -1.55F;
            rightArm.yRot = -0.95F + slash * 1.25F;
            rightArm.zRot = 0.30F;
            leftArm.xRot = -0.72F;
            leftArm.yRot = 0.32F;
            return;
        }

        body.xRot = 0.0F;
        body.yRot = 0.06F;
        rightArm.xRot = -0.88F;
        rightArm.yRot = -0.42F;
        rightArm.zRot = 0.10F;
        leftArm.xRot = -0.28F;
        leftArm.yRot = 0.18F;
    }
}
