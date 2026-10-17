package dev.ssscfw.venusmod.yamato;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** サーバーから独立した通過条件・寿命・再進入の規則。 */
public final class YamatoRiftRules {
    public static final int AFTER_FIRST = 0;
    public static final int FIXED = 1;
    public static final int UNLIMITED = 2;
    private YamatoRiftRules() {}

    public static boolean canEnter(int access, boolean owner, boolean player, boolean friendlyMob) {
        return switch (access) {
            case 0 -> owner && player;
            case 1 -> player;
            case 2 -> player || friendlyMob;
            case 3 -> true;
            default -> false;
        };
    }

    public static long closingTime(int lifetime, int seconds, long openedAt, long firstTransfer) {
        if (seconds < 1 || seconds > 180) throw new IllegalArgumentException("seconds must be 1..180");
        return switch (lifetime) {
            case AFTER_FIRST -> firstTransfer < 0 ? -1 : firstTransfer + seconds * 20L;
            case FIXED -> openedAt + seconds * 20L;
            case UNLIMITED -> -1;
            default -> throw new IllegalArgumentException("unknown lifetime");
        };
    }

    /** tick時間では解除しない。到着した対象ごと・到着端ごとに一度離れるまで保持する。 */
    public static final class ArrivalLocks {
        private final Map<UUID, Set<UUID>> atEndpoint = new HashMap<>();
        public void arrived(UUID entity, UUID endpoint) {
            atEndpoint.computeIfAbsent(endpoint, key -> new HashSet<>()).add(entity);
        }
        public boolean blocked(UUID entity, UUID endpoint) {
            Set<UUID> locked = atEndpoint.get(endpoint);
            return locked != null && locked.contains(entity);
        }
        public boolean observe(UUID entity, UUID endpoint, boolean touching) {
            Set<UUID> locked = atEndpoint.get(endpoint);
            if (touching || locked == null) return false;
            boolean changed = locked.remove(entity);
            if (locked.isEmpty()) atEndpoint.remove(endpoint);
            return changed;
        }
        public Set<UUID> entities(UUID endpoint) {
            return Set.copyOf(atEndpoint.getOrDefault(endpoint, Set.of()));
        }
        /** 回収・消滅した対象は、重なった端に残るロックもまとめて除去する。 */
        public boolean removeEntity(UUID entity) {
            boolean changed = false;
            var iterator = atEndpoint.values().iterator();
            while (iterator.hasNext()) {
                Set<UUID> locked = iterator.next();
                changed |= locked.remove(entity);
                if (locked.isEmpty()) iterator.remove();
            }
            return changed;
        }
        public void removeEndpoint(UUID endpoint) { atEndpoint.remove(endpoint); }
    }
}
