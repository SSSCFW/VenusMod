package dev.ssscfw.venusmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** SlashBladeのOBJから刀身だけを描画するクライアント専用反射ブリッジ。 */
public final class SlashBladeNakedRenderCompat {
    private static boolean lookupDone;
    private static Method stateOf;
    private static Method stateGetModel;
    private static Method stateGetTexture;
    private static Method stateIsBroken;
    private static Method modelManagerGetInstance;
    private static Method modelManagerGetModel;
    private static Method renderNormal;
    private static Method renderLuminous;
    private static Object slashBladeTeisr;
    private static Method teisrDefaultModel;
    private static Method teisrDefaultTexture;
    private static ResourceLocation defaultModel;
    private static ResourceLocation defaultTexture;

    private SlashBladeNakedRenderCompat() {}

    public static boolean render(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light) {
        if (stack == null || stack.isEmpty() || !resolve()) return false;
        try {
            Object rawState = stateOf.invoke(null, stack);
            Object state = rawState instanceof Optional<?> optional ? optional.orElse(null) : null;
            ResourceLocation modelLocation = stackResource(
                    stack, state, stateGetModel, teisrDefaultModel, defaultModel);
            ResourceLocation textureLocation = stackResource(
                    stack, state, stateGetTexture, teisrDefaultTexture, defaultTexture);
            Object manager = modelManagerGetInstance.invoke(null);
            Object model = modelManagerGetModel.invoke(manager, modelLocation);
            boolean broken = state != null && Boolean.TRUE.equals(stateIsBroken.invoke(state));
            String target = broken ? "blade_damaged" : "blade";

            pose.pushPose();
            try {
                pose.scale(0.0046875F, 0.0046875F, 0.0046875F);
                pose.translate(130.0F, 0.0F, 0.0F);
                renderNormal.invoke(null, stack, model, target, textureLocation, pose, buffers, light);
                renderLuminous.invoke(null, stack, model, target + "_luminous", textureLocation, pose, buffers, light);
            } finally {
                pose.popPose();
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static ResourceLocation stackResource(ItemStack stack, Object state, Method getter,
                                                  Method teisrGetter, ResourceLocation fallback)
            throws ReflectiveOperationException {
        if (state != null) {
            Object value = getter.invoke(state);
            if (value instanceof Optional<?> optional
                    && optional.orElse(null) instanceof ResourceLocation location) {
                return location;
            }
        }
        if (slashBladeTeisr != null && teisrGetter != null) {
            Object value = teisrGetter.invoke(slashBladeTeisr, stack);
            if (value instanceof ResourceLocation location) return location;
        }
        return fallback;
    }

    private static boolean resolve() {
        if (lookupDone) return stateOf != null;
        lookupDone = true;
        try {
            Class<?> stateAccess = Class.forName(
                    "mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess");
            Class<?> stateType = Class.forName(
                    "mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState");
            Class<?> modelManager = Class.forName(
                    "mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager");
            Class<?> wavefront = Class.forName(
                    "mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject");
            Class<?> renderState = Class.forName(
                    "mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState");

            stateOf = stateAccess.getMethod("of", ItemStack.class);
            stateGetModel = stateType.getMethod("getModel");
            stateGetTexture = stateType.getMethod("getTexture");
            stateIsBroken = stateType.getMethod("isBroken");
            modelManagerGetInstance = modelManager.getMethod("getInstance");
            modelManagerGetModel = modelManager.getMethod("getModel", ResourceLocation.class);
            renderNormal = renderState.getMethod(
                    "renderOverrided", ItemStack.class, wavefront, String.class, ResourceLocation.class,
                    PoseStack.class, MultiBufferSource.class, int.class);
            renderLuminous = renderState.getMethod(
                    "renderOverridedLuminous", ItemStack.class, wavefront, String.class, ResourceLocation.class,
                    PoseStack.class, MultiBufferSource.class, int.class);
            defaultModel = defaultResource(modelManager, "resourceDefaultModel");
            defaultTexture = defaultResource(modelManager, "resourceDefaultTexture");
            resolveTeisrDefaults();
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            stateOf = null;
            return false;
        }
    }

    private static void resolveTeisrDefaults() {
        try {
            Class<?> minecraft = Class.forName("net.minecraft.client.Minecraft");
            Object client = minecraft.getMethod("getInstance").invoke(null);
            Object dispatcher = minecraft.getMethod("getBlockEntityRenderDispatcher").invoke(client);
            Object models = minecraft.getMethod("getEntityModels").invoke(client);
            Class<?> teisrClass = Class.forName(
                    "mods.flammpfeil.slashblade.client.renderer.SlashBladeTEISR");
            for (Constructor<?> constructor : teisrClass.getConstructors()) {
                Class<?>[] params = constructor.getParameterTypes();
                if (params.length == 2 && params[0].isInstance(dispatcher) && params[1].isInstance(models)) {
                    slashBladeTeisr = constructor.newInstance(dispatcher, models);
                    teisrDefaultModel = teisrClass.getMethod("stackDefaultModel", ItemStack.class);
                    teisrDefaultTexture = teisrClass.getMethod("stackDefaultTexture", ItemStack.class);
                    return;
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            slashBladeTeisr = null;
            teisrDefaultModel = null;
            teisrDefaultTexture = null;
        }
    }

    private static ResourceLocation defaultResource(Class<?> modelManager, String fieldName)
            throws ReflectiveOperationException {
        try {
            Field field = modelManager.getField(fieldName);
            return (ResourceLocation) field.get(null);
        } catch (NoSuchFieldException ignored) {
            Class<?> defaults = Class.forName("mods.flammpfeil.slashblade.init.DefaultResources");
            Field field = defaults.getField(fieldName);
            return (ResourceLocation) field.get(null);
        }
    }
}
