import dev.ssscfw.venusmod.treasure.TreasureRules;

/** 上限の循環、収束率、1〜440本の半円/円形配置を検査する。 */
public final class TreasureRulesLimitChecks {
    private static int checks;
    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        int[] cycle = {8, 24, 48, 80, 120, 168, 224, 288, 360, 440};
        for (int i = 0; i < cycle.length; i++) {
            check(TreasureRules.nextVolleyLimit(cycle[i]) == cycle[(i + 1) % cycle.length], "上限循環");
            check(TreasureRules.select(new int[]{500}, cycle[i]).size() == cycle[i], "設定どおり選択");
        }
        check(TreasureRules.normalizeVolleyLimit(12) == 8, "旧12本から8本へ移行");
        check(TreasureRules.normalizeVolleyLimit(32) == 24, "旧32本から24本へ移行");
        check(TreasureRules.normalizeVolleyLimit(96) == 80, "旧96本から80本へ移行");
        check(TreasureRules.normalizeVolleyLimit(0) == 24, "既定24本を維持");
        check(TreasureRules.normalizeVolleyLimit(Integer.MAX_VALUE) == 24, "不正設定");
        check(TreasureRules.select(new int[]{500}, 999).size() == 440, "絶対上限440本");
        for (int total = 1; total <= 440; total++) {
            int start = 0;
            for (int row = 0; start < total; row++) {
                int size = Math.min(8 * (row + 1), total - start);
                for (int j = 0; j < size; j++) {
                    var left = TreasureRules.formationOffset(start + j, total);
                    var right = TreasureRules.formationOffset(start + size - 1 - j, total);
                    check(Math.abs(left.right() + right.right()) < 1e-8, "左右対称: " + total);
                    check(Math.abs(left.up() - right.up()) < 1e-8, "高さ対称: " + total);
                    check(Math.abs(left.back() - right.back()) < 1e-8, "前後対称: " + total);
                    check(Math.abs(Math.hypot(left.right(), left.up() - 0.35) - 2.8 * (row + 1)) < 1e-8,
                            "同一円弧: " + total);
                    if (j > 0) {
                        var prev = TreasureRules.formationOffset(start + j - 1, total);
                        check(Math.hypot(left.right() - prev.right(), left.up() - prev.up()) > 1.0,
                                "刀間隔1ブロック以上: " + total);
                    }
                }
                start += size;
            }
        }
        check(TreasureRules.normalizeConvergencePercent(-5) == 0, "収束率の下限");
        check(TreasureRules.normalizeConvergencePercent(150) == 100, "収束率の上限");
        check(Math.abs(TreasureRules.convergenceFactor(35) - 0.35D) < 1.0E-9D, "35%を係数化");
        for (int total : new int[]{1, 8, 16, 24, 120, 168, 440}) {
            for (int slot = 0; slot < total; slot++) {
                var circle = TreasureRules.circleOffset(slot, total);
                check(Double.isFinite(circle.right()) && Double.isFinite(circle.up()), "円形配置が有限値");
                check(circle.back() == 0.0D, "円形配置は自分中心の面上");
            }
        }
        check(TreasureRules.EXPLOSION_VOLUME == 1.2F, "爆発音の音量");
        System.out.println("TreasureRulesLimitChecks: " + checks + " passed");
    }
}
