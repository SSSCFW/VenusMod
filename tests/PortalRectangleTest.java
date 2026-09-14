import dev.ssscfw.venusmod.world.PortalRectangle;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntBinaryOperator;

public final class PortalRectangleTest {
    private static int checks;
    private static long key(int u, int v) { return ((long) u << 32) ^ (v & 0xffffffffL); }
    private static Map<Long, Integer> frame(int u, int v, int w, int h) {
        Map<Long, Integer> map = new HashMap<>();
        for (int x = 0; x < w; x++) {
            map.put(key(u + x, v - 1), PortalRectangle.FRAME);
            map.put(key(u + x, v + h), PortalRectangle.FRAME);
        }
        for (int y = 0; y < h; y++) {
            map.put(key(u - 1, v + y), PortalRectangle.FRAME);
            map.put(key(u + w, v + y), PortalRectangle.FRAME);
        }
        return map;
    }
    private static PortalRectangle find(Map<Long, Integer> map, int u, int v) {
        return PortalRectangle.find((x, y) -> map.getOrDefault(key(x, y), PortalRectangle.EMPTY), u, v, -64, 320);
    }
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for (int w = 1; w <= 23; w++) for (int h = 1; h <= 23; h++) {
            Map<Long, Integer> map = frame(-31, -40, w, h);
            boolean expected = w >= 2 && w <= 21 && h >= 3 && h <= 21;
            for (int[] point : new int[][]{{0, 0}, {w - 1, h - 1}, {w / 2, h / 2}}) {
                PortalRectangle r = find(map, -31 + point[0], -40 + point[1]);
                require((r != null) == expected, "size " + w + "x" + h);
                if (r != null) require(r.equals(new PortalRectangle(-31, -40, w, h)), "normalization");
            }
        }
        Map<Long, Integer> map = frame(0, 70, 2, 3);
        require(find(map, 0, 70) != null, "missing corners allowed");
        for (Long edge : map.keySet().toArray(Long[]::new)) {
            int cell = map.remove(edge);
            require(find(map, 0, 70) == null, "missing mandatory edge");
            map.put(edge, cell);
        }
        map.put(key(1, 71), PortalRectangle.BLOCKED);
        require(find(map, 0, 70) == null, "water/obstruction rejected");
        map.put(key(1, 71), PortalRectangle.PORTAL);
        require(find(map, 0, 70) != null, "existing portal cells accepted");
        require(find(new HashMap<>(), 0, 70) == null, "empty world bounded scan");
        require(find(frame(0, -63, 2, 3), 0, -63) != null, "bottom boundary valid");
        require(find(frame(0, -64, 2, 3), 0, -64) == null, "frame outside build height rejected");
        require(find(frame(0, 316, 2, 3), 0, 316) != null, "top boundary valid");
        require(find(frame(0, 317, 2, 3), 0, 317) == null, "roof outside build height rejected");
        System.out.println("PortalRectangleTest: " + checks + " checks passed");
    }
}
