import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 閻魔刀の保存名・設定・階層・手動順を、Minecraft起動なしで実装に対して検査する。 */
public final class YamatoTravelRulesChecks {
    private static int checks;

    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }

    public static void main(String[] args) throws Exception {
        Class<?> rules;
        try {
            rules = Class.forName("dev.ssscfw.venusmod.yamato.YamatoTravelRules");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("次元移動の保存名・階層・手動順ルールが未実装", e);
        }

        Method normalizeName = rules.getMethod("normalizeName", String.class);
        check("金星の拠点".equals(normalizeName.invoke(null, "  金星の拠点  ")), "日本語名と前後空白");
        check(normalizeName.invoke(null, "a".repeat(96)) != null, "名前上限96文字を許可");
        check(normalizeName.invoke(null, "a".repeat(97)) == null, "長すぎる名前を切り詰めず拒否");
        check(normalizeName.invoke(null, " \t ") == null, "空白だけの名前を拒否");
        check(normalizeName.invoke(null, "改\n行") == null, "名前内の改行を拒否");
        check(normalizeName.invoke(null, "制御\u0000文字") == null, "名前内の制御文字を拒否");
        check(normalizeName.invoke(null, "段落\u2029文字") == null, "段落区切りを拒否");
        check(normalizeName.invoke(null, "\uD800") == null, "不正なサロゲートを拒否");
        check("星🌟".equals(normalizeName.invoke(null, "星🌟")), "正しいサロゲート対を維持");
        check(normalizeName.invoke(null, new Object[]{null}) == null, "null名を拒否");

        Method parseId = rules.getMethod("parseId", String.class);
        UUID id = UUID.fromString("12345678-1234-4567-890a-123456789abc");
        check(id.equals(parseId.invoke(null, id.toString())), "正規UUID");
        check(id.equals(parseId.invoke(null, id.toString().toUpperCase(java.util.Locale.ROOT))), "UUIDの大文字表記");
        check(parseId.invoke(null, "1-1-1-1-1") == null, "UUID.fromStringの短縮入力を拒否");
        check(parseId.invoke(null, "broken") == null, "壊れたUUIDを拒否");
        check(parseId.invoke(null, "") == null, "ルート表現はUUIDとして扱わない");
        check(parseId.invoke(null, new Object[]{null}) == null, "null UUIDを拒否");

        Method validSettings = rules.getMethod("validSettings", int.class, int.class, int.class);
        check((boolean)validSettings.invoke(null, 0, 0, 1), "所有者のみ・初回通過後・1秒");
        check((boolean)validSettings.invoke(null, 3, 2, 180), "全エンティティ・無制限・180秒");
        check(!(boolean)validSettings.invoke(null, -1, 0, 1), "負の公開範囲を拒否");
        check(!(boolean)validSettings.invoke(null, 4, 0, 1), "未知の公開範囲を拒否");
        check(!(boolean)validSettings.invoke(null, 0, -1, 1), "負の寿命モードを拒否");
        check(!(boolean)validSettings.invoke(null, 0, 3, 1), "未知の寿命モードを拒否");
        check(!(boolean)validSettings.invoke(null, 0, 1, 0), "0秒を拒否");
        check(!(boolean)validSettings.invoke(null, 0, 1, 181), "180秒超を拒否");
        Method validSort = rules.getMethod("validSort", int.class);
        for (int sort = 0; sort <= 4; sort++) check((boolean)validSort.invoke(null, sort), "表示ソート" + sort);
        check(!(boolean)validSort.invoke(null, -1) && !(boolean)validSort.invoke(null, 5), "未知のソートを拒否");

        UUID root = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        UUID leaf = UUID.randomUUID();
        UUID rootLocation = UUID.randomUUID();
        UUID sibling = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        Map<UUID, UUID> parents = new HashMap<>();
        parents.put(root, null);
        parents.put(child, root);
        parents.put(leaf, child);
        parents.put(rootLocation, null);
        parents.put(sibling, null);
        Set<UUID> folders = Set.of(root, child, sibling);
        Method canUseParent = rules.getMethod("canUseParent", UUID.class, UUID.class, Map.class, Set.class);
        check((boolean)canUseParent.invoke(null, null, child, parents, folders), "新規地点を子フォルダへ保存");
        check((boolean)canUseParent.invoke(null, child, sibling, parents, folders), "フォルダを別フォルダへ移動");
        check((boolean)canUseParent.invoke(null, child, null, parents, folders), "ルートへ移動");
        check(!(boolean)canUseParent.invoke(null, root, child, parents, folders), "子孫への移動を拒否");
        check(!(boolean)canUseParent.invoke(null, root, root, parents, folders), "自分自身への移動を拒否");
        check(!(boolean)canUseParent.invoke(null, leaf, rootLocation, parents, folders), "地点を親として使わない");
        check(!(boolean)canUseParent.invoke(null, leaf, outsider, parents, folders), "別所有者・未知の親を拒否");
        check(!(boolean)canUseParent.invoke(null, outsider, null, parents, folders), "別所有者・未知の移動元を拒否");

        Method descendants = rules.getMethod("descendants", UUID.class, Map.class);
        check(Set.of(root, child, leaf).equals(descendants.invoke(null, root, parents)), "階層を再帰削除する集合");
        check(Set.of(leaf).equals(descendants.invoke(null, leaf, parents)), "地点削除はその地点だけ");
        check(Set.of().equals(descendants.invoke(null, outsider, parents)), "未知の削除対象は空集合");
        Map<UUID, UUID> cycle = new HashMap<>(parents);
        cycle.put(root, child);
        check(!(boolean)canUseParent.invoke(null, sibling, root, cycle, folders), "破損した親循環で無限ループしない");
        check(Set.of(root, child, leaf).equals(descendants.invoke(null, root, cycle)), "破損した循環でも削除集合が終了する");

        Method siblingTarget = rules.getMethod("siblingTargetIndex", List.class, Map.class, UUID.class, int.class);
        List<UUID> order = new ArrayList<>(List.of(root, child, leaf, rootLocation, sibling));
        check((int)siblingTarget.invoke(null, order, parents, root, 1) == 3, "途中の子孫を飛ばして隣の兄弟へ");
        check((int)siblingTarget.invoke(null, order, parents, sibling, -1) == 3, "直前の兄弟へ");
        check((int)siblingTarget.invoke(null, order, parents, root, -1) == -1, "先頭の上移動を拒否");
        check((int)siblingTarget.invoke(null, order, parents, sibling, 1) == -1, "末尾の下移動を拒否");
        check((int)siblingTarget.invoke(null, order, parents, child, 1) == -1, "別フォルダの地点と入れ替えない");
        check((int)siblingTarget.invoke(null, order, parents, outsider, 1) == -1, "未知の手動順操作を拒否");
        check((int)siblingTarget.invoke(null, order, parents, root, 0) == -1, "0の移動量を拒否");
        check((int)siblingTarget.invoke(null, order, parents, root, 2) == -1, "複数段の偽装移動量を拒否");
        check(order.equals(List.of(root, child, leaf, rootLocation, sibling)), "検査は元の手動順を変更しない");

        System.out.println("YamatoTravelRulesChecks: " + checks + " passed");
    }
}
