package dev.ssscfw.venusmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import dev.ssscfw.venusmod.entity.VenusZombie;
import dev.ssscfw.venusmod.entity.ai.VenusBladeMotionTimeline;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Optional PlayerAnimator + SlashBlade animation bridge for blade-wielding Venus
 * zombies.
 *
 * SlashBlade's normal PlayerAnimationOverrider only accepts AbstractClientPlayer, so
 * Venus zombies cannot receive those animations through SlashBlade's normal event
 * path. Instead we reuse SlashBlade's VmdAnimation and PlayerAnimator's
 * AnimationApplier directly on the VenusZombieModel.
 *
 * Everything is reflective so neither PlayerAnimator nor SlashBlade becomes a hard
 * compile/runtime dependency of VenusMod.
 */
public final class PlayerAnimatorCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation MOTION =
            ResourceLocation.fromNamespaceAndPath("slashblade", "model/pa/player_motion.vmd");
    private static final ResourceLocation COMBO_A1 = slashBlade("combo_a1");
    private static final ResourceLocation COMBO_A2 = slashBlade("combo_a2");
    private static final ResourceLocation COMBO_A3 = slashBlade("combo_a3");
    private static final ResourceLocation COMBO_A4 = slashBlade("combo_a4");
    private static final ResourceLocation RAPID_SLASH = slashBlade("rapid_slash");
    private static final ResourceLocation IAI_SLASH = slashBlade("judgement_cut");

    private static boolean lookupDone;
    private static boolean available;
    private static boolean failureLogged;

    private static Map<?, ?> slashBladeAnimations;
    private static Method vmdCloneMethod;
    private static Method vmdPlayMethod;
    private static Constructor<?> vmdConstructor;
    private static Constructor<?> animationApplierConstructor;
    private static Method animationSetTickDeltaMethod;
    private static Method animationUpdatePartMethod;

    // プレイヤーのPlayerRendererMixinが適用している身体全体の移動と回転も使用。
    private static Constructor<?> vectorConstructor;
    private static Method vectorGetXMethod;
    private static Method vectorGetYMethod;
    private static Method vectorGetZMethod;
    private static Method animationGet3DTransformMethod;
    private static Object transformPosition;
    private static Object transformRotation;
    private static Object transformScale;

    // Optional. ModelPart transforms work without these; they only keep PlayerAnimator
    // bend rendering state in sync when its HumanoidModel mixin is available.
    private static Method mutableModelGetSupplierMethod;
    private static Method supplierSetMethod;
    private static Object bendHelper;
    private static Method bendMethod;

    private PlayerAnimatorCompat() {
    }

    /**
     * Applies SlashBlade's real player VMD for the currently synced Venus technique.
     * Returns true only when VMD transforms were successfully applied this frame.
     */
    public static boolean applySlashBladeAnimation(ZombieModel<?> model, VenusZombie zombie, float ageInTicks) {
        if (!isBladeAnimationActive(zombie)) {
            clearAnimationState(model);
            return false;
        }
        if (!resolve()) {
            return false;
        }

        try {
            Object applier = prepareAnimation(zombie, ageInTicks - (float) Math.floor(ageInTicks));
            if (applier == null) {
                clearAnimationState(model);
                return false;
            }

            // PlayerModelMixin#setEmoteと同じModelPart名と同じ更新順序を使用する。
            updatePart(applier, "head", model.head);
            model.hat.copyFrom(model.head);
            updatePart(applier, "leftArm", model.leftArm);
            updatePart(applier, "rightArm", model.rightArm);
            updatePart(applier, "leftLeg", model.leftLeg);
            updatePart(applier, "rightLeg", model.rightLeg);
            updatePart(applier, "torso", model.body);

            // 初期フレームで腕の変化量が小さくても原作VMDを捨てない。
            // 旧実装はその判定で本物のアニメーションを手動姿勢に差し替えていた。
            installAnimationSupplier(model, applier);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logFailureOnce("Failed to apply SlashBlade player VMD to Venus mob", exception);
            clearAnimationState(model);
            return false;
        }
    }

    /**
     * プレイヤー用PlayerRendererMixin#applyBodyTransformsと同じbodyルート変形。
     * 個々の腕の回転だけでは再現できない踏み込みや身体のひねりも揃える。
     */
    public static boolean applySlashBladeBodyTransform(PoseStack poses, VenusZombie zombie, float partialTick) {
        if (!isBladeAnimationActive(zombie) || !resolve()) {
            return false;
        }

        try {
            Object applier = prepareAnimation(zombie, partialTick);
            if (applier == null) {
                return false;
            }

            float[] scale = getBodyTransform(applier, transformScale, 1.0F, 1.0F, 1.0F);
            float[] position = getBodyTransform(applier, transformPosition, 0.0F, 0.0F, 0.0F);
            float[] rotation = getBodyTransform(applier, transformRotation, 0.0F, 0.0F, 0.0F);

            // PlayerRendererMixinと同じ順番。bodyとtorsoは別々のボーン。
            poses.scale(scale[0], scale[1], scale[2]);
            poses.translate(position[0], position[1] + 0.7D, position[2]);
            poses.mulPose(Axis.ZP.rotation(rotation[2]));
            poses.mulPose(Axis.YP.rotation(rotation[1]));
            poses.mulPose(Axis.XP.rotation(rotation[0]));
            poses.translate(0.0D, -0.7D, 0.0D);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logFailureOnce("Failed to apply SlashBlade player body transform to Venus mob", exception);
            return false;
        }
    }

    private static float[] getBodyTransform(Object applier, Object transformType,
                                            float x, float y, float z) throws ReflectiveOperationException {
        Object input = vectorConstructor.newInstance(x, y, z);
        Object result = animationGet3DTransformMethod.invoke(applier, "body", transformType, input);
        return new float[]{
                ((Number) vectorGetXMethod.invoke(result)).floatValue(),
                ((Number) vectorGetYMethod.invoke(result)).floatValue(),
                ((Number) vectorGetZMethod.invoke(result)).floatValue()
        };
    }

    private static boolean isBladeAnimationActive(VenusZombie zombie) {
        return zombie.isBladeTechniqueActive()
                && (zombie.isSlashBladeWielder() || zombie.hasSlashBladeEquipped());
    }

    private static Object prepareAnimation(VenusZombie zombie, float partialTick)
            throws ReflectiveOperationException {
        VenusBladeMotionTimeline.Sample sample = VenusBladeMotionTimeline.sample(
                zombie.getBladeTechnique(), zombie.getBladeTechniqueTick() + partialTick);
        if (sample == null) {
            return null;
        }
        AnimationSelection selection = selectAnimation(sample);
        Object animation = createAnimation(selection);

        // VMD 30fpsとMinecraft 20tpsを相互変換し、整数tick+端数として再生。
        // これで同じVMDの原作キーフレームを任意のMob攻撃時刻で正確にサンプリングする。
        float ticks = sample.frameOffset() * (20.0F / 30.0F);
        int elapsed = (int) Math.floor(ticks);
        vmdPlayMethod.invoke(animation, elapsed);
        Object applier = animationApplierConstructor.newInstance(animation);
        animationSetTickDeltaMethod.invoke(applier, ticks - elapsed);
        return applier;
    }

    private static Object createAnimation(AnimationSelection selection) throws ReflectiveOperationException {
        // SlashBlade 1.21.1のマップ値はVmdAnimationではなくSupplier<IAnimation>。
        // SupplierにgetClone()を呼ぶと例外となり、実際の腕モーションが適用されない。
        if (slashBladeAnimations != null) {
            Object template = slashBladeAnimations.get(selection.animationId());
            if (template instanceof Supplier<?> factory) {
                Object animation = factory.get();
                if (animation != null && vmdCloneMethod.getDeclaringClass().isInstance(animation)) {
                    return animation;
                }
            } else if (template != null && vmdCloneMethod.getDeclaringClass().isInstance(template)) {
                return vmdCloneMethod.invoke(template);
            }
        }

        // SlashBlade 2.0.x PlayerAnimationOverrider uses these exact ranges from
        // slashblade:model/pa/player_motion.vmd. This path avoids depending on its
        // singleton/map registration and is therefore more reliable for non-player mobs.
        return vmdConstructor.newInstance(MOTION, selection.startFrame(), selection.endFrame(), false);
    }

    private static AnimationSelection selectAnimation(VenusBladeMotionTimeline.Sample sample) {
        return switch (sample.motion()) {
            case COMBO_A1 -> new AnimationSelection(COMBO_A1, 1.0D, 41.0D);
            case COMBO_A2 -> new AnimationSelection(COMBO_A2, 100.0D, 151.0D);
            case COMBO_A3 -> new AnimationSelection(COMBO_A3, 200.0D, 306.0D);
            case COMBO_A4 -> new AnimationSelection(COMBO_A4, 500.0D, 608.0D);
            case RAPID_SLASH -> new AnimationSelection(RAPID_SLASH, 2000.0D, 2073.0D);
            case JUDGEMENT_CUT -> new AnimationSelection(IAI_SLASH, 1900.0D, 1963.0D);
        };
    }

    private static void updatePart(Object applier, String name, ModelPart part) throws ReflectiveOperationException {
        animationUpdatePartMethod.invoke(applier, name, part);
    }

    private static boolean resolve() {
        if (lookupDone) {
            return available;
        }
        lookupDone = true;

        if (!ModList.get().isLoaded("slashblade") || !ModList.get().isLoaded("playeranimator")) {
            available = false;
            return false;
        }

        try {
            Class<?> vmdAnimationClass = Class.forName(
                    "mods.flammpfeil.slashblade.compat.playerAnim.VmdAnimation");
            vmdCloneMethod = vmdAnimationClass.getMethod("getClone");
            vmdPlayMethod = vmdAnimationClass.getMethod("play", int.class);
            vmdConstructor = vmdAnimationClass.getConstructor(
                    ResourceLocation.class, double.class, double.class, boolean.class);

            Class<?> iAnimationClass = Class.forName("dev.kosmx.playerAnim.api.layered.IAnimation");
            Class<?> animationApplierClass = Class.forName(
                    "dev.kosmx.playerAnim.impl.animation.AnimationApplier");
            animationApplierConstructor = animationApplierClass.getConstructor(iAnimationClass);
            animationSetTickDeltaMethod = animationApplierClass.getMethod("setTickDelta", float.class);
            animationUpdatePartMethod = animationApplierClass.getMethod("updatePart", String.class, ModelPart.class);

            Class<?> transformTypeClass = Class.forName("dev.kosmx.playerAnim.api.TransformType");
            Class<?> vectorClass = Class.forName("dev.kosmx.playerAnim.core.util.Vec3f");
            vectorConstructor = vectorClass.getConstructor(float.class, float.class, float.class);
            vectorGetXMethod = vectorClass.getMethod("getX");
            vectorGetYMethod = vectorClass.getMethod("getY");
            vectorGetZMethod = vectorClass.getMethod("getZ");
            animationGet3DTransformMethod = animationApplierClass.getMethod(
                    "get3DTransform", String.class, transformTypeClass, vectorClass);
            transformPosition = enumValue(transformTypeClass, "POSITION");
            transformRotation = enumValue(transformTypeClass, "ROTATION");
            transformScale = enumValue(transformTypeClass, "SCALE");

            // PlayerAnimationOverrider is useful but not required. If this optional map
            // lookup fails, createAnimation() directly constructs the known VMD clip.
            try {
                Class<?> overriderClass = Class.forName(
                        "mods.flammpfeil.slashblade.compat.playerAnim.PlayerAnimationOverrider");
                Object overrider = overriderClass.getMethod("getInstance").invoke(null);
                Object animations = overriderClass.getMethod("getAnimation").invoke(overrider);
                if (animations instanceof Map<?, ?> map) {
                    slashBladeAnimations = map;
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
                slashBladeAnimations = null;
            }

            resolveOptionalBendSupport();
            available = true;
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            available = false;
            slashBladeAnimations = null;
            logFailureOnce("SlashBlade/PlayerAnimator animation bridge could not initialize", exception);
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), name);
    }

    private static void resolveOptionalBendSupport() {
        try {
            Class<?> mutableModelClass = Class.forName("dev.kosmx.playerAnim.impl.IMutableModel");
            mutableModelGetSupplierMethod = mutableModelClass.getMethod("getEmoteSupplier");

            Class<?> supplierClass = Class.forName("dev.kosmx.playerAnim.core.util.SetableSupplier");
            supplierSetMethod = supplierClass.getMethod("set", Object.class);

            Class<?> bendHelperClass = Class.forName("dev.kosmx.playerAnim.impl.animation.IBendHelper");
            Field instanceField = bendHelperClass.getField("INSTANCE");
            bendHelper = instanceField.get(null);
            bendMethod = bendHelperClass.getMethod("bend", ModelPart.class, float.class, float.class);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            mutableModelGetSupplierMethod = null;
            supplierSetMethod = null;
            bendHelper = null;
            bendMethod = null;
        }
    }

    private static void installAnimationSupplier(ZombieModel<?> model, Object applier) {
        if (mutableModelGetSupplierMethod == null || supplierSetMethod == null) {
            return;
        }
        try {
            Object supplier = mutableModelGetSupplierMethod.invoke(model);
            if (supplier != null) {
                supplierSetMethod.invoke(supplier, applier);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // ModelPart transforms are already applied. Bend rendering is optional.
        }
    }

    private static void clearAnimationState(ZombieModel<?> model) {
        if (!lookupDone || !available) {
            return;
        }

        if (mutableModelGetSupplierMethod != null && supplierSetMethod != null) {
            try {
                Object supplier = mutableModelGetSupplierMethod.invoke(model);
                if (supplier != null) {
                    supplierSetMethod.invoke(supplier, new Object[] { null });
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // No-op; vanilla/fallback posing still works.
            }
        }

        resetBend(model.body);
        resetBend(model.leftArm);
        resetBend(model.rightArm);
        resetBend(model.leftLeg);
        resetBend(model.rightLeg);
    }

    private static void resetBend(ModelPart part) {
        if (bendHelper == null || bendMethod == null) {
            return;
        }
        try {
            bendMethod.invoke(bendHelper, part, 0.0F, 0.0F);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // No-op when PlayerAnimator changes its internal bend API.
        }
    }

    private static void logFailureOnce(String message, Throwable throwable) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        LOGGER.warn("{}; falling back to VenusMod blade poses", message, throwable);
    }

    private static ResourceLocation slashBlade(String path) {
        return ResourceLocation.fromNamespaceAndPath("slashblade", path);
    }

    private record AnimationSelection(ResourceLocation animationId, double startFrame, double endFrame) {
    }
}
