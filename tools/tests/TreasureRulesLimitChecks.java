import dev.ssscfw.venusmod.treasure.TreasureRules;

/** 王の財宝の可変射出上限をMinecraftなしで検査する。 */
public final class TreasureRulesLimitChecks {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int[] cycle = {8, 12, 24, 32, 48, 96, 120};
        check(TreasureRules.normalizeVolleyLimit(0) == 24, "旧データ/不正値は24本へ正規化");
        for (int i = 0; i < cycle.length; i++) {
            check(TreasureRules.nextVolleyLimit(cycle[i]) == cycle[(i + 1) % cycle.length],
                    "射出上限の循環: " + cycle[i]);
        }
        check(TreasureRules.select(new int[]{200}).size() == 24, "既定上限は24本");
        check(TreasureRules.select(new int[]{200}, 8).size() == 8, "8本設定");
        check(TreasureRules.select(new int[]{200}, 120).size() == 120, "120本設定");
        check(TreasureRules.select(new int[]{200}, 999).size() == 120, "絶対上限は120本");
        check(TreasureRules.formationOffset(119).back() > TreasureRules.formationOffset(23).back(),
                "24本超は外側/後方へ拡張配置");
        System.out.println("TreasureRulesLimitChecks: " + checks + " passed");
    }
}
