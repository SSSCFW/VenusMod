package dev.ssscfw.venusmod.compat;

/**
 * Source-independent movement rules for SlashBlade Resharped 1.21.1's rapid_slash.
 * SlashBlade calls Entity.moveRelative(0.8f, forward) on the ground
 * (0.35f in water) at the first four ticks of this move.
 */
public final class RapidSlashSpeedRules {
    public static final int MAX_LEVEL = 3;
    public static final float BONUS_PER_LEVEL = 0.25F;
    // Follow full blocks, stairs and slabs without enabling long-distance falls.
    public static final double MAX_STEP_DOWN = 1.25D;

    private RapidSlashSpeedRules() {
    }

    /**
     * Match the native movement impulse at the instant SlashBlade generates it.
     * Ordinary player input, other combos and airborne motion do not have this signature.
     */
    public static boolean isRapidSlashImpulse(float amount, double x, double y, double z,
                                              boolean onGround, boolean inWater) {
        if (!onGround || x != 0.0D || y != 0.0D || z != 1.0D) {
            return false;
        }
        return Float.compare(amount, inWater ? 0.35F : 0.8F) == 0;
    }

    /**
     * Attempt ground-following only immediately after leaving actual solid ground
     * horizontally, during non-rising movement. This does not override the
     * collision test: an actual floor is required before the snap-down move.
     */
    public static boolean shouldFollowDownStep(boolean groundedBefore, boolean groundedAfter,
                                               double motionX, double motionY, double motionZ,
                                               double velocityY) {
        return groundedBefore && !groundedAfter
                && motionY <= 0.0D && velocityY <= 0.0D
                && (motionX * motionX + motionZ * motionZ) > 1.0E-6D;
    }

    /** Extra native forward impulse, not a permanent movement-speed attribute. */
    public static float extraImpulse(float nativeAmount, int level) {
        if (level <= 0) {
            return 0.0F;
        }
        return nativeAmount * (BONUS_PER_LEVEL * Math.min(level, MAX_LEVEL));
    }
}
