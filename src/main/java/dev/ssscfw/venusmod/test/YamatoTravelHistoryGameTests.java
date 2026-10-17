package dev.ssscfw.venusmod.test;

import com.mojang.authlib.GameProfile;
import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.yamato.YamatoTravelData;
import dev.ssscfw.venusmod.yamato.YamatoTravelRules;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 実NBTで、開通元の履歴・FIFO上限・所有者分離・倍率の旧保存互換を検査する。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class YamatoTravelHistoryGameTests {
    @GameTest(template = "test/empty")
    public static void openingStoresSourceAndReturnsDefensiveCopies(GameTestHelper h) {
        YamatoTravelData data = new YamatoTravelData();
        UUID owner = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        long earliest = System.currentTimeMillis();
        h.assertTrue(data.recordOpening(owner, "  出発した拠点  ", "minecraft:the_nether",
                12.25D, 77.5D, -42.75D, 123.5F, -90.0F), "サーバーの開通元を履歴に記録");
        long latest = System.currentTimeMillis();
        h.assertTrue(data.isDirty(), "成功した履歴追加でSavedDataを保存対象にする");
        ListTag history = history(data, owner);
        h.assertTrue(history.size() == 1 && entries(data, owner).isEmpty(), "履歴を手動保存の一覧へ混入しない");
        CompoundTag opening = history.getCompound(0);
        UUID id = opening.getUUID("id");
        h.assertTrue(opening.getString("name").equals("出発した拠点") && opening.getString("parent").isEmpty()
                && !opening.getBoolean("folder"), "履歴は名前付きのルート地点");
        h.assertTrue(opening.getString("dimension").equals("minecraft:the_nether")
                && opening.getDouble("x") == 12.25D && opening.getDouble("y") == 77.5D
                && opening.getDouble("z") == -42.75D, "渡された出発ディメンションと座標を保存");
        h.assertTrue(opening.getFloat("yaw") == 123.5F && opening.getFloat("pitch") == -90.0F, "出発端の立体方向を保存");
        h.assertTrue(opening.getLong("created") >= earliest && opening.getLong("created") <= latest, "実際の開通日時を保存");
        h.assertTrue(data.destination(owner, id).equals(opening), "既存の地点取得APIから履歴へ戻れる");
        h.assertTrue(data.destination(outsider, id) == null && history(data, outsider).isEmpty(), "他人の履歴へアクセスしない");

        opening.putString("name", "一覧からの改変");
        history.clear();
        data.destination(owner, id).putDouble("x", -9999.0D);
        h.assertTrue(data.destination(owner, id).getString("name").equals("出発した拠点")
                && data.destination(owner, id).getDouble("x") == 12.25D && history(data, owner).size() == 1,
                "履歴一覧と地点取得を防御的コピーで返す");
        ServerPlayer player = player(h, owner);
        CompoundTag deletion = new CompoundTag();
        deletion.putUUID("id", id);
        h.assertTrue(!data.apply(player, "delete", deletion), "履歴UUIDを手動一覧の編集へ流用しない");
        h.assertTrue(data.recordOpening(owner, "出発した拠点", "minecraft:the_nether",
                12.25D, 77.5D, -42.75D, 123.5F, -90.0F), "同じ場所の別の開通も記録");
        h.assertTrue(!history(data, owner).getCompound(0).getUUID("id").equals(id)
                && history(data, owner).getCompound(1).getUUID("id").equals(id), "同名同座標でも独立したUUIDを新しい順に保持");
        h.succeed();
    }

    @GameTest(template = "test/empty", timeoutTicks = 100)
    public static void thousandAndFirstOpeningEvictsOnlyOldestHistory(GameTestHelper h) {
        UUID owner = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        ListTag manual = new ListTag();
        for (int i = 0; i < YamatoTravelRules.MAX_ENTRIES; i++) {
            CompoundTag folder = new CompoundTag();
            folder.putUUID("id", UUID.randomUUID());
            folder.putString("name", "手動" + i);
            folder.putBoolean("folder", true);
            manual.add(folder);
        }
        YamatoTravelData data = YamatoTravelData.load(saved(owner, manual, new ListTag()), h.getLevel().registryAccess());
        CompoundTag manualBefore = data.snapshot(owner);
        UUID oldest = null;
        h.assertTrue(data.recordOpening(outsider, "別所有者", "minecraft:overworld", 1.0D, 64.0D, 2.0D, 0.0F, 0.0F), "別所有者の履歴を用意");
        CompoundTag outsiderBefore = data.snapshot(outsider);
        for (int i = 0; i <= YamatoTravelRules.DEFAULT_HISTORY_LIMIT; i++) {
            h.assertTrue(data.recordOpening(owner, "開通" + i, "minecraft:overworld",
                    i, 64.0D, -i, 45.0F, -20.0F), "手動保存が満杯でも履歴を追加 " + i);
            if (i == 0) oldest = history(data, owner).getCompound(0).getUUID("id");
        }
        ListTag retained = history(data, owner);
        h.assertTrue(retained.size() == 1000, "1001件目でも最新1000件を保持");
        for (int i = 0; i < retained.size(); i++) {
            h.assertTrue(retained.getCompound(i).getDouble("x") == 1000 - i, "古い順に除去し最新先頭を維持 " + i);
        }
        h.assertTrue(data.destination(owner, oldest) == null, "除去した最古の履歴を転移先として返さない");
        h.assertTrue(entries(data, owner).equals(manualBefore.getList("entries", Tag.TAG_COMPOUND)), "手動2048件を履歴上限で削除しない");
        h.assertTrue(data.snapshot(outsider).equals(outsiderBefore), "所有者別のFIFOを独立させる");
        h.succeed();
    }

    @GameTest(template = "test/empty")
    public static void historyPersistsAndLoadedNbtCannotMutateIt(GameTestHelper h) {
        UUID owner = UUID.randomUUID();
        YamatoTravelData data = new YamatoTravelData();
        for (int i = 0; i < 3; i++) h.assertTrue(data.recordOpening(owner, "履歴" + i,
                "venusmod:venus", i + 0.25D, 80.0D, -i, 180.0F, 90.0F), "保存前の履歴を作成");
        CompoundTag serialized = data.save(new CompoundTag(), h.getLevel().registryAccess());
        h.assertTrue(serialized.getList("players", Tag.TAG_COMPOUND).getCompound(0)
                .getList("history", Tag.TAG_COMPOUND).equals(history(data, owner)), "保存NBTも履歴を最新先頭で記録");
        YamatoTravelData loaded = YamatoTravelData.load(serialized, h.getLevel().registryAccess());
        h.assertTrue(loaded.snapshot(owner).equals(data.snapshot(owner)), "履歴のUUID・座標・方向・日時・順序が保存読込で一致");
        UUID newest = history(loaded, owner).getCompound(0).getUUID("id");
        serialized.getList("players", Tag.TAG_COMPOUND).getCompound(0)
                .getList("history", Tag.TAG_COMPOUND).getCompound(0).putDouble("x", 999.0D);
        h.assertTrue(loaded.destination(owner, newest).getDouble("x") == 2.25D
                && data.destination(owner, newest).getDouble("x") == 2.25D, "保存出力と読み込み元のNBTから内部データを分離");
        h.assertTrue(loaded.recordOpening(owner, null, "minecraft:overworld", 8.0D, 64.0D, 9.0D, 0.0F, 0.0F), "読込後も先頭へ追加");
        h.assertTrue(history(loaded, owner).getCompound(1).getUUID("id").equals(newest), "再読込後に履歴の順序を反転しない");
        h.succeed();
    }

    @GameTest(template = "test/empty", timeoutTicks = 100)
    public static void loadingTrimsOldHistoryAndProtectsManualIdOwnership(GameTestHelper h) {
        UUID owner = UUID.randomUUID();
        UUID manualId = UUID.randomUUID();
        ListTag manual = new ListTag();
        manual.add(location(manualId, "手動地点", -1.0D, 50L));
        ListTag history = new ListTag();
        history.add(location(manualId, "UUIDが重複する履歴", -99.0D, 100L));
        CompoundTag broken = location(UUID.randomUUID(), "不正座標", -10.0D, 1L);
        broken.putDouble("x", Double.NaN);
        history.add(broken);
        CompoundTag folder = location(UUID.randomUUID(), "履歴にフォルダを混入", -11.0D, 1L);
        folder.putBoolean("folder", true);
        history.add(folder);
        CompoundTag newest = location(UUID.randomUUID(), "界".repeat(96), 1000.0D, 0L);
        newest.putString("parent", manualId.toString());
        history.add(newest);
        history.add(newest.copy());
        UUID oldest = null;
        for (int i = 999; i >= 0; i--) {
            UUID id = UUID.randomUUID();
            history.add(location(id, "過去の出発地点", i, 1000L - i));
            if (i == 0) oldest = id;
        }
        YamatoTravelData loaded = YamatoTravelData.load(saved(owner, manual, history), h.getLevel().registryAccess());
        ListTag retained = history(loaded, owner);
        h.assertTrue(retained.size() == 1000 && retained.getCompound(0).getDouble("x") == 1000.0D
                && retained.getCompound(999).getDouble("x") == 1.0D, "壊れた履歴と重複を除き、保存順の最新1000件だけを読む");
        h.assertTrue(loaded.destination(owner, oldest) == null, "読込時にも最古の超過履歴を除去");
        h.assertTrue(loaded.destination(owner, manualId).getDouble("x") == -1.0D, "履歴の重複UUIDで手動地点を上書きしない");
        h.assertTrue(retained.getCompound(0).getString("parent").isEmpty()
                && retained.getCompound(0).getString("name").equals("界".repeat(32)), "読込時も履歴の親と名前を専用形式へ正規化");
        CompoundTag resaved = loaded.save(new CompoundTag(), h.getLevel().registryAccess());
        h.assertTrue(resaved.getList("players", Tag.TAG_COMPOUND).getCompound(0)
                .getList("history", Tag.TAG_COMPOUND).size() == 1000, "保存出力も履歴の上限を維持");

        CompoundTag laterManual = saved(owner, new ListTag(), history);
        CompoundTag secondOwnerRow = new CompoundTag();
        secondOwnerRow.putUUID("owner", owner);
        secondOwnerRow.put("entries", manual);
        laterManual.getList("players", Tag.TAG_COMPOUND).add(secondOwnerRow);
        YamatoTravelData merged = YamatoTravelData.load(laterManual, h.getLevel().registryAccess());
        h.assertTrue(merged.destination(owner, manualId).getDouble("x") == -1.0D,
                "重複した所有者行でも後から読んだ手動地点を履歴UUIDで隠さない");
        h.succeed();
    }

    @GameTest(template = "test/empty")
    public static void invalidOpeningInputsAreAtomic(GameTestHelper h) {
        UUID owner = UUID.randomUUID();
        YamatoTravelData data = new YamatoTravelData();
        h.assertTrue(data.recordOpening(owner, "既存の履歴", "minecraft:overworld", 1.0D, 64.0D, 2.0D, 0.0F, 0.0F), "既存の履歴を用意");
        CompoundTag before = data.save(new CompoundTag(), h.getLevel().registryAccess());
        data.setDirty(false);
        h.assertTrue(!data.recordOpening(null, "所有者なし", "minecraft:overworld", 1.0D, 64.0D, 2.0D, 0.0F, 0.0F), "所有者なしで保存しない");
        h.assertTrue(!data.recordOpening(owner, "ディメンションなし", null, 1.0D, 64.0D, 2.0D, 0.0F, 0.0F), "nullディメンションを例外にしない");
        h.assertTrue(!data.recordOpening(owner, "不正ディメンション", "Bad namespace:space here", 1.0D, 64.0D, 2.0D, 0.0F, 0.0F), "無効なディメンションを保存しない");
        for (double invalid : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            h.assertTrue(!data.recordOpening(owner, "不正X", "minecraft:overworld", invalid, 64.0D, 2.0D, 0.0F, 0.0F), "非有限Xを拒否");
            h.assertTrue(!data.recordOpening(owner, "不正Y", "minecraft:overworld", 1.0D, invalid, 2.0D, 0.0F, 0.0F), "非有限Yを拒否");
            h.assertTrue(!data.recordOpening(owner, "不正Z", "minecraft:overworld", 1.0D, 64.0D, invalid, 0.0F, 0.0F), "非有限Zを拒否");
            h.assertTrue(!data.recordOpening(owner, "不正Yaw", "minecraft:overworld", 1.0D, 64.0D, 2.0D, (float)invalid, 0.0F), "非有限yawを拒否");
            h.assertTrue(!data.recordOpening(owner, "不正Pitch", "minecraft:overworld", 1.0D, 64.0D, 2.0D, 0.0F, (float)invalid), "非有限pitchを拒否");
        }
        h.assertTrue(data.save(new CompoundTag(), h.getLevel().registryAccess()).equals(before) && !data.isDirty(), "全拒否後もデータと保存状態を変更しない");
        h.succeed();
    }

    @GameTest(template = "test/empty")
    public static void extendedSettingsPersistAndRejectMalformedSizesAtomically(GameTestHelper h) {
        UUID owner = UUID.randomUUID();
        YamatoTravelData data = new YamatoTravelData();
        ServerPlayer player = player(h, owner);
        h.assertTrue(data.settings(owner).contains("size", Tag.TAG_DOUBLE) && data.settings(owner).getDouble("size") == 1.0D, "未保存の倍率はdoubleの1倍");
        for (int access = 0; access <= 6; access++) {
            for (double size : new double[]{0.5D, 1.0D, 2.75D, 5.0D}) {
                CompoundTag request = settings(access, size);
                h.assertTrue(data.apply(player, "settings", request), "7種類の通過対象と倍率の境界を受理");
                YamatoTravelData loaded = YamatoTravelData.load(data.save(new CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess());
                h.assertTrue(loaded.settings(owner).equals(request), "追加通過対象とdoubleの倍率を永続化");
            }
        }
        CompoundTag before = data.save(new CompoundTag(), h.getLevel().registryAccess());
        data.setDirty(false);
        for (double invalid : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
                Math.nextDown(0.5D), Math.nextUp(5.0D), 0.0D, -1.0D}) {
            h.assertTrue(!data.apply(player, "settings", settings(0, invalid)), "非有限または範囲外の倍率を拒否");
        }
        CompoundTag wrongType = settings(0, 1.0D);
        wrongType.putInt("size", 1);
        h.assertTrue(!data.apply(player, "settings", wrongType), "整数をdoubleの倍率として受理しない");
        wrongType.putFloat("size", 1.0F);
        h.assertTrue(!data.apply(player, "settings", wrongType), "floatをdoubleの倍率として受理しない");
        wrongType.putString("size", "1.0");
        h.assertTrue(!data.apply(player, "settings", wrongType), "文字列を倍率として読み替えない");
        h.assertTrue(!data.apply(player, "settings", settings(-1, 1.0D))
                && !data.apply(player, "settings", settings(7, 1.0D)), "追加範囲の外の通過対象を拒否");
        h.assertTrue(data.save(new CompoundTag(), h.getLevel().registryAccess()).equals(before) && !data.isDirty(), "不正な倍率で他の設定を部分更新しない");
        h.succeed();
    }

    @GameTest(template = "test/empty")
    public static void legacyAndCorruptedSizesDefaultToOne(GameTestHelper h) {
        UUID owner = UUID.randomUUID();
        CompoundTag saved = saved(owner, new ListTag(), new ListTag());
        CompoundTag playerTag = saved.getList("players", Tag.TAG_COMPOUND).getCompound(0);
        CompoundTag legacySettings = settings(3, 1.0D);
        legacySettings.remove("size");
        playerTag.put("settings", legacySettings);
        playerTag.remove("history");
        YamatoTravelData legacy = YamatoTravelData.load(saved, h.getLevel().registryAccess());
        h.assertTrue(legacy.settings(owner).equals(settings(3, 1.0D)) && history(legacy, owner).isEmpty(), "旧保存の設定と空の履歴を維持して倍率1を追加");
        h.assertTrue(legacy.apply(player(h, owner), "settings", settings(6, 5.0D)), "旧保存の設定を新しい範囲へ変更");
        h.assertTrue(legacy.apply(player(h, owner), "settings", legacySettings)
                && legacy.settings(owner).equals(settings(3, 1.0D)), "旧形式の設定要求は倍率1として受理");

        for (double invalid : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0.49D, 5.01D}) {
            playerTag.put("settings", settings(6, invalid));
            h.assertTrue(YamatoTravelData.load(saved, h.getLevel().registryAccess()).settings(owner).equals(settings(6, 1.0D)),
                    "不正な保存倍率だけを既定値で救済");
        }
        for (Tag invalid : List.of(net.minecraft.nbt.IntTag.valueOf(2), net.minecraft.nbt.FloatTag.valueOf(2.0F), net.minecraft.nbt.StringTag.valueOf("2.0"))) {
            CompoundTag malformed = settings(6, 1.0D);
            malformed.put("size", invalid);
            playerTag.put("settings", malformed);
            h.assertTrue(YamatoTravelData.load(saved, h.getLevel().registryAccess()).settings(owner).equals(settings(6, 1.0D)),
                    "型が違う保存倍率も既定値で救済");
        }
        h.succeed();
    }

    private static ListTag history(YamatoTravelData data, UUID owner) {
        return data.snapshot(owner).getList("history", Tag.TAG_COMPOUND);
    }

    private static ListTag entries(YamatoTravelData data, UUID owner) {
        return data.snapshot(owner).getList("entries", Tag.TAG_COMPOUND);
    }

    private static ServerPlayer player(GameTestHelper h, UUID owner) {
        return new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(owner, "yamato-hist-test"), ClientInformation.createDefault());
    }

    private static CompoundTag settings(int access, double size) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("access", access);
        tag.putInt("lifetime", 1);
        tag.putInt("seconds", 120);
        tag.putDouble("size", size);
        return tag;
    }

    private static CompoundTag location(UUID id, String name, double x, long created) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putString("parent", "");
        tag.putBoolean("folder", false);
        tag.putString("dimension", "minecraft:overworld");
        tag.putDouble("x", x);
        tag.putDouble("y", 64.0D);
        tag.putDouble("z", 0.0D);
        tag.putFloat("yaw", 0.0F);
        tag.putFloat("pitch", 0.0F);
        tag.putLong("created", created);
        return tag;
    }

    private static CompoundTag saved(UUID owner, ListTag manual, ListTag history) {
        CompoundTag player = new CompoundTag();
        player.putUUID("owner", owner);
        player.put("entries", manual);
        player.put("history", history);
        ListTag players = new ListTag();
        players.add(player);
        CompoundTag saved = new CompoundTag();
        saved.put("players", players);
        return saved;
    }
}
