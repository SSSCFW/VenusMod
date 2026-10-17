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
            applyBladePose(zombie, ageInTicks);
        }
    }

    /**
     * VMDを利用できない環境でも腕・胴体の振りが必ず見えるようにする。
     * 専用の攻撃tickはEntityDataでサーバーから同期され、SlashBladeの斬撃判定と一致する。
     */
    private void applyBladePose(VenusZombie zombie, float ageInTicks) {
        int technique = zombie.getBladeTechnique();
        float tick = zombie.getBladeTechniqueTick() + (ageInTicks - Mth.floor(ageInTicks));

        if (technique == VenusZombie.BLADE_TECHNIQUE_COMBO) {
            int slashIndex = 0;
            for (int i = 1; i < VenusZombie.getBladeComboHitCount(); i++) {
                if (tick >= VenusZombie.getBladeComboHitTick(i)) {
                    slashIndex = i;
                } else {
                    break;
                }
            }
            int start = VenusZombie.getBladeComboHitTick(slashIndex);
            int end = slashIndex + 1 < VenusZombie.getBladeComboHitCount()
                    ? VenusZombie.getBladeComboHitTick(slashIndex + 1)
                    : VenusZombie.BLADE_COMBO_END_TICK + 1;
            float progress = Mth.clamp((tick - start) / (end - start), 0.0F, 1.0F);
            float sweep = Mth.cos(progress * Mth.PI);
            float power = Mth.sin(progress * Mth.PI);
            float direction = (slashIndex & 1) == 0 ? 1.0F : -1.0F;

            // 左右交互の薙ぎ払い。胴体のひねりと腕の回転を連動させる。
            body.xRot = 0.08F + 0.14F * power;
            body.yRot = direction * 0.42F * sweep;
            rightArm.xRot = -1.22F - 0.62F * power;
            rightArm.yRot = direction * 0.80F * sweep;
            rightArm.zRot = direction * 0.20F * sweep + 0.12F * power;
            leftArm.xRot = -0.70F - 0.20F * power;
            leftArm.yRot = -direction * 0.24F * sweep;
            leftArm.zRot = -0.08F;
            head.yRot -= direction * 0.10F * sweep;
            return;
        }

        if (technique == VenusZombie.BLADE_TECHNIQUE_RAPID_SLASH) {
            float slash = Mth.sin(Math.min(tick, 5.0F) * 1.25F);
            body.xRot = 0.28F;
            body.yRot = -0.30F + slash * 0.45F;
            rightArm.xRot = -1.55F;
            rightArm.yRot = -0.95F + slash * 1.25F;
            rightArm.zRot = 0.30F;
            leftArm.xRot = -0.72F;
            leftArm.yRot = 0.32F;
            return;
        }

        if (technique == VenusZombie.BLADE_TECHNIQUE_IAI) {
            float progress = Mth.clamp(tick / (VenusZombie.BLADE_IAI_END_TICK + 1.0F), 0.0F, 1.0F);
            float strike = Mth.sin(progress * Mth.PI);
            float rotation = Mth.sin(progress * Mth.TWO_PI);
            body.xRot = 0.22F * strike;
            body.yRot = 0.58F * rotation;
            rightArm.xRot = -1.55F + 0.70F * strike;
            rightArm.yRot = -0.92F + 1.45F * strike;
            rightArm.zRot = 0.22F + 0.22F * rotation;
            leftArm.xRot = -0.68F - 0.35F * strike;
            leftArm.yRot = 0.42F - 0.70F * strike;
            return;
        }

        // 納刀せずに待機している間の構え。攻撃状態には上書きしない。
        body.xRot = 0.0F;
        body.yRot = 0.06F;
        rightArm.xRot = -0.88F;
        rightArm.yRot = -0.42F;
        rightArm.zRot = 0.10F;
        leftArm.xRot = -0.28F;
        leftArm.yRot = 0.18F;
    }
}
