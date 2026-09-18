import dev.ssscfw.venusmod.treasury.PreparedVolleyRules;

public final class PreparedVolleyRulesChecks {
    private static int checks;
    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        check(PreparedVolleyRules.remainingCount(10, 0) == 10, "予約なし");
        check(PreparedVolleyRules.remainingCount(10, 4) == 6, "予約済みを除外");
        check(PreparedVolleyRules.remainingCount(3, 9) == 0, "予約過多でも負にしない");
        check(PreparedVolleyRules.remainingCount(-1, 0) == 0, "不正所蔵数を0へ");
        check(PreparedVolleyRules.totalPrepared(new int[]{3, 5, 0}) == 8, "展開中本数");
        System.out.println("PreparedVolleyRulesChecks: " + checks + " passed");
    }
}
