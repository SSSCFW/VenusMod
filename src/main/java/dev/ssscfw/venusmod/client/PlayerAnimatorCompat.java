package dev.ssscfw.venusmod.client;

import com.mojang.logging.LogUtils;
import dev.ssscfw.venusmod.entity.VenusZombie;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

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

        try {
            Object animation = createAnimation(selection);
            if (animation == null) {
                clearAnimationState(model);
                return false;
            }

            vmdPlayMethod.invoke(animation, selection.localTick());
            Object applier = animationApplierConstructor.newInstance(animation);

            float partialTick = (float) (ageInTicks - Math.floor(ageInTicks));
            animationSetTickDeltaMethod.invoke(applier, partialTick);

            // Do this FIRST. Earlier code attempted to install the IMutableModel supplier
            // before these calls; if that optional bridge failed, no VMD rotation ever
            // reached the zombie. AnimationApplier.updatePart is sufficient to apply the
            // actual VMD position/rotation/scale to these ModelParts.
            updatePart(applier, "head", model.head);
            model.hat.copyFrom(model.head);
            updatePart(applier, "leftArm", model.leftArm);
            updatePart(applier, "rightArm", model.rightArm);
            updatePart(applier, "leftLeg", model.leftLeg);
            updatePart(applier, "rightLeg", model.rightLeg);
            updatePart(applier, "torso", model.body);

            // Best-effort bend integration. Failure here must never discard the already
            // applied VMD rotations above.
            installAnimationSupplier(model, applier);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logFailureOnce("Failed to apply SlashBlade VMD to Venus zombie", exception);
            clearAnimationState(model);
            return false;
        }
    }

    private static Object createAnimation(AnimationSelection selection) throws ReflectiveOperationException {
        // Prefer SlashBlade's own registered template so future compatible versions can
        // adjust their VMD object while keeping the same combo IDs.
        if (slashBladeAnimations != null) {
            Object template = slashBladeAnimations.get(selection.animationId());
            if (template != null) {
                return vmdCloneMethod.invoke(template);
            }
        }

        // SlashBlade 2.0.x PlayerAnimationOverrider uses these exact ranges from
        // slashblade:model/pa/player_motion.vmd. This path avoids depending on its
        // singleton/map registration and is therefore more reliable for non-player mobs.
        return vmdConstructor.newInstance(MOTION, selection.startFrame(), selection.endFrame(), false);
    }

    private static AnimationSelection selectAnimation(VenusZombie zombie) {
        int technique = zombie.getBladeTechnique();
        int tick = Math.max(0, zombie.getBladeTechniqueTick());

        if (technique == VenusZombie.BLADE_TECHNIQUE_RAPID_SLASH) {
            return new AnimationSelection(RAPID_SLASH, tick, 2000.0D, 2073.0D);
        }
        if (technique != VenusZombie.BLADE_TECHNIQUE_COMBO) {
            return null;
        }

        // These transitions mirror VenusZombie's attack timing while using the same
        // VMD clips SlashBlade assigns to spam-click A1/A2/A3/A4.
        if (tick < 4) {
            return new AnimationSelection(COMBO_A1, tick, 1.0D, 41.0D);
        }
        if (tick < 8) {
            return new AnimationSelection(COMBO_A2, tick - 4, 100.0D, 151.0D);
        }
        if (tick < 15) {
            return new AnimationSelection(COMBO_A3, tick - 8, 200.0D, 306.0D);
        }
        return new AnimationSelection(COMBO_A4, tick - 15, 500.0D, 608.0D);
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

    private record AnimationSelection(
            ResourceLocation animationId,
            int localTick,
            double startFrame,
            double endFrame) {
    }
}
