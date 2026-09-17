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
        check(java.util.Arrays.equals(counts, new int[]{2, 1}), "原本を消費しない");
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
