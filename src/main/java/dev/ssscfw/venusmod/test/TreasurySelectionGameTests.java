package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import dev.ssscfw.venusmod.compat.SlashBladeTreasuryCompat;
import dev.ssscfw.venusmod.treasury.KingsTreasurySavedData;
import dev.ssscfw.venusmod.treasury.TreasuryBladeProtection;
import dev.ssscfw.venusmod.treasury.SummonPattern;
import dev.ssscfw.venusmod.treasury.VolleyPriority;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 実SlashBlade導入時だけ保存/再収納/破損を検査する。未導入のスキップを実刀の成功とは扱わない。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TreasurySelectionGameTests {
    @GameTest(template = "test/empty")
    public static void favoriteProtectsPreparedBladeAndSurvivesReload(GameTestHelper h) {
        if (skip(h)) return;
        var data = new KingsTreasurySavedData();
        UUID owner = UUID.randomUUID();
        ItemStack blade = blade(h);
        data.insert(owner, blade, 1);
        var prepared = data.selectForVolley(owner, 24, b -> 0);
        h.assertTrue(prepared.size() == 1, "準備できる刀を作成");
        h.assertTrue(data.toggleFavorite(owner, 0, blade), "お気に入り設定");
        h.assertTrue(!data.consumeAll(owner, prepared), "準備後の保護で全体を中止");
        h.assertTrue(data.page(owner, 0, 54).getFirst().count() == 1, "中止時は消費なし");
        h.assertTrue(data.selectForVolley(owner, 24, b -> 0).isEmpty(), "保護刀を選ばない");
        data.setVolleyPriority(owner, VolleyPriority.RANK_HIGH);
        data.setConvergencePercent(owner, 72);
        data.setSummonPattern(owner, SummonPattern.VIEW_RING);
        var loaded = reload(data.save(new CompoundTag(), h.getLevel().registryAccess()), h);
        h.assertTrue(loaded.page(owner, 0, 54).getFirst().favorite(), "お気に入り永続化");
        h.assertTrue(loaded.volleyPriority(owner) == VolleyPriority.RANK_HIGH, "高ランク優先を永続化");
        h.assertTrue(loaded.convergencePercent(owner) == 72, "収束率を永続化");
        h.assertTrue(loaded.summonPattern(owner) == SummonPattern.VIEW_RING, "召喚パターンを永続化");
        h.assertTrue(loaded.toggleFavorite(owner, 0, loaded.page(owner, 0, 54).getFirst().template()), "最新の刀から解除");
        h.assertTrue(loaded.consumeAll(owner, loaded.selectForVolley(owner, 24, b -> 0)), "解除後に新しく準備して射出可能");
        h.succeed();
    }
    @GameTest(template = "test/empty")
    public static void brokenBladesCannotBeSelectedOrWithdrawnForVolley(GameTestHelper h) {
        if (skip(h)) return;
        var data = new KingsTreasurySavedData();
        UUID owner = UUID.randomUUID();
        ItemStack last = blade(h);
        last.setDamageValue(last.getMaxDamage() - 1);
        ItemStack broken = SlashBladeTreasuryCompat.damageOnePoint(last);
        h.assertTrue(!broken.isEmpty() && !SlashBladeTreasuryCompat.canLaunch(broken), "非消滅型は折れ状態で保持");
        data.insert(owner, broken, 1);
        h.assertTrue(data.selectForVolley(owner, 120, b -> 0).isEmpty(), "折れた刀を展開しない");
        h.assertTrue(!data.consumeAll(owner, List.of(broken)), "射出直前にも折れた刀を拒否");
        h.assertTrue(data.entryCount(owner) == 1, "折れた刀を宝物庫に残す");
        h.succeed();
    }
    @GameTest(template = "test/empty")
    public static void favoritesDoNotPayForAnIdenticalUnprotectedStack(GameTestHelper h) {
        if (skip(h)) return;
        var data = new KingsTreasurySavedData();
        UUID owner = UUID.randomUUID();
        ItemStack blade = blade(h);
        h.assertTrue(data.insert(owner, blade.copyWithCount(1029), 1029) == 1029, "1028+1本の2スタック");
        data.toggleFavorite(owner, 0, blade);
        var selected = data.selectForVolley(owner, 120, b -> 0);
        h.assertTrue(selected.size() == 1 && data.consumeAll(owner, selected), "未保護の1本だけ使用");
        var remaining = data.page(owner, 0, 54);
        h.assertTrue(remaining.size() == 1 && remaining.getFirst().favorite() && remaining.getFirst().count() == 1028,
                "保護された1028本を消費しない");
        h.succeed();
    }
    @GameTest(template = "test/empty")
    public static void prioritySearchesEveryPageAndUsesRemainingDurability(GameTestHelper h) {
        if (skip(h)) return;
        var data = new KingsTreasurySavedData();
        UUID owner = UUID.randomUUID();
        ItemStack template = blade(h);
        for (int i = 0; i < 120; i++) {
            ItemStack protectedBlade = template.copy();
            protectedBlade.set(DataComponents.CUSTOM_NAME, Component.literal("protected-" + i));
            data.insert(owner, protectedBlade, 1);
            data.toggleFavorite(owner, i, protectedBlade);
        }
        ItemStack low = template.copy(); low.setDamageValue(low.getMaxDamage() - 2);
        ItemStack high = template.copy(); high.setDamageValue(1);
        data.insert(owner, high, 1); data.insert(owner, low, 1);
        data.setVolleyPriority(owner, VolleyPriority.DURABILITY_LOW);
        h.assertTrue(ItemStack.isSameItemSameComponents(data.selectForVolley(owner, 1, b -> 0).getFirst(), low), "120番目以降の低耐久も検索");
        data.setVolleyPriority(owner, VolleyPriority.DURABILITY_HIGH);
        h.assertTrue(ItemStack.isSameItemSameComponents(data.selectForVolley(owner, 1, b -> 0).getFirst(), high), "高耐久優先");
        h.succeed();
    }
    @GameTest(template = "test/empty")
    public static void protectionTravelsWithTheBlade(GameTestHelper h) {
        if (skip(h)) return;
        var data = new KingsTreasurySavedData();
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        ItemStack original = blade(h);
        CompoundTag sentinel = new CompoundTag(); sentinel.putString("other_mod", "keep");
        original.set(DataComponents.CUSTOM_DATA, CustomData.of(sentinel));
        original.set(DataComponents.CUSTOM_NAME, Component.literal("keep-name"));
        data.insert(owner, original, 1);
        for (int state : new int[]{1, 2, 0}) {
            ItemStack shown = data.page(owner, 0, 54).getFirst().template();
            h.assertTrue(data.cycleProtection(owner, 0, shown), "3状態切替");
            ItemStack extracted = data.extractOne(owner, 0);
            h.assertTrue(TreasuryBladeProtection.get(extracted) == state, "取り出した刀へ保護を保存");
            h.assertTrue("keep".equals(extracted.get(DataComponents.CUSTOM_DATA).copyTag().getString("other_mod")), "別Modの値保持");
            h.assertTrue(extracted.getHoverName().getString().equals("keep-name"), "カスタム名保持");
            ItemStack serialized = ItemStack.parseOptional(h.getLevel().registryAccess(), (CompoundTag)extracted.save(h.getLevel().registryAccess()));
            data.insert(other, serialized, 1);
            h.assertTrue(TreasuryBladeProtection.get(data.page(other, 0, 54).getFirst().template()) == state, "別所有者へ受け渡しても保持");
            ItemStack returned = data.extractOne(other, 0);
            data.insert(owner, returned, 1);
            h.assertTrue(data.selectForVolley(owner, 1, b -> 0, false).isEmpty() == (state == 1), "通常モードの保護");
            h.assertTrue(data.selectForVolley(owner, 1, b -> 0, true).isEmpty() == (state != 0), "幻想モードの保護");
        }
        h.succeed();
    }
    @GameTest(template = "test/empty")
    public static void legacyFlagsMigrateToItem(GameTestHelper h) {
        if (skip(h)) return;
        var data = new KingsTreasurySavedData(); UUID owner = UUID.randomUUID();
        data.insert(owner, blade(h), 1);
        CompoundTag old = data.save(new CompoundTag(), h.getLevel().registryAccess());
        old.getList("Players", Tag.TAG_COMPOUND).getCompound(0).getList("Entries", Tag.TAG_COMPOUND).getCompound(0).putBoolean("Favorite", true);
        var loaded = reload(old, h);
        ItemStack extracted = loaded.extractOne(owner, 0);
        h.assertTrue(TreasuryBladeProtection.get(extracted) == 1, "旧Favoriteを刀へ移行");
        TreasuryBladeProtection.set(extracted, 2);
        TreasuryBladeProtection.migrate(extracted, true, false);
        h.assertTrue(TreasuryBladeProtection.get(extracted) == 2, "既存の刀設定を旧フラグで上書きしない");
        h.succeed();
    }
    @GameTest(template = "test/empty")
    public static void destructableBladesVanishOnLastDurability(GameTestHelper h) {
        if (skip(h)) return;
        ItemStack original = blade(h);
        try {
            Object optional = Class.forName("mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess")
                    .getMethod("of", ItemStack.class).invoke(null, original);
            Object state = ((java.util.Optional<?>)optional).orElseThrow();
            Class.forName("mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState")
                    .getMethod("setDestructable", boolean.class).invoke(state, true);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        h.assertTrue(SlashBladeTreasuryCompat.isDestructable(original), "ネイティブの消滅フラグ");
        original.setDamageValue(original.getMaxDamage() - 2);
        ItemStack last = SlashBladeTreasuryCompat.damageOnePoint(original);
        h.assertTrue(!last.isEmpty() && last.getDamageValue() == last.getMaxDamage() - 1, "寿命の前は返却");
        h.assertTrue(SlashBladeTreasuryCompat.damageOnePoint(last).isEmpty(), "最後の1耐久で消滅");
        h.assertTrue(!last.isEmpty(), "元ItemStackを消費処理で書き換えない");
        h.succeed();
    }
    private static boolean skip(GameTestHelper h) {
        if (ModList.get().isLoaded("slashblade")) return false;
        System.out.println("TreasurySelectionGameTests: SKIP actual SlashBlade checks (optional mod absent)");
        h.succeed(); return true;
    }
    private static ItemStack blade(GameTestHelper h) {
        ItemStack result = BuiltInRegistries.ITEM.stream().map(item -> item.getDefaultInstance())
                .filter(SlashBladeEnchantmentCompat::isBlade).filter(SlashBladeTreasuryCompat::canLaunch)
                .filter(stack -> !SlashBladeTreasuryCompat.isDestructable(stack)).findFirst().orElse(ItemStack.EMPTY);
        h.assertTrue(!result.isEmpty() && result.getMaxDamage() >= 4, "未破損・非消滅型の試験用SlashBladeが必要");
        return result.copy();
    }
    private static KingsTreasurySavedData reload(CompoundTag tag, GameTestHelper h) {
        try {
            var load = KingsTreasurySavedData.class.getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
            load.setAccessible(true);
            return (KingsTreasurySavedData)load.invoke(null, tag, h.getLevel().registryAccess());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
