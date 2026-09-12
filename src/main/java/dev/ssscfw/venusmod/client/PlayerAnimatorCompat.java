package dev.ssscfw.venusmod.client;

import dev.ssscfw.venusmod.entity.VenusZombie;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelPart;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Optional PlayerAnimator integration for blade-wielding Venus zombies.
 * PlayerAnimator's humanoid bend API is applied to the same synced technique state
 * used by VenusZombieModel. Without PlayerAnimator, normal model rotations remain as
 * the fallback and VenusMod keeps no hard dependency on the animation mod.
 */
public final class PlayerAnimatorCompat {
    private static boolean lookupDone;
    private static Object bendHelper;
    private static Method bendMethod;

    private PlayerAnimatorCompat() {
    }

    public static void applyBladeBends(ZombieModel<?> model, VenusZombie zombie) {
        if (!resolve()) {
            return;
        }

        float bodyAxis = 0.0F;
        float bodyBend = 0.0F;
        float rightArmBend = 0.0F;
        float leftArmBend = 0.0F;

        if (zombie.isSlashBladeWielder()) {
            int technique = zombie.getBladeTechnique();
            int tick = zombie.getBladeTechniqueTick();
            if (technique == VenusZombie.BLADE_TECHNIQUE_COMBO) {
                bodyAxis = (float) Math.sin(tick * 0.65F) * 0.65F;
                bodyBend = 0.18F + 0.08F * (float) Math.sin(tick * 0.8F);
                rightArmBend = 0.35F;
                leftArmBend = 0.15F;
            } else if (technique == VenusZombie.BLADE_TECHNIQUE_RAPID_SLASH) {
                bodyAxis = 0.0F;
                bodyBend = -0.38F;
                rightArmBend = 0.52F;
                leftArmBend = 0.22F;
            }
        }

        bend(model.body, bodyAxis, bodyBend);
        bend(model.rightArm, -0.25F, rightArmBend);
        bend(model.leftArm, 0.25F, leftArmBend);
    }

    private static boolean resolve() {
        if (lookupDone) {
            return bendHelper != null && bendMethod != null;
        }
        lookupDone = true;

        try {
            Class<?> helperClass = Class.forName("dev.kosmx.playerAnim.impl.animation.IBendHelper");
            Field instance = helperClass.getField("INSTANCE");
            bendHelper = instance.get(null);
            bendMethod = helperClass.getMethod("bend", ModelPart.class, float.class, float.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            bendHelper = null;
            bendMethod = null;
            return false;
        }
    }

    private static void bend(ModelPart part, float axis, float amount) {
        try {
            bendMethod.invoke(bendHelper, part, axis, amount);
        } catch (ReflectiveOperationException ignored) {
            // Keep vanilla posing if PlayerAnimator changes its internal API.
        }
    }
}
