package dev.ssscfw.venusmod.compat;

import dev.ssscfw.venusmod.treasure.TreasureRules;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/**
 * 王の財宝から射出した抜刀剣の耐久だけを安全に更新する任意依存ブリッジ。
 * SlashBladeのクラスへ静的リンクせず、最後の1耐久では消失させず折れ状態へ移す。
 */
public final class SlashBladeTreasuryCompat {
    private static boolean lookupDone;
    private static Method bladeStateOfMethod;
    private static Method getDamageMethod;
    private static Method setDamageMethod;
    private static Method isBrokenMethod;
    private static Method setBrokenMethod;

    private SlashBladeTreasuryCompat() {}

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
