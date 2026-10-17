package dev.ssscfw.venusmod.test;

import com.mojang.authlib.GameProfile;
import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.yamato.YamatoTravelData;
import dev.ssscfw.venusmod.yamato.YamatoTravelRules;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 実NBTとServerPlayerで、所有者分離・座標採取・永続化・編集の原子性を検査する。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class YamatoTravelDataGameTests {
    @GameTest(template = "test/empty")
    public static void savesServerPositionAndDimensionForPlayerUuid(GameTestHelper h) {
        YamatoTravelData data = new YamatoTravelData();
        UUID owner = UUID.randomUUID();
        ServerPlayer player = player(h.getLevel(), owner);
        player.setPos(12.25D, 77.5D, -42.75D);
        player.setYRot(123.5F);
        player.setXRot(-28.0F);
        CompoundTag request = named("金星への帰還", "");
        UUID forged = UUID.randomUUID();
        request.putUUID("id", forged);
        request.putUUID("owner", forged);
        request.putString("dimension", "minecraft:the_end");
        request.putDouble("x", Double.NaN);
        request.putDouble("y", -9999.0D);
        request.putDouble("z", 9999.0D);
        request.putFloat("yaw", 0.0F);
        request.putFloat("pitch", 0.0F);
        h.assertTrue(data.apply(player, "save", request), "現在地保存を受理");
        CompoundTag entry = entries(data, owner).getCompound(0);
        UUID id = entry.getUUID("id");
        h.assertTrue(!id.equals(forged), "地点UUIDはサーバーで生成");
        h.assertTrue(entry.getString("dimension").equals(h.getLevel().dimension().location().toString()), "送信座標でなく実ディメンション");
        h.assertTrue(entry.getDouble("x") == 12.25D && entry.getDouble("y") == 77.5D
                && entry.getDouble("z") == -42.75D, "送信座標でなく実座標");
        h.assertTrue(entry.getFloat("yaw") == 123.5F && entry.getFloat("pitch") == -28.0F, "視線も保存");
        h.assertTrue(entry.getLong("created") > 0, "作成時刻を保存");
        h.assertTrue(data.destination(forged, id) == null, "他人の地点を取得しない");
        h.assertTrue(entries(data, forged).isEmpty(), "送信ownerで所有者を上書きしない");

        CompoundTag copy = data.destination(owner, id);
        copy.putDouble("x", 444.0D);
        data.snapshot(owner).getList("entries", Tag.TAG_COMPOUND).getCompound(0).putString("name", "偽装");
        data.settings(owner).putInt("access", 3);
        h.assertTrue(data.destination(owner, id).getDouble("x") == 12.25D, "取得値の変更が内部座標へ伝播しない");
        h.assertTrue(data.destination(owner, id).getString("name").equals("金星への帰還"), "一覧の防御的コピー");
        h.assertTrue(data.settings(owner).getInt("access") == 0 && data.settings(owner).getInt("seconds") == 1,
                "設定の防御的コピーと初期値");

        ServerLevel nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "別ディメンション試験用のネザー");
        ServerPlayer afterRespawn = player(nether, owner);
        afterRespawn.setPos(-8.5D, 65.0D, 7.25D);
        h.assertTrue(data.apply(afterRespawn, "save", named("ネザーの拠点", "")), "同じUUIDの別プレイヤー個体から保存");
        h.assertTrue(entries(data, owner).size() == 2, "死亡やディメンション変更による個体交換後も一覧を共有");
        h.assertTrue(entries(data, owner).getCompound(1).getString("dimension").equals("minecraft:the_nether"), "ネザーの識別子を保存");
        h.assertTrue(YamatoTravelData.get(h.getLevel().getServer()) == YamatoTravelData.get(nether.getServer()),
                "ディメンションに依存せずOverworldの同じSavedDataを取得");
        h.succeed();
    }

    @GameTest(template = "test/empty")
    public static void persistenceRetainsHierarchyManualOrderSettingsAndSort(GameTestHelper h) {
        YamatoTravelData data = new YamatoTravelData();
        UUID owner = UUID.randomUUID();
        ServerPlayer player = player(h.getLevel(), owner);
        UUID folder = add(h, data, player, "folder", "建築", "");
        UUID child = add(h, data, player, "folder", "工場", folder.toString());
        UUID inside = add(h, data, player, "save", "機械室", child.toString());
        UUID rootA = add(h, data, player, "save", "A", "");
        UUID rootB = add(h, data, player, "save", "B", "");
        CompoundTag order = identified(rootB);
        order.putInt("delta", -1);
        h.assertTrue(data.apply(player, "reorder", order), "ルートの隣接兄弟を交換");
        List<UUID> manual = List.of(folder, child, inside, rootB, rootA);
        h.assertTrue(ids(data, owner).equals(manual), "子孫を維持して兄弟だけ順序変更");
        CompoundTag sort = new CompoundTag();
        sort.putInt("sort", 4);
        h.assertTrue(data.apply(player, "sort", sort), "ディメンション表示順を設定");
        h.assertTrue(ids(data, owner).equals(manual), "表示ソートは手動順を破壊しない");
        h.assertTrue(data.apply(player, "settings", settings(2, 1, 120)), "全設定を一括更新");
        CompoundTag serialized = data.save(new CompoundTag(), h.getLevel().registryAccess());
        YamatoTravelData loaded = YamatoTravelData.load(serialized, h.getLevel().registryAccess());
        h.assertTrue(loaded.snapshot(owner).equals(data.snapshot(owner)), "NBT保存と再ロード後の完全一致");
        h.assertTrue(ids(loaded, owner).equals(manual), "手動順の永続化");
        h.assertTrue(loaded.destination(owner, inside).getString("parent").equals(child.toString()), "階層の永続化");
        h.assertTrue(loaded.snapshot(owner).getInt("sort") == 4, "表示ソートの永続化");
        h.assertTrue(loaded.settings(owner).equals(settings(2, 1, 120)), "公開範囲・寿命・秒数の永続化");
        h.assertTrue(loaded.destination(owner, folder) == null, "フォルダを転移先として返さない");
        serialized.getList("players", Tag.TAG_COMPOUND).getCompound(0).getList("entries", Tag.TAG_COMPOUND).clear();
        h.assertTrue(ids(loaded, owner).equals(manual) && ids(data, owner).equals(manual), "保存出力と読み込み元のNBTを共有しない");
        h.succeed();
    }

    @GameTest(template = "test/empty")
    public static void malformedAndCrossOwnerEditsAreAtomic(GameTestHelper h) {
        YamatoTravelData data = new YamatoTravelData();
        UUID owner = UUID.randomUUID();
        ServerPlayer player = player(h.getLevel(), owner);
        ServerPlayer outsider = player(h.getLevel(), UUID.randomUUID());
        UUID folder = add(h, data, player, "folder", "所有者のフォルダ", "");
        UUID location = add(h, data, player, "save", "所有者の地点", folder.toString());
        CompoundTag before = data.save(new CompoundTag(), h.getLevel().registryAccess());
        data.setDirty(false);

        for (String malformed : List.of("broken", "1-1-1-1-1", "", UUID.randomUUID().toString())) {
            CompoundTag request = named("改名", "");
            request.putString("id", malformed);
            h.assertTrue(!data.apply(player, "delete", request), "不正・未知UUIDで削除しない");
            h.assertTrue(!data.apply(player, "rename", request), "不正・未知UUIDで改名しない");
            h.assertTrue(!data.apply(player, "move", request), "不正・未知UUIDで移動しない");
        }
        CompoundTag wrongUuidType = new CompoundTag();
        wrongUuidType.putIntArray("id", new int[]{1, 2, 3});
        h.assertTrue(!data.apply(player, "delete", wrongUuidType), "長さが違うUUID配列を拒否");
        CompoundTag badParent = identified(location);
        badParent.putString("parent", "broken");
        h.assertTrue(!data.apply(player, "move", badParent), "壊れた親をルート移動へ読み替えない");
        badParent.putInt("parent", 0);
        h.assertTrue(!data.apply(player, "move", badParent), "型が違う親を拒否");
        CompoundTag blankName = identified(location);
        blankName.putString("name", "  ");
        h.assertTrue(!data.apply(player, "rename", blankName), "空の改名を拒否");
        h.assertTrue(!data.apply(player, "save", named("a".repeat(97), "")), "長すぎる新規地点名を拒否");
        h.assertTrue(!data.apply(player, "settings", settings(7, 1, 60)), "範囲外の公開設定");
        h.assertTrue(!data.apply(player, "settings", settings(2, 3, 60)), "範囲外の寿命設定");
        h.assertTrue(!data.apply(player, "settings", settings(2, 1, 181)), "範囲外の寿命秒数");
        CompoundTag partial = settings(2, 1, 60);
        partial.remove("seconds");
        h.assertTrue(!data.apply(player, "settings", partial), "設定の欠落を0へ読み替えない");
        partial.putDouble("seconds", 60.5D);
        h.assertTrue(!data.apply(player, "settings", partial), "浮動小数点を整数設定へ読み替えない");
        CompoundTag order = identified(location);
        order.putInt("delta", 2);
        h.assertTrue(!data.apply(player, "reorder", order), "不正な移動量を拒否");
        CompoundTag sort = new CompoundTag();
        sort.putInt("sort", 5);
        h.assertTrue(!data.apply(player, "sort", sort), "未知のソートを拒否");
        h.assertTrue(!data.apply(player, "unknown", new CompoundTag()), "未知の操作を拒否");
        h.assertTrue(!data.apply(player, "delete", null) && !data.apply(player, null, new CompoundTag())
                && !data.apply(null, "delete", identified(location)), "欠落した操作引数を拒否");
        h.assertTrue(!data.apply(outsider, "delete", identified(folder)), "別所有者の削除を拒否");
        h.assertTrue(!data.apply(outsider, "folder", named("侵入", folder.toString())), "他人のフォルダへ作成しない");
        h.assertTrue(data.save(new CompoundTag(), h.getLevel().registryAccess()).equals(before), "拒否した全操作で保存内容は完全不変");
        h.assertTrue(!data.isDirty(), "拒否した操作は保存状態をdirtyにしない");
        h.succeed();
    }

    @GameTest(template = "test/empty")
    public static void folderMovesKeepDescendantsAndDeleteRecursively(GameTestHelper h) {
        YamatoTravelData data = new YamatoTravelData();
        UUID owner = UUID.randomUUID();
        ServerPlayer player = player(h.getLevel(), owner);
        UUID source = add(h, data, player, "folder", "元", "");
        UUID child = add(h, data, player, "folder", "子", source.toString());
        UUID descendant = add(h, data, player, "save", "子孫の地点", child.toString());
        UUID target = add(h, data, player, "folder", "移動先", "");
        UUID targetLocation = add(h, data, player, "save", "移動先の地点", target.toString());
        UUID unrelated = add(h, data, player, "save", "無関係", "");
        CompoundTag before = data.snapshot(owner);
        h.assertTrue(!data.apply(player, "move", move(source, child.toString())), "子孫へ移動して循環させない");
        h.assertTrue(!data.apply(player, "move", move(source, source.toString())), "自身を親にしない");
        h.assertTrue(!data.apply(player, "move", move(source, unrelated.toString())), "地点をフォルダ扱いしない");
        h.assertTrue(data.snapshot(owner).equals(before), "不正な階層操作は不変");
        h.assertTrue(data.apply(player, "move", move(child, target.toString())), "子フォルダを別の親へ移動");
        h.assertTrue(data.destination(owner, descendant).getString("parent").equals(child.toString()), "移動しても子孫の所属を維持");
        List<UUID> afterMove = ids(data, owner);
        h.assertTrue(afterMove.get(afterMove.size() - 1).equals(child), "移動先の手動順末尾へ追加");
        CompoundTag rename = identified(descendant);
        rename.putString("name", "  改名後の地点  ");
        h.assertTrue(data.apply(player, "rename", rename), "地点の改名");
        h.assertTrue(data.destination(owner, descendant).getString("name").equals("改名後の地点"), "正規化した名前を保存");
        h.assertTrue(data.apply(player, "delete", identified(target)), "フォルダ削除");
        h.assertTrue(ids(data, owner).equals(List.of(source, unrelated)), "移動済みフォルダの全子孫だけを削除");
        h.assertTrue(data.destination(owner, targetLocation) == null && data.destination(owner, descendant) == null,
                "削除後に地点の索引が残らない");
        h.succeed();
    }

    @GameTest(template = "test/empty")
    public static void corruptedEntriesDoNotDestroyOtherDestinations(GameTestHelper h) {
        UUID owner = UUID.randomUUID();
        UUID valid = UUID.randomUUID();
        UUID orphan = UUID.randomUUID();
        UUID cycleA = UUID.randomUUID();
        UUID cycleB = UUID.randomUUID();
        ListTag entries = new ListTag();
        entries.add(folder(valid, "先頭", ""));
        CompoundTag malformed = folder(UUID.randomUUID(), "壊れたUUID", "");
        malformed.putIntArray("id", new int[]{1, 2, 3});
        entries.add(malformed);
        entries.add(folder(valid, "重複で上書きしない", ""));
        entries.add(folder(orphan, "孤立しても保持", UUID.randomUUID().toString()));
        entries.add(folder(cycleA, "循環A", cycleB.toString()));
        entries.add(folder(cycleB, "循環B", cycleA.toString()));
        CompoundTag tag = savedOwner(owner, entries);
        YamatoTravelData loaded = YamatoTravelData.load(tag, h.getLevel().registryAccess());
        h.assertTrue(ids(loaded, owner).equals(List.of(valid, orphan, cycleA, cycleB)), "壊れたUUIDだけを除外して後続項目を保持");
        h.assertTrue(entries(loaded, owner).getCompound(0).getString("name").equals("先頭"), "重複UUIDは先に保存された項目を維持");
        h.assertTrue(entries(loaded, owner).getCompound(1).getString("parent").isEmpty(), "孤立フォルダをルートで救出");
        String parentA = entries(loaded, owner).getCompound(2).getString("parent");
        String parentB = entries(loaded, owner).getCompound(3).getString("parent");
        h.assertTrue(parentA.isEmpty() || parentB.isEmpty(), "循環の辺を切って全項目へアクセス可能にする");
        h.assertTrue(loaded.settings(owner).equals(settings(0, 0, 1)), "欠落した旧設定は既定値");
        h.succeed();
    }

    @GameTest(template = "test/empty", timeoutTicks = 100)
    public static void capacityRejectsOnlyNewEntriesWithoutLosingExistingOnes(GameTestHelper h) {
        UUID owner = UUID.randomUUID();
        ListTag entries = new ListTag();
        for (int i = 0; i < YamatoTravelRules.MAX_ENTRIES; i++) entries.add(folder(UUID.randomUUID(), "地点" + i, ""));
        YamatoTravelData data = YamatoTravelData.load(savedOwner(owner, entries), h.getLevel().registryAccess());
        ServerPlayer player = player(h.getLevel(), owner);
        CompoundTag before = data.snapshot(owner);
        h.assertTrue(!data.apply(player, "save", named("上限超過", "")), "2048件の上限では新しい地点を拒否");
        h.assertTrue(data.snapshot(owner).equals(before), "上限到達時に既存データを消さない");
        UUID first = entries.getCompound(0).getUUID("id");
        CompoundTag rename = identified(first);
        rename.putString("name", "上限でも編集できる");
        h.assertTrue(data.apply(player, "rename", rename), "上限でも既存の編集を許可");
        h.assertTrue(data.apply(player, "delete", identified(first)), "削除で容量を戻す");
        h.assertTrue(data.apply(player, "save", named("空いた枠", "")), "空き枠へ現在地を保存");
        h.assertTrue(entries(data, owner).size() == YamatoTravelRules.MAX_ENTRIES, "最大数を維持");
        h.succeed();
    }

    private static ServerPlayer player(ServerLevel level, UUID owner) {
        return new ServerPlayer(level.getServer(), level, new GameProfile(owner, "yamato-test"), ClientInformation.createDefault());
    }

    private static UUID add(GameTestHelper h, YamatoTravelData data, ServerPlayer player,
                            String action, String name, String parent) {
        h.assertTrue(data.apply(player, action, named(name, parent)), action + "を作成");
        ListTag entries = entries(data, player.getUUID());
        return entries.getCompound(entries.size() - 1).getUUID("id");
    }

    private static ListTag entries(YamatoTravelData data, UUID owner) {
        return data.snapshot(owner).getList("entries", Tag.TAG_COMPOUND);
    }

    private static List<UUID> ids(YamatoTravelData data, UUID owner) {
        List<UUID> result = new ArrayList<>();
        for (Tag entry : entries(data, owner)) result.add(((CompoundTag)entry).getUUID("id"));
        return result;
    }

    private static CompoundTag named(String name, String parent) {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("parent", parent);
        return tag;
    }

    private static CompoundTag identified(UUID id) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        return tag;
    }

    private static CompoundTag move(UUID id, String parent) {
        CompoundTag tag = identified(id);
        tag.putString("parent", parent);
        return tag;
    }

    private static CompoundTag settings(int access, int lifetime, int seconds) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("access", access);
        tag.putInt("lifetime", lifetime);
        tag.putInt("seconds", seconds);
        tag.putDouble("size", 1.0D);
        return tag;
    }

    private static CompoundTag folder(UUID id, String name, String parent) {
        CompoundTag tag = named(name, parent);
        tag.putUUID("id", id);
        tag.putBoolean("folder", true);
        tag.putLong("created", 1L);
        return tag;
    }

    private static CompoundTag savedOwner(UUID owner, ListTag entries) {
        CompoundTag player = new CompoundTag();
        player.putUUID("owner", owner);
        player.put("entries", entries);
        ListTag players = new ListTag();
        players.add(player);
        CompoundTag tag = new CompoundTag();
        tag.put("players", players);
        return tag;
    }
}
