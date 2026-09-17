package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import dev.ssscfw.venusmod.compat.SlashBladeTreasuryCompat;
import dev.ssscfw.venusmod.treasury.KingsTreasurySavedData;
import dev.ssscfw.venusmod.treasury.VolleyPriority;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 実SlashBladeが導入されたGameTest環境でSavedDataと反射ブリッジを合わせて確認する。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TreasurySelectionGameTests {
    @GameTest(template = "test/empty")
    public static void favoriteProtectsPreparedBladeAndSurvivesReload(GameTestHelper helper) {
        if (skipWithoutSlashBlade(helper)) return;
        var data = new KingsTreasurySavedData();
        UUID owner = UUID.randomUUID();
        ItemStack blade = blade(helper);
        data.insert(owner, blade, 1);
        List<ItemStack> prepared = data.selectForVolley(owner, 24, b -> 0);
        helper.assertTrue(prepared.size() == 1, "準備できる刀を作成");
        helper.assertTrue(data.toggleFavorite(owner, 0, blade), "右クリック相当のお気に入り設定");
        helper.assertTrue(!data.consumeAll(owner, prepared), "準備後に保護された刀は射出しない");
        helper.assertTrue(data.page(owner, 0, 54).getFirst().count() == 1, "射出中止で消費しない");
        helper.assertTrue(data.selectForVolley(owner, 24, b -> 0).isEmpty(), "お気に入りを選ばない");
        data.setVolleyPriority(owner, VolleyPriority.DURABILITY_HIGH);
        var loaded = reload(data, helper);
        helper.assertTrue(loaded.page(owner, 0, 54).getFirst().favorite(), "お気に入りを永続化");
        helper.assertTrue(loaded.volleyPriority(owner) == VolleyPriority.DURABILITY_HIGH, "優先度を永続化");
        helper.assertTrue(loaded.toggleFavorite(owner, 0, blade), "再右クリックで解除");
        helper.assertTrue(loaded.consumeAll(owner, prepared), "解除後は同じ刀を使用可能");
        helper.succeed();
    }

    @GameTest(template = "test/empty")
    public static void brokenBladesCannotBeSelectedOrWithdrawnForVolley(GameTestHelper helper) {
        if (skipWithoutSlashBlade(helper)) return;
        var data = new KingsTreasurySavedData();
        UUID owner = UUID.randomUUID();
        ItemStack last = blade(helper);
        last.setDamageValue(last.getMaxDamage() - 1);
        ItemStack broken = SlashBladeTreasuryCompat.damageOnePoint(last);
        helper.assertTrue(!broken.isEmpty() && !SlashBladeTreasuryCompat.canLaunch(broken), "折れた刀を保持して射出対象外にする");
        data.insert(owner, broken, 1);
        helper.assertTrue(data.selectForVolley(owner, 120, b -> 0).isEmpty(), "折れた刀を展開しない");
        helper.assertTrue(!data.consumeAll(owner, List.of(broken)), "折れた刀を射出直前にも拒否");
        helper.assertTrue(data.entryCount(owner) == 1, "折れた刀は宝物庫に残す");
        helper.succeed();
    }

    @GameTest(template = "test/empty")
    public static void favoritesDoNotPayForAnIdenticalUnprotectedStack(GameTestHelper helper) {
        if (skipWithoutSlashBlade(helper)) return;
        var data = new KingsTreasurySavedData();
        UUID owner = UUID.randomUUID();
        ItemStack blade = blade(helper);
        helper.assertTrue(data.insert(owner, blade.copyWithCount(1029), 1029) == 1029, "1028本と1本へ分割");
        data.toggleFavorite(owner, 0, blade);
        var selected = data.selectForVolley(owner, 120, b -> 0);
        helper.assertTrue(selected.size() == 1 && data.consumeAll(owner, selected), "未保護スタックの1本だけ使用");
        var remaining = data.page(owner, 0, 54);
        helper.assertTrue(remaining.size() == 1 && remaining.getFirst().favorite()
                && remaining.getFirst().count() == 1028, "同じComponentでも保護スタックを消費しない");
        helper.succeed();
    }

    @GameTest(template = "test/empty")
    public static void prioritySearchesEveryPageAndUsesRemainingDurability(GameTestHelper helper) {
        if (skipWithoutSlashBlade(helper)) return;
        var data = new KingsTreasurySavedData();
        UUID owner = UUID.randomUUID();
        ItemStack template = blade(helper);
        for (int i = 0; i < 120; i++) {
            ItemStack protectedBlade = template.copy();
            protectedBlade.set(DataComponents.CUSTOM_NAME, Component.literal("protected-" + i));
            data.insert(owner, protectedBlade, 1);
            data.toggleFavorite(owner, i, protectedBlade);
        }
        ItemStack low = template.copy();
        low.setDamageValue(low.getMaxDamage() - 2);
        ItemStack high = template.copy();
        high.setDamageValue(1);
        data.insert(owner, high, 1);
        data.insert(owner, low, 1);
        data.setVolleyPriority(owner, VolleyPriority.DURABILITY_LOW);
        helper.assertTrue(ItemStack.isSameItemSameComponents(data.selectForVolley(owner, 1, b -> 0).getFirst(), low),
                "120番目より後も探して残り耐久の少ない刀を優先");
        data.setVolleyPriority(owner, VolleyPriority.DURABILITY_HIGH);
        helper.assertTrue(ItemStack.isSameItemSameComponents(data.selectForVolley(owner, 1, b -> 0).getFirst(), high),
                "残り耐久の多い刀を優先");
        helper.succeed();
    }

    private static boolean skipWithoutSlashBlade(GameTestHelper helper) {
        if (ModList.get().isLoaded("slashblade")) return false;
        System.out.println("TreasurySelectionGameTests: skipped real-blade check (optional SlashBlade absent)");
        helper.succeed();
        return true;
    }

    private static ItemStack blade(GameTestHelper helper) {
        ItemStack result = BuiltInRegistries.ITEM.stream().map(item -> item.getDefaultInstance())
                .filter(SlashBladeEnchantmentCompat::isBlade)
                .filter(SlashBladeTreasuryCompat::canLaunch).findFirst().orElse(ItemStack.EMPTY);
        helper.assertTrue(!result.isEmpty() && result.getMaxDamage() >= 4, "試験用の正常なSlashBladeが必要");
        return result.copy();
    }

    private static KingsTreasurySavedData reload(KingsTreasurySavedData data, GameTestHelper helper) {
        CompoundTag tag = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        try {
            var load = KingsTreasurySavedData.class.getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
            load.setAccessible(true);
            return (KingsTreasurySavedData) load.invoke(null, tag, helper.getLevel().registryAccess());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("SavedDataの保存/再読み込みに失敗", exception);
        }
    }
}
