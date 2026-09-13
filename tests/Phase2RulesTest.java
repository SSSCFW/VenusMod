import dev.ssscfw.venusmod.environment.ExposureRules;
import dev.ssscfw.venusmod.compat.create.CondenserProcess;

public final class Phase2RulesTest {
    private static int checks;
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        require(ExposureRules.gain(0, false, false, 100, 100, 100) == 300, "unprotected rate");
        require(ExposureRules.gain(4, false, false, 100, 100, 100) == 0, "full suit");
        require(ExposureRules.gain(0, true, false, 100, 100, 100) == 0, "portable protection");
        require(ExposureRules.gain(0, false, true, 100, 100, 100) == 200, "fire resistance only removes heat");
        for (int pieces = 0; pieces <= 4; pieces++) {
            int gain = ExposureRules.gain(pieces, false, false, 100, 100, 100);
            require(gain == (4 - pieces) * 75, "partial suit scaling");
            for (int exposure = -500; exposure <= 10500; exposure += 5) {
                for (int grace = 0; grace < 3; grace++) {
                    var next = ExposureRules.step(exposure, grace, gain, 400);
                    require(next.exposure() >= 0 && next.exposure() <= 10000, "exposure range");
                    require(grace == 0 || !next.harmful(), "grace immunity");
                    require(gain > 0 || !next.harmful(), "no damage while protected");
                }
            }
        }
        int value = 0, grace = 60;
        for (int second = 0; second < 60; second++) {
            var step = ExposureRules.step(value, grace, 300, 400); value = step.exposure(); grace = step.graceSeconds();
            require(value == 0, "arrival grace not applied");
        }
        for (int second = 0; second < 34; second++) value = ExposureRules.step(value, 0, 300, 400).exposure();
        require(value == 10000, "default exposure reaches max after 34 unprotected seconds");
        for (int second = 0; second < 25; second++) value = ExposureRules.step(value, 0, 0, 400).exposure();
        require(value == 0, "full recovery after 25 seconds");
        for (float rpm : new float[]{16, -16, 32, -32, 64, 128, 256, 1024}) {
            double progress = 0; int produced = 0;
            for (int tick = 0; tick < 10000; tick++) {
                var next = CondenserProcess.step(progress, rpm, true, 8000);
                require(next.progress() >= 0 && next.progress() < 100, "progress bounds");
                require(next.produced() == 0 || next.produced() == 100, "whole batches");
                progress = next.progress(); produced += next.produced();
            }
            require(produced == Math.round(10000 * Math.min(Math.abs(rpm), 256) / 16), "RPM conservation");
        }
        for (float rpm : new float[]{0, 15, -15, Float.NaN, Float.POSITIVE_INFINITY})
            require(CondenserProcess.step(50, rpm, true, 8000).equals(new CondenserProcess.Step(50, 0)), "invalid/stopped power");
        require(CondenserProcess.step(99, 256, false, 8000).produced() == 0, "no atmosphere");
        require(CondenserProcess.step(99, 256, true, 99).equals(new CondenserProcess.Step(99, 0)), "no partial or overflow batch");
        require(CondenserProcess.step(Double.NaN, 0, false, 0).progress() == 0, "corrupt progress");
        require(CondenserProcess.step(-100, 0, false, 0).progress() == 0, "negative progress");
        System.out.println("Phase2RulesTest: " + checks + " checks passed");
    }
}
