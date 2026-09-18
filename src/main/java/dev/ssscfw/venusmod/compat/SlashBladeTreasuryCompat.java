package dev.ssscfw.venusmod.compat;

import dev.ssscfw.venusmod.treasure.RoyalBladeDamageRules;
import dev.ssscfw.venusmod.treasure.TreasureRules;
import dev.ssscfw.venusmod.treasury.TreasuryBladeRules;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** SlashBlade任意依存ブリッジ。読み取りはコピーに対して行い原本のComponentを変えない。 */
public final class SlashBladeTreasuryCompat {
    private static boolean lookupDone;
    private static Method bladeStateOfMethod;
    private static Method getDamageMethod;
    private static Method setDamageMethod;
    private static Method isBrokenMethod;
    private static Method setBrokenMethod;
    private static boolean damageLookupDone;
    private static Method getBaseAttackModifierMethod;
    private static Method swordTypeFromMethod;
    private static boolean destructableLookupDone;
    private static Method isDestructableMethod;

    private SlashBladeTreasuryCompat() {}

    public static boolean canLaunch(ItemStack stack) { return remainingDurability(stack) > 0; }

    public static boolean isBroken(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !SlashBladeEnchantmentCompat.isBlade(stack) || !resolve()) return false;
        try {
            Object state = getBladeState(stack.copyWithCount(1));
            return state != null && (boolean)isBrokenMethod.invoke(state);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    public static int remainingDurability(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !SlashBladeEnchantmentCompat.isBlade(stack) || !resolve()) return -1;
        try {
            Object state = getBladeState(stack.copyWithCount(1));
            if (state == null || (boolean)isBrokenMethod.invoke(state)) return -1;
            int damage = Math.max(0, ((Number)getDamageMethod.invoke(state)).intValue());
            return Math.max(0, stack.getMaxDamage() - damage);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) { return -1; }
    }

    public static float damageAgainst(ServerLevel level, ItemStack stack, Entity target, DamageSource source, boolean direct) {
        float weaponDamage = EnchantmentHelper.modifyDamage(level, stack, target, source, baseAttackModifier(stack));
        RoyalBladeDamageRules.BladeGrade grade = bladeGrade(stack);
        return direct ? RoyalBladeDamageRules.directDamage(weaponDamage, grade)
                : RoyalBladeDamageRules.blastDamage(weaponDamage, grade);
    }

    public static float baseAttackModifier(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !SlashBladeEnchantmentCompat.isBlade(stack) || !resolveDamageMethods()) return 0.0F;
        try {
            Object state = getBladeState(stack.copyWithCount(1));
            return state == null ? 0.0F : ((Number)getBaseAttackModifierMethod.invoke(state)).floatValue();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) { return 0.0F; }
    }

    public static RoyalBladeDamageRules.BladeGrade bladeGrade(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !SlashBladeEnchantmentCompat.isBlade(stack) || !resolveDamageMethods())
            return RoyalBladeDamageRules.BladeGrade.NORMAL;
        try {
            Object raw = swordTypeFromMethod.invoke(null, stack.copyWithCount(1));
            boolean enchanted = false;
            boolean bewitched = false;
            if (raw instanceof Iterable<?> values) {
                for (Object value : values) {
                    String name = value instanceof Enum<?> e ? e.name() : String.valueOf(value);
                    if ("BEWITCHED".equals(name)) bewitched = true;
                    else if ("ENCHANTED".equals(name)) enchanted = true;
                }
            }
            return RoyalBladeDamageRules.grade(enchanted, bewitched);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return RoyalBladeDamageRules.BladeGrade.NORMAL;
        }
    }

    /** 木偶等の消滅型を名前で推測せず、SlashBlade自身のフラグで判定する。 */
    public static boolean isDestructable(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !SlashBladeEnchantmentCompat.isBlade(stack) || !resolve()) return false;
        if (!destructableLookupDone) {
            destructableLookupDone = true;
            try {
                isDestructableMethod = Class.forName("mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState")
                        .getMethod("isDestructable");
            } catch (ReflectiveOperationException | LinkageError ignored) { isDestructableMethod = null; }
        }
        if (isDestructableMethod == null) return false;
        try {
            Object state = getBladeState(stack.copyWithCount(1));
            return state != null && (boolean)isDestructableMethod.invoke(state);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) { return false; }
    }

    public static int bladeRank(ItemStack stack) {
        var grade = bladeGrade(stack);
        return TreasuryBladeRules.rank(isDestructable(stack),
                grade == RoyalBladeDamageRules.BladeGrade.MARKED,
                grade == RoyalBladeDamageRules.BladeGrade.BEWITCHED);
    }

    public static ItemStack damageOnePoint(ItemStack original) {
        ItemStack result = original == null ? ItemStack.EMPTY : original.copyWithCount(1);
        if (result.isEmpty() || !SlashBladeEnchantmentCompat.isBlade(result) || !resolve()) return result;
        try {
            Object state = getBladeState(result);
            if (state == null) return result;
            int damage = Math.max(0, ((Number)getDamageMethod.invoke(state)).intValue());
            boolean currentlyBroken = (boolean)isBrokenMethod.invoke(state);
            TreasureRules.DurabilityUse use = TreasureRules.useOneDurability(damage, result.getMaxDamage(), currentlyBroken);
            // 耐久消費が成立して寿命に達した消滅型は、折れたItemStackを作らず消す。
            if (TreasuryBladeRules.vanishesOnBreak(isDestructable(result), use.broken())) return ItemStack.EMPTY;
            setDamageMethod.invoke(state, use.damage());
            if (use.broken() != currentlyBroken) setBrokenMethod.invoke(state, use.broken());
            return result;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // API変更時でも消失させず、元の状態で返却する。
            return original.copyWithCount(1);
        }
    }

    private static Object getBladeState(ItemStack stack) throws ReflectiveOperationException {
        Object state = bladeStateOfMethod.invoke(null, stack);
        return state instanceof Optional<?> optional ? optional.orElse(null) : null;
    }
    private static boolean resolveDamageMethods() {
        if (damageLookupDone) return bladeStateOfMethod != null && getBaseAttackModifierMethod != null && swordTypeFromMethod != null;
        damageLookupDone = true;
        try {
            Class<?> access = Class.forName("mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess");
            Class<?> state = Class.forName("mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState");
            bladeStateOfMethod = access.getMethod("of", ItemStack.class);
            getBaseAttackModifierMethod = state.getMethod("getBaseAttackModifier");
            swordTypeFromMethod = Class.forName("mods.flammpfeil.slashblade.item.SwordType").getMethod("from", ItemStack.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            getBaseAttackModifierMethod = null;
            swordTypeFromMethod = null;
            return false;
        }
    }
    private static boolean resolve() {
        if (lookupDone) return bladeStateOfMethod != null && getDamageMethod != null && setDamageMethod != null
                && isBrokenMethod != null && setBrokenMethod != null;
        lookupDone = true;
        try {
            Class<?> access = Class.forName("mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess");
            Class<?> state = Class.forName("mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState");
            bladeStateOfMethod = access.getMethod("of", ItemStack.class);
            getDamageMethod = state.getMethod("getDamage");
            setDamageMethod = state.getMethod("setDamage", int.class);
            isBrokenMethod = state.getMethod("isBroken");
            setBrokenMethod = state.getMethod("setBroken", boolean.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            bladeStateOfMethod = null;
            getDamageMethod = null;
            setDamageMethod = null;
            isBrokenMethod = null;
            setBrokenMethod = null;
            return false;
        }
    }
}
