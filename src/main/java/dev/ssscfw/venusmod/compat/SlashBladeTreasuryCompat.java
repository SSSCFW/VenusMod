package dev.ssscfw.venusmod.compat;

import dev.ssscfw.venusmod.treasure.RoyalBladeDamageRules;
import dev.ssscfw.venusmod.treasure.TreasureRules;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * 王の財宝で使う抜刀剣状態の任意依存ブリッジ。
 * SlashBladeのクラスへ静的リンクせず、耐久・ベース攻撃力・印/妖刀判定だけを反射で読む。
 */
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

    private SlashBladeTreasuryCompat() {}

    public static boolean canLaunch(ItemStack stack) {
        return remainingDurability(stack) > 0;
    }

    /** 折れた刀・API未対応は-1で除外する。正常な刀は残り耐久ポイントを返す。 */
    public static int remainingDurability(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !SlashBladeEnchantmentCompat.isBlade(stack) || !resolve()) return -1;
        try {
            Object state = getBladeState(stack);
            if (state == null || (boolean) isBrokenMethod.invoke(state)) return -1;
            int damage = Math.max(0, ((Number) getDamageMethod.invoke(state)).intValue());
            return Math.max(0, stack.getMaxDamage() - damage);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // 状態を判断できない刀は保護する。破損判定失敗を「正常」とは扱わない。
            return -1;
        }
    }

    /**
     * 刀のgetBaseAttackModifierを初期値として、Minecraft 1.21.1標準の
     * EnchantmentHelper.modifyDamageで対象依存のダメージ系エンチャントを反映する。
     */
    public static float damageAgainst(ServerLevel level, ItemStack stack, Entity target,
                                      DamageSource source, boolean direct) {
        float weaponDamage = EnchantmentHelper.modifyDamage(
                level, stack, target, source, baseAttackModifier(stack));
        RoyalBladeDamageRules.BladeGrade grade = bladeGrade(stack);
        return direct
                ? RoyalBladeDamageRules.directDamage(weaponDamage, grade)
                : RoyalBladeDamageRules.blastDamage(weaponDamage, grade);
    }

    public static float baseAttackModifier(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !SlashBladeEnchantmentCompat.isBlade(stack)
                || !resolveDamageMethods()) return 0.0F;
        try {
            Object state = getBladeState(stack);
            return state == null ? 0.0F
                    : ((Number) getBaseAttackModifierMethod.invoke(state)).floatValue();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return 0.0F;
        }
    }

    /** SwordType.ENCHANTED=印、BEWITCHED=妖刀。妖刀はENCHANTEDも含むため+6だけを優先する。 */
    public static RoyalBladeDamageRules.BladeGrade bladeGrade(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !SlashBladeEnchantmentCompat.isBlade(stack)
                || !resolveDamageMethods()) return RoyalBladeDamageRules.BladeGrade.NORMAL;
        try {
            Object raw = swordTypeFromMethod.invoke(null, stack);
            boolean enchanted = false;
            boolean bewitched = false;
            if (raw instanceof Iterable<?> values) {
                for (Object value : values) {
                    String name = value instanceof Enum<?> enumValue
                            ? enumValue.name() : String.valueOf(value);
                    if ("BEWITCHED".equals(name)) bewitched = true;
                    else if ("ENCHANTED".equals(name)) enchanted = true;
                }
            }
            return RoyalBladeDamageRules.grade(enchanted, bewitched);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return RoyalBladeDamageRules.BladeGrade.NORMAL;
        }
    }

    public static ItemStack damageOnePoint(ItemStack original) {
        ItemStack result = original == null ? ItemStack.EMPTY : original.copyWithCount(1);
        if (result.isEmpty() || !SlashBladeEnchantmentCompat.isBlade(result) || !resolve()) return result;

        try {
            Object state = getBladeState(result);
            if (state == null) return result;

            int currentDamage = Math.max(0, ((Number) getDamageMethod.invoke(state)).intValue());
            boolean currentlyBroken = (boolean) isBrokenMethod.invoke(state);
            TreasureRules.DurabilityUse use = TreasureRules.useOneDurability(
                    currentDamage, result.getMaxDamage(), currentlyBroken);

            setDamageMethod.invoke(state, use.damage());
            if (use.broken() != currentlyBroken) {
                setBrokenMethod.invoke(state, use.broken());
            }
            return result;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // 返却そのものを失敗させない。API変更時は原本コピーをそのまま返す。
            return result;
        }
    }

    private static Object getBladeState(ItemStack stack) throws ReflectiveOperationException {
        Object optionalState = bladeStateOfMethod.invoke(null, stack);
        if (optionalState instanceof Optional<?> optional) return optional.orElse(null);
        return null;
    }

    private static boolean resolveDamageMethods() {
        if (damageLookupDone) {
            return bladeStateOfMethod != null
                    && getBaseAttackModifierMethod != null
                    && swordTypeFromMethod != null;
        }
        damageLookupDone = true;
        try {
            Class<?> access = Class.forName(
                    "mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess");
            Class<?> state = Class.forName(
                    "mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState");
            Class<?> swordType = Class.forName("mods.flammpfeil.slashblade.item.SwordType");
            bladeStateOfMethod = access.getMethod("of", ItemStack.class);
            getBaseAttackModifierMethod = state.getMethod("getBaseAttackModifier");
            swordTypeFromMethod = swordType.getMethod("from", ItemStack.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            getBaseAttackModifierMethod = null;
            swordTypeFromMethod = null;
            return false;
        }
    }

    private static boolean resolve() {
        if (lookupDone) {
            return bladeStateOfMethod != null && getDamageMethod != null && setDamageMethod != null
                    && isBrokenMethod != null && setBrokenMethod != null;
        }
        lookupDone = true;

        try {
            Class<?> access = Class.forName(
                    "mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess");
            Class<?> state = Class.forName(
                    "mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState");
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
