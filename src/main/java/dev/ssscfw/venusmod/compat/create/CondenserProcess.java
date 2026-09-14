package dev.ssscfw.venusmod.compat.create;

/** 回転速度依存の処理量。停止・満杯中に未処理の生成量を蓄積しない。 */
public final class CondenserProcess {
    public static final int CAPACITY = 8000, BATCH = 100;
    public static final double WORK = 100.0;
    public record Step(double progress, int produced) {}
    private CondenserProcess() {}
    public static Step step(double progress, float rpm, boolean atmosphere, int freeSpace) {
        double safeProgress = Double.isFinite(progress) ? Math.clamp(progress, 0.0, WORK - 0.0001) : 0;
        if (!atmosphere || !Float.isFinite(rpm) || Math.abs(rpm) < 16 || freeSpace < BATCH)
            return new Step(safeProgress, 0);
        double next = safeProgress + Math.min(Math.abs((double) rpm), 256.0) / 16.0;
        return next >= WORK ? new Step(next - WORK, BATCH) : new Step(next, 0);
    }
}
