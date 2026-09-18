import dev.ssscfw.venusmod.treasury.TreasuryVolleyRules;
import dev.ssscfw.venusmod.treasury.TreasuryVolleyRules.Candidate;
import dev.ssscfw.venusmod.treasury.VolleyPriority;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** 代表スタックを展開せず、実際に使う選択ロジックを検査する。 */
public final class TreasuryVolleyRulesChecks {
    private static int checks;
    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        var candidates = List.of(
                new Candidate(0, 2, 25, false, false),
                new Candidate(1, 4, 1, true, false),
                new Candidate(2, 6, 100, false, true),
                new Candidate(3, 1, 3, false, false),
                new Candidate(4, 2, 50, false, false));
        check(TreasuryVolleyRules.select(candidates, 120, VolleyPriority.DURABILITY_LOW, b -> 0)
                .equals(List.of(3, 0, 0, 4, 4)), "低い順は残り耐久の昇順。折れ/お気に入りは除外");
        check(TreasuryVolleyRules.select(candidates, 3, VolleyPriority.DURABILITY_HIGH, b -> 0)
                .equals(List.of(4, 4, 0)), "高い順は残り耐久の降順。上限で打ち切る");
        check(TreasuryVolleyRules.select(candidates, 120, VolleyPriority.RANDOM, b -> b - 1)
                .equals(List.of(4, 4, 3, 0, 0)), "ランダムは在庫本数を超えず選択する");
        check(TreasuryVolleyRules.select(List.of(new Candidate(0, 1, 1, true, false)),
                8, VolleyPriority.RANDOM, b -> 0).isEmpty(), "折れた刀のみなら0本");
        check(TreasuryVolleyRules.select(List.of(new Candidate(0, 1, 10, false, true)),
                8, VolleyPriority.RANDOM, b -> 0).isEmpty(), "お気に入りのみなら0本");
        check(TreasuryVolleyRules.select(List.of(new Candidate(0, 1, 1, false, false)),
                8, VolleyPriority.RANDOM, b -> 0).equals(List.of(0)), "残り1でも未破損なら射出可能");
        check(TreasuryVolleyRules.select(candidates, 0, VolleyPriority.RANDOM, b -> 0).isEmpty(), "0上限");
        check(candidates.get(0).count() == 2, "選択で元データを変えない");
        var many = new java.util.ArrayList<Candidate>();
        for (int i = 0; i < 10000; i++) many.add(new Candidate(i, 1028, i + 1, false, i < 9999));
        check(TreasuryVolleyRules.select(many, 120, VolleyPriority.DURABILITY_LOW, b -> 0)
                .stream().allMatch(i -> i == 9999), "最初の120スタックより後にある未保護刀も選べる");
        check(TreasuryVolleyRules.select(many, 999, VolleyPriority.RANDOM, b -> 0).size() == 440,
                "10000スタックの在庫でも絶対上限440本");
        var equal = List.of(new Candidate(5, 1, 10, false, false), new Candidate(7, 1, 10, false, false));
        check(TreasuryVolleyRules.select(equal, 24, VolleyPriority.DURABILITY_LOW, b -> 0)
                .equals(List.of(5, 7)), "同じ耐久ではスロット順で安定");
        for (int seed = 0; seed < 100; seed++) {
            var result = TreasuryVolleyRules.select(candidates, 120, VolleyPriority.RANDOM, new Random(seed)::nextInt);
            check(result.size() == 5 && result.stream().noneMatch(i -> i == 1 || i == 2), "ランダム除外/本数: " + seed);
            check(result.stream().filter(i -> i == 0).count() == 2, "ランダム非復元抽出: " + seed);
        }
        var weighted = List.of(new Candidate(0, 1, 1, false, false), new Candidate(1, 3, 1, false, false));
        int[] hits = new int[2];
        for (int draw = 0; draw < 4; draw++) {
            final int fixed = draw;
            hits[TreasuryVolleyRules.select(weighted, 1, VolleyPriority.RANDOM, b -> fixed).getFirst()]++;
        }
        check(Arrays.equals(hits, new int[]{1, 3}), "ランダムは種類でなく所蔵本数に比例");
        for (VolleyPriority mode : VolleyPriority.values()) {
            check(VolleyPriority.fromId(mode.id()) == mode, "保存IDの往復");
            check(VolleyPriority.fromOrdinal(mode.ordinal()) == mode, "GUI同期値の往復");
        }
        check(VolleyPriority.fromId("unknown") == VolleyPriority.RANDOM, "旧保存データの既定値はランダム");
        check(VolleyPriority.fromOrdinal(-1) == VolleyPriority.RANDOM, "負の同期値を拒否");
        check(VolleyPriority.fromOrdinal(5) == VolleyPriority.RANDOM, "範囲外同期値を拒否");
        var ranks = List.of(new Candidate(0, 1, 20, false, false, 3, 4),
                new Candidate(1, 2, 20, false, false, 0, 2),
                new Candidate(2, 1, 20, false, false, 0, 4),
                new Candidate(3, 1, 20, false, false, 1, 2),
                new Candidate(4, 1, 20, false, false, 2, 1),
                new Candidate(5, 1, 20, false, true, 0, 0),
                new Candidate(6, 1, 1, true, false, 0, 0));
        check(TreasuryVolleyRules.select(ranks, 120, VolleyPriority.RANK_LOW, b -> 0)
                .equals(List.of(1, 1, 2, 3, 4, 0)), "木偶等→通常→印→妖刀。保護/破損は除外");
        check(TreasuryVolleyRules.select(ranks, 2, VolleyPriority.RANK_LOW, b -> 0).equals(List.of(1, 1)), "弱い在庫から先に使う");
        check(TreasuryVolleyRules.select(ranks, 4, VolleyPriority.RANK_HIGH, b -> 0)
                .equals(List.of(0, 4, 3, 2)), "高ランク順は妖刀→印→通常→消滅型通常刀");
        var tied = List.of(new Candidate(0, 1, 20, false, false, 0, 2), new Candidate(1, 1, 3, false, false, 0, 2));
        check(TreasuryVolleyRules.select(tied, 2, VolleyPriority.RANK_LOW, b -> 0).equals(List.of(1, 0)), "同ランク同威力は低耐久優先");
        System.out.println("TreasuryVolleyRulesChecks: " + checks + " passed");
    }
}
