import dev.ssscfw.venusmod.treasure.TreasureRules;
import java.util.List;

/** Minecraftを起動せずに実行できる王の財宝の境界条件検査。 */
public final class TreasureRulesChecks {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(TreasureRules.select(new int[0]).isEmpty(), "空の宝物庫");
        check(TreasureRules.select(new int[]{0, -1}).isEmpty(), "無効な本数");
        check(TreasureRules.select(new int[]{1}).equals(List.of(0)), "1本のみ");
        check(TreasureRules.select(new int[]{2, 1}).equals(List.of(0, 1, 0)), "種類を先に並べる");
        check(TreasureRules.select(new int[]{1028}).size() == 24, "1028本でも24本上限");
        int[] full = new int[10000];
        java.util.Arrays.fill(full, 1028);
        check(TreasureRules.select(full).size() == 24, "一千万本でも有限");
        check(TreasureRules.select(full).getLast() == 23, "多種の刀を優先");
        int[] counts = {2, 1};
        TreasureRules.select(counts);
        check(java.util.Arrays.equals(counts, new int[]{2, 1}), "選択で原本を変更しない");

        int[] consumeCounts = {2, 1};
        int[] plan = TreasureRules.planConsumption(
                List.of("A", "B"), consumeCounts, List.of("A", "B", "A"), String::equals);
        check(java.util.Arrays.equals(plan, new int[]{2, 1}), "同一刀を必要本数だけ計画する");
        check(java.util.Arrays.equals(consumeCounts, new int[]{2, 1}), "消費計画時に原本カウントを変更しない");
        check(TreasureRules.planConsumption(
                List.of("A", "B"), new int[]{1, 1}, List.of("A", "A"), String::equals) == null,
                "1本でも不足なら全体を拒否する");
        check(java.util.Arrays.equals(TreasureRules.planConsumption(
                List.of("A", "A"), new int[]{1, 2}, List.of("A", "A", "A"), String::equals),
                new int[]{1, 2}), "同一論理刀が複数スタックに分かれていても消費できる");

        var p0 = TreasureRules.formationOffset(0);
        var p1 = TreasureRules.formationOffset(1);
        double adjacent = Math.hypot(p0.right() - p1.right(), p0.up() - p1.up());
        check(adjacent > 0.80D, "同じ段の刀間隔を広げる");
        check(TreasureRules.formationOffset(8).back() > p0.back(), "後段を少し後ろへずらす");

        check(TreasureRules.VolleyMode.forSummon(false) == TreasureRules.VolleyMode.PARALLEL,
                "通常召喚は平行射出");
        check(TreasureRules.VolleyMode.forSummon(true) == TreasureRules.VolleyMode.MEDIUM_CONVERGENCE,
                "Shift召喚は中程度収束");
        check(TreasureRules.VolleyMode.PARALLEL.convergence() == 0.0D,
                "平行射出は収束しない");
        check(TreasureRules.VolleyMode.MEDIUM_CONVERGENCE.convergence() >= 0.35D
                        && TreasureRules.VolleyMode.MEDIUM_CONVERGENCE.convergence() <= 0.65D,
                "収束率を中程度に保つ");
        check(TreasureRules.CONVERGENCE_DISTANCE >= 20.0D,
                "収束焦点を近づけすぎない");

        TreasureRules.Wave wave = new TreasureRules.Wave(100);
        check(!wave.expired(1299), "1分未満で収納しない");
        check(wave.expired(1300), "1200tickちょうどで期限切れ");
        check(!wave.fire(1300), "期限切れ射出を拒否");
        TreasureRules.Wave once = new TreasureRules.Wave(100);
        check(once.fire(101), "初回のみ射出可能");
        check(!once.fire(102), "二重発射拒否");
        TreasureRules.Wave cancelled = new TreasureRules.Wave(100);
        cancelled.cancel();
        check(!cancelled.fire(101), "収納済みの射出拒否");
        check(!TreasureRules.validAge(10, 9), "逆行した時刻を拒否");
        TreasureRules.PressLatch input = new TreasureRules.PressLatch();
        check(input.press(false), "最初の右クリックを送信");
        check(!input.press(false), "押しっぱなしで期限後に再展開しない");
        check(input.press(true), "右クリック保持中も左クリック可能");
        check(!input.press(true), "左クリックの重複送信を抑止");
        input.release(false, false);
        check(input.press(false), "離した後は再展開できる");
        System.out.println("TreasureRulesChecks: " + checks + " passed");
    }
}
