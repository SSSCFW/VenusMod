import dev.ssscfw.venusmod.treasury.GiveCaptureRules;

public final class GiveCaptureRulesChecks {
    private static int checks;

    private static void eq(int expected, int actual, String message) {
        checks++;
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    public static void main(String[] args) {
        eq(0, GiveCaptureRules.newlyAdded(5, 5, 1), "existing inventory must not be absorbed");
        eq(1, GiveCaptureRules.newlyAdded(5, 6, 1), "one /give blade is captured");
        eq(2, GiveCaptureRules.newlyAdded(5, 8, 2), "capture is capped by command count");
        eq(0, GiveCaptureRules.newlyAdded(5, 4, 3), "inventory decrease is never captured");
        eq(0, GiveCaptureRules.newlyAdded(0, 10, 0), "zero command count captures nothing");
        System.out.println("GiveCaptureRulesChecks: " + checks + " passed");
    }
}
