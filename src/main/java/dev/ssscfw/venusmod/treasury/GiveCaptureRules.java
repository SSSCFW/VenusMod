package dev.ssscfw.venusmod.treasury;

/** /give前後の同一ItemStack総数から、そのコマンドで増えた分だけを求めるMinecraft非依存ロジック。 */
public final class GiveCaptureRules {
    private GiveCaptureRules() {}

    public static int newlyAdded(int beforeCount, int afterCount, int commandCount) {
        int delta = Math.max(0, afterCount - beforeCount);
        return Math.min(delta, Math.max(0, commandCount));
    }
}
