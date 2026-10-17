package dev.ssscfw.venusmod.compat;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Optional reflection bridge used by VenusMod's SlashBlade-only features. */
public final class SlashBladeEnchantmentCompat {
    private static final ResourceLocation CONCENTRATION_ATTACHMENT =
            ResourceLocation.fromNamespaceAndPath(SlashBladeCompat.MOD_ID, "concentration");
    private static final ResourceLocation PROUDSOUL_TINY =
            ResourceLocation.fromNamespaceAndPath(SlashBladeCompat.MOD_ID, "proudsoul_tiny");

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
    private static Method getProudSoulCountMethod;

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
     * Returns SlashBlade Resharped 1.21.1's native durability damage in vanilla
     * durability points. BladeStateAccess routes this directly to DataComponents.DAMAGE.
     */
    public static int getBladeDamage(ItemStack stack) {
        if (!isBlade(stack) || !resolveMachineMethods()) {
            return 0;
        }
        try {
            Object state = getBladeState(stack);
            if (state == null) {
                return 0;
            }
            return Math.max(0, ((Number) getDamageMethod.invoke(state)).intValue());
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return 0;
        }
    }

    public static boolean needsBladeRepair(ItemStack stack) {
        return getBladeDamage(stack) > 0;
    }

    /**
     * Repairs exactly one native SlashBlade durability point. Resharped 1.21.1 uses
     * ISlashBladeState#setDamage(int); when damage reaches zero that method also
     * clears broken=true for an unsealed blade.
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

            int damage = ((Number) getDamageMethod.invoke(state)).intValue();
            if (damage <= 0) {
                return false;
            }

            setDamageMethod.invoke(state, Math.max(0, damage - 1));
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    /**
     * Breaks one intact blade using SlashBlade Resharped 1.21.1's native broken-blade
     * soul-drop rules. Player breaking always produces slashblade:proudsoul_tiny;
     * the count is based on ProudSoul / 100 and SlashBlade's configured caps. An
     * enchanted blade additionally produces individually enchanted tiny souls using
     * the same supported-enchantment/random-selection rule as ItemSlashBlade#getOnBroken.
     */
    public static Optional<BladeBreakResult> breakBladeForMachine(
            ItemStack input,
            RegistryAccess registryAccess,
            RandomSource random) {
        if (!isBlade(input) || !resolveMachineMethods()) {
            return Optional.empty();
        }
        if (!BuiltInRegistries.ITEM.containsKey(PROUDSOUL_TINY)) {
            return Optional.empty();
        }

        try {
            ItemStack blade = input.copy();
            blade.setCount(1);
            Object state = getBladeState(blade);
            if (state == null || (boolean) isBrokenMethod.invoke(state)) {
                return Optional.empty();
            }

            Item tinySoulItem = BuiltInRegistries.ITEM.get(PROUDSOUL_TINY);
            int proudSoulCount = Math.max(0, ((Number) getProudSoulCountMethod.invoke(state)).intValue());
            List<ItemStack> soulOutputs = new ArrayList<>();

            // Mirror ItemSlashBlade#getOnBroken exactly: enchanted souls are emitted
            // first and consume 100 points each from the local drop calculation.
            if (blade.isEnchanted()) {
                Set<String> nonDroppable = getNonDroppableEnchantments();
                List<Holder.Reference<Enchantment>> enchantments = registryAccess
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .listElements()
                        .filter(blade::supportsEnchantment)
                        .filter(enchantment -> !nonDroppable.contains(enchantment.key().location().toString()))
                        .toList();

                // Native code returns immediately here, so an enchanted blade with no
                // eligible enchantment produces no soul drops at all.
                if (!enchantments.isEmpty()) {
                    int maxEnchantedDrops = getSlashBladeConfigInt("MAX_ENCHANTED_PROUDSOUL_DROP", 10);
                    int enchantedCount = proudSoulCount >= maxEnchantedDrops * 100
                            ? maxEnchantedDrops
                            : Math.max(1, proudSoulCount / 100);

                    for (int i = 0; i < enchantedCount; i++) {
                        ItemStack enchantedSoul = new ItemStack(tinySoulItem);
                        Holder<Enchantment> enchantment = enchantments.get(random.nextInt(enchantments.size()));
                        enchantedSoul.enchant(enchantment, 1);
                        soulOutputs.add(enchantedSoul);
                        proudSoulCount -= 100;
                    }
                } else {
                    ItemStack survivingBlade = makeBrokenSurvivor(blade, state);
                    return Optional.of(new BladeBreakResult(survivingBlade, List.of()));
                }
            }

            int maxNormalDrops = getSlashBladeConfigInt("MAX_PROUDSOUL_DROP", 10);
            int normalCount = proudSoulCount >= maxNormalDrops * 100
                    ? maxNormalDrops
                    : Math.max(1, proudSoulCount / 100);
            ItemStack normalSoul = new ItemStack(tinySoulItem, normalCount);
            soulOutputs.add(normalSoul);

            ItemStack survivingBlade = makeBrokenSurvivor(blade, state);
            return Optional.of(new BladeBreakResult(survivingBlade, List.copyOf(soulOutputs)));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private static ItemStack makeBrokenSurvivor(ItemStack blade, Object state)
            throws ReflectiveOperationException {
        boolean destructable = (boolean) isDestructableMethod.invoke(state);
        if (destructable) {
            return ItemStack.EMPTY;
        }

        int brokenDamage = Math.max(0, blade.getMaxDamage() - 1);
        setDamageMethod.invoke(state, brokenDamage);
        setBrokenMethod.invoke(state, true);
        return blade;
    }

    private static int getSlashBladeConfigInt(String fieldName, int fallback) {
        try {
            Class<?> configClass = Class.forName("mods.flammpfeil.slashblade.SlashBladeConfig");
            Object configValue = configClass.getField(fieldName).get(null);
            Object value = configValue.getClass().getMethod("get").invoke(configValue);
            return Math.max(1, ((Number) value).intValue());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return fallback;
        }
    }

    private static Set<String> getNonDroppableEnchantments() {
        Set<String> result = new HashSet<>();
        try {
            Class<?> configClass = Class.forName("mods.flammpfeil.slashblade.SlashBladeConfig");
            Object configValue = configClass.getField("NON_DROPPABLE_ENCHANTMENT").get(null);
            Object value = configValue.getClass().getMethod("get").invoke(configValue);
            if (value instanceof List<?> list) {
                for (Object entry : list) {
                    if (entry instanceof String id) {
                        result.add(id);
                    }
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // Empty set matches SlashBlade's default configuration.
        }
        return result;
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
            return isBrokenMethod != null
                    && getDamageMethod != null
                    && setDamageMethod != null
                    && getProudSoulCountMethod != null;
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
            setDamageMethod = bladeStateInterface.getMethod("setDamage", int.class);
            getProudSoulCountMethod = bladeStateInterface.getMethod("getProudSoulCount");
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            isBrokenMethod = null;
            isDestructableMethod = null;
            setBrokenMethod = null;
            getDamageMethod = null;
            setDamageMethod = null;
            getProudSoulCountMethod = null;
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

    public record BladeBreakResult(ItemStack survivingBlade, List<ItemStack> soulOutputs) {
    }
}
