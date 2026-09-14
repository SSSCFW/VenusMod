package dev.ssscfw.venusmod.world;

import java.util.function.IntBinaryOperator;

/** ワールドに依存しないポータル枠検査。四隅は不要、内寸2x3〜21x21。 */
public record PortalRectangle(int u, int v, int width, int height) {
    public static final int EMPTY = 0, PORTAL = 1, FRAME = 2, BLOCKED = 3;
    public static final int MAX = 21;

    private static boolean inside(int cell) {
        return cell == EMPTY || cell == PORTAL;
    }

    public static PortalRectangle find(IntBinaryOperator cells, int startU, int startV,
                                       int minV, int maxV) {
        if (startV < minV || startV >= maxV || !inside(cells.applyAsInt(startU, startV))) return null;
        int u = startU, v = startV;
        int steps = 0;
        while (v > minV && inside(cells.applyAsInt(u, v - 1))) {
            if (++steps > MAX) return null;
            v--;
        }
        steps = 0;
        while (inside(cells.applyAsInt(u - 1, v))) {
            if (++steps > MAX) return null;
            u--;
        }
        if (cells.applyAsInt(u - 1, v) != FRAME) return null;
        int width = 0;
        while (width <= MAX && inside(cells.applyAsInt(u + width, v))) {
            if (v == minV || cells.applyAsInt(u + width, v - 1) != FRAME) return null;
            width++;
        }
        if (width < 2 || width > MAX || cells.applyAsInt(u + width, v) != FRAME) return null;
        for (int height = 0; height <= MAX && v + height < maxV; height++) {
            boolean roof = true;
            for (int x = 0; x < width; x++) roof &= cells.applyAsInt(u + x, v + height) == FRAME;
            if (roof) return height >= 3 ? new PortalRectangle(u, v, width, height) : null;
            if (cells.applyAsInt(u - 1, v + height) != FRAME
                    || cells.applyAsInt(u + width, v + height) != FRAME) return null;
            for (int x = 0; x < width; x++) {
                if (!inside(cells.applyAsInt(u + x, v + height))) return null;
            }
        }
        return null;
    }
}
