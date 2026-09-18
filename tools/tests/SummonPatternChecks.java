import dev.ssscfw.venusmod.treasury.SummonPattern;

/** 保存ID・GUI ordinalの互換と3種類の召喚パターンを検査する。 */
public final class SummonPatternChecks {
    private static int checks;
    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        check(SummonPattern.values().length == 3, "召喚パターンは3種類");
        for (SummonPattern pattern : SummonPattern.values()) {
            check(SummonPattern.fromId(pattern.id()) == pattern, "保存ID往復");
            check(SummonPattern.fromOrdinal(pattern.ordinal()) == pattern, "GUI ordinal往復");
        }
        check(SummonPattern.fromId("unknown") == SummonPattern.DEFAULT, "未知IDはデフォルト");
        check(SummonPattern.fromOrdinal(-1) == SummonPattern.DEFAULT, "負ordinalはデフォルト");
        check(SummonPattern.fromOrdinal(99) == SummonPattern.DEFAULT, "範囲外ordinalはデフォルト");
        System.out.println("SummonPatternChecks: " + checks + " passed");
    }
}
