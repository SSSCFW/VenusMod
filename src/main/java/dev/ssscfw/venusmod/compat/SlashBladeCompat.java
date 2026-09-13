package dev.ssscfw.venusmod.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional SlashBlade Resharped bridge. This class never links against SlashBlade at
 * compile time, so VenusMod can still load normally when SlashBlade is not installed.
 */
public final class SlashBladeCompat {
    public static final String MOD_ID = "slashblade";

    public static final ResourceLocation WOODEN_BLADE =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "slashblade_wood");
    public static final ResourceLocation WHITE_SCABBARD =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "slashblade_white");
    public static final ResourceLocation NAMELESS_BLADE =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "slashblade");

    private static final ResourceLocation[] VENUS_ZOMBIE_BLADES = {
            WOODEN_BLADE,
            WHITE_SCABBARD,
            NAMELESS_BLADE
    };

    private static Method doSlashMethod;
    private static Method setSlashDamageMethod;
    private static Method discardSlashMethod;
    private static boolean attackManagerLookupDone;

    private SlashBladeCompat() {
    }

    public static boolean isSlashBladeItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && MOD_ID.equals(id.getNamespace());
    }

    /** 金星将軍用。無銘→白鞘→木偶の順に利用可能な最上位を選ぶ。 */
    public static ItemStack createVenusGeneralBlade() {
        for (ResourceLocation id : new ResourceLocation[]{NAMELESS_BLADE, WHITE_SCABBARD, WOODEN_BLADE}) {
            if (BuiltInRegistries.ITEM.containsKey(id)) {
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item != null) return new ItemStack(item);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Returns one of 木偶 / 白鞘 / 無銘. If SlashBlade is absent, returns EMPTY. */
    public static ItemStack createRandomVenusZombieBlade(RandomSource random) {
        List<Item> available = new ArrayList<>(VENUS_ZOMBIE_BLADES.length);
        for (ResourceLocation id : VENUS_ZOMBIE_BLADES) {
            if (BuiltInRegistries.ITEM.containsKey(id)) {
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item != null) {
                    available.add(item);
                }
            }
        }

        if (available.isEmpty()) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(available.get(random.nextInt(available.size())));
    }

    /**
     * Invokes SlashBlade's real AttackManager.doSlash implementation for its authentic
     * slash entity/effect. VenusMod owns the actual hit/damage decision, so the native
     * slash entity is immediately changed to zero damage while its owner is retained.
     * Keeping the owner is important for SlashBlade's renderer/state, while zero damage
     * prevents its delayed area attack from bypassing VenusMod's species filter.
     *
     * If the SlashBlade version no longer exposes setDamage(double), the spawned effect
     * is discarded rather than leaving a live damaging slash behind.
     */
    public static boolean doSlash(LivingEntity user, float roll, boolean mute, boolean critical, double comboRatio) {
        Method method = resolveDoSlashMethod();
        if (method == null) {
            return false;
        }

        try {
            Object slash = method.invoke(null, user, roll, mute, critical, comboRatio);
            if (slash == null) {
                return false;
            }

            try {
                Method setDamage = setSlashDamageMethod;
                if (setDamage == null || setDamage.getDeclaringClass() != slash.getClass()) {
                    setDamage = slash.getClass().getMethod("setDamage", double.class);
                    setSlashDamageMethod = setDamage;
                }
                setDamage.invoke(slash, 0.0D);
                return true;
            } catch (ReflectiveOperationException | LinkageError neutralizeFailure) {
                discardSlashEffect(slash);
                return false;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static void discardSlashEffect(Object slash) {
        try {
            Method discard = discardSlashMethod;
            if (discard == null || !discard.getDeclaringClass().isAssignableFrom(slash.getClass())) {
                discard = slash.getClass().getMethod("discard");
                discardSlashMethod = discard;
            }
            discard.invoke(slash);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Last-resort compatibility failure: the caller still keeps authoritative
            // Venus damage separate, and future SlashBlade API changes can be handled
            // without making SlashBlade a hard compile-time dependency.
        }
    }

    private static Method resolveDoSlashMethod() {
        if (attackManagerLookupDone) {
            return doSlashMethod;
        }
        attackManagerLookupDone = true;

        try {
            Class<?> attackManager = Class.forName("mods.flammpfeil.slashblade.util.AttackManager");
            doSlashMethod = attackManager.getMethod(
                    "doSlash",
                    LivingEntity.class,
                    float.class,
                    boolean.class,
                    boolean.class,
                    double.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            doSlashMethod = null;
        }
        return doSlashMethod;
    }
}
