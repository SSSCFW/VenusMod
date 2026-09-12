package dev.ssscfw.venusmod.client;

import dev.ssscfw.venusmod.entity.VenusZombie;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Optional PlayerAnimator + SlashBlade animation bridge for blade-wielding Venus
 * zombies. When both mods are present this reuses SlashBlade Resharped's own VMD
 * animations and feeds them through PlayerAnimator's AnimationApplier, so the zombie
 * receives the same humanoid part transforms as a player using those techniques.
 *
 * Everything is reflective so neither PlayerAnimator nor SlashBlade becomes a hard
 * compile/runtime dependency of VenusMod.
 */
public final class PlayerAnimatorCompat {
    private static final ResourceLocation COMBO_A1 = slashBlade("combo_a1");
    private static final ResourceLocation COMBO_A2 = slashBlade("combo_a2");
    private static final ResourceLocation COMBO_A3 = slashBlade("combo_a3");
    private static final ResourceLocation COMBO_A4 = slashBlade("combo_a4");
    private static final ResourceLocation RAPID_SLASH = slashBlade("rapid_slash");

    private static boolean lookupDone;
    private static boolean available;

    private static Map<?, ?> slashBladeAnimations;
    private static Method vmdCloneMethod;
    private static Method vmdPlayMethod;
    private static Constructor<?> animationApplierConstructor;
    private static Method animationSetTickDeltaMethod;
    private static Method animationUpdatePartMethod;
    private static Method mutableModelGetSupplierMethod;
    private static Method supplierSetMethod;
    private static Object bendHelper;
    private static Method bendMethod;

    private PlayerAnimatorCompat() {
    }

    /**
     * Applies SlashBlade's real player VMD for the currently synced Venus technique.
     * Returns true only when the VMD was successfully applied this frame.
     */
    public static boolean applySlashBladeAnimation(ZombieModel<?> model, VenusZombie zombie, float ageInTicks) {
        if (!zombie.isSlashBladeWielder() || !zombie.isBladeTechniqueActive()) {
            clearAnimationState(model);
            return false;
        }
        if (!resolve()) {
            return false;
        }

        AnimationSelection selection = selectAnimation(zombie);
        if (selection == null) {
            clearAnimationState(model);
            return false;
        }

        Object template = slashBladeAnimations.get(selection.animationId());
        if (template == null) {
            clearAnimationState(model);
            return false;
        }

        try {
            Object animation = vmdCloneMethod.invoke(template);
            vmdPlayMethod.invoke(animation, selection.localTick());

            Object applier = animationApplierConstructor.newInstance(animation);
            float partialTick = (float) (ageInTicks - Math.floor(ageInTicks));
            animationSetTickDeltaMethod.invoke(applier, partialTick);

            Object supplier = mutableModelGetSupplierMethod.invoke(model);
            supplierSetMethod.invoke(supplier, applier);

            updatePart(applier, "head", model.head);
            model.hat.copyFrom(model.head);
            updatePart(applier, "leftArm", model.leftArm);
            updatePart(applier, "rightArm", model.rightArm);
            updatePart(applier, "leftLeg", model.leftLeg);
            updatePart(applier, "rightLeg", model.rightLeg);
            updatePart(applier, "torso", model.body);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            clearAnimationState(model);
            return false;
        }
    }

    private static AnimationSelection selectAnimation(VenusZombie zombie) {
        int technique = zombie.getBladeTechnique();
        int tick = Math.max(0, zombie.getBladeTechniqueTick());

        if (technique == VenusZombie.BLADE_TECHNIQUE_RAPID_SLASH) {
            return new AnimationSelection(RAPID_SLASH, tick);
        }
        if (technique != VenusZombie.BLADE_TECHNIQUE_COMBO) {
            return null;
        }

        // VenusZombie's attack timings intentionally mirror the early active frames
        // of SlashBlade's spam-click A combo. Keep each local tick aligned with the
        // corresponding VMD instead of stretching one generic swing over the chain.
        if (tick < 4) {
            return new AnimationSelection(COMBO_A1, tick);
        }
        if (tick < 8) {
            return new AnimationSelection(COMBO_A2, tick - 4);
        }
        if (tick < 15) {
            return new AnimationSelection(COMBO_A3, tick - 8);
        }
        return new AnimationSelection(COMBO_A4, tick - 15);
    }

    private static void updatePart(Object applier, String name, ModelPart part) throws ReflectiveOperationException {
        animationUpdatePartMethod.invoke(applier, name, part);
    }

    private static boolean resolve() {
        if (lookupDone) {
            return available;
        }
        lookupDone = true;

        try {
            Class<?> overriderClass = Class.forName(
                    "mods.flammpfeil.slashblade.compat.playerAnim.PlayerAnimationOverrider");
            Object overrider = overriderClass.getMethod("getInstance").invoke(null);
            Object animations = overriderClass.getMethod("getAnimation").invoke(overrider);
            if (!(animations instanceof Map<?, ?> map)) {
                return false;
            }
            slashBladeAnimations = map;

            Class<?> vmdAnimationClass = Class.forName(
                    "mods.flammpfeil.slashblade.compat.playerAnim.VmdAnimation");
            vmdCloneMethod = vmdAnimationClass.getMethod("getClone");
            vmdPlayMethod = vmdAnimationClass.getMethod("play", int.class);

            Class<?> iAnimationClass = Class.forName("dev.kosmx.playerAnim.api.layered.IAnimation");
            Class<?> animationApplierClass = Class.forName(
                    "dev.kosmx.playerAnim.impl.animation.AnimationApplier");
            animationApplierConstructor = animationApplierClass.getConstructor(iAnimationClass);
            animationSetTickDeltaMethod = animationApplierClass.getMethod("setTickDelta", float.class);
            animationUpdatePartMethod = animationApplierClass.getMethod("updatePart", String.class, ModelPart.class);

            Class<?> mutableModelClass = Class.forName("dev.kosmx.playerAnim.impl.IMutableModel");
            mutableModelGetSupplierMethod = mutableModelClass.getMethod("getEmoteSupplier");

            Class<?> supplierClass = Class.forName("dev.kosmx.playerAnim.core.util.SetableSupplier");
            supplierSetMethod = supplierClass.getMethod("set", Object.class);

            Class<?> bendHelperClass = Class.forName("dev.kosmx.playerAnim.impl.animation.IBendHelper");
            Field instanceField = bendHelperClass.getField("INSTANCE");
            bendHelper = instanceField.get(null);
            bendMethod = bendHelperClass.getMethod("bend", ModelPart.class, float.class, float.class);

            available = true;
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            available = false;
            slashBladeAnimations = null;
            return false;
        }
    }

    private static void clearAnimationState(ZombieModel<?> model) {
        if (!resolve()) {
            return;
        }

        try {
            Object supplier = mutableModelGetSupplierMethod.invoke(model);
            supplierSetMethod.invoke(supplier, new Object[] { null });
        } catch (ReflectiveOperationException ignored) {
            // Fallback posing below remains valid even if PlayerAnimator internals move.
        }

        resetBend(model.body);
        resetBend(model.leftArm);
        resetBend(model.rightArm);
        resetBend(model.leftLeg);
        resetBend(model.rightLeg);
    }

    private static void resetBend(ModelPart part) {
        try {
            bendMethod.invoke(bendHelper, part, 0.0F, 0.0F);
        } catch (ReflectiveOperationException ignored) {
            // No-op when PlayerAnimator changes its internal bend API.
        }
    }

    private static ResourceLocation slashBlade(String path) {
        return ResourceLocation.fromNamespaceAndPath("slashblade", path);
    }

    private record AnimationSelection(ResourceLocation animationId, int localTick) {
    }
}
