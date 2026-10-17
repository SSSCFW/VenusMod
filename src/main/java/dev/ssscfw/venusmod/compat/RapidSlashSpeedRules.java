package dev.ssscfw.venusmod.compat;

/**
 * Source-independent movement rules for SlashBlade Resharped 1.21.1's rapid_slash.
 * SlashBlade calls Entity.moveRelative(0.8f, forward) on the ground
 * (0.35f in water) at the first four ticks of this move.
 */
public final class RapidSlashSpeedRules {
    public static final int MAX_LEVEL = 3;
    public static final float BONUS_PER_LEVEL = 0.25F;

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

    /** Extra native forward impulse, not a permanent movement-speed attribute. */
    public static float extraImpulse(float nativeAmount, int level) {
        if (level <= 0) {
            return 0.0F;
        }
        return nativeAmount * (BONUS_PER_LEVEL * Math.min(level, MAX_LEVEL));
    }
}
