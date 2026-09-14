package dev.ssscfw.venusmod.upgrade;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** SlashBlade本体の内部データを変更せずCUSTOM_DATAへVenusMod強化値だけを保存する。 */
public final class BladeUpgradeData {
    private static final String ROOT = "VenusBladeUpgrades";
    private static final String OBSERVED_DAMAGE = "ObservedDamage";
    private static final String WEAR_CREDIT = "WearCredit";

    private BladeUpgradeData() {}

    public static int getLevel(ItemStack stack, BladeUpgradeType type) {
        CompoundTag upgrades = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(ROOT);
        return BladeUpgradeRules.clampLevel(type.category(), upgrades.getInt(type.getSerializedName()));
    }

    public static void setLevel(ItemStack stack, BladeUpgradeType type, int level) {
        int clamped = BladeUpgradeRules.clampLevel(type.category(), level);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            CompoundTag upgrades = root.getCompound(ROOT);
            upgrades.putInt(type.getSerializedName(), clamped);
            root.put(ROOT, upgrades);
        });
    }

    public static int getObservedDamage(ItemStack stack) {
        CompoundTag upgrades = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(ROOT);
        return upgrades.contains(OBSERVED_DAMAGE) ? upgrades.getInt(OBSERVED_DAMAGE) : -1;
    }

    public static int getWearCredit(ItemStack stack) {
        CompoundTag upgrades = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(ROOT);
        return Math.max(0, upgrades.getInt(WEAR_CREDIT));
    }

    public static void setWearState(ItemStack stack, int observedDamage, int credit) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            CompoundTag upgrades = root.getCompound(ROOT);
            upgrades.putInt(OBSERVED_DAMAGE, Math.max(0, observedDamage));
            upgrades.putInt(WEAR_CREDIT, Math.max(0, credit));
            root.put(ROOT, upgrades);
        });
    }
}
