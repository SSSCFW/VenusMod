package dev.ssscfw.venusmod.yamato;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 閻魔刀の保存一覧で共有する、Minecraftクラスに依存しない検査規則。 */
public final class YamatoTravelRules {
    public static final int MAX_ENTRIES = 2048;
    public static final int MAX_NAME_LENGTH = 96;
    public static final int DEFAULT_HISTORY_LIMIT = 1000;
    public static final int MAX_HISTORY_NAME_LENGTH = 32;
    public static final int DEFAULT_ACCESS = 0;
    public static final int DEFAULT_LIFETIME = 0;
    public static final int DEFAULT_SECONDS = 1;
    public static final int MAX_SECONDS = 180;
    public static final double DEFAULT_SIZE = 1.0D;
    public static final double MIN_SIZE = 0.5D;
    public static final double MAX_SIZE = 5.0D;

    private YamatoTravelRules() {}

    /** EditBoxと同じUTF-16長で検査し、意味が変わる切り詰めは行わない。 */
    public static String normalizeName(String value) {
        if (value == null) return null;
        String name = value.strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) return null;
        for (int offset = 0; offset < name.length();) {
            int point = name.codePointAt(offset);
            if (Character.isISOControl(point) || point == 0x2028 || point == 0x2029
                    || point >= Character.MIN_SURROGATE && point <= Character.MAX_SURROGATE) return null;
            offset += Character.charCount(point);
        }
        return name;
    }

    /** 履歴名は通信量を抑えて短く保ち、無名の開通には出発地点のブロック座標を付ける。 */
    public static String historyName(String value, double x, double y, double z) {
        String name = normalizeName(value);
        if (name == null) {
            name = String.format(Locale.ROOT, "出発地点 %.0f, %.0f, %.0f", Math.floor(x), Math.floor(y), Math.floor(z));
            if (name.length() > MAX_HISTORY_NAME_LENGTH) return "出発地点";
        }
        int end = Math.min(name.length(), MAX_HISTORY_NAME_LENGTH);
        if (end < name.length() && Character.isHighSurrogate(name.charAt(end - 1))) end--;
        return name.substring(0, end).stripTrailing();
    }

    /** UUID.fromStringが受理する短縮UUIDを拒否し、正規表記だけを許可する。 */
    public static UUID parseId(String value) {
        if (value == null || value.length() != 36) return null;
        try {
            UUID id = UUID.fromString(value);
            return id.toString().equalsIgnoreCase(value) ? id : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean validSettings(int access, int lifetime, int seconds) {
        return validSettings(access, lifetime, seconds, DEFAULT_SIZE);
    }

    public static boolean validSettings(int access, int lifetime, int seconds, double size) {
        return validAccess(access) && lifetime >= 0 && lifetime <= 2
                && seconds >= 1 && seconds <= MAX_SECONDS && validSize(size);
    }

    public static boolean validAccess(int access) { return access >= 0 && access <= 6; }

    public static boolean validSize(double size) {
        return Double.isFinite(size) && size >= MIN_SIZE && size <= MAX_SIZE;
    }

    public static boolean validSort(int sort) { return sort >= 0 && sort <= 4; }

    /** null親はルート。親も祖先も同じ所有者のフォルダでなければならない。 */
    public static boolean canUseParent(UUID movingId, UUID parentId,
                                       Map<UUID, UUID> parents, Set<UUID> folders) {
        if (movingId != null && !parents.containsKey(movingId)) return false;
        Set<UUID> visited = new HashSet<>();
        for (UUID cursor = parentId; cursor != null; cursor = parents.get(cursor)) {
            if (cursor.equals(movingId) || !parents.containsKey(cursor) || !folders.contains(cursor)
                    || !visited.add(cursor)) return false;
        }
        return true;
    }

    /** 自分と全子孫を求める。破損した保存データに循環があっても終了する。 */
    public static Set<UUID> descendants(UUID id, Map<UUID, UUID> parents) {
        if (id == null || !parents.containsKey(id)) return Set.of();
        Set<UUID> result = new LinkedHashSet<>();
        ArrayDeque<UUID> pending = new ArrayDeque<>();
        pending.add(id);
        while (!pending.isEmpty()) {
            UUID current = pending.removeFirst();
            if (!result.add(current)) continue;
            parents.forEach((child, parent) -> {
                if (current.equals(parent) && child != null && !result.contains(child)) pending.addLast(child);
            });
        }
        return result;
    }

    /** 永続化された全体順から、同じ親の隣接要素の添字だけを返す。 */
    public static int siblingTargetIndex(List<UUID> order, Map<UUID, UUID> parents, UUID id, int delta) {
        if (delta != -1 && delta != 1 || id == null || !parents.containsKey(id)) return -1;
        int current = order.indexOf(id);
        if (current < 0) return -1;
        UUID parent = parents.get(id);
        for (int next = current + delta; next >= 0 && next < order.size(); next += delta) {
            UUID candidate = order.get(next);
            if (parents.containsKey(candidate) && Objects.equals(parent, parents.get(candidate))) return next;
        }
        return -1;
    }
}
