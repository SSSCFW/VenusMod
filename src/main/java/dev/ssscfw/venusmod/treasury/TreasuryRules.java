package dev.ssscfw.venusmod.treasury;

/** Minecraft本体に依存しない王の宝物庫の容量計算。 */
public final class TreasuryRules {
    /** 宝物庫に作成できる論理スタック数。×1028のスタックでも1として数える。 */
    public static final int MAX_STACKS = 10_000;
    public static final int MAX_LOGICAL_STACK = 1_028;
    public static final int PAGE_SIZE = 54;

    private TreasuryRules() {}

    /**
     * 既存論理スタックへ今回追加できる本数を返す。
     * 全入力は防御的に0以上へ補正し、1論理スタック1028本を越えない。
     */
    public static int acceptedIntoStack(int currentCount, int requested) {
        int current = Math.max(0, Math.min(MAX_LOGICAL_STACK, currentCount));
        int want = Math.max(0, requested);
        return Math.min(want, MAX_LOGICAL_STACK - current);
    }

    /** 新しい論理スタックを1つ作成できるか。 */
    public static boolean canCreateStack(int currentStacks) {
        return Math.max(0, currentStacks) < MAX_STACKS;
    }
}
