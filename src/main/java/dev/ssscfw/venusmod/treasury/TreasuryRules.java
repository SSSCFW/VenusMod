package dev.ssscfw.venusmod.treasury;

/** Minecraft本体に依存しない王の宝物庫の容量計算。 */
public final class TreasuryRules {
    public static final int MAX_TOTAL = 10_000;
    public static final int MAX_LOGICAL_STACK = 1_028;
    public static final int PAGE_SIZE = 54;

    private TreasuryRules() {}

    /**
     * 既存論理スタックへ今回追加できる本数を返す。
     * 全入力は防御的に0以上へ補正し、論理1028本/全体10000本を越えない。
     */
    public static int acceptedIntoStack(int currentCount, int requested, int remainingTotalCapacity) {
        int current = Math.max(0, Math.min(MAX_LOGICAL_STACK, currentCount));
        int want = Math.max(0, requested);
        int remaining = Math.max(0, remainingTotalCapacity);
        return Math.min(want, Math.min(MAX_LOGICAL_STACK - current, remaining));
    }
}
