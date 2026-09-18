package dev.ssscfw.venusmod.treasure;

import dev.ssscfw.venusmod.compat.SlashBladeTreasuryCompat;
import dev.ssscfw.venusmod.treasury.TreasuryBladeRules;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/** モードの同期と、放出した刀自身のエンチャント効果。現在の手持ち刀は参照しない。 */
public final class RoyalBladeEffects {
    private static final String PHANTASM_KEY = "venusmod_broken_phantasm";
    private RoyalBladeEffects() {}

    public static boolean isPhantasm(ItemStack key) {
        return !key.isEmpty() && key.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getBoolean(PHANTASM_KEY);
    }

    /** CUSTOM_DATAは他の値を保持し、標準custom_model_dataのモデル切替を同期する。 */
    public static void setPhantasm(ItemStack key, boolean enabled) {
        CompoundTag data = key.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putBoolean(PHANTASM_KEY, enabled);
        key.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        if (enabled) key.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));
        else key.remove(DataComponents.CUSTOM_MODEL_DATA);
    }

    public static int level(ServerLevel level, ItemStack blade, ResourceKey<Enchantment> key) {
        var enchantment = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
        return RoyalBladeEffectsRules.enchantmentLevel(EnchantmentHelper.getItemEnchantmentLevel(enchantment, blade));
    }

    /** 直撃・爆風で実際にダメージが成立した相手だけを燃やす。ブロックには着火しない。 */
    public static void ignite(ServerLevel level, ItemStack firedBlade, LivingEntity target) {
        if (target.fireImmune()) return;
        int seconds = RoyalBladeEffectsRules.fireSeconds(level(level, firedBlade, Enchantments.FIRE_ASPECT));
        if (seconds > 0) target.igniteForSeconds((float) seconds);
    }

    public record ReturnResult(ItemStack stack, boolean broke) {}

    /** 返却1回に対して1回だけ耐久力を抽選し、実際に折れ状態へ移行/消滅したかも返す。 */
    public static ReturnResult returnBladeResult(ServerLevel level, ItemStack original) {
        if (original == null || original.isEmpty()) return new ReturnResult(ItemStack.EMPTY, false);
        if (original.has(DataComponents.UNBREAKABLE)) {
            return new ReturnResult(original.copyWithCount(1), false);
        }
        int unbreaking = level(level, original, Enchantments.UNBREAKING);
        int roll = unbreaking == 0 ? 0 : level.getRandom().nextInt(unbreaking + 1);
        if (!RoyalBladeEffectsRules.usesDurability(unbreaking, roll)) {
            return new ReturnResult(original.copyWithCount(1), false);
        }
        boolean wasBroken = SlashBladeTreasuryCompat.isBroken(original);
        boolean vanishingCurse = level(level, original, Enchantments.VANISHING_CURSE) > 0;
        ItemStack returned = SlashBladeTreasuryCompat.damageOnePoint(original);
        boolean broke = !wasBroken && (returned.isEmpty() || SlashBladeTreasuryCompat.isBroken(returned));
        if (TreasuryBladeRules.vanishesOnBreak(false, vanishingCurse, broke)) {
            return new ReturnResult(ItemStack.EMPTY, true);
        }
        return new ReturnResult(returned, broke);
    }

    public static ItemStack returnBlade(ServerLevel level, ItemStack original) {
        return returnBladeResult(level, original).stack();
    }
}
