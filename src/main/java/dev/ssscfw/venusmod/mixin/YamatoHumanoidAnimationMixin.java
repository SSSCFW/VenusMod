package dev.ssscfw.venusmod.mixin;

import dev.ssscfw.venusmod.client.YamatoCutAnimation;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PlayerAnimator未導入時も、バニラ姿勢を計算した後に二振りの姿勢を適用する。 */
@Mixin(HumanoidModel.class)
public abstract class YamatoHumanoidAnimationMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void venusmod$yamatoCut(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                    float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        YamatoCutAnimation.applyFallbackPose((HumanoidModel<?>)(Object)this, entity, ageInTicks);
    }
}
