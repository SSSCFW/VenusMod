package dev.ssscfw.venusmod.yamato;

import java.util.ArrayList;
import java.util.List;

/** 描画と接触に共用する、内側に湾曲した四頂点のアステロイド曲線。 */
public final class YamatoRiftGeometry {
    public static final double HALF_WIDTH = 1.25;
    public static final double HALF_HEIGHT = 1.6;
    public static final double CENTER_HEIGHT = 1.6;
    public static final double HALF_DEPTH = 0.18;
    public static final int FADE_TICKS = 6;
    private YamatoRiftGeometry() {}

    public static double pointX(double radians) {
        double cos = Math.cos(radians);
        return HALF_WIDTH * cos * cos * cos;
    }

    public static double pointY(double radians) {
        double sin = Math.sin(radians);
        return HALF_HEIGHT * sin * sin * sin;
    }

    public static boolean inside(double horizontal, double vertical) {
        return Double.isFinite(horizontal) && Double.isFinite(vertical)
                && Math.pow(Math.abs(horizontal) / HALF_WIDTH, 2.0 / 3.0)
                + Math.pow(Math.abs(vertical) / HALF_HEIGHT, 2.0 / 3.0) <= 1.0 + 1.0E-9;
    }

    /** ポータル座標系で軸に平行な箱と曲線面の接触。曲線の空白の隅は除外する。 */
    public static boolean intersects(double minU, double maxU, double minV, double maxV,
                                     double minDepth, double maxDepth) {
        if (minDepth > HALF_DEPTH || maxDepth < -HALF_DEPTH) return false;
        double u = minU > 0 ? minU : maxU < 0 ? maxU : 0;
        double v = minV > 0 ? minV : maxV < 0 ? maxV : 0;
        return inside(u, v);
    }

    /**
     * ポータル中心を原点とするワールドAABBを検査する。引数順はminXYZ、maxXYZ、yaw度、描画倍率。
     * XZ長方形を深さスラブで切り取ってからU範囲を求め、回転時のUと深さの相関を維持する。
     * YはXZと独立なので、残ったU範囲とY範囲の原点に最も近い点で曲線との交差を判定できる。
     */
    public static boolean intersectsWorldBox(double minX, double minY, double minZ,
                                             double maxX, double maxY, double maxZ,
                                             double yawDegrees, double scale) {
        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
                || !Double.isFinite(yawDegrees) || !Double.isFinite(scale) || scale <= 0
                || minX > maxX || minY > maxY || minZ > maxZ) return false;
        double radians = Math.toRadians(yawDegrees % 360.0);
        double cos = Math.cos(radians), sin = Math.sin(radians);
        List<PlanePoint> polygon = List.of(
                project(minX, minZ, cos, sin), project(maxX, minZ, cos, sin),
                project(maxX, maxZ, cos, sin), project(minX, maxZ, cos, sin));
        for (PlanePoint point : polygon) {
            if (!Double.isFinite(point.u) || !Double.isFinite(point.depth)) return false;
        }
        polygon = clipDepth(polygon, -HALF_DEPTH, false);
        polygon = clipDepth(polygon, HALF_DEPTH, true);
        if (polygon.isEmpty()) return false;
        double minU = Double.POSITIVE_INFINITY, maxU = Double.NEGATIVE_INFINITY;
        for (PlanePoint point : polygon) {
            if (!Double.isFinite(point.u)) return false;
            minU = Math.min(minU, point.u);
            maxU = Math.max(maxU, point.u);
        }
        // 開閉の倍率は面の幅と高さへ適用し、接触を捉える深さは固定する。
        return intersects(minU / scale, maxU / scale, minY / scale, maxY / scale, -HALF_DEPTH, HALF_DEPTH);
    }

    private static PlanePoint project(double x, double z, double cos, double sin) {
        return new PlanePoint(x * cos + z * sin, -x * sin + z * cos);
    }

    /** Sutherland–Hodgman法で、深さの片側の境界を含む半平面へ切り取る。 */
    private static List<PlanePoint> clipDepth(List<PlanePoint> polygon, double boundary, boolean keepBelow) {
        if (polygon.isEmpty()) return polygon;
        List<PlanePoint> clipped = new ArrayList<>(6);
        PlanePoint previous = polygon.get(polygon.size() - 1);
        boolean previousInside = keepBelow ? previous.depth <= boundary : previous.depth >= boundary;
        for (PlanePoint current : polygon) {
            boolean currentInside = keepBelow ? current.depth <= boundary : current.depth >= boundary;
            if (currentInside != previousInside) {
                double t = (boundary - previous.depth) / (current.depth - previous.depth);
                clipped.add(new PlanePoint(previous.u + t * (current.u - previous.u), boundary));
            }
            if (currentInside) clipped.add(current);
            previous = current;
            previousInside = currentInside;
        }
        return clipped;
    }

    private record PlanePoint(double u, double depth) {}

    /** closesAtは接触を無効にする時刻。その後6tickかけて自然に閉じる。 */
    public static double scale(double now, long openedAt, long closesAt) {
        double opening = Math.max(0.0, Math.min(1.0, (now - openedAt) / FADE_TICKS));
        double closing = closesAt < 0 ? 1 : Math.max(0.0, Math.min(1.0, 1 - (now - closesAt) / FADE_TICKS));
        return Math.min(opening, closing);
    }
}
