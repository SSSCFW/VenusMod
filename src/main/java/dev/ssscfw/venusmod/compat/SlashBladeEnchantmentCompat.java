package dev.ssscfw.venusmod.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.lang.reflect.Method;
import java.util.Optional;

/** Optional reflection bridge used by VenusMod's SlashBlade-only features. */
public final class SlashBladeEnchantmentCompat {
    private static final ResourceLocation CONCENTRATION_ATTACHMENT =
            ResourceLocation.fromNamespaceAndPath(SlashBladeCompat.MOD_ID, "concentration");
    private static final ResourceLocation PROUDSOUL =
            ResourceLocation.fromNamespaceAndPath(SlashBladeCompat.MOD_ID, "proudsoul");

    private static boolean lookupDone;
    private static Class<?> slashBladeItemClass;
    private static Class<?> summonedSwordClass;
    private static Method bladeStateOfMethod;
    private static Method resolveCurrentComboMethod;
    private static Method getUnitCapacityMethod;
    private static Method getRankPointModifierDamageMethod;
    private static Method getRankPointModifierComboMethod;
    private static Method addRankPointMethod;

    private static boolean machineLookupDone;
    private static Method isBrokenMethod;
    private static Method isDestructableMethod;
    private static Method setBrokenMethod;
    private static Method getDamageMethod;
    private static Method setDamageMethod;

    private SlashBladeEnchantmentCompat() {
    }

    public static boolean isBlade(ItemStack stack) {
        if (stack.isEmpty() || !resolve()) {
            return false;
        }
        return slashBladeItemClass.isInstance(stack.getItem());
    }

    public static boolean isSummonedSword(Entity entity) {
        return entity != null && resolve() && summonedSwordClass.isInstance(entity);
    }

    /**
     * Adds only the extra Stylish bonus. SlashBlade itself has already awarded its
     * normal rank points by the time this is called from LivingDamageEvent.Post.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void addStylishRankBonus(LivingEntity user, DamageSource source, int enchantmentLevel) {
        if (enchantmentLevel <= 0 || !resolve() || !isBlade(user.getMainHandItem())) {
            return;
        }

        AttachmentType<?> attachment = NeoForgeRegistries.ATTACHMENT_TYPES.get(CONCENTRATION_ATTACHMENT);
        if (attachment == null) {
            return;
        }

        try {
            Object rank = user.getData((AttachmentType) attachment);
            long unitCapacity = ((Number) getUnitCapacityMethod.invoke(rank)).longValue();
            float modifier = getNativeRankModifier(rank, user, source);
            long normalGain = (long) (modifier * unitCapacity);
            long bonus = (long) (normalGain * (0.25D * enchantmentLevel));
            if (bonus > 0L) {
                addRankPointMethod.invoke(rank, user, bonus);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // SlashBlade remains optional. A future API change disables only this bonus.
        }
    }

    /** Returns whether this SlashBlade has already entered SlashBlade's broken state. */
    public static boolean isBrokenBlade(ItemStack stack) {
        if (!isBlade(stack) || !resolveMachineMethods()) {
            return false;
        }
        try {
            Object state = getBladeState(stack);
            return state != null && (boolean) isBrokenMethod.invoke(state);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    /**
     * Returns SlashBlade's own normalized durability damage (0.0 = healthy,
     * 1.0 = fully damaged/broken). ItemStack#getDamageValue is not authoritative
     * for SlashBlade and must not be used by the repair machine.
     */
    public static float getBladeDamage(ItemStack stack) {
        if (!isBlade(stack) || !resolveMachineMethods()) {
            return 0.0F;
        }
        try {
            Object state = getBladeState(stack);
            if (state == null) {
                return 0.0F;
            }
            return Math.max(0.0F, ((Number) getDamageMethod.invoke(state)).floatValue());
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return 0.0F;
        }
    }

    public static boolean needsBladeRepair(ItemStack stack) {
        return getBladeDamage(stack) > 0.0F;
    }

    /**
     * Repairs exactly one vanilla durability point worth of SlashBlade's normalized
     * damage. SlashBladeState#setDamage is used deliberately: when damage reaches
     * zero it also clears broken=true for non-sealed blades according to SlashBlade's
     * native repair rule.
     */
    public static boolean repairBladeOnePoint(ItemStack stack) {
        if (!isBlade(stack) || !resolveMachineMethods()) {
            return false;
        }

        try {
            Object state = getBladeState(stack);
            if (state == null) {
                return false;
            }

            float damage = ((Number) getDamageMethod.invoke(state)).floatValue();
            if (damage <= 0.0F) {
                return false;
            }

            int maxDamage = Math.max(1, stack.getMaxDamage());
            float repaired = Math.max(0.0F, damage - (1.0F / maxDamage));
            // Avoid leaving a tiny positive floating-point remainder at full repair.
            if (repaired < (0.5F / maxDamage)) {
                repaired = 0.0F;
            }
            setDamageMethod.invoke(state, repaired);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    /**
     * Breaks one intact blade for the Create machine and creates exactly one regular
     * 刀の魂片 (slashblade:proudsoul). Destructable blades disappear. Blades that
     * normally survive breaking are returned at maxDamage - 1 with broken=true.
     */
    public static Optional<BladeBreakResult> breakBladeForMachine(ItemStack input) {
        if (!isBlade(input) || !resolveMachineMethods()) {
            return Optional.empty();
        }
        if (!BuiltInRegistries.ITEM.containsKey(PROUDSOUL)) {
            return Optional.empty();
        }

        try {
            ItemStack blade = input.copy();
            blade.setCount(1);
            Object state = getBladeState(blade);
            if (state == null || (boolean) isBrokenMethod.invoke(state)) {
                return Optional.empty();
            }

            Item soulItem = BuiltInRegistries.ITEM.get(PROUDSOUL);
            ItemStack soul = new ItemStack(soulItem, 1);

            boolean destructable = (boolean) isDestructableMethod.invoke(state);
            if (destructable) {
                return Optional.of(new BladeBreakResult(ItemStack.EMPTY, soul));
            }

            // SlashBlade's visible durability is state-backed and normalized.
            setDamageMethod.invoke(state, 1.0F);
            setBrokenMethod.invoke(state, true);
            return Optional.of(new BladeBreakResult(blade, soul));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private static float getNativeRankModifier(Object rank, LivingEntity user, DamageSource source)
            throws ReflectiveOperationException {
        Object optionalState = bladeStateOfMethod.invoke(null, user.getMainHandItem());
        if (optionalState instanceof Optional<?> optional && optional.isPresent()) {
            Object state = optional.get();
            Object combo = resolveCurrentComboMethod.invoke(state, user);
            if (combo instanceof ResourceLocation location) {
                return ((Number) getRankPointModifierComboMethod.invoke(rank, location)).floatValue();
            }
        }
        return ((Number) getRankPointModifierDamageMethod.invoke(rank, source)).floatValue();
    }

    private static Object getBladeState(ItemStack stack) throws ReflectiveOperationException {
        Object optionalState = bladeStateOfMethod.invoke(null, stack);
        if (optionalState instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        return null;
    }

    private static boolean resolveMachineMethods() {
        if (machineLookupDone) {
            return isBrokenMethod != null && getDamageMethod != null && setDamageMethod != null;
        }
        machineLookupDone = true;

        if (!resolve()) {
            return false;
        }

        try {
            Class<?> bladeStateInterface = Class.forName(
                    "mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState");
            isBrokenMethod = bladeStateInterface.getMethod("isBroken");
            isDestructableMethod = bladeStateInterface.getMethod("isDestructable");
            setBrokenMethod = bladeStateInterface.getMethod("setBroken", boolean.class);
            getDamageMethod = bladeStateInterface.getMethod("getDamage");
            setDamageMethod = bladeStateInterface.getMethod("setDamage", float.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            isBrokenMethod = null;
            getDamageMethod = null;
            setDamageMethod = null;
            return false;
        }
    }

    private static boolean resolve() {
        if (lookupDone) {
            return slashBladeItemClass != null && summonedSwordClass != null;
        }
        lookupDone = true;

        try {
            slashBladeItemClass = Class.forName("mods.flammpfeil.slashblade.item.ItemSlashBlade");
            summonedSwordClass = Class.forName("mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword");

            Class<?> bladeStateAccess = Class.forName(
                    "mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess");
            bladeStateOfMethod = bladeStateAccess.getMethod("of", ItemStack.class);

            Class<?> bladeStateInterface = Class.forName(
                    "mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState");
            resolveCurrentComboMethod = bladeStateInterface.getMethod(
                    "resolvCurrentComboState", LivingEntity.class);

            Class<?> rankInterface = Class.forName(
                    "mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank");
            getUnitCapacityMethod = rankInterface.getMethod("getUnitCapacity");
            getRankPointModifierDamageMethod = rankInterface.getMethod(
                    "getRankPointModifier", DamageSource.class);
            getRankPointModifierComboMethod = rankInterface.getMethod(
                    "getRankPointModifier", ResourceLocation.class);
            addRankPointMethod = rankInterface.getMethod(
                    "addRankPoint", LivingEntity.class, long.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            slashBladeItemClass = null;
            summonedSwordClass = null;
            return false;
        }
    }

    public record BladeBreakResult(ItemStack survivingBlade, ItemStack soulOutput) {
    }
}
