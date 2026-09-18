package dev.ssscfw.venusmod.treasury;

import dev.ssscfw.venusmod.treasure.RoyalBladeEffectsRules;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** 保護状態はスロットではなく刀のCUSTOM_DATAへ保存する。他のデータを上書きしない。 */
public final class TreasuryBladeProtection {
    private static final String KEY = "venusmod_treasury_protection";
    private TreasuryBladeProtection() {}

    public static int get(ItemStack blade) {
        return TreasuryBladeRules.protection(stored(blade), false, false);
    }

    private static int stored(ItemStack blade) {
        if (blade == null || blade.isEmpty()) return -1;
        CompoundTag tag = blade.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(KEY, Tag.TAG_INT) ? tag.getInt(KEY) : -1;
    }

    public static void set(ItemStack blade, int state) {
        if (blade == null || blade.isEmpty()) return;
        int safe = TreasuryBladeRules.protection(state, false, false);
        CompoundTag tag = blade.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        // 通常への明示解除も0として残し、古い保存データのフラグが復活しないようにする。
        tag.putInt(KEY, safe);
        blade.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void cycle(ItemStack blade) {
        set(blade, RoyalBladeEffectsRules.nextProtection(get(blade)));
    }

    /** 旧データだけを移行。設定のない通常刀のComponentを無用に増やさない。 */
    public static void migrate(ItemStack blade, boolean oldFavorite, boolean oldPhantasmProtected) {
        int stored = stored(blade);
        if (stored >= 0 && stored <= 2) return;
        if (oldFavorite || oldPhantasmProtected) {
            set(blade, TreasuryBladeRules.protection(stored, oldFavorite, oldPhantasmProtected));
        }
    }
}
