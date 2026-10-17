import dev.ssscfw.venusmod.compat.RapidSlashSpeedRules;

/** Pure-Java regression checks for grounded Rapid Slash impulses and level scaling. */
public final class RapidSlashSpeedRulesChecks {
    private RapidSlashSpeedRulesChecks() {
    }

    public static void main(String[] args) {
        for (int level = 1; level <= 3; level++) {
            checkClose(0.8F * (1.0F + 0.25F * level),
                    0.8F + RapidSlashSpeedRules.extraImpulse(0.8F, level),
                    "grounded level " + level);
            checkClose(0.35F * (1.0F + 0.25F * level),
                    0.35F + RapidSlashSpeedRules.extraImpulse(0.35F, level),
                    "water level " + level);
        }

        checkClose(0.0F, RapidSlashSpeedRules.extraImpulse(0.8F, 0), "no enchant");
        checkClose(0.0F, RapidSlashSpeedRules.extraImpulse(0.8F, -1), "invalid level");
        checkClose(0.6F, RapidSlashSpeedRules.extraImpulse(0.8F, 9), "level cap");

        check(true, RapidSlashSpeedRules.isRapidSlashImpulse(0.8F, 0, 0, 1, true, false),
                "grounded native dash");
        check(true, RapidSlashSpeedRules.isRapidSlashImpulse(0.35F, 0, 0, 1, true, true),
                "water native dash");
        check(false, RapidSlashSpeedRules.isRapidSlashImpulse(0.8F, 0, 0, 1, false, false),
                "airborne excluded");
        check(false, RapidSlashSpeedRules.isRapidSlashImpulse(0.8F, 0, 0, 1, true, true),
                "mismatched water impulse");
        check(false, RapidSlashSpeedRules.isRapidSlashImpulse(0.2F, 0, 0, 1, true, false),
                "ordinary movement excluded");
        check(false, RapidSlashSpeedRules.isRapidSlashImpulse(0.8F, 1, 0, 0, true, false),
                "strafe excluded");
        check(false, RapidSlashSpeedRules.isRapidSlashImpulse(0.8F, 0, 1, 0, true, false),
                "jump excluded");
        check(false, RapidSlashSpeedRules.isRapidSlashImpulse(0.8F, 0, 0, -1, true, false),
                "backwards excluded");
        check(false, RapidSlashSpeedRules.isRapidSlashImpulse(
                RapidSlashSpeedRules.extraImpulse(0.8F, 3), 0, 0, 1, true, false),
                "no recursive boost");

        // A one-block edge is eligible, provided the caller probes a real floor.
        check(true, RapidSlashSpeedRules.shouldFollowDownStep(
                true, false, 0.9D, -0.08D, 0.5D, -0.08D), "horizontal down-step");
        check(true, RapidSlashSpeedRules.shouldFollowDownStep(
                true, false, 0.1D, 0.0D, 0.0D, 0.0D), "zero vertical movement");
        check(false, RapidSlashSpeedRules.shouldFollowDownStep(
                false, false, 1.0D, -0.1D, 0.0D, -0.1D), "already falling");
        check(false, RapidSlashSpeedRules.shouldFollowDownStep(
                true, true, 1.0D, -0.1D, 0.0D, -0.1D), "already grounded");
        check(false, RapidSlashSpeedRules.shouldFollowDownStep(
                true, false, 1.0D, 0.25D, 0.0D, 0.25D), "jumping");
        check(false, RapidSlashSpeedRules.shouldFollowDownStep(
                true, false, 1.0D, 0.0D, 0.0D, 0.2D), "rising velocity");
        check(false, RapidSlashSpeedRules.shouldFollowDownStep(
                true, false, 0.0D, -0.08D, 0.0D, -0.08D), "not moving horizontally");
        checkClose(1.25F, (float) RapidSlashSpeedRules.MAX_STEP_DOWN, "one-block snap limit");
        System.out.println("PASS: Rapid Slash impulse, speed levels and grounded down-step rules");
    }

    private static void check(boolean expected, boolean actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void checkClose(float expected, float actual, String label) {
        if (Math.abs(expected - actual) > 0.00001F) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
