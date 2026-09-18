package dev.ssscfw.venusmod.treasury;

/** 複数回の召喚準備で同じ在庫刀を二重予約しないための純Java規則。 */
public final class PreparedVolleyRules {
    private PreparedVolleyRules() {}

    public static int remainingCount(int stored, int reserved) {
        return Math.max(0, Math.max(0, stored) - Math.max(0, reserved));
    }

    public static int totalPrepared(int[] preparedPerSet) {
        if (preparedPerSet == null) return 0;
        int total = 0;
        for (int count : preparedPerSet) total = Math.addExact(total, Math.max(0, count));
        return total;
    }
}
