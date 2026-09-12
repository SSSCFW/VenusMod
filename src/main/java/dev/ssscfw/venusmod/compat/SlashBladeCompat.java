package dev.ssscfw.venusmod.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
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
     * slash entity/effect. The returned projectile's owner is then cleared so it stays
     * visual-only; VenusZombie performs the authoritative species-filtered damage.
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
                Method setOwner = slash.getClass().getMethod("setOwner", Entity.class);
                setOwner.invoke(slash, new Object[] { null });
            } catch (ReflectiveOperationException ignored) {
                // Damage filtering still prevents cross-species hits if a future
                // SlashBlade version changes the projectile owner API.
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
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
