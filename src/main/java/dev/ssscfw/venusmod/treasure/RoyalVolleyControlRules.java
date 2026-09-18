package dev.ssscfw.venusmod.treasure;

/** 長押し再召喚と、召喚時/射出時で共有する収束方向をMinecraft非依存で計算する。 */
public final class RoyalVolleyControlRules {
    public static final int HOLD_REPEAT_TICKS = 6;

    private RoyalVolleyControlRules() {}

    public static boolean repeatDue(int heldTicks) {
        return heldTicks >= HOLD_REPEAT_TICKS && heldTicks % HOLD_REPEAT_TICKS == 0;
    }

    public record Direction(double x, double y, double z) {}

    public static Direction direction(double x, double y, double z,
                                      double parallelX, double parallelY, double parallelZ,
                                      double focusX, double focusY, double focusZ,
                                      double convergence) {
        Direction parallel = normalize(parallelX, parallelY, parallelZ, 0.0D, 0.0D, 1.0D);
        Direction towardFocus = normalize(
                focusX - x, focusY - y, focusZ - z,
                parallel.x(), parallel.y(), parallel.z());
        double factor = Math.max(0.0D, Math.min(1.0D, convergence));
        return normalize(
                parallel.x() * (1.0D - factor) + towardFocus.x() * factor,
                parallel.y() * (1.0D - factor) + towardFocus.y() * factor,
                parallel.z() * (1.0D - factor) + towardFocus.z() * factor,
                parallel.x(), parallel.y(), parallel.z());
    }

    private static Direction normalize(double x, double y, double z,
                                       double fallbackX, double fallbackY, double fallbackZ) {
        double lengthSq = x * x + y * y + z * z;
        if (lengthSq < 1.0E-12D || !Double.isFinite(lengthSq)) {
            double fallbackLengthSq = fallbackX * fallbackX + fallbackY * fallbackY + fallbackZ * fallbackZ;
            if (fallbackLengthSq < 1.0E-12D || !Double.isFinite(fallbackLengthSq)) {
                return new Direction(0.0D, 0.0D, 1.0D);
            }
            double fallbackLength = Math.sqrt(fallbackLengthSq);
            return new Direction(fallbackX / fallbackLength, fallbackY / fallbackLength, fallbackZ / fallbackLength);
        }
        double length = Math.sqrt(lengthSq);
        return new Direction(x / length, y / length, z / length);
    }
}
