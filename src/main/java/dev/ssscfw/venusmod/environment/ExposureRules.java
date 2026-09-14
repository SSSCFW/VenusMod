package dev.ssscfw.venusmod.environment;

/** 1ポイント=100内部単位。サーバー/描画に依存しない曝露計算。 */
public final class ExposureRules {
    public static final int MAX = 10000;
    public record Step(int exposure, int graceSeconds, boolean harmful) {}
    private ExposureRules() {}
    public static int gain(int pieces, boolean lifeSupport, boolean heatResistant, int heat, int pressure, int corrosion) {
        if (lifeSupport) return 0;
        int missing = 4 - Math.clamp(pieces, 0, 4);
        return missing * (Math.max(0, pressure) + Math.max(0, corrosion) + (heatResistant ? 0 : Math.max(0, heat))) / 4;
    }
    public static Step step(int exposure, int grace, int gain, int recovery) {
        int current = Math.clamp(exposure, 0, MAX);
        if (grace > 0) return new Step(Math.max(0, current - Math.max(0, recovery)), grace - 1, false);
        int next = gain > 0 ? (int) Math.min(MAX, (long) current + gain) : Math.max(0, current - Math.max(0, recovery));
        return new Step(next, 0, gain > 0 && next == MAX);
    }
}
