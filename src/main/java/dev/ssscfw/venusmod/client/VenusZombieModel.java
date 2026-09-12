package dev.ssscfw.venusmod.client;

import dev.ssscfw.venusmod.entity.VenusZombie;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public final class VenusZombieModel extends ZombieModel<VenusZombie> {
    public VenusZombieModel(ModelPart root) {
        super(root);
    }

    private static boolean isBladeWielderForRender(VenusZombie zombie) {
        // Prefer the synced Venus flag, but also trust the actually synced held item.
        // This keeps animation/rendering correct even if entity NBT and SynchedEntityData
        // arrive on different client ticks after spawning/loading.
        return zombie.isSlashBladeWielder() || zombie.hasSlashBladeEquipped();
    }

    @Override
    public boolean isAggressive(VenusZombie zombie) {
        // AbstractZombieModel always calls animateZombieArms when this returns true,
        // even with attackTime == 0. Blade wielders use SlashBlade VMD/fallback poses
        // instead, so suppress the vanilla zombie-arm animation entirely for them.
        return !isBladeWielderForRender(zombie) && super.isAggressive(zombie);
    }

    @Override
    public void setupAnim(VenusZombie zombie, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        boolean bladeWielder = isBladeWielderForRender(zombie);

        // A blade-wielding Venus zombie must not inherit HumanoidModel's normal melee
        // swing either. SlashBlade's VMD (or our fallback blade pose) is applied after
        // vanilla locomotion/head setup instead.
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

        // Ready stance while holding the blade. This also serves as the fallback when
        // PlayerAnimator is absent or SlashBlade changes its optional animation API.
        body.yRot = 0.06F;
        rightArm.xRot = -0.88F;
        rightArm.yRot = -0.42F;
        rightArm.zRot = 0.10F;
        leftArm.xRot = -0.28F;
        leftArm.yRot = 0.18F;
    }
}
